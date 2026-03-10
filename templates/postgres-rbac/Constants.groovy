import common.ColumnPrefixMapper

/**
 * Constants used by SearchScript / SyncScript (query building, filter translation, UID types).
 *
 * If you add JOINs and want filters to apply to joined tables, you will extend the
 * ColumnPrefixMapper instances here.
 */
class Constants {

    public static final ColumnPrefixMapper PREFIX_USER = new ColumnPrefixMapper("u")
    public static final ColumnPrefixMapper PREFIX_ROLE = new ColumnPrefixMapper("r")
    public static final ColumnPrefixMapper PREFIX_ORG = new ColumnPrefixMapper("o")

    // PostgreSQL `bigserial` maps to `Long` in Java/Groovy.
    public static final Class<?> UID_TYPE_USER = Long
    public static final Class<?> UID_TYPE_ROLE = Long
    public static final Class<?> UID_TYPE_ORG = Long

    public static final int SYNC_MAX_ROWS = 5000

    // Base queries (SELECT columns list is kept simple; SearchScript can add joins as needed).
    public static final String QUERY_USER_BASE =
            "select " + PREFIX_USER.defaultPrefix + ".* from " + BaseScript.TABLE_USERS + " " + PREFIX_USER.defaultPrefix

    public static final String QUERY_ROLE_BASE =
            "select " + PREFIX_ROLE.defaultPrefix + ".* from " + BaseScript.TABLE_ROLES + " " + PREFIX_ROLE.defaultPrefix

    public static final String QUERY_ORG_BASE =
            "select " + PREFIX_ORG.defaultPrefix + ".* from " + BaseScript.TABLE_ORG_UNIT + " " + PREFIX_ORG.defaultPrefix

    // Basic sync for users only (token = updated_at;id). See README for membership-change caveats.
    public static final String SYNC_USERS =
            "SELECT " +
                    PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UPDATED_AT + ", " +
                    PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UID + " " +
            "FROM " + BaseScript.TABLE_USERS + " " + PREFIX_USER.defaultPrefix + " " +
            "WHERE " +
            "    (" + PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UPDATED_AT + " = :ts AND " +
                       PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UID + " > :id) OR " +
            "    (" + PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UPDATED_AT + " > :ts) " +
            "ORDER BY " + PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UPDATED_AT + ", " +
                        PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UID

    public static final String SYNC_TOKEN_USERS =
            "SELECT " +
                    PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UPDATED_AT + ", " +
                    PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UID + " " +
            "FROM " + BaseScript.TABLE_USERS + " " + PREFIX_USER.defaultPrefix + " " +
            "ORDER BY " + PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UPDATED_AT + " DESC, " +
                        PREFIX_USER.defaultPrefix + "." + BaseScript.COL_USER_UID + " DESC " +
            "LIMIT 1"
}

