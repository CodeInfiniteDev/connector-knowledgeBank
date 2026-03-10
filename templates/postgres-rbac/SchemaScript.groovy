import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos
import org.identityconnectors.framework.spi.operations.SearchOp

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.MULTIVALUED
import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.NOT_RETURNED_BY_DEFAULT

/*
 * SchemaScript defines what midPoint can "see" on this connector.
 *
 * Think of it as the contract:
 * - object classes
 * - attributes
 * - flags (multi-valued, required, not-returned-by-default, etc.)
 * - operation options (paging, sorting, ...)
 *
 * If you want associations and references, this is where you expose the attributes
 * that midPoint will later treat as associations/references.
 */

def log = log as Log
def operation = operation as OperationType
def builder = builder as ICFObjectBuilder
def configuration = configuration as ScriptedSQLConfiguration

log.info("Entering " + operation + " Script")

builder.schema({
    // =======================
    //  ACCOUNT / Users table
    // =======================
    objectClass {
        type ObjectClass.ACCOUNT_NAME
        attributes {
            // Your normal "business" attributes.
            givenName()
            familyName()
            email()

            // Reference-like attribute: user -> org unit name.
            // SearchScript resolves it from FK, Create/Update resolve it to FK.
            orgUnit()

            // Association attribute: user -> role names (entitlements).
            // - Connector side: just a multi-valued string attribute.
            // - midPoint side: define it as an association in schemaHandling.
            roleNames String.class, MULTIVALUED

            // Operational attributes.
            OperationalAttributeInfos.ENABLE
            OperationalAttributeInfos.PASSWORD

            // Optional operational attribute if you implement it.
            // OperationalAttributeInfos.LOCK_OUT
        }
    }

    // ======================
    //  Roles / entitlements
    // ======================
    objectClass {
        type BaseScript.ROLE_NAME
        attributes {
            // Simple role metadata.
            description()

            // Example of a "computed" attribute you might want to expose but not fetch by default.
            // (In this template we don't implement it in SearchScript; it's just a pattern.)
            roleMemberCount Integer.class, NOT_RETURNED_BY_DEFAULT
        }
    }

    // ======================
    //  Org units
    // ======================
    objectClass {
        type BaseScript.ORG_UNIT_NAME
        attributes {
            // org unit tree: parentName is a simple way to reconstruct the tree in midPoint.
            parentName()
        }
    }

    // Paged search (recommended for large tables).
    defineOperationOption OperationOptionInfoBuilder.buildPagedResultsOffset(), SearchOp
    // defineOperationOption OperationOptionInfoBuilder.buildPageSize(), SearchOp
})
