import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*
import org.identityconnectors.framework.common.objects.filter.Filter

import java.sql.Connection

/*
 * SearchScript implements:
 * - basic reconciliation for auth_user
 * - "get object" (the filter is currently ignored; all rows are returned)
 *
 * This is intentionally minimal: it selects all rows from auth_user and lets
 * midPoint filter further. You can extend it with proper ICF filter support later.
 */

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def configuration = configuration as ScriptedSQLConfiguration
def filter = filter as Filter
def connection = connection as Connection
def query = query as Closure
def handler = handler as ResultsHandler

log.info("Entering " + operation + " Script")

def sql = new Sql(connection)

switch (objectClass) {
    case ObjectClass.ACCOUNT:
        handleAuthUserSearch(sql)
        break
    default:
        throw new ConnectorException("Unknown object class " + objectClass)
}

return new SearchResult()

void handleAuthUserSearch(Sql sql) {
    Closure closure = { row ->
        ICFObjectBuilder.co {
            objectClass ObjectClass.ACCOUNT

            // __UID__ and __NAME__ both use user_login_id.
            uid row[BaseScript.COL_USER_LOGIN_ID] as String
            id row[BaseScript.COL_USER_LOGIN_ID]

            attribute 'loginId', row[BaseScript.COL_USER_LOGIN_ID]
            attribute 'userAccountId', row[BaseScript.COL_USER_ACCOUNT_ID]
            attribute 'userOid', row[BaseScript.COL_USER_OID]
            attribute 'accounts', row[BaseScript.COL_ACCOUNTS]
            attribute 'updatedAt', row[BaseScript.COL_UPDATED_AT]
        }
    }

    // Build as plain String to avoid treating the table name as a SQL parameter.
    String sqlQuery = "select * from " + BaseScript.TABLE_AUTH_USER

    sql.withTransaction {
        log.info("Executing SELECT on {0}", BaseScript.TABLE_AUTH_USER)
        sql.eachRow(sqlQuery) { row ->
            ConnectorObject obj = closure.call(row)
            handler.handle(obj)
        }
    }
}
