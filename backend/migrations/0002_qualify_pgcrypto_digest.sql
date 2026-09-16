-- Milestone 3A corrective migration.
-- Supabase installs pgcrypto in the extensions schema. Keep the function's
-- restricted search_path and qualify the cryptographic function explicitly.
-- This replaces only the RPC function; it does not alter or delete text notes.

create or replace function public.upsert_text_note(
    p_client_note_id uuid,
    p_content text,
    p_created_at timestamptz,
    p_updated_at timestamptz
)
returns public.text_notes
language plpgsql
security invoker
set search_path = public
as $$
declare
    existing_note public.text_notes;
    content_digest text;
begin
    if auth.uid() is null then
        raise exception 'authenticated user required' using errcode = '28000';
    end if;

    if p_content is null or char_length(trim(p_content)) = 0 then
        raise exception 'text content is required' using errcode = '22023';
    end if;

    if char_length(p_content) > 100000 then
        raise exception 'text content exceeds limit' using errcode = '22023';
    end if;

    if p_updated_at < p_created_at then
        raise exception 'updated_at must not precede created_at' using errcode = '22023';
    end if;

    content_digest := encode(extensions.digest(p_content, 'sha256'), 'hex');

    select *
    into existing_note
    from public.text_notes
    where owner_id = auth.uid()
      and client_note_id = p_client_note_id;

    if found then
        if existing_note.content_sha256 <> content_digest then
            raise exception 'client_note_id already exists with different immutable content'
                using errcode = 'P0001';
        end if;
        return existing_note;
    end if;

    insert into public.text_notes (
        owner_id,
        client_note_id,
        content,
        content_sha256,
        created_at,
        updated_at
    ) values (
        auth.uid(),
        p_client_note_id,
        p_content,
        content_digest,
        p_created_at,
        p_updated_at
    )
    returning * into existing_note;

    return existing_note;
end;
$$;

revoke all on function public.upsert_text_note(uuid, text, timestamptz, timestamptz) from public;
grant execute on function public.upsert_text_note(uuid, text, timestamptz, timestamptz) to authenticated;
