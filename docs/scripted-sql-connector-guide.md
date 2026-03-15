# Scripted SQL Connector Guide

This repository contains a MidPoint/OpenICF connector that does almost all of its real work in Groovy scripts.

If you are new to this connector, the most important thing to understand is:

- MidPoint does not talk to your SQL tables directly.
- MidPoint calls connector operations like `create`, `search`, `update`, `delete`, `test`, and `sync`.
- This connector maps each operation to a Groovy script.
- Your Groovy scripts are responsible for reading MidPoint data, running SQL, and returning connector objects back to MidPoint.

## Repository Layout

There are two different script examples in this repository.

### Main example

The folder [`scriptedsql/`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql) is the main example copied from the official MidPoint repository.

This is the one you should study if you want to write your own connector scripts.

It contains:

- `ScriptedSQL.xml`: MidPoint resource configuration example
- `BaseScript.groovy`: shared constants and object-class definitions
- `SchemaScript.groovy`: connector schema definition
- `CreateScript.groovy`: create logic
- `SearchScript.groovy`: search/get logic
- `UpdateScript.groovy`: update logic
- `DeleteScript.groovy`: delete logic
- `TestScript.groovy`: connectivity checks
- `SyncScript.groovy`: live sync logic
- `common/`: reusable helper utilities

### Test example

The folder [`src/test/resources/scripts/`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/src/test/resources/scripts) is a smaller test/demo example used by unit tests.

It is useful for comparison, but it is simpler and less reusable than `scriptedsql/`.

## High-Level Runtime Flow

When MidPoint needs to work with a resource object, this is the flow:

1. MidPoint decides which connector operation to call.
2. The connector opens a JDBC connection using the configured driver, URL, username, and password.
3. The connector loads the configured Groovy script for that operation.
4. The connector injects operation context into the Groovy script as variables.
5. The Groovy script executes SQL and returns the expected connector result.

Example:

- MidPoint wants to create an account.
- The connector loads `CreateScript.groovy`.
- The script receives variables like `connection`, `objectClass`, `attributes`, and `id`.
- The script inserts a row into the database.
- The script returns a `Uid`.

## Thin Java Layer

The Java code in this repository is very small.

### Connector class

[`src/main/java/com/evolveum/polygon/connector/scripted/sql/ScriptedSQLConnector.java`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/src/main/java/com/evolveum/polygon/connector/scripted/sql/ScriptedSQLConnector.java)

This class extends the base scripted SQL connector implementation from ForgeRock/OpenICF.

Its main custom behavior is:

- for non-schema operations, it injects `schema` into the Groovy binding

That means scripts like `UpdateScript.groovy` and `DeleteScript.groovy` can access the schema if needed.

### Configuration class

[`src/main/java/com/evolveum/polygon/connector/scripted/sql/ScriptedSQLConfiguration.java`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/src/main/java/com/evolveum/polygon/connector/scripted/sql/ScriptedSQLConfiguration.java)

This class does not add any custom properties. It just exposes the base scripted SQL configuration type as this connector's config class.

## MidPoint Resource Configuration

The example resource file is:

[`scriptedsql/ScriptedSQL.xml`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/ScriptedSQL.xml)

This file is what tells MidPoint how to run the connector.

Important configuration properties:

- `createScriptFileName`
- `updateScriptFileName`
- `deleteScriptFileName`
- `schemaScriptFileName`
- `searchScriptFileName`
- `testScriptFileName`
- `syncScriptFileName`
- `scriptRoots`
- `classpath`
- `scriptBaseClass`
- `user`
- `password`
- `jdbcDriver`
- `jdbcUrlTemplate`

In other words, MidPoint is configured to say:

- "Use this connector class"
- "Load Groovy scripts from this directory"
- "Use these exact script files for each operation"
- "Connect to the database with these JDBC settings"

## What Variables Are Available Inside Scripts

The connector passes variables into each Groovy script through a Groovy binding.

The exact set depends on the operation, but common variables include:

- `log`
- `operation`
- `configuration`
- `connection`
- `objectClass`
- `attributes`
- `id`
- `uid`
- `filter`
- `options`
- `handler`
- `token`
- `schema`
- `builder`

You can see this pattern at the top of each script, for example:

- `CreateScript.groovy` reads `attributes`, `connection`, `id`, `objectClass`
- `SearchScript.groovy` reads `filter`, `options`, `handler`, `connection`
- `SyncScript.groovy` reads `token`, `handler`, `objectClass`
- `SchemaScript.groovy` reads `builder`

The scripts usually cast these variables first, for example:

```groovy
def log = log as Log
def objectClass = objectClass as ObjectClass
def connection = connection as Connection
```

This is a good habit because it makes the script clearer and safer.

## Base Script

The shared script base class is:

[`scriptedsql/BaseScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/BaseScript.groovy)

It defines shared constants such as:

- `GROUP_NAME = "Group"`
- `GROUP = new ObjectClass("Group")`
- `ORGANIZATION_NAME = "Organization"`
- `ORGANIZATION = new ObjectClass("Organization")`
- `TABLE_USER = "Users"`
- `TABLE_GROUPS = "Groups"`

Because `ScriptedSQL.xml` sets `scriptBaseClass` to `BaseScript`, the operation scripts can use these constants directly.

This is how the scripts avoid repeating table names and object-class definitions in many files.

## Schema Script

The schema script is:

[`scriptedsql/SchemaScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/SchemaScript.groovy)

This script defines what the connector exposes to MidPoint.

That means:

- which object classes exist
- which attributes exist
- which operational attributes are supported
- which operation options are supported

In the example:

- account object class exposes `firstname`, `lastname`, `fullname`, `email`, `organization`
- account also exposes `__ENABLE__`, `__PASSWORD__`, and `__LOCK_OUT__`
- group object class exposes `name` and `description`
- paged search offset is declared as a supported search option

This script is extremely important.

If an attribute is not defined here, MidPoint will not treat it as part of the connector schema even if your SQL table has that column.

## Create Operation

The create script is:

[`scriptedsql/CreateScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/CreateScript.groovy)

The basic logic is:

1. Read incoming connector values.
2. Decide which table to insert into based on `objectClass`.
3. Build a parameter map.
4. Execute an insert.
5. Return the generated `Uid`.

For accounts, the example maps:

- `id` to `login`
- `firstname` to `firstname`
- `lastname` to `lastname`
- `fullname` to `fullname`
- `email` to `email`
- `organization` to `organization`
- `__PASSWORD__` to `password`
- `__ENABLE__` to inverse `disabled`

That last point matters:

- MidPoint uses `__ENABLE__`
- the database column is `disabled`
- so the script converts `enabled` to `!disabled`

If your table uses a different convention, this is where you adapt it.

### What `id` means here

In create operations, `id` is usually the connector `__NAME__` value, not the numeric database primary key.

In this example:

- `id` becomes the `login` column
- the database-generated primary key becomes `__UID__`

This is a common and important pattern:

- `__NAME__` = human/business identifier
- `__UID__` = stable unique technical identifier

## Search Operation

The search script is:

[`scriptedsql/SearchScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/SearchScript.groovy)

Its job is to:

1. Inspect `objectClass`
2. Build a base `select`
3. Translate the MidPoint filter into SQL
4. Execute the query
5. Convert each result row into a `ConnectorObject`
6. Send each object to the `handler`

For accounts, `buildAccount(...)` maps a row to:

- `uid = row.id`
- `id = row.login`
- `__ENABLE__ = !row.disabled`
- `__PASSWORD__ = row.password`
- `fullname = row.fullname`
- `firstname = row.firstname`
- `lastname = row.lastname`
- `email = row.email`
- `organization = row.organization`

This is the reverse mapping of the create operation.

### `uid` vs `id` in search results

Inside the object builder:

- `uid ...` sets `__UID__`
- `id ...` sets `__NAME__`

So in this example:

- `__UID__` comes from the database primary key
- `__NAME__` comes from the login column

That is why create, update, delete, and sync can work reliably even if names change.

## Update Operation

The update script is:

[`scriptedsql/UpdateScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/UpdateScript.groovy)

Its job is:

1. Receive `uid`
2. Receive changed attributes
3. Convert special MidPoint attributes into DB columns
4. Update the correct table row using `uid`

The example handles two important special cases:

- `__ENABLE__` becomes inverse `disabled`
- `__PASSWORD__` becomes plain `password`

Then it updates the row:

- accounts: `where id = uid`
- groups: `where id = uid`

### Why `Uid.NAME` is skipped

The script skips `Uid.NAME` in account update processing.

That prevents accidental writing of connector metadata back into the DB as if it were a normal business column.

When writing your own scripts, be careful to distinguish:

- real resource attributes
- connector operational attributes
- connector identity attributes

## Delete Operation

The delete script is:

[`scriptedsql/DeleteScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/DeleteScript.groovy)

This one is simple:

- receive `uid`
- determine the correct table from `objectClass`
- delete the row by `id`

The important design choice is that delete uses `__UID__`, not `__NAME__`.

That is safer because names are often mutable while primary keys should stay stable.

## Test Operation

The test script is:

[`scriptedsql/TestScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/TestScript.groovy)

It checks whether:

- the JDBC connection works
- expected tables are reachable

It delegates the actual validation to `ScriptedSqlUtils.testConnection(...)`.

This is not a full functional test. It is only a connectivity and basic structure test.

## Sync Operation

The sync script is:

[`scriptedsql/SyncScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/SyncScript.groovy)

This script supports two sync-related operations:

- `GET_LATEST_SYNC_TOKEN`
- `SYNC`

### Sync token design

The example uses a compound token:

```text
timestamp;id
```

This is smarter than using only timestamp.

Why:

- many rows can share the same timestamp
- adding `id` makes ordering deterministic
- the connector can continue from the exact last processed row

### Sync flow

1. MidPoint asks for latest sync token.
2. Script queries the newest `(timestamp, id)` pair.
3. MidPoint stores that token.
4. Later MidPoint calls `SYNC` with the old token.
5. Script queries rows newer than that token.
6. For each changed row, script loads the full account object.
7. Script creates a `SyncDelta`.
8. Script passes the delta to the sync handler.

In this example, sync currently supports only accounts.

### Important limitation

This example emits `CREATE_OR_UPDATE` deltas. It does not model delete tracking from a change log table.

If your database needs proper delete sync, you usually need one of these:

- a separate audit/change-log table
- soft-delete rows with a status flag and timestamp
- database triggers that record changes into a sync table

## Shared Helpers

The reusable helper code lives in:

- [`scriptedsql/common/ScriptedSqlUtils.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/common/ScriptedSqlUtils.groovy)
- [`scriptedsql/common/ScriptedSqlFilterVisitor.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/common/ScriptedSqlFilterVisitor.groovy)
- [`scriptedsql/common/ColumnPrefixMapper.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/common/ColumnPrefixMapper.groovy)
- [`scriptedsql/Constants.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/Constants.groovy)

These files are a large part of what makes the official example easier to reuse.

### `ScriptedSqlUtils.groovy`

This helper handles:

- paging
- query execution
- filter-to-where-clause integration
- insert SQL generation
- update SQL generation
- delete SQL generation
- attribute value extraction
- password conversion
- timestamp conversion

Examples:

- `buildAndExecuteInsertQuery(...)`
- `buildAndExecuteUpdateQuery(...)`
- `buildAndExecuteDeleteQuery(...)`
- `executeQuery(...)`
- `getString(...)`
- `getBoolean(...)`
- `getPassword(...)`

### `ScriptedSqlFilterVisitor.groovy`

This helper translates ICF filters into SQL conditions.

Examples:

- `EQUALS` becomes `=`
- `STARTSWITH` becomes `like 'abc%'`
- `ENDSWITH` becomes `like '%abc'`
- `CONTAINS` becomes `like '%abc%'`
- `GREATERTHAN` becomes `>`

It also maps connector identity attributes to DB columns:

- `__UID__` -> your UID column
- `__NAME__` -> your name column

That is how MidPoint search requests become SQL `where` clauses.

### `ColumnPrefixMapper.groovy`

This helper lets the filter translation know:

- the default table alias
- custom column-name mappings
- prefix overrides

This becomes more useful when your queries join multiple tables.

### `Constants.groovy`

This stores:

- prefix mappers
- UID types
- sync SQL
- common base queries
- sync batch size

## The Important Identity Model

When building your own connector, keep these three identities separate:

### Database primary key

Example:

- `Users.id`

This is usually the best value for connector `__UID__`.

### Business identifier

Example:

- `Users.login`

This is usually the best value for connector `__NAME__`.

### MidPoint user attributes

Examples:

- `givenName`
- `familyName`
- `emailAddress`

These are mapped in the MidPoint resource configuration to connector attributes such as:

- `firstname`
- `lastname`
- `email`

Do not mix these layers mentally. A lot of connector confusion comes from mixing:

- MidPoint object fields
- connector attributes
- database columns

## End-to-End Example: Create Account

This is the simplest way to think about the connector.

### Step 1: MidPoint prepares outbound values

From `schemaHandling` in `ScriptedSQL.xml`, MidPoint maps focus fields into connector attributes.

Example:

- MidPoint `name` -> connector `Name`
- MidPoint `givenName` -> connector `firstname`
- MidPoint `familyName` -> connector `lastname`
- MidPoint password -> connector `__PASSWORD__`

### Step 2: Connector calls `CreateScript.groovy`

The script receives:

- `id`
- `attributes`
- `objectClass`
- `connection`

### Step 3: Script maps connector values to SQL values

Example:

- `id` -> `login`
- `__PASSWORD__` -> `password`
- `__ENABLE__` -> inverse `disabled`

### Step 4: Script inserts row

The helper builds a named-parameter insert query and executes it.

### Step 5: Script returns `Uid`

The generated DB key becomes connector `__UID__`.

### Step 6: MidPoint stores the shadow using that `Uid`

From then on, later updates and deletes will usually target that stable `Uid`.

## How To Adapt This Example To Your Own Database

If you want to write your own connector scripts for another SQL schema, change these parts first.

### 1. Define your object classes and attributes

Edit:

- [`scriptedsql/SchemaScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/SchemaScript.groovy)

Decide:

- which object classes you need
- which attributes MidPoint should see
- which operational attributes you support

### 2. Define your table names and object-class constants

Edit:

- [`scriptedsql/BaseScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/BaseScript.groovy)

Change:

- table names
- custom object classes if needed

### 3. Define identity mapping

For each object class, decide:

- which DB column is `__UID__`
- which DB column is `__NAME__`

This decision affects:

- create
- search
- update
- delete
- sync
- filter translation

### 4. Rewrite create/search/update/delete mappings

Edit:

- [`scriptedsql/CreateScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/CreateScript.groovy)
- [`scriptedsql/SearchScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/SearchScript.groovy)
- [`scriptedsql/UpdateScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/UpdateScript.groovy)
- [`scriptedsql/DeleteScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/DeleteScript.groovy)

For each attribute, decide:

- what MidPoint calls it
- what connector schema calls it
- which DB column stores it
- whether type conversion is needed

### 5. Adjust search filter mappings if needed

If your SQL uses:

- joins
- aliases
- renamed columns
- non-integer UID types

then update:

- [`scriptedsql/Constants.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/Constants.groovy)
- [`scriptedsql/common/ColumnPrefixMapper.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/common/ColumnPrefixMapper.groovy)
- maybe `SearchScript.groovy`

### 6. Redesign sync for your real change model

If your database does not have a reliable `timestamp` column, you will need another token strategy.

Good options:

- monotonically increasing sequence/change number
- last-modified timestamp plus primary key
- dedicated change-log table

## Common Mistakes

### Mistake 1: Treating MidPoint attributes as DB column names

They are not the same thing unless you intentionally map them that way.

### Mistake 2: Using mutable business identifiers as `__UID__`

Prefer a stable primary key for `__UID__`.

### Mistake 3: Forgetting special operational attributes

Examples:

- `__PASSWORD__`
- `__ENABLE__`

These usually need conversion logic.

### Mistake 4: Defining attributes in SQL scripts but not in schema

If `SchemaScript.groovy` does not expose the attribute, MidPoint will not see it properly.

### Mistake 5: Writing sync without a stable ordering rule

If multiple changed rows can share the same timestamp, use a compound token such as `timestamp + id`.

## Suggested Learning Order

If you want to understand this repository quickly, read files in this order:

1. [`scriptedsql/ScriptedSQL.xml`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/ScriptedSQL.xml)
2. [`scriptedsql/SchemaScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/SchemaScript.groovy)
3. [`scriptedsql/BaseScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/BaseScript.groovy)
4. [`scriptedsql/CreateScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/CreateScript.groovy)
5. [`scriptedsql/SearchScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/SearchScript.groovy)
6. [`scriptedsql/UpdateScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/UpdateScript.groovy)
7. [`scriptedsql/DeleteScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/DeleteScript.groovy)
8. [`scriptedsql/SyncScript.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/SyncScript.groovy)
9. [`scriptedsql/common/ScriptedSqlUtils.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/common/ScriptedSqlUtils.groovy)
10. [`scriptedsql/common/ScriptedSqlFilterVisitor.groovy`](/Volumes/blackcat/workspace/connector/connector-scripted-sql/scriptedsql/common/ScriptedSqlFilterVisitor.groovy)

## Practical Summary

This connector works by translating between three layers:

### MidPoint layer

- users
- roles
- mappings
- schema handling

### Connector layer

- object classes
- `__UID__`
- `__NAME__`
- operational attributes
- search filters
- sync deltas

### SQL layer

- tables
- columns
- rows
- joins
- primary keys
- timestamps

Your Groovy scripts are the bridge between those layers.

If you can clearly answer these questions, you can usually write the connector:

- What object classes do I need?
- What attributes should MidPoint see?
- Which DB column is `__UID__`?
- Which DB column is `__NAME__`?
- How do I insert, search, update, and delete rows?
- How will I detect changes for sync?

## Next Step

The fastest way to write your own version is to copy `scriptedsql/` and then replace:

- table names
- object classes
- attribute-to-column mappings
- sync SQL
- JDBC settings in the resource XML

Do that incrementally:

1. Make `schema` work.
2. Make `test` work.
3. Make `search` return one correct account.
4. Make `create` work.
5. Make `update` work.
6. Make `delete` work.
7. Add sync last.

That order is much easier to debug than starting from sync.
