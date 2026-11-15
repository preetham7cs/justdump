-- Owner-scoped derived knowledge and durable processing state for local v1.
-- Apply after 0001 through 0004 in Supabase SQL Editor.

create table if not exists public.knowledge_documents (
    owner_id uuid not null references auth.users(id) on delete cascade,
    client_item_id uuid not null,
    source_kind text not null check (source_kind in ('TEXT', 'LINK', 'IMAGE')),
    content text not null check (char_length(content) > 0),
    content_sha256 text not null check (content_sha256 ~ '^[0-9a-f]{64}$'),
    processing_version text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (owner_id, client_item_id)
);

create table if not exists public.knowledge_chunks (
    owner_id uuid not null,
    client_item_id uuid not null,
    chunking_version text not null,
    chunk_index integer not null check (chunk_index >= 0),
    source_content_sha256 text not null check (source_content_sha256 ~ '^[0-9a-f]{64}$'),
    start_offset integer not null check (start_offset >= 0),
    end_offset integer not null check (end_offset > start_offset),
    content text not null check (char_length(content) > 0),
    search_vector tsvector generated always as (to_tsvector('english', content)) stored,
    primary key (owner_id, client_item_id, chunking_version, chunk_index),
    foreign key (owner_id, client_item_id) references public.knowledge_documents(owner_id, client_item_id) on delete cascade
);
create index if not exists knowledge_chunks_search_vector_idx on public.knowledge_chunks using gin(search_vector);

create table if not exists public.knowledge_embeddings (
    owner_id uuid not null,
    client_item_id uuid not null,
    chunking_version text not null,
    chunk_index integer not null,
    embedding_model text not null,
    embedding extensions.vector(1536) not null,
    primary key (owner_id, client_item_id, chunking_version, chunk_index, embedding_model),
    foreign key (owner_id, client_item_id, chunking_version, chunk_index)
      references public.knowledge_chunks(owner_id, client_item_id, chunking_version, chunk_index) on delete cascade
);

create table if not exists public.processing_jobs (
    owner_id uuid not null references auth.users(id) on delete cascade,
    client_item_id uuid not null,
    processing_version text not null,
    status text not null default 'PENDING' check (status in ('PENDING', 'RUNNING', 'READY', 'FAILED')),
    attempts integer not null default 0 check (attempts >= 0 and attempts <= 3),
    last_error text,
    updated_at timestamptz not null default now(),
    primary key (owner_id, client_item_id, processing_version)
);

alter table public.knowledge_documents enable row level security;
alter table public.knowledge_chunks enable row level security;
alter table public.knowledge_embeddings enable row level security;
alter table public.processing_jobs enable row level security;
revoke all on public.knowledge_documents, public.knowledge_chunks, public.knowledge_embeddings, public.processing_jobs from anon;
grant select, insert, update, delete on public.knowledge_documents, public.knowledge_chunks, public.knowledge_embeddings, public.processing_jobs to authenticated;

drop policy if exists "owner manages own knowledge documents" on public.knowledge_documents;
drop policy if exists "owner manages own knowledge chunks" on public.knowledge_chunks;
drop policy if exists "owner manages own knowledge embeddings" on public.knowledge_embeddings;
drop policy if exists "owner manages own processing jobs" on public.processing_jobs;
create policy "owner manages own knowledge documents" on public.knowledge_documents for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
create policy "owner manages own knowledge chunks" on public.knowledge_chunks for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
create policy "owner manages own knowledge embeddings" on public.knowledge_embeddings for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);
create policy "owner manages own processing jobs" on public.processing_jobs for all to authenticated using ((select auth.uid()) = owner_id) with check ((select auth.uid()) = owner_id);

create or replace function public.upsert_knowledge_document(
    p_client_item_id uuid, p_source_kind text, p_content text, p_content_sha256 text, p_processing_version text
) returns public.knowledge_documents
language plpgsql security invoker set search_path = public
as $$
declare result public.knowledge_documents;
begin
  if auth.uid() is null then raise exception 'authenticated user required' using errcode = '28000'; end if;
  if p_source_kind not in ('TEXT', 'LINK', 'IMAGE') or char_length(trim(p_content)) = 0 then raise exception 'invalid knowledge document' using errcode = '22023'; end if;
  insert into public.knowledge_documents(owner_id, client_item_id, source_kind, content, content_sha256, processing_version)
  values(auth.uid(), p_client_item_id, p_source_kind, p_content, p_content_sha256, p_processing_version)
  on conflict (owner_id, client_item_id) do update set
    source_kind = excluded.source_kind, content = excluded.content, content_sha256 = excluded.content_sha256,
    processing_version = excluded.processing_version, updated_at = now()
  returning * into result;
  return result;
end;
$$;

create or replace function public.replace_knowledge_chunks(
    p_client_item_id uuid, p_chunking_version text, p_source_content_sha256 text, p_chunks jsonb
) returns integer
language plpgsql security invoker set search_path = public
as $$
declare count_inserted integer;
begin
  if not exists (select 1 from public.knowledge_documents where owner_id = auth.uid() and client_item_id = p_client_item_id and content_sha256 = p_source_content_sha256) then
    raise exception 'knowledge source is missing or changed' using errcode = '22023';
  end if;
  delete from public.knowledge_chunks where owner_id = auth.uid() and client_item_id = p_client_item_id and chunking_version = p_chunking_version;
  insert into public.knowledge_chunks(owner_id, client_item_id, chunking_version, chunk_index, source_content_sha256, start_offset, end_offset, content)
  select auth.uid(), p_client_item_id, p_chunking_version, c.chunk_index, p_source_content_sha256, c.start_offset, c.end_offset, c.content
  from jsonb_to_recordset(p_chunks) as c(chunk_index integer, start_offset integer, end_offset integer, content text);
  get diagnostics count_inserted = row_count;
  return count_inserted;
end;
$$;

create or replace function public.upsert_knowledge_embeddings(
    p_client_item_id uuid, p_chunking_version text, p_embedding_model text, p_embeddings jsonb
) returns integer
language plpgsql security invoker set search_path = public
as $$
declare count_inserted integer;
begin
  insert into public.knowledge_embeddings(owner_id, client_item_id, chunking_version, chunk_index, embedding_model, embedding)
  select auth.uid(), p_client_item_id, p_chunking_version, e.chunk_index, p_embedding_model, e.embedding::extensions.vector
  from jsonb_to_recordset(p_embeddings) as e(chunk_index integer, embedding text)
  join public.knowledge_chunks c on c.owner_id = auth.uid() and c.client_item_id = p_client_item_id and c.chunking_version = p_chunking_version and c.chunk_index = e.chunk_index
  on conflict (owner_id, client_item_id, chunking_version, chunk_index, embedding_model) do update set embedding = excluded.embedding;
  get diagnostics count_inserted = row_count;
  return count_inserted;
end;
$$;

create or replace function public.search_knowledge_fts(p_query text, p_limit integer default 5)
returns table(client_item_id uuid, source_kind text, chunk_index integer, start_offset integer, end_offset integer, content text, score real)
language sql stable security invoker set search_path = public
as $$
  select c.client_item_id, d.source_kind, c.chunk_index, c.start_offset, c.end_offset, c.content,
         ts_rank_cd(c.search_vector, websearch_to_tsquery('english', p_query))::real
  from public.knowledge_chunks c join public.knowledge_documents d using(owner_id, client_item_id)
  where c.owner_id = auth.uid() and c.search_vector @@ websearch_to_tsquery('english', p_query)
  order by 7 desc, c.client_item_id, c.chunk_index limit greatest(1, least(p_limit, 10));
$$;

create or replace function public.search_knowledge_vector(p_embedding_model text, p_query_embedding extensions.vector, p_limit integer default 5)
returns table(client_item_id uuid, source_kind text, chunk_index integer, start_offset integer, end_offset integer, content text, score real)
language sql stable security invoker set search_path = public
as $$
  select c.client_item_id, d.source_kind, c.chunk_index, c.start_offset, c.end_offset, c.content,
         (1 - (e.embedding OPERATOR(extensions.<=>) p_query_embedding))::real
  from public.knowledge_embeddings e join public.knowledge_chunks c using(owner_id, client_item_id, chunking_version, chunk_index)
  join public.knowledge_documents d using(owner_id, client_item_id)
  where e.owner_id = auth.uid() and e.embedding_model = p_embedding_model
  order by e.embedding OPERATOR(extensions.<=>) p_query_embedding, c.client_item_id, c.chunk_index limit greatest(1, least(p_limit, 10));
$$;

create or replace function public.set_vault_processing_status(p_client_item_id uuid, p_status text, p_error text default null)
returns void language plpgsql security invoker set search_path = public as $$
begin
  update public.vault_items set processing_status = p_status, processing_error = p_error where owner_id = auth.uid() and client_item_id = p_client_item_id;
end;
$$;

grant execute on function public.upsert_knowledge_document(uuid, text, text, text, text), public.replace_knowledge_chunks(uuid, text, text, jsonb), public.upsert_knowledge_embeddings(uuid, text, text, jsonb), public.search_knowledge_fts(text, integer), public.search_knowledge_vector(text, extensions.vector, integer), public.set_vault_processing_status(uuid, text, text) to authenticated;
