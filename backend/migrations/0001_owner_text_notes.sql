-- Milestone 3A: owner-scoped, immutable text notes.
-- Apply this script in Supabase Dashboard -> SQL Editor as the project owner.
-- It intentionally does not create a service credential or grant anonymous access.

create extension if not exists pgcrypto;

create table if not exists public.text_notes (
    owner_id uuid not null references auth.users(id) on delete cascade,
    client_note_id uuid not null,
    content text not null check (char_length(content) between 1 and 100000),
    content_sha256 text not null check (content_sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz not null,
    updated_at timestamptz not null check (updated_at >= created_at),
    cloud_created_at timestamptz not null default now(),
    primary key (owner_id, client_note_id)
);

alter table public.text_notes enable row level security;

revoke all on table public.text_notes from anon;
grant select, insert on table public.text_notes to authenticated;

drop policy if exists "owner reads own text notes" on public.text_notes;
create policy "owner reads own text notes"
on public.text_notes
for select
to authenticated
using ((select auth.uid()) = owner_id);

drop policy if exists "owner inserts own text notes" on public.text_notes;
create policy "owner inserts own text notes"
on public.text_notes
for insert
to authenticated
with check ((select auth.uid()) = owner_id);

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

    content_digest := encode(digest(p_content, 'sha256'), 'hex');

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
