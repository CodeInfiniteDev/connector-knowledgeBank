import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import org.forgerock.openicf.connectors.scriptedsql.ScriptedSQLConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.StringUtil
import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.exceptions.ConnectorException
import org.identityconnectors.framework.common.objects.*

import java.sql.Timestamp
import java.util.function.BiFunction
import java.util.function.Function

/*
 * SyncScript implements Live Sync (midPoint capability liveSync).
 *
 * Token design:
 * - token = "updated_at;id"
 * - this avoids missing rows when multiple updates share the same timestamp
 *
 * Important limitations (by design in this template):
 * - This sync tracks only changes in `users.updated_at`.
 * - It will NOT automatically detect changes in `user_role` unless your DB logic updates
 *   `users.updated_at` when membership changes.
 *
 * For a production-grade sync model, prefer a change log table with triggers.
 */

def configuration = configuration as ScriptedSQLConfiguration
def operation = operation as OperationType
def objectClass = objectClass as ObjectClass
def log = log as Log

log.info("Entering " + operation + " Script")

def sql = new Sql(connection)

switch (operation) {
    case OperationType.SYNC:
        def token = token as Object
        def handler = handler as SyncResultsHandler
        handleSync(sql, token, handler)
        break
    case OperationType.GET_LATEST_SYNC_TOKEN:
        return handleGetLatestSyncToken(sql)
}

void handleSync(Sql sql, Object tokenObject, SyncResultsHandler handler) {
    String token = (String) tokenObject

    switch (objectClass) {
        case ObjectClass.ACCOUNT:
            handleSyncUsers(sql, token, handler)
            break
        default:
            throw new ConnectorException("Unsupported object class for sync: " + objectClass)
    }
}

Object handleGetLatestSyncToken(Sql sql) {
    switch (objectClass) {
        case ObjectClass.ACCOUNT:
            return handleSyncTokenUsers(sql)
        default:
            throw new ConnectorException("Unsupported object class for token: " + objectClass)
    }
}

private void handleSyncUsers(Sql sql, String token, SyncResultsHandler handler) {
    handleSyncGeneric(
            sql,
            token,
            handler,
            Constants.SYNC_USERS,
            { t -> buildParamsFromToken(t) },
            syncTokenRowTransform(),
            { sql1, row -> buildUserObject(sql1, row) },
            ObjectClass.ACCOUNT
    )
}

private Object handleSyncTokenUsers(Sql sql) {
    return handleSyncTokenGeneric(sql, Constants.SYNC_TOKEN_USERS, syncTokenRowTransform())
}

private Map buildParamsFromToken(String token) {
    String[] array = token.split(";")
    return [
            ts: Timestamp.valueOf(array[0]),
            id: array[1] as Long
    ]
}

private Closure syncTokenRowTransform() {
    return { row ->
        [
                row.get(BaseScript.COL_USER_UPDATED_AT).toString(),
                row.get(BaseScript.COL_USER_UID)?.toString()
        ]
    }
}

private ConnectorObject buildUserObject(Sql sql, GroovyRowResult rowResult) {
    String query =
            "select u.*, o.name as org_unit_name " +
            "from " + BaseScript.TABLE_USERS + " u " +
            "left join " + BaseScript.TABLE_ORG_UNIT + " o on o.id = u." + BaseScript.COL_USER_ORG_FK + " " +
            "where u." + BaseScript.COL_USER_UID + " = :id"

    Map params = [id: (rowResult.get(BaseScript.COL_USER_UID) as Number).longValue()]

    log.info("Executing sync query to build user object {0} with params {1}", query, params)

    List<GroovyRowResult> rows = sql.rows(params, query, 0, 1)
    if (rows == null || rows.isEmpty()) {
        log.info("Couldn't find user for specified identifier {0}", params)
        return null
    }

    // Reuse the same builder logic as SearchScript.
    return SearchScript.buildAccount(sql, rows.get(0), null)
}

private void handleSyncGeneric(Sql sql, String token, SyncResultsHandler handler, String query,
                               Function<String, Map> buildParamsFromToken,
                               Function<GroovyRowResult, List<String>> buildTokenFromRow,
                               BiFunction<Sql, GroovyRowResult, ConnectorObject> buildConnectorObject,
                               ObjectClass oc) {
    if (token == null) {
        return
    }

    Map params = buildParamsFromToken.apply(token)
    int countProcessed = 0

    List<GroovyRowResult> results
    outer:
    while (true) {
        log.info("Executing sync query {0} with params {1}", query, params)

        sql.withTransaction {
            results = sql.rows(params, query, 1, Constants.SYNC_MAX_ROWS)
        }

        if (results == null || results.isEmpty()) {
            log.info("Nothing found in sync queue")
            break
        }

        for (GroovyRowResult result : results) {
            ConnectorObject object
            String newToken
            sql.withTransaction {
                object = buildConnectorObject.apply(sql, result)
                newToken = buildSyncToken(result, buildTokenFromRow)
            }

            if (object == null) {
                continue
            }

            SyncDelta delta = buildSyncDelta(SyncDeltaType.CREATE_OR_UPDATE, oc, newToken, object)

            if (!handler.handle(delta)) {
                log.info("Handler paused processing")
                break outer
            }

            params = buildParamsFromToken.apply(newToken)
            countProcessed++
        }
    }

    log.info("Synchronization done, processed {0} events", countProcessed)
}

private Object handleSyncTokenGeneric(Sql sql, String query, Function<GroovyRowResult, List<String>> rowTransform) {
    String result

    sql.withTransaction {
        log.ok("Executing get-latest-token query {0}", query)

        GroovyRowResult row = sql.firstRow(query)
        if (row == null) {
            row = sql.firstRow("select now() as updated_at, 0 as id")
        }

        result = buildSyncToken(row, rowTransform)
        log.info("Created token: {0}", result)
    }

    return result
}

private String buildSyncToken(Map row, Function<GroovyRowResult, List<String>> rowTransform) {
    if (row == null) {
        return null
    }

    List<String> values = rowTransform.apply(row)
    if (values == null || values.isEmpty()) {
        return null
    }

    if (values.size() == 1) {
        return values.get(0)
    }

    return StringUtil.join(values, (char) ';')
}

private SyncDelta buildSyncDelta(SyncDeltaType type, ObjectClass oc, Object newToken, ConnectorObject obj) {
    SyncDeltaBuilder builder = new SyncDeltaBuilder()
    builder.setDeltaType(type)
    builder.setObjectClass(oc)

    if (newToken != null) {
        builder.setToken(new SyncToken(newToken))
    }

    builder.setObject(obj)
    builder.setUid(obj.getUid())
    return builder.build()
}

