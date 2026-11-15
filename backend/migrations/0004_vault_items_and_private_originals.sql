-- v1 capture storage: owner-scoped metadata plus private image originals.
-- Apply after 0001 through 0003 in Supabase Dashboard -> SQL Editor.
-- Storage objects are accessed only through its API; the storage schema itself
-- is not modified beyond bucket configuration and RLS policies.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'justdump-originals', 'justdump-originals', false, 10485760,
    array['image/jpeg', 'image/png', 'image/webp', 'image/heic']
)
on conflict (id) do update set
    public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create table if not exists public.vault_items (
    owner_id uuid not null references auth.users(id) on delete cascade,
    client_item_id uuid not null,
    kind text not null check (kind in ('IMAGE', 'LINK')),
    source_url text,
    original_object_path text,
    mime_type text,
    content_sha256 text not null check (content_sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz not null,
    updated_at timestamptz not null check (updated_at >= created_at),
    processing_status text not null default 'PENDING' check (processing_status in ('PENDING', 'READY', 'FAILED')),
    processing_error text,
    cloud_created_at timestamptz not null default now(),
    primary key (owner_id, client_item_id),
    check (
        (kind = 'IMAGE' and original_object_path is not null and mime_type like 'image/%' and source_url is null)
        or (kind = 'LINK' and source_url ~ '^https?://' and original_object_path is null and mime_type = 'text/uri-list')
    )
);

alter table public.vault_items enable row level security;
revoke all on public.vault_items from anon;
grant select, insert on public.vault_items to authenticated;

drop policy if exists "owner reads own vault items" on public.vault_items;
create policy "owner reads own vault items" on public.vault_items
for select to authenticated using ((select auth.uid()) = owner_id);

drop policy if exists "owner inserts own vault items" on public.vault_items;
create policy "owner inserts own vault items" on public.vault_items
for insert to authenticated with check ((select auth.uid()) = owner_id);

drop policy if exists "owner inserts private originals" on storage.objects;
create policy "owner inserts private originals" on storage.objects
for insert to authenticated with check (
    bucket_id = 'justdump-originals'
    and owner_id = (select auth.uid()::text)
    and (storage.foldername(name))[1] = (select auth.uid()::text)
);

drop policy if exists "owner reads private originals" on storage.objects;
create policy "owner reads private originals" on storage.objects
for select to authenticated using (
    bucket_id = 'justdump-originals'
    and owner_id = (select auth.uid()::text)
);

drop policy if exists "owner deletes private originals" on storage.objects;
create policy "owner deletes private originals" on storage.objects
for delete to authenticated using (
    bucket_id = 'justdump-originals'
    and owner_id = (select auth.uid()::text)
);

create or replace function public.upsert_vault_item(
    p_client_item_id uuid,
    p_kind text,
    p_source_url text,
    p_original_object_path text,
    p_mime_type text,
    p_content_sha256 text,
    p_created_at timestamptz,
    p_updated_at timestamptz
)
returns public.vault_items
language plpgsql security invoker set search_path = public
as $$
declare existing_item public.vault_items;
begin
    if auth.uid() is null then raise exception 'authenticated user required' using errcode = '28000'; end if;
    if p_kind not in ('IMAGE', 'LINK') then raise exception 'unsupported item kind' using errcode = '22023'; end if;
    if p_updated_at < p_created_at then raise exception 'updated_at must not precede created_at' using errcode = '22023'; end if;
    if p_kind = 'IMAGE' and (p_original_object_path is null or p_mime_type not like 'image/%') then
        raise exception 'image original and image mime type are required' using errcode = '22023';
    end if;
    if p_kind = 'LINK' and (p_source_url is null or p_source_url !~ '^https?://') then
        raise exception 'public http or https URL is required' using errcode = '22023';
    end if;
    select * into existing_item from public.vault_items
      where owner_id = auth.uid() and client_item_id = p_client_item_id;
    if found then
        if existing_item.content_sha256 <> p_content_sha256 then
            raise exception 'client_item_id already exists with different immutable content' using errcode = 'P0001';
        end if;
        return existing_item;
    end if;
    insert into public.vault_items (
        owner_id, client_item_id, kind, source_url, original_object_path, mime_type,
        content_sha256, created_at, updated_at
    ) values (
        auth.uid(), p_client_item_id, p_kind, p_source_url, p_original_object_path, p_mime_type,
        p_content_sha256, p_created_at, p_updated_at
    ) returning * into existing_item;
    return existing_item;
end;
$$;

revoke all on function public.upsert_vault_item(uuid, text, text, text, text, text, timestamptz, timestamptz) from public;
grant execute on function public.upsert_vault_item(uuid, text, text, text, text, text, timestamptz, timestamptz) to authenticated;
