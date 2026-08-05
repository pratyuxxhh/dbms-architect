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
public class PostGressGenerator {
    @Autowired
    private UserRepository userRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_IDENTIFIER_LENGTH = 63;

    private static final Set<String> FUNCTION_DEFAULTS = Set.of(
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "NOW()", "NOW"
    );

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "bigint", "int", "smallint", "tinyint", "mediumint",
            "decimal", "numeric", "float", "double", "real", "year"
    );

    private static final Set<String> ALLOWED_FK_ACTIONS = Set.of(
            "CASCADE", "SET NULL", "SET DEFAULT", "NO ACTION", "RESTRICT"
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
            Map.entry("bytea", "blob")
    );

    private static final Map<String, String> FIXED_TYPE_MAP = Map.ofEntries(
            Map.entry("int", "INTEGER"),
            Map.entry("bigint", "BIGINT"),
            Map.entry("smallint", "SMALLINT"),
            Map.entry("tinyint", "SMALLINT"),
            Map.entry("mediumint", "INTEGER"),
            Map.entry("year", "SMALLINT"),
            Map.entry("float", "REAL"),
            Map.entry("double", "DOUBLE PRECISION"),
            Map.entry("real", "REAL"),
            Map.entry("text", "TEXT"),
            Map.entry("tinytext", "TEXT"),
            Map.entry("mediumtext", "TEXT"),
            Map.entry("longtext", "TEXT"),
            Map.entry("blob", "BYTEA"),
            Map.entry("tinyblob", "BYTEA"),
            Map.entry("mediumblob", "BYTEA"),
            Map.entry("longblob", "BYTEA"),
            Map.entry("boolean", "BOOLEAN"),
            Map.entry("date", "DATE"),
            Map.entry("time", "TIME"),
            Map.entry("time with time zone", "TIME WITH TIME ZONE"),
            Map.entry("datetime", "TIMESTAMP"),
            Map.entry("timestamp", "TIMESTAMP"),
            Map.entry("timestamp with time zone", "TIMESTAMP WITH TIME ZONE"),
            Map.entry("json", "JSONB"),
            Map.entry("jsonb", "JSONB"),
            Map.entry("uuid", "UUID"),
            Map.entry("money", "MONEY")
    );

    public File generateDownloadableFile(String inputJson) throws InvalidSchemaException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity u = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (inputJson == null || inputJson.trim().isEmpty()) {
            throw new IllegalArgumentException("inputJson must not be null or empty");
        }

        File sqlFile = File.createTempFile("schema-postgres-", ".sql");
        String[] str = new String[1];
        String sql = generateSql(inputJson, str);
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

    public String generateSql(String schemaJson ,String[] str) throws InvalidSchemaException {
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
        List<String> fkStatements = new ArrayList<>();
        List<String> indexStatements = new ArrayList<>();
        List<String> commentStatements = new ArrayList<>();

        for (JsonNode schema : schemas) {
            String schemaName = schema.path("name").asText(null);
            str[0] = schemaName;
            if (schemaName != null && !schemaName.isBlank()) {
                checkIdentifierLength("Schema name", schemaName);
                sql.append("CREATE SCHEMA IF NOT EXISTS ").append(quoteIdent(schemaName)).append(";\n\n");
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
            sql.append("-- Comments\n");
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
                mapType(dataType, false);
            } catch (IllegalArgumentException e) {
                throw new InvalidSchemaException(
                        "Column '" + tableName + "." + colName + "' has an unsupported data type: '" + dataType + "'", e);
            }
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
                    + label + " action '" + action + "'. PostgreSQL supports: " + ALLOWED_FK_ACTIONS);
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
                    label + " exceeds PostgreSQL's " + MAX_IDENTIFIER_LENGTH
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
        sb.append("\n);\n\n");

        String tableComment = table.path("comment").asText(null);
        if (tableComment != null && !tableComment.isBlank()) {
            commentStatements.add("COMMENT ON TABLE " + fqName + " IS '" + tableComment.replace("'", "''") + "';");
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

    private String buildColumnDefinition(JsonNode col, Set<String> primaryKeys, String tableName,
                                         List<String> commentStatements) {
        String name = col.path("name").asText();
        String dataType = col.path("dataType").asText();
        boolean nullable = col.path("nullable").asBoolean(true);
        boolean primaryKey = col.path("primaryKey").asBoolean(false);
        boolean unique = col.path("unique").asBoolean(false);
        boolean autoIncrement = col.path("autoIncrement").asBoolean(false);
        String defaultValue = col.path("defaultValue").asText("");
        String comment = col.path("comment").asText(null);

        if (primaryKey) {
            primaryKeys.add(name);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdent(name)).append(" ").append(mapType(dataType, autoIncrement));

        if (!nullable) {
            sb.append(" NOT NULL");
        }
        if (!defaultValue.isBlank() && !defaultValue.equalsIgnoreCase("NULL")) {
            sb.append(" DEFAULT ").append(formatDefault(defaultValue, dataType));
        }
        if (unique && !primaryKey) {
            sb.append(" UNIQUE");
        }

        String enumCheck = buildEnumCheck(dataType, name);
        if (enumCheck != null) {
            sb.append(" ").append(enumCheck);
        }

        if (comment != null && !comment.isBlank()) {
            String fqCol = quoteIdent(tableName) + "." + quoteIdent(name);
            commentStatements.add("COMMENT ON COLUMN " + fqCol + " IS '" + comment.replace("'", "''") + "';");
        }

        return sb.toString();
    }

    private record TypeInfo(String base, String precision, boolean isArray) {}

    private TypeInfo parseType(String rawDataType) {
        String original = rawDataType.trim();

        boolean isArray = false;
        if (original.endsWith("[]")) {
            isArray = true;
            original = original.substring(0, original.length() - 2).trim();
        }

        int parenIdx = original.indexOf('(');
        String basePartOriginal = parenIdx >= 0 ? original.substring(0, parenIdx) : original;
        String precision = parenIdx >= 0 ? original.substring(parenIdx) : "";

        String baseLower = basePartOriginal.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        baseLower = TYPE_ALIASES.getOrDefault(baseLower, baseLower);
        return new TypeInfo(baseLower, precision, isArray);
    }

    private boolean isSerialFamily(String base) {
        return base.equals("serial") || base.equals("bigserial") || base.equals("smallserial");
    }

    private String mapType(String rawDataType, boolean autoIncrement) {
        TypeInfo type = parseType(rawDataType);
        String base = type.base();
        if (base.isEmpty()) {
            throw new IllegalArgumentException("Empty data type");
        }

        String mapped;
        if (isSerialFamily(base)) {
            mapped = base.toUpperCase(Locale.ROOT);
        } else if (autoIncrement && (base.equals("int") || base.equals("bigint") || base.equals("smallint"))) {
            mapped = switch (base) {
                case "bigint" -> "BIGSERIAL";
                case "smallint" -> "SMALLSERIAL";
                default -> "SERIAL";
            };
        } else if (base.equals("enum")) {
            mapped = "TEXT";
        } else if (FIXED_TYPE_MAP.containsKey(base)) {
            mapped = FIXED_TYPE_MAP.get(base);
        } else if (base.equals("varchar") || base.equals("char") || base.equals("decimal")
                || base.equals("numeric")) {
            mapped = (base.equals("decimal") ? "NUMERIC" : base.toUpperCase(Locale.ROOT)) + type.precision();
        } else {
            mapped = base.toUpperCase(Locale.ROOT) + type.precision();
        }

        return type.isArray() ? mapped + "[]" : mapped;
    }

    private String buildEnumCheck(String rawDataType, String columnName) {
        TypeInfo type = parseType(rawDataType);
        if (!type.base().equals("enum") || type.precision().isEmpty()) {
            return null;
        }
        return "CHECK (" + quoteIdent(columnName) + " IN " + type.precision() + ")";
    }

    private String formatDefault(String defaultValue, String dataType) {
        String unwrapped = defaultValue.trim();
        if (unwrapped.length() >= 2 && unwrapped.startsWith("'") && unwrapped.endsWith("'")) {
            unwrapped = unwrapped.substring(1, unwrapped.length() - 1).replace("''", "'");
        }

        String upper = unwrapped.toUpperCase(Locale.ROOT);
        String base = parseType(dataType).base();

        if (FUNCTION_DEFAULTS.contains(upper)) {
            return (upper.equals("NOW") || upper.equals("NOW()")) ? "CURRENT_TIMESTAMP" : upper;
        }

        if (base.equals("boolean")) {
            boolean boolVal = upper.equals("TRUE") || upper.equals("1");
            return boolVal ? "TRUE" : "FALSE";
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
                    "Identifier exceeds PostgreSQL's " + MAX_IDENTIFIER_LENGTH + "-character limit: " + trimmed);
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