import common.RbacSqlUtils
import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.ConnectorObject
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.ResultsHandler
import org.identityconnectors.framework.common.objects.SearchResult
import org.identityconnectors.framework.common.objects.filter.Filter

import java.sql.Connection

import static common.ScriptedSqlUtils.*

/*
 * SearchScript implements:
 * - Search (reconciliation) for users/roles/org units
 * - "Get object" (when filter targets __UID__ or __NAME__)
 *
 * Performance note:
 * - Associations (roleNames) require extra queries.
 * - This template fetches roleNames only if requested via attributesToGet
 *   OR if attributesToGet is null (which means "connector decides").
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
        handleAccount(sql)
        break
    case BaseScript.ROLE:
        handleRole(sql)
        break
    case BaseScript.ORG_UNIT:
        handleOrgUnit(sql)
        break
    default:
        throw new ConnectorException("Unknown object class " + objectClass)
}

return new SearchResult()

// =================================================================================

private boolean wantsAttribute(String name) {
    def atg = options?.attributesToGet
    if (atg == null) {
        return true
    }
    if (atg instanceof String[]) {
        return (atg as String[]).toList().contains(name)
    }
    if (atg instanceof Collection) {
        return ((Collection) atg).contains(name)
    }
    return true
}

static ConnectorObject buildAccount(Sql sql, GroovyObject row, List<String> roleNamesOrNull) {
    // Join to org_unit to expose orgUnit by name.
    // Note: row may contain `org_unit_name` from the query.
    def orgUnitName = row.org_unit_name

    return ICFObjectBuilder.co {
        objectClass ObjectClass.ACCOUNT

        uid row.id as String
        id row.username

        // midPoint activation: __ENABLE__ (Boolean) is the standard attribute.
        // Here we map DB "disabled" to __ENABLE__.
        attribute '__ENABLE__', !(row.disabled as Boolean)

        // Password should normally be write-only. Do not return it unless you have a concrete need.
        // attribute '__PASSWORD__', stringToGuarded(row.password_hash as String)

        attribute 'givenName', row.given_name
        attribute 'familyName', row.family_name
        attribute 'email', row.email
        attribute 'orgUnit', orgUnitName

        if (roleNamesOrNull != null) {
            attribute 'roleNames', roleNamesOrNull
        }
    }
}

void handleAccount(Sql sql) {
    Closure closure = { row ->
        List<String> roleNames = null
        if (wantsAttribute("roleNames")) {
            roleNames = RbacSqlUtils.getRoleNamesForUser(sql, (row.id as Number).longValue())
        }
        buildAccount(sql, row, roleNames)
    }

    // We join org_unit to expose org name while still keeping UID/NAME in users table.
    String sqlQuery =
            "select u.*, o.name as org_unit_name " +
            "from " + BaseScript.TABLE_USERS + " u " +
            "left join " + BaseScript.TABLE_ORG_UNIT + " o on o.id = u." + BaseScript.COL_USER_ORG_FK

    Map params = [:]
    String where = buildWhereClause(filter, params,
            BaseScript.COL_USER_UID,
            BaseScript.COL_USER_NAME,
            Constants.PREFIX_USER,
            Constants.UID_TYPE_USER)
    if (!where.isEmpty()) {
        sqlQuery += " where " + where
    }

    sql.withTransaction {
        executeQuery(sqlQuery, params, options, closure, handler, sql)
    }
}

void handleRole(Sql sql) {
    Closure closure = { row ->
        ICFObjectBuilder.co {
            objectClass BaseScript.ROLE

            uid row.id as String
            id row.name
            attribute 'description', row.description
        }
    }

    String sqlQuery = Constants.QUERY_ROLE_BASE
    Map params = [:]

    String where = buildWhereClause(filter, params,
            BaseScript.COL_ROLE_UID,
            BaseScript.COL_ROLE_NAME,
            Constants.PREFIX_ROLE,
            Constants.UID_TYPE_ROLE)
    if (!where.isEmpty()) {
        sqlQuery += " where " + where
    }

    sql.withTransaction {
        executeQuery(sqlQuery, params, options, closure, handler, sql)
    }
}

void handleOrgUnit(Sql sql) {
    Closure closure = { row ->
        // Translate parent_id -> parentName (more usable in midPoint).
        String parentName = null
        if (row.parent_id != null) {
            def p = sql.firstRow("select name from org_unit where id = :id", [id: (row.parent_id as Number).longValue()])
            parentName = p?.name as String
        }

        ICFObjectBuilder.co {
            objectClass BaseScript.ORG_UNIT

            uid row.id as String
            id row.name
            attribute 'parentName', parentName
        }
    }

    String sqlQuery = Constants.QUERY_ORG_BASE
    Map params = [:]

    String where = buildWhereClause(filter, params,
            BaseScript.COL_ORG_UID,
            BaseScript.COL_ORG_NAME,
            Constants.PREFIX_ORG,
            Constants.UID_TYPE_ORG)
    if (!where.isEmpty()) {
        sqlQuery += " where " + where
    }

    sql.withTransaction {
        executeQuery(sqlQuery, params, options, closure, handler, sql)
    }
}
