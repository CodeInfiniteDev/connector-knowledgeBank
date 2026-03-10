import common.ScriptedSqlUtils
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
 * DeleteScript deletes by __UID__ (stable DB PK).
 *
 * In relational schemas, remember to handle dependent rows:
 * - user_role join table should be cleaned up (either via ON DELETE CASCADE or explicit delete).
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
        return handleDelete(sql, BaseScript.TABLE_USERS, BaseScript.COL_USER_UID, uid)
    case BaseScript.ROLE:
        return handleDelete(sql, BaseScript.TABLE_ROLES, BaseScript.COL_ROLE_UID, uid)
    case BaseScript.ORG_UNIT:
        return handleDelete(sql, BaseScript.TABLE_ORG_UNIT, BaseScript.COL_ORG_UID, uid)
    default:
        throw new ConnectorException("Unknown object class " + objectClass)
}

Object handleDelete(Sql sql, String table, String uidColumn, Uid uid) {
    long pk = (uid.getUidValue() as Long)

    sql.withTransaction {
        // If you do not have FK cascades, delete join-table rows explicitly here.
        if (table == BaseScript.TABLE_USERS) {
            sql.executeUpdate("delete from " + BaseScript.TABLE_USER_ROLE + " where " + BaseScript.COL_UR_USER_FK + " = :uid", [uid: pk])
        }

        ScriptedSqlUtils.buildAndExecuteDeleteQuery(sql, table, [(uidColumn): pk])
    }

    return null
}
