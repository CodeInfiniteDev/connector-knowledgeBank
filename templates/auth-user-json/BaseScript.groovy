import org.identityconnectors.framework.common.objects.ObjectClass

/**
 * BaseScript is configured as `scriptBaseClass` for the auth_user JSON template.
 *
 * It centralizes:
 * - object class name
 * - table and column names
 *
 * Adapt this file first when you change your real database schema.
 */
class BaseScript extends Script {

    // We expose a single object class: the "auth view" of a midPoint user.
    public static final String AUTH_USER_NAME = ObjectClass.ACCOUNT_NAME
    public static final ObjectClass AUTH_USER = ObjectClass.ACCOUNT

    // Table name.
    public static final String TABLE_AUTH_USER = "auth_user"

    // Column names (PostgreSQL-style example).
    public static final String COL_LOGIN_ID      = "login_id"
    public static final String COL_DISPLAY_NAME  = "display_name"
    public static final String COL_EMAIL         = "email"
    public static final String COL_ACCOUNTS_JSON = "accounts_json"
    public static final String COL_UPDATED_AT    = "last_modified"

    @Override
    Object run() {
        return null
    }
}

