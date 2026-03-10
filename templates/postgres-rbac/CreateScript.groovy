import common.RbacSqlUtils
import common.ScriptedSqlUtils
import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*

import java.sql.Connection
import java.sql.SQLIntegrityConstraintViolationException

import static common.ScriptedSqlUtils.*

/*
 * CreateScript handles CREATE for all object classes (Account, Role, OrgUnit).
 *
 * You will see three distinct identities in play:
 * - id: the requested __NAME__ (string) passed by the connector for create
 * - uid: the returned __UID__ (stable DB PK)
 * - normal attributes: things like givenName, email, roleNames, ...
 */

def log = log as Log
def operation = operation as OperationType
def options = options as OperationOptions
def objectClass = objectClass as ObjectClass
def attributes = attributes as Set<Attribute>
def connection = connection as Connection
def id = id as String
def configuration = configuration as ScriptedSQLConfiguration

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
    // Map connector attributes -> DB columns.
    // This is where you implement your "reference" logic (orgUnit name -> FK id).
    Long orgUnitId = null
    String orgUnitName = getString(attributes, "orgUnit")
    if (orgUnitName != null) {
        orgUnitId = RbacSqlUtils.resolveOrgUnitIdByName(sql, orgUnitName)
    }

    Map<String, Object> params = [
            (BaseScript.COL_USER_NAME)     : id,
            (BaseScript.COL_USER_GIVEN)    : getString(attributes, "givenName"),
            (BaseScript.COL_USER_FAMILY)   : getString(attributes, "familyName"),
            (BaseScript.COL_USER_EMAIL)    : getString(attributes, "email"),
            (BaseScript.COL_USER_ORG_FK)   : orgUnitId,
            (BaseScript.COL_USER_PASSWORD) : RbacSqlUtils.passwordToStoredValue(getPassword(attributes, "__PASSWORD__")),
            (BaseScript.COL_USER_DISABLED) : !(getBoolean(attributes, "__ENABLE__") ?: true),
            (BaseScript.COL_USER_UPDATED_AT): new java.sql.Timestamp(System.currentTimeMillis())
    ]

    def uid = null
    sql.withTransaction {
        try {
            def ret = ScriptedSqlUtils.buildAndExecuteInsertQuery(sql, BaseScript.TABLE_USERS, params)
            uid = new Uid(String.valueOf(ret[0][0]))

            // Association on create (optional):
            // if midPoint provides roleNames during create, create memberships now.
            Attribute rolesAttr = AttributeUtil.find("roleNames", attributes)
            if (rolesAttr != null) {
                Collection<String> roleNames = (Collection<String>) rolesAttr.getValue()
                RbacSqlUtils.replaceUserRoles(sql, (uid.getUidValue() as Long), roleNames)
            }
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new AlreadyExistsException("Account with name " + id + " already exists", ex)
        }
    }
    return uid
}

Uid handleRole(Sql sql) {
    Map<String, Object> params = [
            (BaseScript.COL_ROLE_NAME)      : id,
            (BaseScript.COL_ROLE_DESC)      : getString(attributes, "description"),
            (BaseScript.COL_ROLE_UPDATED_AT): new java.sql.Timestamp(System.currentTimeMillis())
    ]

    def uid = null
    sql.withTransaction {
        try {
            def ret = ScriptedSqlUtils.buildAndExecuteInsertQuery(sql, BaseScript.TABLE_ROLES, params)
            uid = new Uid(String.valueOf(ret[0][0]))
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new AlreadyExistsException("Role with name " + id + " already exists", ex)
        }
    }
    return uid
}

Uid handleOrgUnit(Sql sql) {
    // If you want to create org unit trees from midPoint, you can resolve parentName -> parent_id here.
    Long parentId = null
    String parentName = getString(attributes, "parentName")
    if (parentName != null) {
        def row = sql.firstRow("select id from org_unit where name = :n", [n: parentName])
        if (row == null) {
            throw new ConnectorException("Unknown parent org unit name: " + parentName)
        }
        parentId = (row.id as Number).longValue()
    }

    Map<String, Object> params = [
            (BaseScript.COL_ORG_NAME)       : id,
            (BaseScript.COL_ORG_PARENT_FK)  : parentId,
            (BaseScript.COL_ORG_UPDATED_AT) : new java.sql.Timestamp(System.currentTimeMillis())
    ]

    def uid = null
    sql.withTransaction {
        try {
            def ret = ScriptedSqlUtils.buildAndExecuteInsertQuery(sql, BaseScript.TABLE_ORG_UNIT, params)
            uid = new Uid(String.valueOf(ret[0][0]))
        } catch (SQLIntegrityConstraintViolationException ex) {
            throw new AlreadyExistsException("Org unit with name " + id + " already exists", ex)
        }
    }
    return uid
}

