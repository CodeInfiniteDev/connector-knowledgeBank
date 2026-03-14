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

    // Table name (change this if your real table name is different).
    public static final String TABLE_AUTH_USER = "auth_user"

    // Column names for the per-user auth view.
    //
    // This variant uses:
    // - user_login_id : primary key, ties to midPoint user login
    // - user_account_id : optional, e.g. primary account id in a main system
    // - user_oid : optional, midPoint user OID (for troubleshooting / joins)
    // - accounts : JSON string with all linked accounts for this user
    // - updated_at : last update timestamp
    public static final String COL_USER_LOGIN_ID   = "user_login_id"
    public static final String COL_USER_ACCOUNT_ID = "user_account_id"
    public static final String COL_USER_OID        = "user_oid"
    public static final String COL_ACCOUNTS        = "accounts"
    public static final String COL_UPDATED_AT      = "updated_at"

    @Override
    Object run() {
        return null
    }
}
