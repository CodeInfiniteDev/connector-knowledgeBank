import common.ScriptedSqlUtils
import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log

import java.sql.Connection

/*
 * TestScript is called when you press "Test connection" in midPoint.
 *
 * It should fail fast with a clear error if:
 * - JDBC connection is broken
 * - expected tables are not reachable
 */

def log = log as Log
def operation = operation as OperationType
def configuration = configuration as ScriptedSQLConfiguration
def connection = connection as Connection

log.info("Entering " + operation + " Script")

def sql = new Sql(connection)

ScriptedSqlUtils.testConnection(sql, [
        BaseScript.TABLE_USERS,
        BaseScript.TABLE_ROLES,
        BaseScript.TABLE_ORG_UNIT,
        BaseScript.TABLE_USER_ROLE
])

return null

