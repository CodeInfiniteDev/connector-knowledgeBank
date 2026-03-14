-- Example DDL for the auth_user JSON view table.
--
-- One row per midPoint user, containing all external resource accounts in a JSON column.
--
-- Adjust types / keywords for your database (PostgreSQL, SQL Server, etc.).

create table auth_user (
    user_login_id    varchar(128) primary key,
    user_account_id  varchar(256),   -- optional main account id in some primary system
    user_oid         varchar(64),    -- optional midPoint user OID
    accounts         text,           -- JSON with [{ "resource": "...", "accountId": "..." }, ...]
    updated_at       timestamp       -- used for troubleshooting / optional sync
);
