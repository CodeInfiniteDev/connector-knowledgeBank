import org.identityconnectors.framework.common.objects.ObjectClass

/**
 * BaseScript is configured as `scriptBaseClass` in the midPoint resource.
 *
 * That means:
 * - every operation script (CreateScript, SearchScript, ...) can use these constants directly
 * - you keep table names / object class names in one place
 *
 * Adapt this file first when you move to your real database schema.
 */
class BaseScript extends Script {

    // Object class names as midPoint will see them in the connector schema.
    public static final String ROLE_NAME = "Role"
    public static final ObjectClass ROLE = new ObjectClass(ROLE_NAME)

    public static final String ORG_UNIT_NAME = "OrgUnit"
    public static final ObjectClass ORG_UNIT = new ObjectClass(ORG_UNIT_NAME)

    // Table names (PostgreSQL example).
    public static final String TABLE_USERS = "users"
    public static final String TABLE_ROLES = "roles"
    public static final String TABLE_ORG_UNIT = "org_unit"
    public static final String TABLE_USER_ROLE = "user_role"

    // Column names (adapt freely).
    public static final String COL_USER_UID = "id"
    public static final String COL_USER_NAME = "username"
    public static final String COL_USER_GIVEN = "given_name"
    public static final String COL_USER_FAMILY = "family_name"
    public static final String COL_USER_EMAIL = "email"
    public static final String COL_USER_ORG_FK = "org_unit_id"
    public static final String COL_USER_DISABLED = "disabled"
    public static final String COL_USER_PASSWORD = "password_hash"
    public static final String COL_USER_UPDATED_AT = "updated_at"

    public static final String COL_ROLE_UID = "id"
    public static final String COL_ROLE_NAME = "name"
    public static final String COL_ROLE_DESC = "description"
    public static final String COL_ROLE_UPDATED_AT = "updated_at"

    public static final String COL_ORG_UID = "id"
    public static final String COL_ORG_NAME = "name"
    public static final String COL_ORG_PARENT_FK = "parent_id"
    public static final String COL_ORG_UPDATED_AT = "updated_at"

    public static final String COL_UR_USER_FK = "user_id"
    public static final String COL_UR_ROLE_FK = "role_id"

    @Override
    Object run() {
        return null
    }
}

