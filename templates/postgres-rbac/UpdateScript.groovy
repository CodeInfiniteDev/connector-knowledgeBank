import common.RbacSqlUtils
import common.ScriptedSqlUtils
import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*

import java.sql.Connection

/*
 * UpdateScript handles:
 * - normal column updates
 * - password updates (__PASSWORD__)
 * - enable/disable (__ENABLE__)
 * - association replacement (roleNames -> user_role join table)
 * - reference updates (orgUnit name -> users.org_unit_id)
 *
 * This template implements "replace" semantics for multi-valued roleNames:
 * - delete all memberships
 * - insert the new set
 *
 * If you need "add/remove one value" semantics (e.g. add role without sending full list),
 * you would implement additional connector operations (UpdateAttributeValuesOp /
 * RemoveAttributeValuesOp) and corresponding scripts. That is a bigger change.
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
        return handleAccount(sql)
    case BaseScript.ROLE:
        return handleRole(sql)
    case BaseScript.ORG_UNIT:
        return handleOrgUnit(sql)
    default:
        throw new ConnectorException("Unknown object class " + objectClass)
}

Uid handleAccount(Sql sql) {
    long userId = (uid.getUidValue() as Long)

    sql.withTransaction {
        Map<String, Object> params = [:]

        // Identity attributes should not be blindly persisted as columns.
        List skipAttributes = [Uid.NAME]

        Collection<String> newRoleNames = null

        for (Attribute attribute : attributes) {
            if (skipAttributes.contains(attribute.getName())) {
                continue
            }

            switch (attribute.getName()) {
                case "__ENABLE__":
                    params.put(BaseScript.COL_USER_DISABLED, !AttributeUtil.getBooleanValue(attribute))
                    break
                case "__PASSWORD__":
                    String clear = AttributeUtil.getGuardedStringValue(attribute).decryptChars().toString()
                    params.put(BaseScript.COL_USER_PASSWORD, RbacSqlUtils.passwordToStoredValue(clear))
                    break
                case "orgUnit":
                    String orgUnitName = AttributeUtil.getStringValue(attribute)
                    params.put(BaseScript.COL_USER_ORG_FK, RbacSqlUtils.resolveOrgUnitIdByName(sql, orgUnitName))
                    break
                case "roleNames":
                    // Association update is handled separately (join table).
                    newRoleNames = (Collection<String>) attribute.getValue()
                    break
                default:
                    // Map connector attribute name to DB column names.
                    // In this template we keep connector attribute names different from DB column names
                    // (givenName -> given_name). So we translate explicitly.
                    Object value = (attribute.getValue() != null && attribute.getValue().size() > 1) ?
                            attribute.getValue() :
                            AttributeUtil.getSingleValue(attribute)

                    switch (attribute.getName()) {
                        case "givenName":
                            params.put(BaseScript.COL_USER_GIVEN, value)
                            break
                        case "familyName":
                            params.put(BaseScript.COL_USER_FAMILY, value)
                            break
                        case "email":
                            params.put(BaseScript.COL_USER_EMAIL, value)
                            break
                        default:
                            // If you add more attributes, map them here.
                            params.put(attribute.getName(), value)
                    }
                    break
            }
        }

        // Keep updated_at current (helpful for sync).
        params.put(BaseScript.COL_USER_UPDATED_AT, new java.sql.Timestamp(System.currentTimeMillis()))

        ScriptedSqlUtils.buildAndExecuteUpdateQuery(
                sql,
                BaseScript.TABLE_USERS,
                params,
                [(BaseScript.COL_USER_UID): userId]
        )

        if (newRoleNames != null) {
            RbacSqlUtils.replaceUserRoles(sql, userId, newRoleNames)
        }
    }

    return new Uid(uid.getUidValue())
}

Uid handleRole(Sql sql) {
    long roleId = (uid.getUidValue() as Long)

    sql.withTransaction {
        Map<String, Object> params = [:]

        for (Attribute attribute : attributes) {
            Object value = (attribute.getValue() != null && attribute.getValue().size() > 1) ?
                    attribute.getValue() :
                    AttributeUtil.getSingleValue(attribute)

            switch (attribute.getName()) {
                case "description":
                    params.put(BaseScript.COL_ROLE_DESC, value)
                    break
                default:
                    params.put(attribute.getName(), value)
            }
        }

        params.put(BaseScript.COL_ROLE_UPDATED_AT, new java.sql.Timestamp(System.currentTimeMillis()))

        ScriptedSqlUtils.buildAndExecuteUpdateQuery(
                sql,
                BaseScript.TABLE_ROLES,
                params,
                [(BaseScript.COL_ROLE_UID): roleId]
        )
    }

    return new Uid(uid.getUidValue())
}

Uid handleOrgUnit(Sql sql) {
    long orgId = (uid.getUidValue() as Long)

    sql.withTransaction {
        Map<String, Object> params = [:]

        for (Attribute attribute : attributes) {
            Object value = (attribute.getValue() != null && attribute.getValue().size() > 1) ?
                    attribute.getValue() :
                    AttributeUtil.getSingleValue(attribute)

            switch (attribute.getName()) {
                case "parentName":
                    if (value == null) {
                        params.put(BaseScript.COL_ORG_PARENT_FK, null)
                    } else {
                        def row = sql.firstRow("select id from org_unit where name = :n", [n: value as String])
                        if (row == null) {
                            throw new ConnectorException("Unknown parent org unit name: " + value)
                        }
                        params.put(BaseScript.COL_ORG_PARENT_FK, (row.id as Number).longValue())
                    }
                    break
                default:
                    params.put(attribute.getName(), value)
            }
        }

        params.put(BaseScript.COL_ORG_UPDATED_AT, new java.sql.Timestamp(System.currentTimeMillis()))

        ScriptedSqlUtils.buildAndExecuteUpdateQuery(
                sql,
                BaseScript.TABLE_ORG_UNIT,
                params,
                [(BaseScript.COL_ORG_UID): orgId]
        )
    }

    return new Uid(uid.getUidValue())
}

