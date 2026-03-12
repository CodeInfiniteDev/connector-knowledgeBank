import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.Schema
import org.identityconnectors.framework.common.objects.Uid

import java.sql.Connection

/*
 * DeleteScript deletes from auth_user by __UID__ (login_id).
 */

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def uid = uid as Uid
def id = id as String
def configuration = configuration as ScriptedSQLConfiguration
def connection = connection as Connection
def schema = schema as Schema

log.info("Entering " + operation + " Script")

def sql = new Sql(connection)

switch (objectClass) {
    case ObjectClass.ACCOUNT:
        return handleDeleteAuthUser(sql, uid)
    default:
        throw new ConnectorException("Unknown object class " + objectClass)
}

Object handleDeleteAuthUser(Sql sql, Uid uid) {
    String loginId = uid.getUidValue()

    String query = "delete from ${BaseScript.TABLE_AUTH_USER} where ${BaseScript.COL_LOGIN_ID} = :loginId"
    Map params = [loginId: loginId]

    sql.withTransaction {
        log.info("Executing DELETE on {0} for loginId {1}", BaseScript.TABLE_AUTH_USER, loginId)
        int deleted = sql.executeUpdate(query, params)
        log.ok("Deleted {0} row(s) for loginId {1}", deleted, loginId)
    }

    return null
}

