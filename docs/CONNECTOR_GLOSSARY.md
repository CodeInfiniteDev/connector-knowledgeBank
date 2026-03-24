# ConnId SCIM Connector Glossary

This file explains important classes and concepts in this repository in simple words.

The focus is:

- what each name is
- whether it belongs to SCIM, ConnId, Jackson, or this repository
- how this connector transforms SCIM data into ConnId objects that midPoint can use

This document is meant to grow. When you ask about more classes later, they can be added here.

## Big Picture

This repository is a **ConnId connector** for a **SCIM** target system.

The main translation flow is:

1. `midPoint` talks to the connector through **ConnId APIs**.
2. The connector exposes a ConnId `Schema` that tells midPoint which object classes and attributes exist.
3. For read/search operations, the connector calls the remote SCIM server by using `SCIMv2Client`.
4. The SCIM JSON response is parsed into Java DTO objects like `SCIMv2User` and `SCIMv2Group`.
5. Those DTO objects are converted into ConnId `ConnectorObject` values with ConnId `Attribute` values.
6. midPoint reads those ConnId objects and uses them for mappings, synchronization, provisioning, and UI.

For updates:

1. midPoint sends ConnId `Attribute` or `AttributeDelta` values to the connector.
2. The connector converts them into SCIM DTO changes or SCIM PATCH operations.
3. `SCIMv2Client` sends the request to the SCIM server.

In short:

`midPoint <-> ConnId classes <-> this connector <-> SCIM DTO/JSON <-> SCIM server`

## The Most Important Distinction

There are two different "schema" ideas here:

- `Schema`:
  the **ConnId schema** shown to midPoint
- `SCIMSchema`:
  a **SCIM-style schema definition object** used by this connector to describe custom SCIM attributes/resources

There are also two different "object class" ideas:

- `ObjectClass`:
  the ConnId object-class type, for example `__ACCOUNT__` or `__GROUP__`
- `objectClass`:
  usually just a field name or variable name that holds a ConnId `ObjectClass`

## Terms

### `SCIMv2Client`

- Layer: repository code
- Package: `net.tirasa.connid.bundles.scim.v2.service`
- File: `src/main/java/net/tirasa/connid/bundles/scim/v2/service/SCIMv2Client.java`

What it is:

- The SCIM v2 service client.
- This is the class that actually talks to the remote SCIM server by HTTP.

What it does:

- read users and groups from SCIM
- create users and custom resources
- update users and groups
- send SCIM PATCH requests
- delete groups and custom resources
- return raw `JsonNode` for generic custom resources

Why it matters:

- This is the boundary between connector logic and remote SCIM API calls.
- If you want to know "where the connector sends the REST call", this is one of the main places.

Simple mental model:

- `SCIMv2Client` = "HTTP/REST gateway to the SCIM server"

### `Schema`

- Layer: ConnId framework
- Java type: `org.identityconnectors.framework.common.objects.Schema`

What it is:

- The complete schema that the connector exposes to ConnId and therefore to midPoint.

What it contains:

- all supported `ObjectClassInfo`
- each object class's `AttributeInfo`
- account, group, and optionally custom resource object classes

How this repository builds it:

- `SCIMv2Connector.schema()` calls `SCIMAttributeUtils.buildSchema(...)`
- that method builds account and group definitions and may add custom resource object classes

Why midPoint cares:

- midPoint discovers what objects and attributes exist through this `Schema`
- midPoint validates returned attributes against this schema
- association and mapping behavior depends on it

Simple mental model:

- `Schema` = "the contract this connector shows to midPoint"

### `SCIMv2User`

- Layer: repository code
- Package: `net.tirasa.connid.bundles.scim.v2.dto`
- File: `src/main/java/net/tirasa/connid/bundles/scim/v2/dto/SCIMv2User.java`

What it is:

- Java DTO representing a SCIM v2 User resource.

What it contains:

- user core attributes like `userName`, `displayName`, `active`
- enterprise extension data
- groups, roles, entitlements, photos, emails, addresses
- custom extension attributes

How it is used:

- when reading from SCIM, JSON is deserialized into `SCIMv2User`
- when creating/updating, connector data is assembled into `SCIMv2User`
- then it is converted to ConnId `Attribute` values by `toAttributes(...)`

Simple mental model:

- `SCIMv2User` = "in-memory Java form of one SCIM User"

### `SCIMv2Group`

- Layer: repository code
- Package: `net.tirasa.connid.bundles.scim.v2.dto`
- File: `src/main/java/net/tirasa/connid/bundles/scim/v2/dto/SCIMv2Group.java`

What it is:

- Java DTO representing a SCIM v2 Group resource.

What it contains:

- group id
- display name
- members
- meta
- schemas
- custom extension attributes

How it is used:

- read SCIM group JSON into Java
- update group memberships
- convert SCIM group data into ConnId attributes for midPoint

Simple mental model:

- `SCIMv2Group` = "in-memory Java form of one SCIM Group"

### `SCIMBaseAttribute`

- Layer: repository code
- Package: `net.tirasa.connid.bundles.scim.common.dto`
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/dto/SCIMBaseAttribute.java`

What it is:

- Base class for SCIM attribute definitions.

What it describes:

- attribute name
- attribute type
- whether it is multi-valued
- whether it is required
- description
- canonical values
- sub-attributes

How it is used here:

- it is the parent type for version-specific schema attribute definitions such as `SCIMv2Attribute`
- it helps the connector understand custom SCIM schema metadata

Important:

- This is **not** a runtime value like ConnId `Attribute`
- it is a **definition of an attribute**

Simple mental model:

- `SCIMBaseAttribute` = "metadata describing one SCIM attribute"

### `objectClass`

What it is:

- Usually this is just a variable or field name.
- In practice, it usually holds a ConnId `ObjectClass`.

Important distinction:

- lowercase `objectClass` is usually a variable name
- uppercase `ObjectClass` is the ConnId Java class

What it means functionally:

- it tells the connector what kind of thing is being handled
- examples:
  - account/user
  - group
  - custom resource class

How it is used:

- in search, create, update, and delete logic the connector checks the `ObjectClass`
- example logic:
  - if object class is account, work with users
  - if object class is group, work with groups
  - otherwise maybe handle a custom resource

Simple mental model:

- `objectClass` = "which kind of object are we talking about right now?"

### `ObjectClassInfo`

- Layer: ConnId framework
- Java type: `org.identityconnectors.framework.common.objects.ObjectClassInfo`

What it is:

- The schema description for one ConnId object class.

Examples:

- account object class
- group object class
- custom resource object class

What it contains:

- object class name/type
- attribute definitions (`AttributeInfo`)

How it is used:

- `Schema` contains many `ObjectClassInfo`
- midPoint reads them to know which attributes are valid for each object type

Simple mental model:

- `ObjectClassInfo` = "schema for one object type"

### `OperationOptions`

- Layer: ConnId framework
- Java type: `org.identityconnectors.framework.common.objects.OperationOptions`

What it is:

- Extra options passed to a connector operation.

Examples of what it may carry:

- `attributesToGet`
- page size
- paged results cookie

How this repository uses it:

- search methods read `attributesToGet`
- paging logic reads page size and cookie
- custom resource operations accept it too

Why it matters:

- It changes how an operation is executed without changing the operation itself.

Simple mental model:

- `OperationOptions` = "optional execution settings for a connector call"

### `Attribute`

- Layer: ConnId framework
- Java type: `org.identityconnectors.framework.common.objects.Attribute`

What it is:

- A runtime attribute instance passed between the connector and ConnId/midPoint.

Examples:

- `userName = alice`
- `displayName = Alice Wong`
- `members = [id1, id2]`

How it is used:

- create operations receive a set of `Attribute`
- read/search operations return `Attribute`
- DTOs like `SCIMv2User` and `SCIMv2Group` convert themselves into sets of `Attribute`

Important:

- `Attribute` is an actual value
- `AttributeInfo` is only the schema definition of that value

Simple mental model:

- `Attribute` = "one real field value being exchanged with midPoint"

### `AttributeInfo`

- Layer: ConnId framework
- Java type: `org.identityconnectors.framework.common.objects.AttributeInfo`

What it is:

- The schema definition for one ConnId attribute.

What it describes:

- attribute name
- Java type
- multi-valued or not
- required or not
- updateable or read-only
- readable/createable flags

How it is used here:

- built in `SCIMAttributeUtils.buildSchema(...)`
- attached to `ObjectClassInfo`
- used by midPoint and by the connector's validation logic

Simple mental model:

- `AttributeInfo` = "schema metadata for one ConnId attribute"

### `AttributeDelta`

- Layer: ConnId framework
- Java type: `org.identityconnectors.framework.common.objects.AttributeDelta`

What it is:

- A change description for one attribute during update-delta operations.

What it can express:

- values to add
- values to remove
- values to replace

Why it exists:

- full replace update is not always enough
- SCIM PATCH maps naturally to delta-style changes

How this repository uses it:

- user/group updates build SCIM PATCH operations from `AttributeDelta`
- membership changes especially rely on `AttributeDelta`

Simple mental model:

- `AttributeDelta` = "how one attribute should change"

### `SCIMSchema`

- Layer: repository code
- Package: `net.tirasa.connid.bundles.scim.common.dto`
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/dto/SCIMSchema.java`

What it is:

- A Java model for SCIM schema-like configuration data.

What it contains:

- schema id
- name
- description
- schema URI
- endpoint
- list of SCIM attributes

How it is used in this repository:

- parse configured custom attributes
- define custom SCIM resources
- help build additional ConnId schema entries

Important:

- This is **not** the ConnId `Schema`
- It is a helper structure describing SCIM resources/attributes

Simple mental model:

- `SCIMSchema` = "SCIM-flavored metadata used by the connector"

### `Token`

What I found in this repository:

- There is **no Java class named `Token`** in this repository.
- There is also no `interface Token` here.

What probably caused the confusion:

- the connector has **bearer token / access token** logic for authentication
- that logic is in `AbstractSCIMService.getBearerToken(...)`

What it means here:

- "token" is an authentication concept, not a repository DTO class

Simple mental model:

- `Token` here = "OAuth/bearer access token used for HTTP auth", not a connector model class

### `JsonNode`

- Layer: Jackson library
- Java type: `com.fasterxml.jackson.databind.JsonNode`

What it is:

- A generic JSON tree node from Jackson.

Why it exists:

- sometimes the connector does not want to deserialize JSON into a fixed Java DTO immediately
- this is useful for dynamic or custom resource handling

How this repository uses it:

- generic custom resource read/create/patch handling
- token response parsing
- raw HTTP response parsing
- merge and inspect arbitrary JSON payloads

Simple mental model:

- `JsonNode` = "raw JSON tree in memory"

## How ConnId Data Is Transformed For midPoint

This is the part that usually causes the most confusion.

### 1. The connector tells midPoint what exists

`SCIMv2Connector.schema()` builds a ConnId `Schema`.

That schema contains:

- account object class
- group object class
- optional custom resource object classes

Each object class contains `AttributeInfo` definitions.

This is how midPoint learns things like:

- account has `userName`
- group has `displayName`
- custom object class may have custom attributes

### 2. The connector reads SCIM data

When midPoint searches or gets an object:

- connector calls `SCIMv2Client`
- SCIM server returns JSON
- JSON is converted into `SCIMv2User`, `SCIMv2Group`, or `JsonNode`

### 3. The connector converts SCIM objects to ConnId objects

The important methods are:

- `AbstractSCIMConnector.fromUser(...)`
- `AbstractSCIMConnector.fromGroup(...)`

What happens there:

- create a `ConnectorObjectBuilder`
- set ConnId `ObjectClass`
- set `Uid`
- set `Name`
- convert DTO fields into ConnId `Attribute`
- include only requested attributes when needed

Result:

- midPoint receives a ConnId `ConnectorObject`

### 4. The connector converts incoming ConnId data back to SCIM

When midPoint creates or updates:

- it sends ConnId `Attribute` or `AttributeDelta`
- connector maps them into:
  - SCIM DTO fields like `SCIMv2User`
  - SCIM PATCH structures like `SCIMv2Patch` and `SCIMv2PatchOperation`
- `SCIMv2Client` sends the HTTP request

## Useful Mapping Summary

- `Schema` -> complete ConnId schema exposed to midPoint
- `ObjectClassInfo` -> schema for one object type
- `AttributeInfo` -> schema for one attribute
- `Attribute` -> runtime value
- `AttributeDelta` -> runtime change
- `SCIMSchema` -> SCIM/custom schema metadata used by the connector
- `SCIMBaseAttribute` -> metadata for one SCIM attribute definition
- `SCIMv2User` / `SCIMv2Group` -> SCIM resource DTOs
- `SCIMv2Client` -> HTTP client for SCIM operations
- `JsonNode` -> generic JSON tree for dynamic handling

## Recommended Mental Model

If you are reading this code, think in these layers:

1. **ConnId layer**
   - `Schema`
   - `ObjectClassInfo`
   - `AttributeInfo`
   - `Attribute`
   - `AttributeDelta`
   - `OperationOptions`

2. **Connector internal mapping layer**
   - `AbstractSCIMConnector`
   - `SCIMAttributeUtils`
   - `fromUser(...)`
   - `fromGroup(...)`

3. **SCIM model layer**
   - `SCIMv2User`
   - `SCIMv2Group`
   - `SCIMSchema`
   - `SCIMBaseAttribute`

4. **JSON/HTTP layer**
   - `SCIMv2Client`
   - `JsonNode`
   - bearer token handling

## Repository-Specific Classes You Should Learn First

These classes are especially useful if you want to fully understand this project. They are the connector's own architecture, not just framework types.

### `SCIMv2Connector`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/v2/SCIMv2Connector.java`

What it is:

- The main SCIM v2 connector entry point.

Why it is important:

- If you want to understand "what this project does", this is one of the best starting points.
- It connects ConnId operations to the SCIM v2 implementation.

What it does:

- builds the ConnId `Schema`
- creates the `SCIMv2Client`
- handles SCIM v2-specific patch logic
- wires in custom resource support
- decides how user/group updates become SCIM PATCH requests

Simple mental model:

- `SCIMv2Connector` = "main SCIM v2 connector brain"

### `AbstractSCIMConnector`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/AbstractSCIMConnector.java`

What it is:

- The common base connector implementation shared by SCIM versions.

Why it is important:

- This is where a lot of the real ConnId behavior lives.
- It handles the generic bridge between ConnId operations and SCIM resource objects.

What it does:

- implements ConnId operations like create, search, update, delete
- resolves search filters and operation options
- converts SCIM DTOs into ConnId `ConnectorObject`
- converts incoming ConnId attributes into SCIM DTOs and patch data
- contains `fromUser(...)` and `fromGroup(...)`

Simple mental model:

- `AbstractSCIMConnector` = "generic connector engine"

### `SCIMAttributeUtils`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/utils/SCIMAttributeUtils.java`

What it is:

- Utility class for schema building and attribute conversion.

Why it is important:

- This class explains how SCIM fields become ConnId attributes.
- It is one of the most important mapping classes in the project.

What it does:

- builds ConnId account/group schema definitions
- defines many standard SCIM attribute names
- creates `AttributeInfo`
- helps convert Java object fields into ConnId `Attribute`
- includes special attribute naming rules for extensions and complex values

Simple mental model:

- `SCIMAttributeUtils` = "attribute naming and schema mapping toolbox"

### `AbstractSCIMService`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/service/AbstractSCIMService.java`

What it is:

- Base HTTP service layer used by concrete clients like `SCIMv2Client`.

Why it is important:

- This class explains how the connector communicates with SCIM servers at runtime.
- It contains shared REST, auth, retry, and JSON behavior.

What it does:

- builds CXF `WebClient`
- applies auth headers or basic auth
- gets bearer tokens
- handles proxy and redirect settings
- sends GET/POST/PUT/PATCH/DELETE requests
- parses JSON responses
- handles HTTP/service errors

Simple mental model:

- `AbstractSCIMService` = "shared SCIM HTTP runtime"

### `SCIMConnectorConfiguration`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/SCIMConnectorConfiguration.java`

What it is:

- The full connector configuration object.

Why it is important:

- It defines what administrators can configure in ConnId or midPoint.
- Many runtime behaviors depend on these settings.

What it contains:

- base URL
- credentials or bearer-token settings
- content type and accept headers
- custom user/group attributes JSON
- custom resource JSON
- provider-specific flags
- proxy settings
- extension attribute separator behavior

Simple mental model:

- `SCIMConnectorConfiguration` = "all connector settings in one place"

### `SCIMUtils`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/utils/SCIMUtils.java`

What it is:

- General utility class for SCIM parsing, JSON mapping, and helper logic.

Why it is important:

- This class is used across the project for many shared tasks.
- It helps explain custom attribute parsing and error handling.

What it does:

- holds the shared Jackson `ObjectMapper`
- parses custom user/group schema definitions
- parses custom resource configuration
- cleans requested attribute names before querying SCIM
- handles connector exceptions
- provides helper methods for path and field handling

Simple mental model:

- `SCIMUtils` = "shared low-level helper toolkit"

### `SCIMCustomResourceConfig`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/dto/SCIMCustomResourceConfig.java`

What it is:

- A configuration model describing a custom SCIM resource that should be exposed as a ConnId object class.

Why it is important:

- This is one of the key extension mechanisms in this repository.
- It lets you expose resources beyond standard Users and Groups without writing a whole new connector.

What it contains:

- SCIM schema/resource metadata
- endpoint name
- ConnId object class name
- id attribute
- name attribute
- optional handler class
- list of custom attributes

Simple mental model:

- `SCIMCustomResourceConfig` = "definition of one custom SCIM resource type"

### `SCIMCustomResourceService`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/v2/custom/SCIMCustomResourceService.java`

What it is:

- Service class that maps generic custom SCIM resources to ConnId operations.

Why it is important:

- This class explains how the project supports custom resources dynamically.
- It is the main implementation behind custom object classes.

What it does:

- converts `JsonNode` custom resource data into ConnId `ConnectorObject`
- builds create payloads for custom resources
- builds patch/update logic for custom resources
- applies optional custom handlers

Simple mental model:

- `SCIMCustomResourceService` = "adapter for custom SCIM resources"

### `SCIMv2CustomResourceHandler`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/v2/custom/SCIMv2CustomResourceHandler.java`

What it is:

- Extension interface for custom resource-specific logic.

Why it is important:

- It lets this connector support provider-specific special cases without hardcoding everything into the main flow.

What it can customize:

- request JSON before create
- patch payload before update
- returned ConnId attributes after read

Simple mental model:

- `SCIMv2CustomResourceHandler` = "plugin point for one custom resource type"

### `SCIMv2Patch` and `SCIMv2PatchOperation`

- Layer: repository code
- Files:
  - `src/main/java/net/tirasa/connid/bundles/scim/v2/dto/SCIMv2Patch.java`
  - `src/main/java/net/tirasa/connid/bundles/scim/v2/dto/SCIMv2PatchOperation.java`

What they are:

- Java models for SCIM v2 PATCH requests.

Why they are important:

- Update-delta behavior in ConnId is translated into these objects.
- If you want to understand how `AttributeDelta` becomes SCIM PATCH, these classes matter.

What they represent:

- operation type like `add`, `remove`, `replace`
- path to the target attribute
- value payload

Simple mental model:

- `SCIMv2Patch` = "whole patch request"
- `SCIMv2PatchOperation` = "one patch step"

### `BaseResourceReference`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/dto/BaseResourceReference.java`

What it is:

- A small DTO representing a SCIM reference to another resource.

Where it appears:

- group members
- group references on users
- entitlement references
- PATCH payloads involving references

What it contains:

- `value`
- `$ref`
- `display`
- `type`

Simple mental model:

- `BaseResourceReference` = "pointer to another SCIM object"

### `SCIMBaseResource`

- Layer: repository code
- File: `src/main/java/net/tirasa/connid/bundles/scim/common/dto/SCIMBaseResource.java`

What it is:

- Common interface for SCIM resource DTOs.

Why it is important:

- It defines the contract shared by users, groups, and other resource models.

What it requires:

- SCIM `schemas`
- `meta`
- `id`
- conversion to ConnId `Attribute`
- conversion from ConnId `Attribute`

Simple mental model:

- `SCIMBaseResource` = "common contract for SCIM resource models"

## Short Reading Order For Full Understanding

If your goal is to understand the whole project quickly, this order is better than reading files randomly:

1. `SCIMv2Connector`
2. `AbstractSCIMConnector`
3. `SCIMAttributeUtils`
4. `SCIMConnectorConfiguration`
5. `AbstractSCIMService`
6. `SCIMv2Client`
7. `SCIMv2User`
8. `SCIMv2Group`
9. `SCIMCustomResourceConfig`
10. `SCIMCustomResourceService`
11. `SCIMv2Patch` and `SCIMv2PatchOperation`

## Where To Read Next

If you want to understand the connector in the best order, read these next:

1. `src/main/java/net/tirasa/connid/bundles/scim/v2/SCIMv2Connector.java`
2. `src/main/java/net/tirasa/connid/bundles/scim/common/AbstractSCIMConnector.java`
3. `src/main/java/net/tirasa/connid/bundles/scim/common/utils/SCIMAttributeUtils.java`
4. `src/main/java/net/tirasa/connid/bundles/scim/v2/service/SCIMv2Client.java`
5. `src/main/java/net/tirasa/connid/bundles/scim/v2/dto/SCIMv2User.java`
6. `src/main/java/net/tirasa/connid/bundles/scim/v2/dto/SCIMv2Group.java`

## Notes For Future Questions

When you ask about another class later, it is useful to answer these five points:

1. Which layer does it belong to?
2. Is it a schema-definition class or a runtime-value class?
3. Is it used by midPoint directly, by ConnId, or only inside the connector?
4. Which methods create it?
5. Which methods consume it?
