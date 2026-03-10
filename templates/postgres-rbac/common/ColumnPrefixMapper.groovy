package common

/**
 * Helps filter-to-SQL translation by providing:
 * - default table alias (prefix)
 * - column overrides (connector attribute name -> DB column name)
 * - per-column prefix overrides (useful for JOIN queries)
 */
class ColumnPrefixMapper {

    String defaultPrefix
    Map<String, String> columns = [:]
    Map<String, String> prefixes = [:]

    ColumnPrefixMapper(String defaultPrefix) {
        this.defaultPrefix = defaultPrefix
    }
}

