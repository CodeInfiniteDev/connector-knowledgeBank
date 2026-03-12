# Auth-User JSON Template (midPoint ScriptedSQL, Groovy)

This template shows a minimal ScriptedSQL connector implementation for an `auth_user` table where:

- there is **one row per midPoint user**
- a `accounts_json` column contains **all external resource accounts** for that user

MidPoint focuses on:

- building `accountsJson` (string) from the `user` + its projections (linkRef)
- pushing that as a normal attribute to this ScriptedSQL resource

The connector scripts in this folder:

- map midPoint account attributes to the `auth_user` table
- do **no complex logic** for accounts; they expect JSON to be ready

## Files

- `BaseScript.groovy`  
  Centralizes table and column names for `auth_user`.

- `SchemaScript.groovy`  
  Exposes a single `__ACCOUNT__` object class with attributes:
  - `loginId`
  - `displayName`
  - `email`
  - `accountsJson`
  - `lastModified`

- `CreateScript.groovy`  
  Inserts a row into `auth_user`.  
  Assumes:
  - `__NAME__` and `__UID__` both equal `login_id`
  - `accountsJson` is already a JSON string built in midPoint

- `UpdateScript.groovy`  
  Updates `display_name`, `email`, `accounts_json` and always refreshes `last_modified`.

- `DeleteScript.groovy`  
  Deletes by `login_id` (`__UID__`).

- `SearchScript.groovy`  
  Minimal reconciliation: selects all rows from `auth_user` and returns them as accounts.  
  Filtering is not implemented yet; midPoint can filter after reading.

- `TestScript.groovy`  
  Simple connectivity check that verifies the `auth_user` table exists.

- `ddl.sql`  
  Example PostgreSQL schema for the `auth_user` table.

- `ScriptedSQL-auth-user-json.xml`  
  Starter midPoint resource snippet that points the ScriptedSQL connector at this folder.

## How to Use This Template

1. Create the table using `ddl.sql` (or adapt it to your DB).
2. Copy these scripts to a folder on the midPoint server (e.g. `/opt/midpoint/var/scripts/auth-user-json/`).
3. Import `ScriptedSQL-auth-user-json.xml` into midPoint and adjust:
   - JDBC driver, URL, user, password
   - `scriptRoots` path
4. In `schemaHandling` and outbound mappings:
   - map `__NAME__` / `__UID__` to `loginId`
   - compute `accountsJson` from the user and its projections (e.g. Groovy/ECMAScript expression)
   - send `accountsJson` as a simple string attribute to this ScriptedSQL resource

This keeps the connector logic simple and lets midPoint own the complex logic for
looking at all projections and serializing them into one JSON column.

