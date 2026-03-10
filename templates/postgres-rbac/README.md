# PostgreSQL RBAC Template (midPoint ScriptedSQL, Groovy)

This folder is a copy-and-modify template for writing Groovy scripts for the ScriptedSQL connector when you have:

- `Users` (accounts)
- `Roles` (entitlements / groups)
- complex many-to-many membership (`user_role` join table)
- optional `OrgUnit` table (organization tree) referenced by users

It focuses on common midPoint capabilities:

- **Account CRUD**
- **Role/entitlement CRUD**
- **Association** (account <-> role membership)
- **Reference** (account -> org unit reference using FK)
- **Password sync** (`__PASSWORD__`)
- **Activation sync** (`__ENABLE__` mapped to `disabled`/`enabled`)
- **Live sync** (basic token-based sync using `updated_at`)

If you only need a simple “one table = one object class” mapping, the main example in [`scriptedsql/`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql) is smaller.

---

## 1. Example PostgreSQL schema (DDL)

See: [`ddl.sql`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/templates/postgres-rbac/ddl.sql)

Key design choices:

- `__UID__` is the DB primary key (`bigint`).
- `__NAME__` is the business identifier (`username`, `role_name`, `org_unit_name`).
- User-role membership is stored in `user_role` (join table).
- `updated_at` is used as the source for sync tokens. If you want sync to react to membership changes, you must either:
  - update `users.updated_at` whenever membership changes, or
  - build sync from a change log table, or
  - sync memberships separately (advanced).

---

## 2. Object classes and attributes exposed to midPoint

See: [`SchemaScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/templates/postgres-rbac/SchemaScript.groovy)

This template exposes three object classes:

- `__ACCOUNT__` (midPoint kind=account)
  - `__UID__` = `users.id`
  - `__NAME__` = `users.username`
  - `givenName`, `familyName`, `email`
  - `orgUnit` (string): org unit name (reference-like behavior)
  - `roleNames` (multi-valued string): association to roles (entitlement membership)
  - `__ENABLE__` and `__PASSWORD__` (operational attributes)
- `Role` (midPoint kind=entitlement or kind=generic)
  - `__UID__` = `roles.id`
  - `__NAME__` = `roles.name`
  - `description`
- `OrgUnit` (midPoint kind=generic or kind=org)
  - `__UID__` = `org_unit.id`
  - `__NAME__` = `org_unit.name`
  - `parentName` (string): parent org unit name (to build org trees in midPoint)

Important:

- If an attribute is not defined in `SchemaScript.groovy`, midPoint will not reliably work with it.
- Associations in midPoint are configured in the resource `schemaHandling`, but they are implemented in the connector as plain multi-valued attributes (here: `roleNames`).

---

## 3. What variables you get inside scripts

The connector injects a Groovy binding. Most-used variables:

- `log` (`Log`)
- `operation` (`OperationType`)
- `configuration` (`ScriptedSQLConfiguration`)
- `connection` (`java.sql.Connection`)
- `objectClass` (`ObjectClass`)
- `attributes` (`Set<Attribute>`) for create/update
- `id` (`String`) for create (the requested `__NAME__`)
- `uid` (`Uid`) for update/delete (the target `__UID__`)
- `filter` (`Filter`) and `handler` (`ResultsHandler`) for search
- `options` (`OperationOptions`) for search/create/update
- `token` (`Object`) and `handler` (`SyncResultsHandler`) for sync

You can see the exact casts at the top of each script file.

---

## 4. How the “advanced” parts are implemented

### 4.1 Association: user role membership (`roleNames`)

Files:

- search: [`SearchScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/templates/postgres-rbac/SearchScript.groovy)
- update: [`UpdateScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/templates/postgres-rbac/UpdateScript.groovy)
- helpers: [`common/RbacSqlUtils.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/templates/postgres-rbac/common/RbacSqlUtils.groovy)

The template uses “replace all memberships” semantics:

1. MidPoint sends a multi-valued attribute `roleNames` in the update.
2. The script deletes existing rows from `user_role` for that user.
3. The script resolves role IDs by role name.
4. The script inserts membership rows for the new set.

This is the simplest correct approach for connectors that only implement `UpdateOp` (not “add/remove attribute values” ops).

### 4.2 Reference-like mapping: org unit (`orgUnit`)

Files:

- search: joins `users.org_unit_id -> org_unit.name`
- create/update: resolves org unit `name -> id` (FK) and stores `org_unit_id`

This pattern is how you map “reference by name” to a foreign key in SQL.

### 4.3 Password sync

Files:

- create: writes `__PASSWORD__` to `users.password_hash` via `RbacSqlUtils.passwordToStoredValue(...)`
- update: updates `users.password_hash` when `__PASSWORD__` is present

This template stores a clear password by default (for demo only). You should replace it with hashing (BCrypt/SCrypt/Argon2) or call into your system’s password procedure.

### 4.4 Activation / active status sync

Files:

- schema: exposes `OperationalAttributeInfos.ENABLE`
- create/update/search: maps:
  - midPoint `__ENABLE__ = true` to DB `disabled = false`
  - midPoint `__ENABLE__ = false` to DB `disabled = true`

If your DB uses a different convention (e.g. `status='ACTIVE'`), edit only the mapping points and keep midPoint using `__ENABLE__`.

---

## 5. Quick start: how to adapt for your database

1. Start with [`BaseScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/templates/postgres-rbac/BaseScript.groovy):
   update table names and column names.
2. Update [`SchemaScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/templates/postgres-rbac/SchemaScript.groovy):
   expose exactly the attributes you want midPoint to see.
3. Make `TestScript.groovy` pass with your tables.
4. Make `SearchScript.groovy` return a correct account (including `__UID__`, `__NAME__`, `__ENABLE__`).
5. Implement create, then update, then delete.
6. Add sync last.

---

## 6. MidPoint resource XML snippet

This repo already contains a complete example resource config in [`scriptedsql/ScriptedSQL.xml`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/ScriptedSQL.xml).

For this template, see a smaller starter snippet:

- [`ScriptedSQL-postgres-rbac.xml`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/templates/postgres-rbac/ScriptedSQL-postgres-rbac.xml)

It shows:

- how to point the connector to this folder as `scriptRoots`
- a suggested way to define an association in `schemaHandling` based on `roleNames`

