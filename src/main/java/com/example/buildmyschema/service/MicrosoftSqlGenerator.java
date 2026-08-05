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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MicrosoftSqlGenerator {
    @Autowired
    private UserRepository userRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_IDENTIFIER_LENGTH = 128;

    private static final String DEFAULT_SCHEMA = "dbo";

    private static final Set<String> FUNCTION_DEFAULTS = Set.of(
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "NOW()", "NOW", "GETDATE()", "GETDATE"
    );

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "bigint", "int", "smallint", "tinyint", "mediumint",
            "decimal", "numeric", "float", "double", "real", "year"
    );

    private static final Set<String> ALLOWED_FK_ACTIONS = Set.of(
            "CASCADE", "SET NULL", "SET DEFAULT", "NO ACTION"
    );

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
            Map.entry("int", "INT"),
            Map.entry("bigint", "BIGINT"),
            Map.entry("smallint", "SMALLINT"),
            Map.entry("tinyint", "TINYINT"),
            Map.entry("mediumint", "INT"),
            Map.entry("year", "SMALLINT"),
            Map.entry("float", "FLOAT"),
            Map.entry("double", "FLOAT"),
            Map.entry("real", "REAL"),
            Map.entry("text", "VARCHAR(MAX)"),
            Map.entry("tinytext", "VARCHAR(MAX)"),
            Map.entry("mediumtext", "VARCHAR(MAX)"),
            Map.entry("longtext", "VARCHAR(MAX)"),
            Map.entry("blob", "VARBINARY(MAX)"),
            Map.entry("tinyblob", "VARBINARY(MAX)"),
            Map.entry("mediumblob", "VARBINARY(MAX)"),
            Map.entry("longblob", "VARBINARY(MAX)"),
            Map.entry("boolean", "BIT"),
            Map.entry("date", "DATE"),
            Map.entry("time", "TIME"),
            Map.entry("time with time zone", "DATETIMEOFFSET"),
            Map.entry("datetime", "DATETIME2"),
            Map.entry("timestamp", "DATETIME2"),
            Map.entry("timestamp with time zone", "DATETIMEOFFSET"),
            Map.entry("json", "NVARCHAR(MAX)"),
            Map.entry("uuid", "UNIQUEIDENTIFIER"),
            Map.entry("money", "MONEY"),
            Map.entry("array", "NVARCHAR(MAX)")
    );

    private static final Set<String> JSON_CHECK_TYPES = Set.of("json", "array");

    public File generateDownloadableFile(String inputJson) throws InvalidSchemaException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assert authentication != null;
        UserEntity u = userRepository.findByUsername(authentication.getName()).orElse(null);

        if (inputJson == null || inputJson.trim().isEmpty()) {
            throw new IllegalArgumentException("inputJson must not be null or empty");
        }
        String[] str = new String[1];
        String sql = generateSql(inputJson , str);
        File sqlFile = File.createTempFile("schema-mssql-", ".sql");
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

        for (JsonNode schema : schemas) {
            String schemaName = schema.path("name").asText(null);
            str[0] = schemaName;
            boolean hasSchema = schemaName != null && !schemaName.isBlank();
            if (hasSchema) {
                checkIdentifierLength("Schema name", schemaName);
                sql.append("IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = '")
                        .append(schemaName.replace("'", "''")).append("')\n")
                        .append("BEGIN\n")
                        .append("    EXEC('CREATE SCHEMA ").append(quoteIdent(schemaName)).append("')\n")
                        .append("END\n")
                        .append("GO\n\n");
            }

            JsonNode tables = schema.path("tables");
            if (!tables.isArray() || tables.isEmpty()) {
                throw new InvalidSchemaException(
                        "Schema '" + describeSchema(schemaName) + "' has no 'tables' array");
            }

            Set<String> seenTableNames = new HashSet<>();
            for (JsonNode table : tables) {
                validateTable(table, schemaName);
                String tableName = table.path("name").asText();
                if (!seenTableNames.add(tableName.toLowerCase(Locale.ROOT))) {
                    throw new InvalidSchemaException(
                            "Schema '" + describeSchema(schemaName) + "' has a duplicate table name: '" + tableName + "'");
                }
                sql.append(buildCreateTable(table, schemaName, commentStatements));
                fkStatements.addAll(buildForeignKeys(table, schemaName));
                indexStatements.addAll(buildIndexes(table, schemaName));
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
            sql.append("-- Extended properties (descriptions)\n");
            for (String c : commentStatements) {
                sql.append(c).append("\n");
            }
        }

        return sql.toString();
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
                    "Table '" + tableName + "' defines more than one IDENTITY column; SQL Server allows only one per table");
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
            validateFkAction(fk.path("onDelete").asText(""), "ON DELETE", tableName);
            validateFkAction(fk.path("onUpdate").asText(""), "ON UPDATE", tableName);
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

    private void validateFkAction(String action, String label, String tableName) throws InvalidSchemaException {
        if (action == null || action.isBlank()) {
            return;
        }
        String normalized = normalizeFkAction(action);
        if (!ALLOWED_FK_ACTIONS.contains(normalized)) {
            throw new InvalidSchemaException("Table '" + tableName + "' has a foreign key with an unsupported "
                    + label + " action '" + action + "'. SQL Server supports: " + ALLOWED_FK_ACTIONS);
        }
    }

    private String normalizeFkAction(String action) {
        String normalized = action.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.equals("RESTRICT") ? "NO ACTION" : normalized;
    }

    private String describeSchema(String schemaName) {
        return (schemaName == null || schemaName.isBlank()) ? "<unnamed>" : schemaName;
    }

    private void checkIdentifierLength(String label, String identifier) throws InvalidSchemaException {
        if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
            throw new InvalidSchemaException(
                    label + " exceeds SQL Server's " + MAX_IDENTIFIER_LENGTH
                            + "-character identifier limit: '" + identifier + "'");
        }
    }

    private String buildCreateTable(JsonNode table, String schemaName, List<String> commentStatements) {
        String tableName = table.path("name").asText();
        String fqName = qualifiedName(schemaName, tableName);

        List<String> lines = new ArrayList<>();
        Set<String> primaryKeys = new LinkedHashSet<>();
        String effectiveSchema = (schemaName == null || schemaName.isBlank()) ? DEFAULT_SCHEMA : schemaName;

        for (JsonNode col : table.path("columns")) {
            lines.add("  " + buildColumnDefinition(col, primaryKeys, effectiveSchema, tableName, commentStatements));
        }

        if (!primaryKeys.isEmpty()) {
            lines.add("  PRIMARY KEY (" + String.join(", ", quoteAll(new ArrayList<>(primaryKeys))) + ")");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(fqName).append(" (\n");
        sb.append(String.join(",\n", lines));
        sb.append("\n);\nGO\n\n");

        String tableComment = table.path("comment").asText(null);
        if (tableComment != null && !tableComment.isBlank()) {
            commentStatements.add(buildExtendedProperty(tableComment, effectiveSchema, tableName, null));
        }

        return sb.toString();
    }

    private List<String> buildIndexes(JsonNode table, String schemaName) {
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
            String keyword = unique ? "CREATE UNIQUE INDEX" : "CREATE INDEX";
            statements.add(keyword + " " + quoteIdent(idxName) + " ON " + fqName
                    + " (" + String.join(", ", quoteAll(idxColumns)) + ");");
        }
        return statements;
    }

    private String buildColumnDefinition(JsonNode col, Set<String> primaryKeys, String schemaName,
                                         String tableName, List<String> commentStatements) {
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
            sb.append(" IDENTITY(1,1)");
        }
        sb.append(nullable ? " NULL" : " NOT NULL");
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
            commentStatements.add(buildExtendedProperty(comment, schemaName, tableName, name));
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
            return "INT";
        }
        if (base.equals("bigserial")) {
            return "BIGINT";
        }
        if (base.equals("smallserial")) {
            return "SMALLINT";
        }
        if (base.equals("enum")) {
            return "VARCHAR(255)";
        }
        if (base.equals("varchar")) {
            String precision = type.precision().isEmpty() ? "(255)" : type.precision();
            return "VARCHAR" + precision;
        }
        if (base.equals("char")) {
            String precision = type.precision().isEmpty() ? "(1)" : type.precision();
            return "CHAR" + precision;
        }
        if (base.equals("decimal") || base.equals("numeric")) {
            return "DECIMAL" + type.precision();
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
            return "CHECK (ISJSON(" + quoteIdent(columnName) + ") = 1)";
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
                case "NOW", "NOW()", "CURRENT_TIMESTAMP", "CURRENT_TIME", "GETDATE" -> "GETDATE()";
                case "CURRENT_DATE" -> "CAST(GETDATE() AS DATE)";
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

    private List<String> buildForeignKeys(JsonNode table, String schemaName) {
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
            String refFqName = qualifiedName(schemaName, refTable);

            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ").append(fqName)
                    .append(" ADD CONSTRAINT ").append(quoteIdent(constraintName))
                    .append(" FOREIGN KEY (").append(quoteIdent(column)).append(")")
                    .append(" REFERENCES ").append(refFqName)
                    .append(" (").append(quoteIdent(refColumn)).append(")");

            if (!onDelete.isBlank()) {
                sb.append(" ON DELETE ").append(normalizeFkAction(onDelete));
            }
            if (!onUpdate.isBlank()) {
                sb.append(" ON UPDATE ").append(normalizeFkAction(onUpdate));
            }
            sb.append(";");

            statements.add(sb.toString());
        }
        return statements;
    }

    private String buildExtendedProperty(String comment, String schemaName, String tableName, String columnName) {
        StringBuilder sb = new StringBuilder();
        sb.append("EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'")
                .append(comment.replace("'", "''")).append("',\n")
                .append("    @level0type = N'SCHEMA', @level0name = N'").append(schemaName.replace("'", "''")).append("',\n")
                .append("    @level1type = N'TABLE', @level1name = N'").append(tableName.replace("'", "''")).append("'");
        if (columnName != null) {
            sb.append(",\n    @level2type = N'COLUMN', @level2name = N'").append(columnName.replace("'", "''")).append("'");
        }
        sb.append(";");
        return sb.toString();
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
                    "Identifier exceeds SQL Server's " + MAX_IDENTIFIER_LENGTH + "-character limit: " + trimmed);
        }
        return "[" + trimmed.replace("]", "]]") + "]";
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