package common

import org.identityconnectors.common.logging.Log
import org.identityconnectors.framework.common.objects.Attribute
import org.identityconnectors.framework.common.objects.AttributeUtil
import org.identityconnectors.framework.common.objects.Name
import org.identityconnectors.framework.common.objects.Uid
import org.identityconnectors.framework.common.objects.filter.*

import java.sql.Timestamp
import java.time.ZonedDateTime

/**
 * Translates ConnId/ICF filters (from midPoint searches) into SQL WHERE fragments.
 *
 * Notes:
 * - It maps connector identity attributes:
 *   - __UID__ -> your chosen UID column (e.g. "id")
 *   - __NAME__ -> your chosen NAME column (e.g. "username")
 * - It supports basic comparisons and LIKE patterns.
 * - For complex joins/associations, you usually:
 *   - add JOINs in SearchScript
 *   - configure ColumnPrefixMapper so filters target the right alias/column
 */
class ScriptedSqlFilterVisitor implements FilterVisitor<String, Void> {

    private static Log LOG = Log.getLog(ScriptedSqlFilterVisitor.class)

    private static final int DISCRIMINATOR_MAX_BUCKET = 10000

    private final Map<String, Object> params
    private final String uidColumn
    private final String nameColumn
    private final ColumnPrefixMapper columnMapper
    private final Class<?> uidType

    ScriptedSqlFilterVisitor(Map<String, Object> params, String uidColumn, String nameColumn, ColumnPrefixMapper columnMapper, Class<?> uidType) {
        this.params = params
        this.uidColumn = uidColumn
        this.nameColumn = nameColumn
        this.columnMapper = columnMapper
        this.uidType = uidType
    }

    @Override
    String visitOrFilter(Void v, OrFilter filter) {
        return visitFilter(filter, "or")
    }

    @Override
    String visitAndFilter(Void v, AndFilter filter) {
        return visitFilter(filter, "and")
    }

    @Override
    String visitNotFilter(Void v, NotFilter filter) {
        return "not (" + filter.getFilter().accept(this, null) + ")"
    }

    @Override
    String visitContainsFilter(Void v, ContainsFilter filter) {
        return visitFilter(filter, "CONTAINS")
    }

    @Override
    String visitEndsWithFilter(Void v, EndsWithFilter filter) {
        return visitFilter(filter, "ENDSWITH")
    }

    @Override
    String visitEqualsFilter(Void v, EqualsFilter filter) {
        return visitFilter(filter, "EQUALS")
    }

    @Override
    String visitGreaterThanFilter(Void v, GreaterThanFilter filter) {
        return visitFilter(filter, "GREATERTHAN")
    }

    @Override
    String visitGreaterThanOrEqualFilter(Void v, GreaterThanOrEqualFilter filter) {
        return visitFilter(filter, "GREATERTHANOREQUAL")
    }

    @Override
    String visitLessThanFilter(Void v, LessThanFilter filter) {
        return visitFilter(filter, "LESSTHAN")
    }

    @Override
    String visitLessThanOrEqualFilter(Void v, LessThanOrEqualFilter filter) {
        return visitFilter(filter, "LESSTHANOREQUAL")
    }

    @Override
    String visitStartsWithFilter(Void v, StartsWithFilter filter) {
        return visitFilter(filter, "STARTSWITH")
    }

    @Override
    String visitContainsAllValuesFilter(Void v, ContainsAllValuesFilter filter) {
        throw new UnsupportedOperationException("ContainsAllValuesFilter transformation is not supported")
    }

    @Override
    String visitExtendedFilter(Void v, Filter filter) {
        throw new UnsupportedOperationException("Filter type is not supported: " + filter.getClass())
    }

    @Override
    String visitEqualsIgnoreCaseFilter(Void v, EqualsIgnoreCaseFilter filter) {
        throw new UnsupportedOperationException("Filter type is not supported: " + filter.getClass())
    }

    private Map<String, Object> createMap(String operation, AttributeFilter filter) {
        Map<String, Object> map = new LinkedHashMap(4)
        String name = filter.getAttribute().getName()
        String value = AttributeUtil.getAsStringValue(filter.getAttribute())

        map.put("not", false)
        map.put("operation", operation)
        map.put("left", name)
        map.put("right", value)

        return map
    }

    protected String visitFilter(Filter filter, String type) {
        if (filter in AttributeFilter) {
            return visitAttributeFilter(filter, type)
        }
        return visitCompositeFilter(filter, type)
    }

    private String translateAttributeName(String original) {
        String left = original

        if (Uid.NAME == left) {
            left = uidColumn
        } else if (Name.NAME == left) {
            left = nameColumn
        } else if ("discriminator" == left) {
            // "discriminator" is a special pseudo-attribute sometimes used for sharding/bucketing.
            left = "(" + DISCRIMINATOR_MAX_BUCKET +
                    " + hashtext(concat(" + translateAttributeName(uidColumn) + ", " + (DISCRIMINATOR_MAX_BUCKET - 1) + ")))"
        }

        left = left.toLowerCase()

        if (columnMapper == null) {
            return left
        }

        String prefix = ""
        if ("discriminator" != original) {
            if (columnMapper.prefixes.containsKey(left)) {
                prefix = columnMapper.prefixes[left]
            }
            if (prefix.isEmpty() && columnMapper.defaultPrefix != null) {
                prefix = columnMapper.defaultPrefix
            }
        }

        if (columnMapper.columns.containsKey(left)) {
            left = columnMapper.columns[left]
        }

        if (!prefix.isEmpty()) {
            left = prefix + "." + left
        }

        return left
    }

    private String visitAttributeFilter(AttributeFilter filter, String type) {
        Map<String, Object> query = createMap(type, filter)
        LOG.info("Visiting attribute filter, query {0}, uidColumn {1}, nameColumn {2}", query, uidColumn, nameColumn)

        String columnName = query.get("left").toLowerCase()
        if (columnName.contains(".")) {
            columnName = columnName.replaceFirst("[\\w]+\\.", "")
        }

        Attribute attr = filter.getAttribute()
        String left = translateAttributeName(query.get("left"))

        Object right = query.get("right")
        if ("discriminator" == columnName) {
            right = rightPad((String) right, DISCRIMINATOR_MAX_BUCKET.toString().length(), (char) "0")
        } else if (this.uidType != null && "__uid__" == columnName) {
            right = right.asType(this.uidType)
        } else if (attr != null && attr.getValue() != null && attr.getValue().size() == 1) {
            Object val = attr.getValue().get(0)
            if (val instanceof ZonedDateTime) {
                java.util.Date date = Date.from(((ZonedDateTime) val).toInstant())
                right = new Timestamp(date.getTime())
            }
        }

        String operation = query.get("operation")
        boolean not = query.get("not")

        if (right == null && "EQUALS" == operation) {
            return left + (not ? " is not null" : " is null")
        }

        switch (operation) {
            case "CONTAINS":
                right = '%' + right + '%'
                break
            case "ENDSWITH":
                right = '%' + right
                break
            case "STARTSWITH":
                right = right + '%'
                break
        }

        String paramName = columnName
        int i = 0
        while (params.containsKey(paramName)) {
            paramName = columnName + i
            i++
        }

        params.put(paramName, right)
        right = ":" + paramName

        String where
        switch (operation) {
            case "EQUALS":
                where = " " + left + (not ? " <> " : " = ") + right
                break
            case "CONTAINS":
            case "ENDSWITH":
            case "STARTSWITH":
                where = " " + left + (not ? " not like " : " like ") + right
                break
            case "GREATERTHAN":
                where = " " + left + (not ? " <= " : " > ") + right
                break
            case "GREATERTHANOREQUAL":
                where = " " + left + (not ? " < " : " >= ") + right
                break
            case "LESSTHAN":
                where = " " + left + (not ? " >= " : " < ") + right
                break
            case "LESSTHANOREQUAL":
                where = " " + left + (not ? " > " : " <= ") + right
                break
            default:
                where = ""
        }

        LOG.info("Filter translated to: {0}, with parameters {1}", where, params)
        return where
    }

    private String visitCompositeFilter(CompositeFilter filter, String type) {
        List<String> partial = []
        for (Filter f : filter.getFilters()) {
            String where = f.accept(this, null)
            if (where != null && !where.trim().isEmpty()) {
                partial.add("(" + where + ")")
            }
        }
        if (partial.isEmpty()) {
            return ""
        }
        if ("or" == type || "and" == type) {
            return partial.join(" " + type + " ")
        }
        throw new UnsupportedOperationException("Composite filter is not supported: " + filter.getClass())
    }

    private static String rightPad(String s, int length, char padChar) {
        if (s == null) {
            return null
        }
        if (s.length() >= length) {
            return s
        }
        StringBuilder sb = new StringBuilder(s)
        while (sb.length() < length) {
            sb.append(padChar)
        }
        return sb.toString()
    }
}

