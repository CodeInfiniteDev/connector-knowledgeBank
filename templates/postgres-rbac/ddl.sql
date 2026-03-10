-- Template schema for PostgreSQL.
-- This is only an example. You should adapt it to your real tables.

create table if not exists org_unit (
  id           bigserial primary key,
  name         text not null unique,
  parent_id    bigint references org_unit(id),
  updated_at   timestamptz not null default now()
);

create table if not exists roles (
  id           bigserial primary key,
  name         text not null unique,
  description  text,
  updated_at   timestamptz not null default now()
);

create table if not exists users (
  id             bigserial primary key,
  username       text not null unique,
  given_name     text,
  family_name    text,
  email          text,
  org_unit_id    bigint references org_unit(id),
  disabled       boolean not null default false,
  password_hash  text,
  updated_at     timestamptz not null default now()
);

create table if not exists user_role (
  user_id     bigint not null references users(id) on delete cascade,
  role_id     bigint not null references roles(id) on delete cascade,
  updated_at  timestamptz not null default now(),
  primary key (user_id, role_id)
);

create index if not exists idx_users_updated_at on users(updated_at, id);
create index if not exists idx_roles_updated_at on roles(updated_at, id);
create index if not exists idx_org_unit_updated_at on org_unit(updated_at, id);

