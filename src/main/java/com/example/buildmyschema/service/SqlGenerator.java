package com.example.buildmyschema.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts a JSON schema description into MySQL 8.x DDL.
 *
 * <p>This generator only ever emits MySQL syntax. Earlier versions supported a
 * {@code Dialect} switch (Postgres / generic) that could produce non-MySQL
 * constructs such as {@code SERIAL}, {@code TIMESTAMPTZ}, or {@code CREATE SCHEMA}.
 * That switch is preserved on the constructor only for source compatibility;
 * passing anything other than {@link Dialect#MYSQL} now fails fast at
 * construction time rather than silently producing SQL that MySQL rejects.
 */
@Service
public class SqlGenerator {   //  little more work is needed

    /** @deprecated only {@link #MYSQL} is supported; kept for source compatibility. */
    @Deprecated
    public enum Dialect { POSTGRES, MYSQL, GENERIC }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** MySQL's identifier length limit (applies to tables, columns, constraints, indexes, databases). */
    private static final int MAX_IDENTIFIER_LENGTH = 64;

    private static final String DEFAULT_ENGINE_CLAUSE =
            "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci";

    private static final Set<String> FUNCTION_DEFAULTS = Set.of(
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "NOW()", "NOW"
    );

    /** Column base types for which a bare (unparenthesized) function default is legal in MySQL. */
    private static final Set<String> TEMPORAL_TYPES = Set.of(
            "timestamp", "datetime", "date", "time", "year"
    );

    /**
     * Since MySQL 8.0.13, BLOB/TEXT/JSON/GEOMETRY columns require their default
     * to be given as an expression - a bare string literal is rejected, it must
     * be wrapped in parentheses, e.g. {@code DEFAULT ('{}')}.
     */
    private static final Set<String> EXPRESSION_DEFAULT_TYPES = Set.of(
            "text", "tinytext", "mediumtext", "longtext",
            "blob", "tinyblob", "mediumblob", "longblob",
            "json", "geometry"
    );

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "bigint", "int", "smallint", "tinyint", "mediumint",
            "decimal", "float", "double", "real", "year"
    );

    /** The only FK referential actions InnoDB actually supports (SET DEFAULT is parsed but rejected). */
    private static final Set<String> ALLOWED_FK_ACTIONS = Set.of(
            "CASCADE", "SET NULL", "NO ACTION", "RESTRICT"
    );

    /** Simple base-type aliases (no embedded precision) normalized before MySQL mapping. */
    private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
            Map.entry("integer", "int"),
            Map.entry("int4", "int"),
            Map.entry("int8", "bigint"),
            Map.entry("int2", "smallint"),
            Map.entry("float4", "float"),
            Map.entry("float8", "double"),
            Map.entry("double precision", "double"),
            Map.entry("numeric", "decimal"),
            Map.entry("character varying", "varchar"),
            Map.entry("character", "char"),
            Map.entry("bool", "boolean"),
            Map.entry("timestamptz", "timestamp"),
            Map.entry("timestamp with time zone", "timestamp"),
            Map.entry("timestamp without time zone", "timestamp"),
            Map.entry("timetz", "time"),
            Map.entry("time with time zone", "time"),
            Map.entry("time without time zone", "time"),
            Map.entry("jsonb", "json"),
            Map.entry("bytea", "blob")
    );

    /** Base types whose MySQL equivalent bakes in its own precision/args; original precision is discarded. */
    private static final Map<String, String> FIXED_TYPE_OVERRIDES = Map.of(
            "uuid", "CHAR(36)",
            "money", "DECIMAL(19,2)",
            "array", "JSON"
    );

    private final Dialect dialect;

    public SqlGenerator() {
        this(Dialect.MYSQL);
    }

    /**
     * @param dialect must be {@link Dialect#MYSQL}. Retained only so callers compiled
     *                against the old constructor signature still link; any other value
     *                is rejected immediately because it can no longer be honoured correctly.
     * @throws IllegalArgumentException if a non-MySQL dialect is requested.
     */
    public SqlGenerator(Dialect dialect) {
        if (dialect != Dialect.MYSQL) {
            throw new IllegalArgumentException(
                    "SqlGenerator now only supports Dialect.MYSQL; " + dialect
                            + " produced invalid SQL (SERIAL/TIMESTAMPTZ/CREATE SCHEMA/etc.) and has been removed.");
        }
        this.dialect = dialect;
    }

    /**
     * Converts the given schema JSON string to SQL and writes the result to outputSqlFile.
     *
     * @throws InvalidSchemaException if the JSON is malformed or missing required fields
     * @throws IOException            for any write failure
     */
    public File generateSqlFile(String inputJson, File outputSqlFile)
            throws InvalidSchemaException, IOException {

        if (inputJson == null || inputJson.trim().isEmpty()) {
            throw new IllegalArgumentException("inputJson must not be null or empty");
        }

        String sql = generateSql(inputJson);

        if (outputSqlFile == null) {
            throw new IllegalArgumentException("outputSqlFile must not be null");
        }
        File parent = outputSqlFile.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Could not create output directory: " + parent.getPath());
            }
        }
        if (outputSqlFile.exists() && !outputSqlFile.canWrite()) {
            throw new IOException("Output SQL file exists and is not writable: " + outputSqlFile.getPath());
        }

        try {
            Files.writeString(outputSqlFile.toPath(), sql, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to write SQL output to: " + outputSqlFile.getPath(), e);
        }

        return outputSqlFile;
    }

    public File generateDownloadableFile(String inputJson)
            throws InvalidSchemaException, IOException {

        if (inputJson == null || inputJson.trim().isEmpty()) {
            throw new IllegalArgumentException("inputJson must not be null or empty");
        }

        String sql = generateSql(inputJson);

        File sqlFile = File.createTempFile("schema-", ".sql");

        try {
            Files.writeString(sqlFile.toPath(), sql, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IOException("Failed to write SQL file.", e);
        }

        return sqlFile;
    }

    /** Convenience overload taking a String path instead of a File object for the output. */
    public File generateSqlFile(String inputJson, String outputSqlPath)
            throws InvalidSchemaException, IOException {
        if (inputJson == null || inputJson.trim().isEmpty()) {
            throw new IllegalArgumentException("inputJson must not be null or empty");
        }
        if (outputSqlPath == null || outputSqlPath.trim().isEmpty()) {
            throw new IllegalArgumentException("outputSqlPath must not be null or empty");
        }
        return generateSqlFile(inputJson, new File(outputSqlPath));
    }

    /**
     * Same conversion, but returns the SQL as raw bytes instead of writing to disk.
     * Useful for streaming straight back in an HTTP response.
     */
    public byte[] generateSqlBytes(String schemaJson) throws InvalidSchemaException {
        return generateSql(schemaJson).getBytes(StandardCharsets.UTF_8);
    }

    /** Core conversion: JSON string -&gt; MySQL 8.x DDL string. */
    public String generateSql(String schemaJson) throws InvalidSchemaException {
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

        for (JsonNode schema : schemas) {
            String schemaName = schema.path("name").asText(null);

            if (schemaName != null && !schemaName.isBlank()) {
                checkIdentifierLength("Database name", schemaName);
                sql.append("CREATE DATABASE IF NOT EXISTS ").append(quoteIdent(schemaName))
                        .append(" CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n");
                sql.append("USE ").append(quoteIdent(schemaName)).append(";\n\n");
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
                sql.append(buildCreateTable(table, schemaName));
                fkStatements.addAll(buildForeignKeys(table, schemaName));
            }
        }

        if (!fkStatements.isEmpty()) {
            sql.append("-- Foreign key constraints\n");
            for (String fk : fkStatements) {
                sql.append(fk).append("\n");
            }
        }

        return sql.toString();
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

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
        List<String> primaryKeyOrder = new ArrayList<>();
        int autoIncrementCount = 0;
        String autoIncrementColumn = null;

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

            boolean primaryKey = col.path("primaryKey").asBoolean(false);
            boolean autoIncrement = col.path("autoIncrement").asBoolean(false)
                    || isSerialFamily(parseType(dataType).base());

            if (primaryKey) {
                primaryKeyOrder.add(colName);
            }
            if (autoIncrement) {
                autoIncrementCount++;
                autoIncrementColumn = colName;
            }
        }

        if (autoIncrementCount > 1) {
            throw new InvalidSchemaException(
                    "Table '" + tableName + "' defines more than one AUTO_INCREMENT column; MySQL allows only one per table");
        }
        if (autoIncrementCount == 1 && primaryKeyOrder.size() > 1
                && !primaryKeyOrder.getFirst().equals(autoIncrementColumn)) {
            throw new InvalidSchemaException(
                    "Table '" + tableName + "': when AUTO_INCREMENT is part of a composite PRIMARY KEY, it must be "
                            + "the first column listed (MySQL requirement). Column '" + autoIncrementColumn
                            + "' is not first in " + primaryKeyOrder);
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
                    + label + " action '" + action + "'. MySQL/InnoDB supports: " + ALLOWED_FK_ACTIONS);
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
                    label + " exceeds MySQL's " + MAX_IDENTIFIER_LENGTH
                            + "-character identifier limit: '" + identifier + "'");
        }
    }

    // ------------------------------------------------------------------
    // CREATE TABLE
    // ------------------------------------------------------------------

    private String buildCreateTable(JsonNode table, String schemaName) {
        String tableName = table.path("name").asText();
        String fqName = qualifiedName(schemaName, tableName);

        List<String> lines = new ArrayList<>();
        Set<String> primaryKeys = new LinkedHashSet<>();

        for (JsonNode col : table.path("columns")) {
            lines.add("  " + buildColumnDefinition(col, primaryKeys));
        }

        if (!primaryKeys.isEmpty()) {
            lines.add("  PRIMARY KEY (" + String.join(", ", quoteAll(new ArrayList<>(primaryKeys))) + ")");
        }

        appendIndexClauses(table, tableName, lines);

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(fqName).append(" (\n");
        sb.append(String.join(",\n", lines));
        sb.append("\n) ").append(DEFAULT_ENGINE_CLAUSE);

        String tableComment = table.path("comment").asText(null);
        if (tableComment != null && !tableComment.isBlank()) {
            sb.append(" COMMENT='").append(tableComment.replace("'", "''")).append("'");
        }
        sb.append(";\n\n");
        return sb.toString();
    }

    /** Renders any optional per-table "indexes": [{ name?, columns: [...], unique? }] entries as inline KEY clauses. */
    private void appendIndexClauses(JsonNode table, String tableName, List<String> lines) {
        JsonNode indexes = table.path("indexes");
        if (!indexes.isArray()) {
            return;
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
            String keyword = unique ? "UNIQUE KEY" : "KEY";
            lines.add("  " + keyword + " " + quoteIdent(idxName) + " (" + String.join(", ", quoteAll(idxColumns)) + ")");
        }
    }

    private String buildColumnDefinition(JsonNode col, Set<String> primaryKeys) {
        String name = col.path("name").asText();
        String dataType = col.path("dataType").asText();
        boolean nullable = col.path("nullable").asBoolean(true);
        boolean primaryKey = col.path("primaryKey").asBoolean(false);
        boolean unique = col.path("unique").asBoolean(false);
        boolean declaredAutoIncrement = col.path("autoIncrement").asBoolean(false);
        String defaultValue = col.path("defaultValue").asText("");
        String comment = col.path("comment").asText(null);

        TypeInfo type = parseType(dataType);
        boolean autoIncrement = declaredAutoIncrement || isSerialFamily(type.base());
        // MySQL requires an AUTO_INCREMENT column to be indexed; if the schema
        // didn't already make it a primary key, grant it a UNIQUE key so the
        // generated DDL doesn't fail at execution time.
        boolean effectiveUnique = unique || (autoIncrement && !primaryKey);

        if (primaryKey) {
            primaryKeys.add(name);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdent(name)).append(" ").append(mapType(dataType));

        if (!nullable) {
            sb.append(" NOT NULL");
        }
        if (!defaultValue.isBlank() && !defaultValue.equalsIgnoreCase("NULL")) {
            sb.append(" DEFAULT ").append(formatDefault(defaultValue, dataType));
        }
        // Column-definition clause order per MySQL's grammar: type, NOT NULL,
        // DEFAULT, AUTO_INCREMENT, UNIQUE/KEY, COMMENT.
        if (autoIncrement) {
            sb.append(" AUTO_INCREMENT");
        }
        if (effectiveUnique && !primaryKey) {
            sb.append(" UNIQUE");
        }
        if (comment != null && !comment.isBlank()) {
            sb.append(" COMMENT '").append(comment.replace("'", "''")).append("'");
        }

        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Type mapping
    // ------------------------------------------------------------------

    /** Parsed representation of a raw JSON data type: an alias-resolved base keyword plus its untouched precision/args. */
    private record TypeInfo(String base, String precision) {}

    private TypeInfo parseType(String rawDataType) {
        String original = rawDataType.trim();
        int parenIdx = original.indexOf('(');
        String basePartOriginal = parenIdx >= 0 ? original.substring(0, parenIdx) : original;
        // Precision/args are kept exactly as written (case-sensitive) so that
        // e.g. ENUM('Active','Inactive') literal values are never mangled.
        String precision = parenIdx >= 0 ? original.substring(parenIdx) : "";

        String baseLower = basePartOriginal.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (baseLower.contains("[]")) {
            // Postgres-style array type (e.g. text[], integer[]) - MySQL has no
            // array type, the closest safe equivalent is JSON.
            return new TypeInfo("array", "");
        }
        baseLower = TYPE_ALIASES.getOrDefault(baseLower, baseLower);
        return new TypeInfo(baseLower, precision);
    }

    private boolean isSerialFamily(String base) {
        return base.equals("serial") || base.equals("bigserial") || base.equals("smallserial");
    }

    private String mapType(String rawDataType) {
        TypeInfo type = parseType(rawDataType);
        String base = type.base();
        if (base.isEmpty()) {
            throw new IllegalArgumentException("Empty data type");
        }

        if (FIXED_TYPE_OVERRIDES.containsKey(base)) {
            return FIXED_TYPE_OVERRIDES.get(base);
        }

        return switch (base) {
            case "serial" -> "INT";
            case "bigserial" -> "BIGINT";
            case "smallserial" -> "SMALLINT";
            case "boolean" -> "TINYINT(1)";
            default -> {
                String precision = type.precision();
                if (base.equals("varchar") && precision.isEmpty()) {
                    // MySQL requires an explicit length for VARCHAR (unlike Postgres);
                    // 255 is a safe, common default that fits any index by default.
                    precision = "(255)";
                }
                yield base.toUpperCase(Locale.ROOT) + precision;
            }
        };
    }

    // ------------------------------------------------------------------
    // Default values
    // ------------------------------------------------------------------

    private String formatDefault(String defaultValue, String dataType) {

        // If the source JSON already wrapped the value in single quotes
        // (e.g. "'ACTIVE'"), unwrap it and un-escape doubled quotes so we
        // don't end up re-quoting an already-quoted literal.
        String unwrapped = defaultValue.trim();
        if (unwrapped.length() >= 2 && unwrapped.startsWith("'") && unwrapped.endsWith("'")) {
            unwrapped = unwrapped.substring(1, unwrapped.length() - 1).replace("''", "'");
        }

        String upper = unwrapped.toUpperCase(Locale.ROOT);
        String base = parseType(dataType).base();

        // SQL functions / keywords are never quoted. A bare CURRENT_TIMESTAMP is
        // only legal MySQL syntax on TIMESTAMP/DATETIME columns; anywhere else it
        // must be given as a parenthesized expression default (MySQL 8.0.13+).
        if (FUNCTION_DEFAULTS.contains(upper)) {
            String canonical = (upper.equals("NOW") || upper.equals("NOW()")) ? "CURRENT_TIMESTAMP" : upper;
            return TEMPORAL_TYPES.contains(base) ? canonical : "(" + canonical + ")";
        }

        // Boolean defaults are never quoted; MySQL has no native BOOLEAN literal,
        // BOOLEAN is a TINYINT(1) alias, so emit 1/0.
        if (base.equals("boolean")) {
            boolean boolVal = upper.equals("TRUE") || upper.equals("1");
            return boolVal ? "1" : "0";
        }

        // Numeric defaults are never quoted.
        if (NUMERIC_TYPES.contains(base)) {
            try {
                Double.parseDouble(unwrapped);
                return unwrapped;
            } catch (NumberFormatException ignored) {
                // Not actually numeric despite the column type -> fall through and quote as a string.
            }
        }

        // Everything else is a string literal: quote exactly once, escaping any
        // embedded single quotes by doubling them.
        String literal = "'" + unwrapped.replace("'", "''") + "'";

        // TEXT/BLOB/JSON/GEOMETRY defaults must be given as an expression
        // (parenthesized), even when the expression is just a literal.
        return EXPRESSION_DEFAULT_TYPES.contains(base) ? "(" + literal + ")" : literal;
    }

    // ------------------------------------------------------------------
    // Foreign keys
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Identifier quoting
    // ------------------------------------------------------------------

    private String qualifiedName(String schemaName, String tableName) {
        if (schemaName == null || schemaName.isBlank()) {
            return quoteIdent(tableName);
        }
        return quoteIdent(schemaName) + "." + quoteIdent(tableName);
    }

    /**
     * Quotes a MySQL identifier with backticks, escaping any embedded backtick by
     * doubling it (MySQL's own escaping rule). Backtick-quoting means reserved
     * words (order, key, group, ...) and unusual characters are always safe to
     * use as identifiers, and a raw value can never break out of the identifier
     * position to inject SQL.
     */
    private String quoteIdent(String ident) {
        if (ident == null || ident.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be null or blank");
        }
        String trimmed = ident.trim();
        if (trimmed.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                    "Identifier exceeds MySQL's " + MAX_IDENTIFIER_LENGTH + "-character limit: " + trimmed);
        }
        return "`" + trimmed.replace("`", "``") + "`";
    }

    /** Shortens a generated (not user-supplied) identifier to fit MySQL's 64-char limit, preserving uniqueness via a hash suffix. */
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

    /** Thrown when the input JSON is malformed or missing required schema fields. */
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