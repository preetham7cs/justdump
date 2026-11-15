-- Milestone 3R: owner-scoped chunks plus an optional pgvector comparison.
-- Apply after 0001 and 0002 in Supabase Dashboard -> SQL Editor.
-- This migration stores only material explicitly selected for indexing; it
-- never automatically indexes the owner's existing vault.

create extension if not exists vector with schema extensions;

create table if not exists public.retrieval_chunks (
    owner_id uuid not null references auth.users(id) on delete cascade,
    client_note_id uuid not null,
    chunking_version text not null,
    chunk_index integer not null check (chunk_index >= 0),
    source_content_sha256 text not null check (source_content_sha256 ~ '^[0-9a-f]{64}$'),
    start_offset integer not null check (start_offset >= 0),
    end_offset integer not null check (end_offset > start_offset),
    content text not null check (char_length(content) > 0),
    search_vector tsvector generated always as (to_tsvector('english', content)) stored,
    primary key (owner_id, client_note_id, chunking_version, chunk_index),
    foreign key (owner_id, client_note_id)
        references public.text_notes(owner_id, client_note_id) on delete cascade
);

create index if not exists retrieval_chunks_search_vector_idx
    on public.retrieval_chunks using gin(search_vector);

create table if not exists public.retrieval_embeddings (
    owner_id uuid not null,
    client_note_id uuid not null,
    chunking_version text not null,
    chunk_index integer not null,
    embedding_model text not null,
    embedding extensions.vector(1536) not null,
    primary key (owner_id, client_note_id, chunking_version, chunk_index, embedding_model),
    foreign key (owner_id, client_note_id, chunking_version, chunk_index)
        references public.retrieval_chunks(owner_id, client_note_id, chunking_version, chunk_index)
        on delete cascade
);

alter table public.retrieval_chunks enable row level security;
alter table public.retrieval_embeddings enable row level security;
revoke all on public.retrieval_chunks, public.retrieval_embeddings from anon;
grant select, insert, update, delete on public.retrieval_chunks, public.retrieval_embeddings to authenticated;

drop policy if exists "owner manages own retrieval chunks" on public.retrieval_chunks;
create policy "owner manages own retrieval chunks" on public.retrieval_chunks
for all to authenticated using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);
drop policy if exists "owner manages own retrieval embeddings" on public.retrieval_embeddings;
create policy "owner manages own retrieval embeddings" on public.retrieval_embeddings
for all to authenticated using ((select auth.uid()) = owner_id)
with check ((select auth.uid()) = owner_id);

create or replace function public.replace_retrieval_chunks(
    p_client_note_id uuid,
    p_chunking_version text,
    p_source_content_sha256 text,
    p_chunks jsonb
)
returns integer
language plpgsql security invoker set search_path = public
as $$
declare
    inserted_count integer;
begin
    if auth.uid() is null then raise exception 'authenticated user required' using errcode = '28000'; end if;
    if not exists (
        select 1 from public.text_notes
        where owner_id = auth.uid() and client_note_id = p_client_note_id
          and content_sha256 = p_source_content_sha256
    ) then
        raise exception 'source note is missing or changed' using errcode = '22023';
    end if;
    delete from public.retrieval_chunks
    where owner_id = auth.uid() and client_note_id = p_client_note_id
      and chunking_version = p_chunking_version;
    insert into public.retrieval_chunks (
        owner_id, client_note_id, chunking_version, chunk_index,
        source_content_sha256, start_offset, end_offset, content
    )
    select auth.uid(), p_client_note_id, p_chunking_version, c.chunk_index,
           p_source_content_sha256, c.start_offset, c.end_offset, c.content
    from jsonb_to_recordset(p_chunks) as c(
        chunk_index integer, start_offset integer, end_offset integer, content text
    );
    get diagnostics inserted_count = row_count;
    return inserted_count;
end;
$$;

create or replace function public.upsert_retrieval_embeddings(
    p_client_note_id uuid,
    p_chunking_version text,
    p_embedding_model text,
    p_embeddings jsonb
)
returns integer
language plpgsql security invoker set search_path = public
as $$
declare inserted_count integer;
begin
    if auth.uid() is null then raise exception 'authenticated user required' using errcode = '28000'; end if;
    insert into public.retrieval_embeddings (
        owner_id, client_note_id, chunking_version, chunk_index, embedding_model, embedding
    )
    select auth.uid(), p_client_note_id, p_chunking_version, e.chunk_index,
           p_embedding_model, e.embedding::extensions.vector
    from jsonb_to_recordset(p_embeddings) as e(chunk_index integer, embedding text)
    join public.retrieval_chunks c on c.owner_id = auth.uid()
      and c.client_note_id = p_client_note_id and c.chunking_version = p_chunking_version
      and c.chunk_index = e.chunk_index
    on conflict (owner_id, client_note_id, chunking_version, chunk_index, embedding_model)
    do update set embedding = excluded.embedding;
    get diagnostics inserted_count = row_count;
    return inserted_count;
end;
$$;

create or replace function public.search_retrieval_fts(p_query text, p_limit integer default 3)
returns table(client_note_id uuid, chunk_index integer, start_offset integer, end_offset integer, content text, score real)
language sql stable security invoker set search_path = public
as $$
    select c.client_note_id, c.chunk_index, c.start_offset, c.end_offset, c.content,
           ts_rank_cd(c.search_vector, websearch_to_tsquery('english', p_query)) as score
    from public.retrieval_chunks c
    where c.owner_id = auth.uid()
      and c.search_vector @@ websearch_to_tsquery('english', p_query)
    order by score desc, c.client_note_id, c.chunk_index
    limit greatest(1, least(p_limit, 20));
$$;

create or replace function public.search_retrieval_vector(
    p_embedding_model text, p_query_embedding extensions.vector, p_limit integer default 3
)
returns table(client_note_id uuid, chunk_index integer, start_offset integer, end_offset integer, content text, score real)
language sql stable security invoker set search_path = public
as $$
    select c.client_note_id, c.chunk_index, c.start_offset, c.end_offset, c.content,
           (1 - (e.embedding OPERATOR(extensions.<=>) p_query_embedding))::real as score
    from public.retrieval_embeddings e
    join public.retrieval_chunks c using (owner_id, client_note_id, chunking_version, chunk_index)
    where e.owner_id = auth.uid() and e.embedding_model = p_embedding_model
    order by e.embedding OPERATOR(extensions.<=>) p_query_embedding, c.client_note_id, c.chunk_index
    limit greatest(1, least(p_limit, 20));
$$;

revoke all on function public.replace_retrieval_chunks(uuid, text, text, jsonb) from public;
revoke all on function public.upsert_retrieval_embeddings(uuid, text, text, jsonb) from public;
revoke all on function public.search_retrieval_fts(text, integer) from public;
revoke all on function public.search_retrieval_vector(text, extensions.vector, integer) from public;
grant execute on function public.replace_retrieval_chunks(uuid, text, text, jsonb) to authenticated;
grant execute on function public.upsert_retrieval_embeddings(uuid, text, text, jsonb) to authenticated;
grant execute on function public.search_retrieval_fts(text, integer) to authenticated;
grant execute on function public.search_retrieval_vector(text, extensions.vector, integer) to authenticated;
