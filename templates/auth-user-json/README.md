# Auth-User JSON Template (midPoint ScriptedSQL, Groovy)

This template shows a minimal ScriptedSQL connector implementation for an `auth_user` table where:

- there is **one row per midPoint user**
- an `accounts` column contains **all external resource accounts** for that user as JSON

MidPoint focuses on:

- building `accounts` (string) from the `user` + its projections (`linkRef`)
- pushing that as a normal attribute to this ScriptedSQL resource

The connector scripts in this folder:

- map midPoint attributes to the `auth_user` table
- do **no complex logic** for accounts; they expect the JSON string to be ready

## Files

- `BaseScript.groovy`  
  Centralizes table and column names for `auth_user`.

- `SchemaScript.groovy`  
  Exposes a single `__ACCOUNT__` object class with attributes:
  - `loginId`        (maps to `user_login_id`, used for `__NAME__`/`__UID__`)
  - `userAccountId`  (optional main account id in a primary system)
  - `userOid`        (optional midPoint `user` OID)
  - `accounts`       (JSON string with all external accounts)
  - `updatedAt`

- `CreateScript.groovy`  
  Inserts a row into `auth_user`.  
  Assumes:
  - `__NAME__` and `__UID__` both equal `user_login_id`
  - `accounts` is already a JSON string built in midPoint

- `UpdateScript.groovy`  
  Updates `user_account_id`, `user_oid`, `accounts` and always refreshes `updated_at`.

- `DeleteScript.groovy`  
  Deletes by `user_login_id` (`__UID__`).

- `SearchScript.groovy`  
  Minimal reconciliation: selects all rows from `auth_user` and returns them as accounts.  
  Filtering is not implemented yet; midPoint can filter after reading.

- `TestScript.groovy`  
  Simple connectivity check that verifies the `auth_user` table exists.

- `ddl.sql`  
  Example schema for the `auth_user` table (adapt types/keywords for your DB).

- `ScriptedSQL-auth-user-json.xml`  
  Starter midPoint resource snippet that points the ScriptedSQL connector at this folder.

## How to Use This Template

1. Create the table using `ddl.sql` (or adapt it to your DB).
2. Copy these scripts to a folder on the midPoint server (e.g. `/opt/midpoint/var/scripts/auth-user-json/`).
3. Import `ScriptedSQL-auth-user-json.xml` into midPoint and adjust:
   - JDBC driver, URL, user, password
   - `scriptRoots` path
4. In `schemaHandling` and outbound mappings:
   - map `__NAME__` / `__UID__` to `loginId` (stored as `user_login_id`)
   - optionally map a primary account id to `userAccountId`
   - optionally map the midPoint `user` OID to `userOid`
   - compute `accounts` from the user and its projections (e.g. Groovy/ECMAScript expression)
   - send `accounts` as a simple string attribute to this ScriptedSQL resource

This keeps the connector logic simple and lets midPoint own the complex logic for
looking at all projections and serializing them into one JSON column.
