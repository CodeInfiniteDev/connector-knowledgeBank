import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder
import org.identityconnectors.framework.spi.operations.SearchOp

/*
 * SchemaScript defines what midPoint can "see" on this ScriptedSQL resource.
 *
 * This template exposes a single object class (__ACCOUNT__) that represents the
 * consolidated auth view row in the `auth_user` table.
 */

def log = log as Log
def operation = operation as OperationType
def builder = builder as ICFObjectBuilder
def configuration = configuration as ScriptedSQLConfiguration

log.info("Entering " + operation + " Script")

builder.schema({
    // ===========================
    //  ACCOUNT / auth_user table
    // ===========================
    objectClass {
        type ObjectClass.ACCOUNT_NAME
        attributes {
            // Business attributes.
            //
            // - __NAME__ / __UID__ will both map to login_id in this template.
            // - These attribute names are what midPoint mappings use.
            loginId()
            displayName()
            email()

            // This is the JSON string column that contains all external accounts:
            // [ { "resource": "...", "accountId": "..." }, ... ]
            accountsJson()

            // Optional, but often useful for troubleshooting and sync.
            lastModified()
        }
    }

    // Paged search option (offset-based).
    defineOperationOption OperationOptionInfoBuilder.buildPagedResultsOffset(), SearchOp
})

