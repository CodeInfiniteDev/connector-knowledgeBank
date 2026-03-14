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
    // Prefer explicit loginId attribute; fall back to __NAME__ (id).
    String loginId = id

    def loginAttr = AttributeUtil.find("loginId", attributes)
    if (loginAttr != null) {
        loginId = AttributeUtil.getStringValue(loginAttr)
    }

    if (loginId == null || loginId.trim().isEmpty()) {
        throw new ConnectorException("Required attribute loginId is missing or empty for CREATE")
    }

    // Attributes may be missing in some flows; handle nulls gracefully instead of NPE.
    String userAccountId = null
    String userOid = null
    String accounts = null

    def userAccountAttr = AttributeUtil.find("userAccountId", attributes)
    if (userAccountAttr != null) {
        userAccountId = AttributeUtil.getStringValue(userAccountAttr)
    }

    def userOidAttr = AttributeUtil.find("userOid", attributes)
    if (userOidAttr != null) {
        userOid = AttributeUtil.getStringValue(userOidAttr)
    }

    def accountsAttr = AttributeUtil.find("accounts", attributes)
    if (accountsAttr != null) {
        accounts = AttributeUtil.getStringValue(accountsAttr)
    }

    Map params = [
            (BaseScript.COL_USER_LOGIN_ID)   : loginId,
            (BaseScript.COL_USER_ACCOUNT_ID) : userAccountId,
            (BaseScript.COL_USER_OID)        : userOid,
            (BaseScript.COL_ACCOUNTS)        : accounts,
            (BaseScript.COL_UPDATED_AT)      : new java.sql.Timestamp(System.currentTimeMillis())
    ]

    // Build SQL as a plain String so table/column names are inlined, and only the
    // value placeholders use named parameters.
    String query =
            "insert into " + BaseScript.TABLE_AUTH_USER +
            " (" +
                BaseScript.COL_USER_LOGIN_ID + ", " +
                BaseScript.COL_USER_ACCOUNT_ID + ", " +
                BaseScript.COL_USER_OID + ", " +
                BaseScript.COL_ACCOUNTS + ", " +
                BaseScript.COL_UPDATED_AT +
            ") values (" +
                ":user_login_id, :user_account_id, :user_oid, :accounts, :updated_at" +
            ")"

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
