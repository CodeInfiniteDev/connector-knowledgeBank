## Auth-User JSON on SQL Server With Per-Account View

This note describes how to use the existing `templates/auth-user-json` Scripted SQL template with **SQL Server** and still give other systems an easy, normalized view:

- MidPoint + Scripted SQL own a single **per-user JSON table**.
- SQL Server exposes a **flattened view** with one row per `(user_login_id, resource_name, resource_account_id)`.

This matches the requirement:

> Other systems should be able to run  
> `SELECT resource_account_id FROM ... WHERE user_login_id = ? AND resource_name = ?`

without calling midPoint REST.

---

## 1. SQL Server DDL

### 1.1 Canonical per-user JSON table

The Scripted SQL connector writes to this table. It is the SQL Server equivalent of the PostgreSQL `auth_user` table in `templates/auth-user-json/ddl.sql`.

```sql
CREATE TABLE dbo.AuthUser (
    user_login_id   nvarchar(128)  NOT NULL PRIMARY KEY,  -- midPoint login / username
    user_name       nvarchar(256)      NULL,              -- display name
    user_oid        nvarchar(36)       NULL,              -- optional: midPoint user OID
    accounts_json   nvarchar(max)      NULL,              -- JSON array of resource accounts
    updated_at      datetime2      NOT NULL
        CONSTRAINT DF_AuthUser_UpdatedAt DEFAULT (sysutcdatetime())
);
```

Example `accounts_json`:

```json
[
  { "resource": "jira",     "accountId": "ment@example.com" },
  { "resource": "jira",     "accountId": "ment_dev@example.com" },
  { "resource": "AD",       "accountId": "cn=marry, Cheung,cn=users,dc=example,dc=com" },
  { "resource": "happyLab", "accountId": "marryCheung333" }
]
```

This lets you know, per midPoint user (`user_login_id`), which resource accounts exist, including duplicates per resource.

### 1.2 Flattened per-account view

To make querying simple for other systems, expose a view:

```sql
CREATE VIEW dbo.AuthUserAccount
AS
SELECT
    u.user_login_id,
    JSON_VALUE(j.value, '$.resource')   AS resource_name,
    JSON_VALUE(j.value, '$.accountId')  AS resource_account_id,
    u.updated_at
FROM dbo.AuthUser AS u
CROSS APPLY OPENJSON(u.accounts_json) AS j;
GO
```

Now consumers can query:

```sql
SELECT resource_account_id
FROM dbo.AuthUserAccount
WHERE user_login_id = N'marryCheung'
  AND resource_name  = N'jira';
```

If needed, you can later materialize this into a physical table and refresh it (e.g. with a job), but starting with a view keeps the Scripted SQL logic simple.

---

## 2. Connector Scripts: Reuse `auth-user-json`

The existing `templates/auth-user-json` scripts already implement the right behavior:

- one row per midPoint user
- a single string attribute `accountsJson` is written to the `accounts_json` column
- the connector itself does **not** try to understand the JSON

To adapt to SQL Server you mainly need to:

1. Use the SQL Server DDL above instead of the PostgreSQL `ddl.sql`.
2. Optionally adjust `BaseScript.groovy` to use `AuthUser`, `user_login_id`, etc., instead of `auth_user`, `login_id` if you want to match the DDL names exactly.
3. Point the Scripted SQL connector config (`jdbcDriver`, `jdbcUrlTemplate`) at SQL Server.

No change is needed to the core create/update/search logic as long as:

- `__NAME__` / `__UID__` map to `user_login_id` (or `login_id` if you keep the original names).
- `accountsJson` carries the JSON string described below.

---

## 3. Outbound Mapping: Building `accountsJson`

The key part for this design is the **outbound mapping** in midPoint that computes `accountsJson` from the user and all its projections.

### 3.1 Target attribute

In the Scripted SQL resource schema handling, expose an attribute like `ri:accountsJson` and map it outbound:

```xml
<attribute>
  <ref>ri:accountsJson</ref>
  <outbound>
    <strength>strong</strength>
    <expression>
      <script>
        <language>http://midpoint.evolveum.com/xml/ns/public/expression/language#groovy</language>
        <code>
          import com.evolveum.midpoint.xml.ns._public.common.common_3.*
          import groovy.json.JsonOutput

          def u = focus as UserType
          def repo = midpoint.repositoryService

          def entries = []

          (u.linkRef ?: []).each { linkRef ->
              def shadowOid = linkRef.oid
              if (!shadowOid) {
                  return
              }

              def shadowObj = repo.getObject(ShadowType.class, shadowOid, null,
                      new OperationResult("loadShadow"))
              def shadow = shadowObj.asObjectable()

              // Resolve resource name
              def resOid = shadow.resourceRef?.oid
              def resObj = resOid ?
                      repo.getObject(ResourceType.class, resOid, null,
                              new OperationResult("loadResource")) : null
              def res = resObj?.asObjectable()
              def resourceName = res?.name?.orig ?: "UNKNOWN"

              // Choose an account identifier; simplest is ICF name
              def attrsContainer = shadow.attributes
              def icfNameAttr = attrsContainer?.any?.find {
                  it.elementName?.localPart == 'name'
              }
              def accountId = icfNameAttr?.realValue ?: shadow.name?.orig

              if (accountId) {
                  entries << [
                      resource  : resourceName,
                      accountId : accountId.toString()
                  ]
              }
          }

          JsonOutput.toJson(entries)
        </code>
      </script>
    </expression>
  </outbound>
</attribute>
```

You can modify:

- how `resourceName` is built (e.g. by intent, by a custom property on the resource).
- which attribute is used as `accountId` (mail, sAMAccountName, etc.).
- which projections are included or excluded.

### 3.2 Other attributes

In the same object type you usually also map:

- `ri:loginId` / `ri:userLoginId` from `name` or a dedicated identifier.
- `ri:userName` from `fullName`.
- `ri:userOid` from the user `oid` if you want it in the table.

The Scripted SQL scripts can then write these into `user_login_id`, `user_name`, `user_oid`.

---

## 4. Alternative: Direct Per-Account Table

If you decide you truly need a physical per-account table instead of a view, the pattern is:

```sql
CREATE TABLE dbo.AuthUserAccount (
    user_login_id        nvarchar(128) NOT NULL,
    resource_name        nvarchar(128) NOT NULL,
    resource_account_id  nvarchar(256) NOT NULL,
    updated_at           datetime2     NOT NULL
        CONSTRAINT DF_AuthUserAccount_UpdatedAt DEFAULT (sysutcdatetime()),
    CONSTRAINT PK_AuthUserAccount PRIMARY KEY (user_login_id, resource_name, resource_account_id)
);
```

Then, in the Scripted SQL `CreateScript` / `UpdateScript`, you:

- still receive `accountsJson` as above
- parse it (e.g. with `JsonSlurper`)
- delete existing rows for the user
- insert one row per `resource/accountId`

This works but introduces multi-row fan-out logic into the connector. The **JSON table + SQL Server view** described above is usually simpler and safer, while keeping queries easy for other systems.

