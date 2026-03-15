# Scripted SQL Connector Configuration Reference

Based on `connector-groovy 2.4` — `ScriptedConfiguration` (base) + `ScriptedSQLConfiguration`.

---

## Script Files

All filenames are relative to `Script Root Folders`. Each maps to a connector operation.

| Config | Purpose |
|---|---|
| **Create Script filename** | Called when midPoint creates an account/object on the resource |
| **Update Script filename** | Called when midPoint modifies an existing object |
| **Delete Script filename** | Called when midPoint removes an object |
| **Search Script filename** | Called for reads, reconciliation, and get-object operations |
| **Schema Script filename** | Called once to tell midPoint what object classes and attributes the resource has |
| **Test Script filename** | Called when you click "Test Connection" in midPoint |
| **Sync Script filename** | Called during Live Sync to fetch changed objects since the last token |
| **Authenticate Script filename** | Called when midPoint needs to verify credentials. Optional — only needed if you support authentication. |
| **ResolveUsername Script filename** | Called to resolve a username string to a uid. Optional — used in some auth flows. |
| **ScriptOnResource Script filename** | Called when midPoint executes a "script on resource" provisioning operation. Rarely used. |
| **Customizer Script filename** | Runs at connector initialization. Can customize the Groovy engine itself (add imports, etc.). Rarely needed for ScriptedSQL. |

---

## Groovy Engine / Script Compilation

Control how Groovy compiles and reloads your scripts at runtime.

| Config | Purpose |
|---|---|
| **Script Root Folders** | Directory paths where the connector looks for `.groovy` script files. Base path for all script filenames above. |
| **Script Base Class** | Groovy base class all scripts extend. Set to `BaseScript` to give all scripts access to shared constants/methods. |
| **Classpath** | Additional directories/JARs added to the Groovy classloader. Use `.` to include the script root so scripts can import each other. |
| **Source Encoding** | Character encoding for reading `.groovy` source files. Default: `UTF-8`. |
| **Recompile Groovy Source** | If `true`, Groovy watches script files and recompiles on change without restarting the connector. Useful in development. |
| **Minimum Recompilation Interval** | How often (ms) Groovy checks if scripts changed on disk. Only relevant when `Recompile Groovy Source` is `true`. |
| **Target Directory** | Directory where Groovy writes compiled `.class` files. Optional — can speed up startup. |
| **Warning Level** | Groovy compiler warning level: `0`=none, `1`=likely errors, `2`=possible errors, `3`=paranoia. |
| **Tolerance** | Number of compilation errors Groovy tolerates before aborting. Default: `10`. |
| **Verbose** | Enables verbose Groovy compiler output. |
| **Debug** | Enables debug output from the Groovy compiler/engine. |
| **scriptExtensions.display** | File extensions recognized as scripts (e.g. `groovy`). Usually left at default. |
| **Disabled Global AST Transformations** | List of Groovy AST transformation class names to disable globally. Advanced — only needed if a transformation causes conflicts. |

---

## Custom Data Passing to Scripts

Use these to pass resource-specific config into your scripts without hardcoding values.

| Config | Purpose |
|---|---|
| **Custom Configuration** | Free-form string (e.g. JSON, key=value) passed into every script as `customConfiguration`. Example: `{"tableName":"HR_USERS","softDelete":true}` |
| **Custom Sensitive Configuration** | Same as above but stored as a `GuardedString` (encrypted at rest). Use for passwords or tokens your scripts need beyond the DB password. |

---

## Database Connection

| Config | Purpose |
|---|---|
| **User** | Database username |
| **User Password** | Database password |
| **JDBC Driver** | Fully qualified JDBC driver class, e.g. `org.postgresql.Driver` |
| **JDBC Connection URL** | JDBC URL, e.g. `jdbc:postgresql://host:5432/dbname` |
| **Datasource Path** | JNDI name of a datasource, e.g. `java:comp/env/jdbc/myds`. Alternative to JDBC Driver + URL — use one or the other. |
| **Initial JNDI Properties** | Key=value pairs for JNDI context initialization. Only needed with `Datasource Path`. |
| **Auto Commit** | Whether DB connections auto-commit after each statement. Default `false` — connector manages transactions. |
| **Default Transaction Isolation** | JDBC isolation level: `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`, or `SERIALIZABLE`. Leave blank to use driver default. |

---

## Key Patterns and Tips

- **Custom Configuration** — put resource-specific settings like table names or feature flags here as JSON; read in scripts via the `customConfiguration` variable.
- **Custom Sensitive Configuration** — same idea but for secrets (API keys, extra passwords).
- **Recompile + Minimum Recompilation Interval** — enable hot-reload during development so you don't restart midPoint on every script change.
- **Script Base Class** — `BaseScript.groovy` is the place for shared constants, helper methods, and `ObjectClass` definitions reused across all scripts.
- **Classpath `.`** — makes scripts in the script root importable by other scripts.
- **Customizer Script** — only needed for advanced engine-level customization; skip for typical SQL connectors.
- **Authenticate / ResolveUsername** — only implement if your use case requires credential validation against the resource.
