import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log

import java.sql.Connection

/*
 * TestScript is called when you press "Test connection" in midPoint.
 *
 * It verifies:
 * - JDBC connectivity
 * - visibility of the auth_user table
 */

def log = log as Log
def operation = operation as OperationType
def configuration = configuration as ScriptedSQLConfiguration
def connection = connection as Connection

log.info("Entering " + operation + " Script")

def sql = new Sql(connection)

sql.eachRow("select 1 from ${BaseScript.TABLE_AUTH_USER} limit 1") { /* ignore */ }

return null

