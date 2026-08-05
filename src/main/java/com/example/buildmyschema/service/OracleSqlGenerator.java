package com.example.buildmyschema.service;

import com.example.buildmyschema.entity.users.UserEntity;
import com.example.buildmyschema.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OracleSqlGenerator {

    @Autowired
    private UserRepository userRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private static final Set<String> FUNCTION_DEFAULTS = Set.of(
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "NOW()", "NOW", "SYSDATE"
    );

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "bigint", "int", "smallint", "tinyint", "mediumint",
            "decimal", "numeric", "float", "double", "real", "year"
    );

    private static final Set<String> ALLOWED_FK_DELETE_ACTIONS = Set.of("CASCADE", "SET NULL");

    private static final Set<String> ORACLE_RESERVED_WORDS = Set.of(
            "ACCESS", "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUDIT", "BETWEEN", "BY", "CHAR",
            "CHECK", "CLUSTER", "COLUMN", "COMMENT", "COMPRESS", "CONNECT", "CREATE", "CURRENT", "DATE",
            "DECIMAL", "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "EXCLUSIVE", "EXISTS",
            "FILE", "FLOAT", "FOR", "FROM", "GRANT", "GROUP", "HAVING", "IDENTIFIED", "IMMEDIATE", "IN",
            "INCREMENT", "INDEX", "INITIAL", "INSERT", "INTEGER", "INTERSECT", "INTO", "IS", "LEVEL",
            "LIKE", "LOCK", "LONG", "MAXEXTENTS", "MINUS", "MODE", "MODIFY", "NOAUDIT", "NOCOMPRESS",
            "NOT", "NOWAIT", "NULL", "NUMBER", "OF", "OFFLINE", "ON", "ONLINE", "OPTION", "OR", "ORDER",
            "PCTFREE", "PRIOR", "PRIVILEGES", "PUBLIC", "RAW", "RENAME", "RESOURCE", "REVOKE", "ROW",
            "ROWID", "ROWNUM", "ROWS", "SELECT", "SESSION", "SET", "SHARE", "SIZE", "SMALLINT", "START",
            "SUCCESSFUL", "SYNONYM", "SYSDATE", "TABLE", "THEN", "TO", "TRIGGER", "UID", "UNION",
            "UNIQUE", "UPDATE", "USER", "VALIDATE", "VALUES", "VARCHAR", "VARCHAR2", "VIEW", "WHENEVER",
            "WHERE", "WITH"
    );

    private static final String[] FOREIGN_DIALECT_MARKERS = {
            "AUTO_INCREMENT", "ENGINE=", "IDENTITY(1,1)", "NVARCHAR", "DATETIMEOFFSET", "DATETIME2",
            "::", "`", "UNSIGNED", "LIMIT ", " TOP (", "BIGSERIAL", "SMALLSERIAL"
    };

    private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
            Map.entry("integer", "int"),
            Map.entry("int4", "int"),
            Map.entry("int8", "bigint"),
            Map.entry("int2", "smallint"),
            Map.entry("float4", "float"),
            Map.entry("float8", "double"),
            Map.entry("double precision", "double"),
            Map.entry("bool", "boolean"),
            Map.entry("character varying", "varchar"),
            Map.entry("character", "char"),
            Map.entry("timestamptz", "timestamp with time zone"),
            Map.entry("timestamp without time zone", "timestamp"),
            Map.entry("timetz", "time with time zone"),
            Map.entry("time without time zone", "time"),
            Map.entry("bytea", "blob"),
            Map.entry("jsonb", "json")
    );

    private static final Map<String, String> FIXED_TYPE_MAP = Map.ofEntries(
            Map.entry("int", "NUMBER(10)"),
            Map.entry("bigint", "NUMBER(19)"),
            Map.entry("smallint", "NUMBER(5)"),
            Map.entry("tinyint", "NUMBER(3)"),
            Map.entry("mediumint", "NUMBER(7)"),
            Map.entry("year", "NUMBER(4)"),
            Map.entry("float", "BINARY_FLOAT"),
            Map.entry("double", "BINARY_DOUBLE"),
            Map.entry("real", "BINARY_FLOAT"),
            Map.entry("text", "CLOB"),
            Map.entry("tinytext", "CLOB"),
            Map.entry("mediumtext", "CLOB"),
            Map.entry("longtext", "CLOB"),
            Map.entry("blob", "BLOB"),
            Map.entry("tinyblob", "BLOB"),
            Map.entry("mediumblob", "BLOB"),
            Map.entry("longblob", "BLOB"),
            Map.entry("boolean", "NUMBER(1)"),
            Map.entry("date", "DATE"),
            Map.entry("time", "TIMESTAMP"),
            Map.entry("time with time zone", "TIMESTAMP WITH TIME ZONE"),
            Map.entry("datetime", "TIMESTAMP"),
            Map.entry("timestamp", "TIMESTAMP"),
            Map.entry("timestamp with time zone", "TIMESTAMP WITH TIME ZONE"),
            Map.entry("json", "CLOB"),
            Map.entry("uuid", "VARCHAR2(36)"),
            Map.entry("money", "NUMBER(19,2)"),
            Map.entry("array", "CLOB")
    );

    private static final Set<String> JSON_CHECK_TYPES = Set.of("json", "array");

    public File generateDownloadableFile(String inputJson) throws InvalidSchemaException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity u = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (inputJson == null || inputJson.trim().isEmpty()) {
            throw new IllegalArgumentException("inputJson must not be null or empty");
        }
        String[] str = new String[1];
        String sql = generateSql(inputJson , str);
        File sqlFile = File.createTempFile("schema-oracle-", ".sql");
        try {
            if (u != null) {
                u.setUpdatedAt(LocalDateTime.now());
                u.getHistory().add(Map.of(str[0], sql));
                userRepository.save(u);
            }
            Files.writeString(sqlFile.toPath(), sql, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to write SQL file.", e);
        }
        return sqlFile;
    }

    public String generateSql(String schemaJson,String[] str) throws InvalidSchemaException {
        if (schemaJson == null || schemaJson.trim().isEmpty()) {
            throw new InvalidSchemaException("Schema JSON is null or empty");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(schemaJson);
        } catch (JsonProcessingException e) {
            throw new InvalidSchemaException("Malformed JSON: " + e.getOriginalMessage(), e);
        }

        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new InvalidSchemaException("JSON root is empty or null");
        }

        JsonNode schemas = root.path("schemas");
        if (!schemas.isArray() || schemas.isEmpty()) {
            throw new InvalidSchemaException("JSON must contain a non-empty 'schemas' array");
        }

        StringBuilder sql = new StringBuilder();
        List<String> indexStatements = new ArrayList<>();
        List<String> fkStatements = new ArrayList<>();
        List<String> commentStatements = new ArrayList<>();
        List<String> reservedWordWarnings = new ArrayList<>();
        Set<String> usedConstraintNames = new HashSet<>();
        Set<String> usedIndexNames = new HashSet<>();

        for (JsonNode schema : schemas) {
            String schemaName = schema.path("name").asText(null);
            str[0]=schemaName;
            if (schemaName != null && !schemaName.isBlank()) {
                checkIdentifierLength("Schema name", schemaName);
                sql.append("-- Schema '").append(schemaName)
                        .append("' maps to an Oracle user/schema; create it separately with CREATE USER + GRANTs, ")
                        .append("this script assumes it already exists.\n\n");
            }

            JsonNode tables = schema.path("tables");
            if (!tables.isArray() || tables.isEmpty()) {
                throw new InvalidSchemaException(
                        "Schema '" + describeSchema(schemaName) + "' has no 'tables' array");
            }

            // Pass 1: structural validation of every table, plus a column registry so
            // foreign keys can be cross-checked against their real referenced tables/columns.
            Set<String> seenTableNames = new HashSet<>();
            Map<String, Map<String, ColumnInfo>> registry = new HashMap<>();
            for (JsonNode table : tables) {
                validateTable(table, schemaName);
                String tableName = table.path("name").asText();
                String tableKey = tableName.toLowerCase(Locale.ROOT);
                if (!seenTableNames.add(tableKey)) {
                    throw new InvalidSchemaException(
                            "Schema '" + describeSchema(schemaName) + "' has a duplicate table name: '" + tableName + "'");
                }
                registry.put(tableKey, buildColumnRegistry(table));
                collectReservedWordWarnings(table, schemaName, reservedWordWarnings);
            }

            // Pass 2: now that every table's columns are known, validate cross-table FK
            // references (existence, datatype match, SET NULL nullability) and emit SQL.
            for (JsonNode table : tables) {
                String tableName = table.path("name").asText();
                validateForeignKeyReferences(table, tableName, registry);
                sql.append(buildCreateTable(table, schemaName, commentStatements));
                fkStatements.addAll(buildForeignKeys(table, schemaName, usedConstraintNames));
                indexStatements.addAll(buildIndexes(table, schemaName, usedIndexNames));
            }
        }

        if (!indexStatements.isEmpty()) {
            sql.append("-- Indexes\n");
            for (String idx : indexStatements) {
                sql.append(idx).append("\n");
            }
            sql.append("\n");
        }

        if (!fkStatements.isEmpty()) {
            sql.append("-- Foreign key constraints\n");
            for (String fk : fkStatements) {
                sql.append(fk).append("\n");
            }
            sql.append("\n");
        }

        if (!commentStatements.isEmpty()) {
            sql.append("-- Comments\n");
            for (String c : commentStatements) {
                sql.append(c).append("\n");
            }
        }

        String result = sql.toString();
        assertNoForeignDialectSyntax(result);

        if (!reservedWordWarnings.isEmpty()) {
            StringBuilder header = new StringBuilder();
            header.append("-- NOTE: the following identifiers are Oracle reserved words. They are valid here because\n")
                    .append("-- every identifier in this script is double-quoted, but any SQL you write against this\n")
                    .append("-- schema by hand must quote them too (e.g. \"").append(reservedWordWarnings.get(0)).append("\"):\n");
            for (String w : reservedWordWarnings) {
                header.append("--   ").append(w).append("\n");
            }
            header.append("\n");
            result = header + result;
        }

        return result;
    }

    private record ColumnInfo(String mappedType, boolean nullable) {}

    private Map<String, ColumnInfo> buildColumnRegistry(JsonNode table) {
        Map<String, ColumnInfo> map = new HashMap<>();
        for (JsonNode col : table.path("columns")) {
            String name = col.path("name").asText();
            String dataType = col.path("dataType").asText();
            boolean nullable = col.path("nullable").asBoolean(true);
            map.put(name.toLowerCase(Locale.ROOT), new ColumnInfo(mapType(dataType), nullable));
        }
        return map;
    }

    private void validateForeignKeyReferences(JsonNode table, String tableName,
                                              Map<String, Map<String, ColumnInfo>> registry) throws InvalidSchemaException {
        Map<String, ColumnInfo> ownColumns = registry.get(tableName.toLowerCase(Locale.ROOT));
        for (JsonNode fk : table.path("foreignKeys")) {
            String column = fk.path("column").asText();
            String refTable = fk.path("referencedTable").asText();
            String refColumn = fk.path("referencedColumn").asText();
            String onDelete = fk.path("onDelete").asText("");

            Map<String, ColumnInfo> refColumns = registry.get(refTable.toLowerCase(Locale.ROOT));
            if (refColumns == null) {
                throw new InvalidSchemaException("Table '" + tableName + "' has a foreign key referencing table '"
                        + refTable + "', which does not exist in this schema");
            }
            ColumnInfo refInfo = refColumns.get(refColumn.toLowerCase(Locale.ROOT));
            if (refInfo == null) {
                throw new InvalidSchemaException("Table '" + tableName + "' has a foreign key referencing column '"
                        + refTable + "." + refColumn + "', which does not exist");
            }
            ColumnInfo ownInfo = ownColumns.get(column.toLowerCase(Locale.ROOT));
            if (!ownInfo.mappedType().equals(refInfo.mappedType())) {
                throw new InvalidSchemaException("Table '" + tableName + "." + column + "' (" + ownInfo.mappedType()
                        + ") does not match the datatype of referenced column '" + refTable + "." + refColumn
                        + "' (" + refInfo.mappedType()
                        + "); Oracle requires a foreign key column to share the exact datatype of the column it references");
            }
            if (!onDelete.isBlank() && normalizeFkAction(onDelete).equals("SET NULL") && !ownInfo.nullable()) {
                throw new InvalidSchemaException("Table '" + tableName + "." + column
                        + "' specifies ON DELETE SET NULL, but the column is NOT NULL; "
                        + "ON DELETE SET NULL requires the referencing column to be nullable");
            }
        }
    }

    private void collectReservedWordWarnings(JsonNode table, String schemaName, List<String> warnings) {
        String tableName = table.path("name").asText();
        if (ORACLE_RESERVED_WORDS.contains(tableName.toUpperCase(Locale.ROOT))) {
            warnings.add(qualifiedNameForWarning(schemaName, tableName));
        }
        for (JsonNode col : table.path("columns")) {
            String colName = col.path("name").asText();
            if (ORACLE_RESERVED_WORDS.contains(colName.toUpperCase(Locale.ROOT))) {
                warnings.add(qualifiedNameForWarning(schemaName, tableName) + "." + colName);
            }
        }
    }

    private String qualifiedNameForWarning(String schemaName, String tableName) {
        return (schemaName == null || schemaName.isBlank()) ? tableName : schemaName + "." + tableName;
    }

    private void assertNoForeignDialectSyntax(String sql) throws InvalidSchemaException {
        for (String marker : FOREIGN_DIALECT_MARKERS) {
            if (sql.contains(marker)) {
                throw new InvalidSchemaException(
                        "Internal generator error: produced non-Oracle syntax token '" + marker
                                + "'. This indicates a bug in OracleSqlGenerator; please report it rather than run the output.");
            }
        }
    }

    private void validateTable(JsonNode table, String schemaName) throws InvalidSchemaException {
        String tableName = table.path("name").asText(null);
        if (tableName == null || tableName.isBlank()) {
            throw new InvalidSchemaException(
                    "A table in schema '" + describeSchema(schemaName) + "' is missing its 'name' field");
        }
        checkIdentifierLength("Table name", tableName);

        JsonNode columns = table.path("columns");
        if (!columns.isArray() || columns.isEmpty()) {
            throw new InvalidSchemaException("Table '" + tableName + "' has no 'columns' array");
        }

        Set<String> seenColumnNames = new HashSet<>();
        int identityCount = 0;

        for (JsonNode col : columns) {
            String colName = col.path("name").asText(null);
            String dataType = col.path("dataType").asText(null);
            if (colName == null || colName.isBlank()) {
                throw new InvalidSchemaException("Table '" + tableName + "' has a column missing its 'name'");
            }
            if (dataType == null || dataType.isBlank()) {
                throw new InvalidSchemaException(
                        "Column '" + tableName + "." + colName + "' is missing its 'dataType'");
            }
            checkIdentifierLength("Column '" + tableName + "." + colName + "'", colName);

            if (!seenColumnNames.add(colName.toLowerCase(Locale.ROOT))) {
                throw new InvalidSchemaException(
                        "Table '" + tableName + "' has a duplicate column name: '" + colName + "'");
            }

            try {
                mapType(dataType);
            } catch (IllegalArgumentException e) {
                throw new InvalidSchemaException(
                        "Column '" + tableName + "." + colName + "' has an unsupported data type: '" + dataType + "'", e);
            }

            if (col.path("autoIncrement").asBoolean(false) || isSerialFamily(dataType)) {
                identityCount++;
            }
        }

        if (identityCount > 1) {
            throw new InvalidSchemaException(
                    "Table '" + tableName + "' defines more than one IDENTITY column; Oracle allows only one per table");
        }

        for (JsonNode fk : table.path("foreignKeys")) {
            String column = fk.path("column").asText("");
            String refTable = fk.path("referencedTable").asText("");
            String refColumn = fk.path("referencedColumn").asText("");
            if (column.isEmpty() || refTable.isEmpty() || refColumn.isEmpty()) {
                throw new InvalidSchemaException(
                        "Table '" + tableName + "' has a foreign key with missing column/referencedTable/referencedColumn");
            }
            if (!seenColumnNames.contains(column.toLowerCase(Locale.ROOT))) {
                throw new InvalidSchemaException(
                        "Table '" + tableName + "' has a foreign key referencing unknown column '" + column + "'");
            }
            validateFkOnDelete(fk.path("onDelete").asText(""), tableName);
        }

        JsonNode indexes = table.path("indexes");
        if (indexes.isArray()) {
            for (JsonNode idx : indexes) {
                JsonNode idxColumns = idx.path("columns");
                if (!idxColumns.isArray() || idxColumns.isEmpty()) {
                    throw new InvalidSchemaException(
                            "Table '" + tableName + "' has an index with an empty or missing 'columns' array");
                }
                for (JsonNode c : idxColumns) {
                    String colName = c.asText("");
                    if (!seenColumnNames.contains(colName.toLowerCase(Locale.ROOT))) {
                        throw new InvalidSchemaException(
                                "Table '" + tableName + "' has an index referencing unknown column '" + colName + "'");
                    }
                }
            }
        }
    }

    private void validateFkOnDelete(String action, String tableName) throws InvalidSchemaException {
        if (action == null || action.isBlank()) {
            return;
        }
        String normalized = normalizeFkAction(action);
        if (normalized.equals("RESTRICT") || normalized.equals("NO ACTION")) {
            return;
        }
        if (!ALLOWED_FK_DELETE_ACTIONS.contains(normalized)) {
            throw new InvalidSchemaException("Table '" + tableName + "' has a foreign key with an unsupported "
                    + "ON DELETE action '" + action + "'. Oracle supports only: " + ALLOWED_FK_DELETE_ACTIONS
                    + " (RESTRICT/NO ACTION are the implicit default and need no clause)");
        }
    }

    private String normalizeFkAction(String action) {
        return action.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String describeSchema(String schemaName) {
        return (schemaName == null || schemaName.isBlank()) ? "<unnamed>" : schemaName;
    }

    private void checkIdentifierLength(String label, String identifier) throws InvalidSchemaException {
        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new InvalidSchemaException(
                    label + " exceeds Oracle's " + MAX_IDENTIFIER_LENGTH
                            + "-character identifier limit: '" + identifier + "'");
        }
    }

    private String buildCreateTable(JsonNode table, String schemaName, List<String> commentStatements) {
        String tableName = table.path("name").asText();
        String fqName = qualifiedName(schemaName, tableName);

        List<String> lines = new ArrayList<>();
        Set<String> primaryKeys = new LinkedHashSet<>();

        for (JsonNode col : table.path("columns")) {
            lines.add("  " + buildColumnDefinition(col, primaryKeys, tableName, commentStatements));
        }

        if (!primaryKeys.isEmpty()) {
            lines.add("  PRIMARY KEY (" + String.join(", ", quoteAll(new ArrayList<>(primaryKeys))) + ")");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(fqName).append(" (\n");
        sb.append(String.join(",\n", lines));
        sb.append("\n)\n/\n\n");

        String tableComment = table.path("comment").asText(null);
        if (tableComment != null && !tableComment.isBlank()) {
            commentStatements.add("COMMENT ON TABLE " + fqName + " IS '" + tableComment.replace("'", "''") + "';");
        }

        return sb.toString();
    }

    private List<String> buildIndexes(JsonNode table, String schemaName, Set<String> usedIndexNames) {
        List<String> statements = new ArrayList<>();
        String tableName = table.path("name").asText();
        String fqName = qualifiedName(schemaName, tableName);

        JsonNode indexes = table.path("indexes");
        if (!indexes.isArray()) {
            return statements;
        }

        int autoIdx = 0;
        for (JsonNode idx : indexes) {
            autoIdx++;
            List<String> idxColumns = new ArrayList<>();
            for (JsonNode c : idx.path("columns")) {
                idxColumns.add(c.asText());
            }
            boolean unique = idx.path("unique").asBoolean(false);
            String idxName = idx.path("name").asText(null);
            if (idxName == null || idxName.isBlank()) {
                idxName = "idx_" + tableName + "_" + String.join("_", idxColumns) + "_" + autoIdx;
            }
            idxName = truncateIdentifier(idxName);
            idxName = dedupeName(idxName, usedIndexNames);
            String keyword = unique ? "CREATE UNIQUE INDEX" : "CREATE INDEX";
            statements.add(keyword + " " + quoteIdent(idxName) + " ON " + fqName
                    + " (" + String.join(", ", quoteAll(idxColumns)) + ");");
        }
        return statements;
    }

    private String buildColumnDefinition(JsonNode col, Set<String> primaryKeys, String tableName,
                                         List<String> commentStatements) {
        String name = col.path("name").asText();
        String dataType = col.path("dataType").asText();
        boolean nullable = col.path("nullable").asBoolean(true);
        boolean primaryKey = col.path("primaryKey").asBoolean(false);
        boolean unique = col.path("unique").asBoolean(false);
        boolean autoIncrement = col.path("autoIncrement").asBoolean(false) || isSerialFamily(dataType);
        String defaultValue = col.path("defaultValue").asText("");
        String comment = col.path("comment").asText(null);

        if (primaryKey) {
            primaryKeys.add(name);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdent(name)).append(" ").append(mapType(dataType));

        if (autoIncrement) {
            sb.append(" GENERATED BY DEFAULT AS IDENTITY");
        }
        if (!nullable) {
            sb.append(" NOT NULL");
        }
        if (!autoIncrement && !defaultValue.isBlank() && !defaultValue.equalsIgnoreCase("NULL")) {
            sb.append(" DEFAULT ").append(formatDefault(defaultValue, dataType));
        }
        if (unique && !primaryKey) {
            sb.append(" UNIQUE");
        }

        String check = buildCheckConstraint(dataType, name);
        if (check != null) {
            sb.append(" ").append(check);
        }

        if (comment != null && !comment.isBlank()) {
            String fqCol = quoteIdent(tableName) + "." + quoteIdent(name);
            commentStatements.add("COMMENT ON COLUMN " + fqCol + " IS '" + comment.replace("'", "''") + "';");
        }

        return sb.toString();
    }

    private record TypeInfo(String base, String precision) {}

    private TypeInfo parseType(String rawDataType) {
        String original = rawDataType.trim();
        int parenIdx = original.indexOf('(');
        String basePartOriginal = parenIdx >= 0 ? original.substring(0, parenIdx) : original;
        String precision = parenIdx >= 0 ? original.substring(parenIdx) : "";

        String baseLower = basePartOriginal.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (baseLower.endsWith("[]")) {
            return new TypeInfo("array", "");
        }
        baseLower = TYPE_ALIASES.getOrDefault(baseLower, baseLower);
        return new TypeInfo(baseLower, precision);
    }

    private boolean isSerialFamily(String rawDataType) {
        String base = parseType(rawDataType).base();
        return base.equals("serial") || base.equals("bigserial") || base.equals("smallserial");
    }

    private String mapType(String rawDataType) {
        TypeInfo type = parseType(rawDataType);
        String base = type.base();
        if (base.isEmpty()) {
            throw new IllegalArgumentException("Empty data type");
        }

        if (base.equals("serial")) {
            return "NUMBER(10)";
        }
        if (base.equals("bigserial")) {
            return "NUMBER(19)";
        }
        if (base.equals("smallserial")) {
            return "NUMBER(5)";
        }
        if (base.equals("enum")) {
            return "VARCHAR2(255)";
        }
        if (base.equals("varchar")) {
            String precision = type.precision().isEmpty() ? "(255)" : type.precision();
            return "VARCHAR2" + precision;
        }
        if (base.equals("char")) {
            return "CHAR" + type.precision();
        }
        if (base.equals("decimal") || base.equals("numeric")) {
            return "NUMBER" + type.precision();
        }
        if (FIXED_TYPE_MAP.containsKey(base)) {
            return FIXED_TYPE_MAP.get(base);
        }

        throw new IllegalArgumentException("Unsupported data type: " + rawDataType);
    }

    private String buildCheckConstraint(String rawDataType, String columnName) {
        TypeInfo type = parseType(rawDataType);
        String base = type.base();
        if (base.equals("enum") && !type.precision().isEmpty()) {
            return "CHECK (" + quoteIdent(columnName) + " IN " + type.precision() + ")";
        }
        if (JSON_CHECK_TYPES.contains(base)) {
            return "CHECK (" + quoteIdent(columnName) + " IS JSON)";
        }
        if (base.equals("boolean")) {
            return "CHECK (" + quoteIdent(columnName) + " IN (0, 1))";
        }
        return null;
    }

    private String formatDefault(String defaultValue, String dataType) {
        String unwrapped = defaultValue.trim();
        if (unwrapped.length() >= 2 && unwrapped.startsWith("'") && unwrapped.endsWith("'")) {
            unwrapped = unwrapped.substring(1, unwrapped.length() - 1).replace("''", "'");
        }

        String upper = unwrapped.toUpperCase(Locale.ROOT);
        String base = parseType(dataType).base();

        if (FUNCTION_DEFAULTS.contains(upper)) {
            return switch (upper) {
                case "NOW", "NOW()", "CURRENT_TIME" -> "CURRENT_TIMESTAMP";
                default -> upper;
            };
        }

        if (base.equals("boolean")) {
            boolean boolVal = upper.equals("TRUE") || upper.equals("1");
            return boolVal ? "1" : "0";
        }

        if (NUMERIC_TYPES.contains(base)) {
            try {
                Double.parseDouble(unwrapped);
                return unwrapped;
            } catch (NumberFormatException ignored) {
            }
        }

        return "'" + unwrapped.replace("'", "''") + "'";
    }

    private List<String> buildForeignKeys(JsonNode table, String schemaName, Set<String> usedConstraintNames) {
        List<String> statements = new ArrayList<>();
        String tableName = table.path("name").asText();
        String fqName = qualifiedName(schemaName, tableName);

        int idx = 0;
        for (JsonNode fk : table.path("foreignKeys")) {
            idx++;
            String column = fk.path("column").asText();
            String refTable = fk.path("referencedTable").asText();
            String refColumn = fk.path("referencedColumn").asText();
            String onDelete = fk.path("onDelete").asText("");
            String onUpdate = fk.path("onUpdate").asText("");

            String constraintName = truncateIdentifier("fk_" + tableName + "_" + column + "_" + idx);
            constraintName = dedupeName(constraintName, usedConstraintNames);
            String refFqName = qualifiedName(schemaName, refTable);

            if (!onUpdate.isBlank()) {
                String normalizedOnUpdate = normalizeFkAction(onUpdate);
                if (!normalizedOnUpdate.equals("NO ACTION") && !normalizedOnUpdate.equals("RESTRICT")) {
                    statements.add("-- NOTE: '" + column + "' specified ON UPDATE " + normalizedOnUpdate
                            + ", but Oracle has no ON UPDATE referential actions; clause omitted, "
                            + "enforce this behaviour with a trigger if it's required.");
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ").append(fqName)
                    .append(" ADD CONSTRAINT ").append(quoteIdent(constraintName))
                    .append(" FOREIGN KEY (").append(quoteIdent(column)).append(")")
                    .append(" REFERENCES ").append(refFqName)
                    .append(" (").append(quoteIdent(refColumn)).append(")");

            if (!onDelete.isBlank()) {
                String normalizedOnDelete = normalizeFkAction(onDelete);
                if (!normalizedOnDelete.equals("RESTRICT") && !normalizedOnDelete.equals("NO ACTION")) {
                    sb.append(" ON DELETE ").append(normalizedOnDelete);
                }
            }
            sb.append(";");

            statements.add(sb.toString());
        }
        return statements;
    }

    private String dedupeName(String base, Set<String> usedNames) {
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            String suffixStr = "_" + suffix;
            int keep = Math.max(MAX_IDENTIFIER_LENGTH - suffixStr.length(), 1);
            candidate = (base.length() > keep ? base.substring(0, keep) : base) + suffixStr;
            suffix++;
        }
        return candidate;
    }

    private String qualifiedName(String schemaName, String tableName) {
        if (schemaName == null || schemaName.isBlank()) {
            return quoteIdent(tableName);
        }
        return quoteIdent(schemaName) + "." + quoteIdent(tableName);
    }

    private String quoteIdent(String ident) {
        if (ident == null || ident.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be null or blank");
        }
        String trimmed = ident.trim();
        if (trimmed.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    "Identifier exceeds Oracle's " + MAX_IDENTIFIER_LENGTH + "-character limit: " + trimmed);
        }
        return "\"" + trimmed.replace("\"", "\"\"") + "\"";
    }

    private String truncateIdentifier(String name) {
        if (name.length() <= MAX_IDENTIFIER_LENGTH) {
            return name;
        }
        String hash = Integer.toHexString(name.hashCode());
        int keep = Math.max(MAX_IDENTIFIER_LENGTH - hash.length() - 1, 1);
        return name.substring(0, keep) + "_" + hash;
    }

    private List<String> quoteAll(List<String> idents) {
        List<String> out = new ArrayList<>(idents.size());
        for (String i : idents) {
            out.add(quoteIdent(i));
        }
        return out;
    }

    public static class InvalidSchemaException extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        public InvalidSchemaException(String message) {
            super(message);
        }

        public InvalidSchemaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}