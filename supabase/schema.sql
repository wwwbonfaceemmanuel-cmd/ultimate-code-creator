-- AI Code Studio: run this once in the Supabase SQL editor.
create extension if not exists pgcrypto;

create table if not exists public.projects (
  id          uuid primary key default gen_random_uuid(),
  owner_id    uuid references auth.users (id) on delete cascade, -- reserved for per-user auth
  name        text not null,
  description text not null default '',
  language    text not null,
  prompt      text not null default '',
  files       jsonb not null default '[]'::jsonb,  -- [{ "path": "...", "content": "..." }]
  file_count  integer not null default 0,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

create index if not exists projects_updated_at_idx on public.projects (updated_at desc);
create index if not exists projects_owner_idx on public.projects (owner_id);

-- Row Level Security is on with no policies: the browser (anon key) can read
-- nothing. Only the backend, using the service-role key, can access the table.
alter table public.projects enable row level security;
