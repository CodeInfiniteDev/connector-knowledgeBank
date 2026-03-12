## Auth DB Design for Multi‑Resource Users (midPoint + Scripted SQL)

This document describes a practical way to expose all resource accounts for a midPoint user to an external auth system using the **scripted SQL connector**.

The main goal:  
> An auth system can query a database by `loginId` (midPoint user) and optionally `resource_name`, and get back all relevant `resourceAccountId` values for that user.

---

## High‑Level Decision

There are two natural modeling options for the SQL table:

- **A. One row per (user, resource account)**  
  Example: 1 user with 10 accounts ⇒ 10 rows.
- **B. One row per user, with a column that contains *all* accounts**  
  Example: 1 user ⇒ 1 row with a list of 10 accounts in a structured string (e.g. JSON).

From a midPoint + scripted SQL connector perspective, **option B is strongly preferred**:

- midPoint works best when **one projection = one row** on a resource.
- The scripted SQL connector is called to create/update **one record at a time**, not to fan out to multiple rows.
- Complex account structure can be handled **inside the connector script** as a string; midPoint only needs to see a scalar attribute.

---

## Recommended Table Schema

Create a dedicated table that represents an “auth view” of the midPoint user:

```sql
CREATE TABLE auth_user (
  login_id         VARCHAR(128) PRIMARY KEY,  -- midPoint user identifier
  display_name     VARCHAR(256),
  email            VARCHAR(256),
  accounts_json    TEXT,                      -- JSON containing all resource accounts
  last_modified    TIMESTAMP
);
```

Example content for `accounts_json`:

```json
[
  { "resource": "AD",    "accountId": "jdoe" },
  { "resource": "SAP",   "accountId": "123456" },
  { "resource": "LDAP1", "accountId": "uid=jdoe,ou=people,dc=example,dc=com" }
]
```

Key points:

- `login_id` is the main key that ties back to the midPoint `user`.
- `accounts_json` is a **single string** from midPoint’s point of view.
- The auth system is responsible for parsing/filtering the JSON when needed.

---

## Why Not “One Row Per Account”?

A more normalized design would be:

```sql
CREATE TABLE auth_user_account (
  login_id            VARCHAR(128),
  resource_name       VARCHAR(128),
  resource_account_id VARCHAR(256),
  PRIMARY KEY (login_id, resource_name, resource_account_id)
);
```

This looks clean at the DB level, but it does **not** fit midPoint’s model well:

- A single user with 10 accounts would mean 10 projections on the same SQL resource.
- midPoint is not designed for *one projection creating 10 rows*; each projection normally maps to **one row**.
- Implementing “fan‑out” (one midPoint object -> many rows) in the scripted SQL connector is fragile:
  - You must manage inserts/updates/deletes of multiple rows on every change.
  - Error handling and retries become more complex.

For these reasons, **we model the table as one row per user, not per account**.

---

## midPoint Modeling

### Resource Definition

Define a resource in midPoint (e.g. `Auth DB`) that represents the `auth_user` table.

- Object type: `ACCOUNT` (one account per user row).
- Identifier: `login_id`.
- Attributes:
  - `login_id` (string, required, primary identifier).
  - `display_name` (string).
  - `email` (string).
  - `accounts_json` (string) – this is the **complex attribute** from the DB’s perspective, but **scalar** for midPoint.

### Projections

For each midPoint user:

- There is exactly **one projection** on the `Auth DB` resource.
- When the user or any of its projections to other resources change, midPoint updates this `Auth DB` projection.
- The connector script builds `accounts_json` from the user’s current projections.

---

## Building `accounts_json` in the Scripted SQL Connector

### Concept

The connector script sees the midPoint `user` and its `linkRef` (shadows for other resources). It can:

1. Iterate all projections.
2. For each projection:
   - Determine a `resourceName` (e.g. resource OID, name, or a custom label).
   - Determine the account identifier (often `icfs:name` or another key attribute).
3. Build a JSON array string and set it as `accounts_json`.

midPoint only needs to know that `accounts_json` is a string attribute; the connector script is responsible for the JSON structure.

### Pseudo‑Groovy Example

Below is **illustrative pseudo‑code**, not a full connector script. It shows the idea:

```groovy
import groovy.json.JsonOutput

// 'user' is the focus object from midPoint
def buildAccountsJson(user, prismContext) {
    def repo = context.repositoryService  // or use available services from script binding

    def result = []

    user.linkRef.each { linkRef ->
        def shadowOid = linkRef.oid
        if (!shadowOid) {
            return
        }

        // Load shadow object
        def shadow = repo.getObject(ShadowType.class, shadowOid, null, new OperationResult("loadShadow"))
        def shadowBean = shadow.asObjectable()

        def resourceOid = shadowBean.resourceRef?.oid
        def resource = resourceOid ? repo.getObject(ResourceType.class, resourceOid, null, new OperationResult("loadResource")) : null
        def resourceName = resource ? resource.asObjectable().name?.orig : "UNKNOWN"

        // Account identifier: use ICF name or another attribute
        def attrs = shadowBean.attributes
        def icfName = attrs?.get(ConnectorObjectClassDefinition.ICFS_NAME)?.value?.get(0)

        if (icfName) {
            result << [
                resource  : resourceName,
                accountId : icfName.toString()
            ]
        }
    }

    return JsonOutput.toJson(result)
}
```

In the outbound mapping for the `accounts_json` attribute, you call this helper and assign its string result to the attribute value.

You can adapt this to:

- Choose different `resourceName` values (e.g. by intent, by a custom property, etc.).
- Pick a different attribute than `icfs:name` as the `resourceAccountId`.

---

## How the Auth System Queries

The auth system’s typical workflows:

### 1. Get All Accounts for a User

1. Query:

   ```sql
   SELECT accounts_json
   FROM auth_user
   WHERE login_id = ?;
   ```

2. Parse `accounts_json` as JSON.
3. Use all entries to know which resource accounts exist for that user.

### 2. Get Accounts for a User on a Specific Resource

Two options:

- **Application‑side filter** (simplest):
  - Same query as above.
  - After parsing JSON, filter in application code: `resource == resource_name`.

- **DB‑side JSON filtering** (if DB supports JSON functions, e.g. PostgreSQL, MySQL 5.7+):
  - Keep `accounts_json` as JSON.
  - Optionally create indexes on JSON paths.
  - Use DB‑specific JSON predicates to filter by `resource_name`.

The choice depends on performance and complexity requirements; midPoint is unaffected.

---

## Sync and Change Handling

When anything relevant changes in midPoint:

- User attribute changes (e.g. display name, email).
- A projection (account) is added/removed/renamed on another resource.

Then:

1. midPoint recomputes the `Auth DB` projection.
2. The scripted SQL connector is called once for that row.
3. The script recomputes the `accounts_json` from the current `linkRef` list and updates the row.

Because we persist **one row per user**, we avoid:

- Multiple rows being partly updated on failures.
- Complex row‑by‑row reconciliation for all 10 accounts.

---

## Practical Tips and Best Practices

- **Keep `accounts_json` schema stable**:  
  If external systems rely on this format, version it or extend it carefully to avoid breaking changes.

- **Log JSON generation** in the connector script:  
  Helps debug issues where some resource accounts are missing or mis‑labeled.

- **Avoid midPoint‑side complex types**:  
  In the resource schema, define `accounts_json` as a simple `string`. All complex structure stays inside the connector script.

- **Normalize resource names**:  
  Decide a stable naming scheme (e.g. `"AD"`, `"SAP"`, `"LDAP1"`) and keep it consistent so that consumers can reliably query/filter.

- **Test projection edge cases**:  
  - User with zero projections (no accounts).
  - User with many projections (10+ accounts).
  - Deleted or disabled accounts.

---

## Summary

- Use a single `auth_user` table with **one row per midPoint user**.
- Store all external accounts in a **single JSON string column** (`accounts_json`).
- Let the scripted SQL connector build this JSON string from the user’s `linkRef` projections.
- Let the auth system parse and filter accounts per resource using either application logic or DB JSON functions.

This approach aligns with midPoint’s projection model, keeps the connector logic manageable, and provides the auth system a simple and flexible way to query all resource accounts for a given user.

