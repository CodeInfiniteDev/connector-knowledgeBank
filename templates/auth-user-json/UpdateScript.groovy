import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*

import java.sql.Connection

/*
 * UpdateScript updates the auth_user row by __UID__ (login_id).
 *
 * It supports changes to:
 * - displayName
 * - email
 * - accountsJson
 * and always refreshes the last_modified column.
 */

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def attributes = attributes as Set<Attribute>
def uid = uid as Uid
def id = id as String
def configuration = configuration as ScriptedSQLConfiguration
def connection = connection as Connection
def schema = schema as Schema

log.info("Entering " + operation + " Script")

def sql = new Sql(connection)

switch (objectClass) {
    case ObjectClass.ACCOUNT:
        return handleAuthUserUpdate(sql)
    default:
        throw new ConnectorException("Unknown object class " + objectClass)
}

Uid handleAuthUserUpdate(Sql sql) {
    String loginId = uid.getUidValue()

    Map<String, Object> params = [:]

    for (Attribute attribute : attributes) {
        if (Uid.NAME.equals(attribute.getName())) {
            continue
        }

        Object value = attribute.getValue() != null && attribute.getValue().size() > 1
                ? attribute.getValue()
                : AttributeUtil.getSingleValue(attribute)

        switch (attribute.getName()) {
            case "displayName":
                params.put(BaseScript.COL_DISPLAY_NAME, value)
                break
            case "email":
                params.put(BaseScript.COL_EMAIL, value)
                break
            case "accountsJson":
                params.put(BaseScript.COL_ACCOUNTS_JSON, value)
                break
            default:
                // Ignore unknown attributes in this minimal template.
                break
        }
    }

    // Always bump last_modified when we touch the row.
    params.put(BaseScript.COL_UPDATED_AT, new java.sql.Timestamp(System.currentTimeMillis()))

    if (params.isEmpty()) {
        log.info("No updatable attributes provided for loginId {0}", loginId)
        return new Uid(loginId)
    }

    StringBuilder sb = new StringBuilder()
    sb.append("update ").append(BaseScript.TABLE_AUTH_USER).append(" set ")

    List<String> sets = []
    params.each { k, v ->
        sets.add("${k} = :${k}")
    }
    sb.append(sets.join(", "))
    sb.append(" where ").append(BaseScript.COL_LOGIN_ID).append(" = :loginId")

    Map<String, Object> fullParams = [:]
    fullParams.putAll(params)
    fullParams.put("loginId", loginId)

    sql.withTransaction {
        log.info("Executing UPDATE on {0} for loginId {1}", BaseScript.TABLE_AUTH_USER, loginId)
        int changed = sql.executeUpdate(sb.toString(), fullParams)
        log.ok("Updated {0} row(s) for loginId {1}", changed, loginId)
    }

    return new Uid(loginId)
}

