-- Example PostgreSQL DDL for the auth_user JSON view table.
--
-- One row per midPoint user, containing all external resource accounts in a JSON column.

create table auth_user (
    login_id         varchar(128) primary key,
    display_name     varchar(256),
    email            varchar(256),
    accounts_json    text,        -- JSON with [{ "resource": "...", "accountId": "..." }, ...]
    last_modified    timestamp    -- used for troubleshooting / optional sync
);

