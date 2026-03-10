package common

import groovy.sql.Sql
import org.identityconnectors.framework.common.exceptions.ConnectorException

import java.sql.Timestamp

/**
 * Helpers specific to this RBAC template:
 * - resolve FK by name (org unit, role)
 * - read / replace user-role membership (association implementation)
 * - password storage conversion hook
 */
class RbacSqlUtils {

    /**
     * Convert clear password to stored value.
     *
     * Replace this with a secure hash:
     * - BCrypt / SCrypt / Argon2
     * - or call your DB/system procedure
     *
     * Important: midPoint treats __PASSWORD__ as write-only. You should NOT return password back
     * from SearchScript in real deployments.
     */
    static String passwordToStoredValue(String clearPassword) {
        return clearPassword
    }

    static Long resolveOrgUnitIdByName(Sql sql, String orgUnitName) {
        if (orgUnitName == null || orgUnitName.trim().isEmpty()) {
            return null
        }

        def row = sql.firstRow(
                "select id from org_unit where name = :name",
                [name: orgUnitName]
        )
        if (row == null) {
            throw new ConnectorException("Unknown org unit name: " + orgUnitName)
        }
        return (row.id as Number).longValue()
    }

    static Long resolveRoleIdByName(Sql sql, String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            throw new ConnectorException("Role name is empty")
        }

        def row = sql.firstRow(
                "select id from roles where name = :name",
                [name: roleName]
        )
        if (row == null) {
            throw new ConnectorException("Unknown role name: " + roleName)
        }
        return (row.id as Number).longValue()
    }

    static List<String> getRoleNamesForUser(Sql sql, long userId) {
        List<String> names = []
        sql.eachRow(
                "select r.name " +
                        "from user_role ur " +
                        "join roles r on r.id = ur.role_id " +
                        "where ur.user_id = :uid " +
                        "order by r.name",
                [uid: userId]
        ) { row ->
            names.add(row.name as String)
        }
        return names
    }

    /**
     * Replace all user-role memberships with the provided role names.
     *
     * This matches the semantics of UpdateOp in the example scripts:
     * - you get a Set<Attribute> (not add/remove deltas)
     * - easiest correct behavior is "replace"
     */
    static void replaceUserRoles(Sql sql, long userId, Collection<String> roleNames) {
        // Normalize input to a stable set.
        Set<String> normalized = new LinkedHashSet<>()
        if (roleNames != null) {
            for (String r : roleNames) {
                if (r == null) {
                    continue
                }
                String trimmed = r.trim()
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed)
                }
            }
        }

        sql.executeUpdate(
                "delete from user_role where user_id = :uid",
                [uid: userId]
        )

        if (normalized.isEmpty()) {
            return
        }

        Timestamp now = new Timestamp(System.currentTimeMillis())
        for (String roleName : normalized) {
            long roleId = resolveRoleIdByName(sql, roleName)
            sql.executeInsert(
                    "insert into user_role(user_id, role_id, updated_at) values(:uid, :rid, :ts)",
                    [uid: userId, rid: roleId, ts: now]
            )
        }
    }
}

