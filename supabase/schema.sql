-- AI Code Studio: run this in the Supabase SQL editor.
-- It is safe to run again after an update; existing data is kept.
create extension if not exists pgcrypto;

create table if not exists public.projects (
  id          uuid primary key default gen_random_uuid(),
  owner_id    uuid references auth.users (id) on delete cascade, -- the signed-in user who owns the project
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

-- One row per AI request, used to enforce per-user hourly and daily limits.
create table if not exists public.ai_usage (
  id         bigint generated always as identity primary key,
  user_id    uuid references auth.users (id) on delete cascade,
  kind       text not null,
  created_at timestamptz not null default now()
);

create index if not exists ai_usage_user_time_idx on public.ai_usage (user_id, created_at desc);

alter table public.ai_usage enable row level security;
