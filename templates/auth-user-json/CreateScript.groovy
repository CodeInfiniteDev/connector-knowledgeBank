import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*

import java.sql.Connection
import java.sql.SQLIntegrityConstraintViolationException

/*
 * CreateScript handles CREATE for the single auth_user object class.
 *
 * In this template:
 * - __NAME__ == login_id (string primary key)
 * - __UID__  == login_id (same value, stable)
 *
 * JSON for accounts is expected to be provided by midPoint as `accountsJson`
 * attribute; the connector simply persists it.
 */

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def attributes = attributes as Set<Attribute>
def connection = connection as Connection
def id = id as String              // requested __NAME__
def configuration = configuration as ScriptedSQLConfiguration

log.info("Entering " + operation + " Script")

def sql = new Sql(connection)

switch (objectClass) {
    case ObjectClass.ACCOUNT:
        return handleAuthUserCreate(sql)
    default:
        throw new ConnectorException("Unknown object class " + objectClass)
}

Uid handleAuthUserCreate(Sql sql) {
    String loginId = id

    String displayName = AttributeUtil.getStringValue(AttributeUtil.find("displayName", attributes))
    String email       = AttributeUtil.getStringValue(AttributeUtil.find("email", attributes))
    String accountsJson = AttributeUtil.getStringValue(AttributeUtil.find("accountsJson", attributes))

    Map params = [
            (BaseScript.COL_LOGIN_ID)     : loginId,
            (BaseScript.COL_DISPLAY_NAME) : displayName,
            (BaseScript.COL_EMAIL)        : email,
            (BaseScript.COL_ACCOUNTS_JSON): accountsJson,
            (BaseScript.COL_UPDATED_AT)   : new java.sql.Timestamp(System.currentTimeMillis())
    ]

    String query = """
        insert into ${BaseScript.TABLE_AUTH_USER}
            (${BaseScript.COL_LOGIN_ID}, ${BaseScript.COL_DISPLAY_NAME},
             ${BaseScript.COL_EMAIL}, ${BaseScript.COL_ACCOUNTS_JSON},
             ${BaseScript.COL_UPDATED_AT})
        values (:loginId, :displayName, :email, :accountsJson, :updatedAt)
    """

    sql.withTransaction {
        try {
            log.info("Executing INSERT into {0} for loginId {1}", BaseScript.TABLE_AUTH_USER, loginId)
            sql.executeInsert(query, params)
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new AlreadyExistsException("Auth user with loginId " + loginId + " already exists", ex)
        }
    }

    // We choose __UID__ == login_id for simplicity.
    return new Uid(loginId)
}

