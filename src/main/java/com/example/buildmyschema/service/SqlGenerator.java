package com.example.buildmyschema.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.*;

@Service
public class SqlGenerator {

    public enum Dialect { POSTGRES, MYSQL, GENERIC }
    private final Dialect dialect;
    public SqlGenerator() {
        this(Dialect.POSTGRES);
    }
    public SqlGenerator(Dialect dialect) {
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
            Files.writeString(outputSqlFile.toPath(), sql);
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

        // Create a temporary .sql file
        File sqlFile = File.createTempFile("schema-", ".sql");

        try {
            Files.writeString(
                    sqlFile.toPath(),
                    sql,
                    StandardCharsets.UTF_8
            );
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

    /** Core conversion: JSON string -> SQL DDL string. */
    public String generateSql(String schemaJson) throws InvalidSchemaException {
        if (schemaJson == null || schemaJson.trim().isEmpty()) {
            throw new InvalidSchemaException("Schema JSON is null or empty");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try {
            root = mapper.readTree(schemaJson);
        } catch (JsonProcessingException e) {
            throw new InvalidSchemaException("Malformed JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new InvalidSchemaException("Could not parse JSON", e);
        }

        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new InvalidSchemaException("JSON root is empty or null");
        }

        JsonNode schemas = root.path("schemas");
        if (!schemas.isArray() || schemas.isEmpty()) {
            throw new InvalidSchemaException("JSON must contain a non-empty 'schemas' array");
        }

        StringBuilder sql = new StringBuilder();
        sql.append("-- Auto-generated SQL script\n");
        sql.append("-- Generated at: ").append(new Date()).append("\n\n");

        List<String> fkStatements = new ArrayList<>();

        for (JsonNode schema : schemas) {
            String schemaName = schema.path("name").asText(null);

            if (schemaName != null && !schemaName.isEmpty() && dialect != Dialect.MYSQL) {
                sql.append("CREATE SCHEMA IF NOT EXISTS ").append(quoteIdent(schemaName)).append(";\n\n");
            }

            JsonNode tables = schema.path("tables");
            if (!tables.isArray() || tables.isEmpty()) {
                throw new InvalidSchemaException(
                        "Schema '" + (schemaName == null ? "<unnamed>" : schemaName) + "' has no 'tables' array");
            }

            for (JsonNode table : tables) {
                validateTable(table, schemaName);
                sql.append(buildCreateTable(table, schemaName));
                sql.append("\n");
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
    private void validateTable(JsonNode table, String schemaName) throws InvalidSchemaException {
        String tableName = table.path("name").asText(null);
        if (tableName == null || tableName.isEmpty()) {
            throw new InvalidSchemaException(
                    "A table in schema '" + schemaName + "' is missing its 'name' field");
        }
        JsonNode columns = table.path("columns");
        if (!columns.isArray() || columns.isEmpty()) {
            throw new InvalidSchemaException("Table '" + tableName + "' has no 'columns' array");
        }
        for (JsonNode col : columns) {
            String colName = col.path("name").asText(null);
            String dataType = col.path("dataType").asText(null);
            if (colName == null || colName.isEmpty()) {
                throw new InvalidSchemaException("Table '" + tableName + "' has a column missing its 'name'");
            }
            if (dataType == null || dataType.isEmpty()) {
                throw new InvalidSchemaException(
                        "Column '" + tableName + "." + colName + "' is missing its 'dataType'");
            }
        }
        for (JsonNode fk : table.path("foreignKeys")) {
            if (fk.path("column").asText("").isEmpty()
                    || fk.path("referencedTable").asText("").isEmpty()
                    || fk.path("referencedColumn").asText("").isEmpty()) {
                throw new InvalidSchemaException(
                        "Table '" + tableName + "' has a foreign key with missing column/referencedTable/referencedColumn");
            }
        }
    }
    private String buildCreateTable(JsonNode table, String schemaName) {
        String tableName = table.path("name").asText();
        String fqName = qualifiedName(schemaName, tableName);

        List<String> lines = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();

        for (JsonNode col : table.path("columns")) {
            lines.add("  " + buildColumnDefinition(col, primaryKeys));
        }

        if (!primaryKeys.isEmpty()) {
            lines.add("  PRIMARY KEY (" + String.join(", ", quoteAll(primaryKeys)) + ")");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(fqName).append(" (\n");
        sb.append(String.join(",\n", lines));
        sb.append("\n);\n");
        return sb.toString();
    }
    private String buildColumnDefinition(JsonNode col, List<String> primaryKeys) {
        String name = col.path("name").asText();
        String dataType = col.path("dataType").asText();
        boolean nullable = col.path("nullable").asBoolean(true);
        boolean primaryKey = col.path("primaryKey").asBoolean(false);
        boolean unique = col.path("unique").asBoolean(false);
        boolean autoIncrement = col.path("autoIncrement").asBoolean(false);
        String defaultValue = col.path("defaultValue").asText("");

        if (primaryKey) {
            primaryKeys.add(name);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdent(name)).append(" ").append(mapType(dataType, autoIncrement));

        if (!nullable) {
            sb.append(" NOT NULL");
        }

        if (!defaultValue.isEmpty() && !defaultValue.equalsIgnoreCase("NULL")) {
            sb.append(" DEFAULT ").append(formatDefault(defaultValue, dataType));
        }

        if (unique && !primaryKey) {
            sb.append(" UNIQUE");
        }

        if (autoIncrement && dialect == Dialect.MYSQL) {
            sb.append(" AUTO_INCREMENT");
        }

        return sb.toString();
    }
    private String mapType(String dataType, boolean autoIncrement) {
        String type = dataType.toLowerCase().trim();
        String baseType = type.replaceAll("\\(.*\\)", "");
        String precision = type.contains("(") ? type.substring(type.indexOf("(")) : "";

        switch (dialect) {
            case POSTGRES:
                if (autoIncrement) {
                    if (baseType.equals("bigint")) return "BIGSERIAL";
                    if (baseType.equals("int") || baseType.equals("integer")) return "SERIAL";
                    if (baseType.equals("smallint")) return "SMALLSERIAL";
                }
                if (baseType.equals("timestamptz") || baseType.equals("timestamp with time zone")) {
                    return "TIMESTAMPTZ";
                }
                return dataType.toUpperCase();

            case MYSQL:
                if (baseType.equals("timestamptz") || baseType.equals("timestamp with time zone")) {
                    return "TIMESTAMP";
                }
                if (baseType.equals("bigserial")) return "BIGINT";
                if (baseType.equals("serial")) return "INT";
                return (baseType.toUpperCase() + precision).toUpperCase();

            case GENERIC:
            default:
                return dataType.toUpperCase();
        }
    }
    private static final Set<String> FUNCTION_DEFAULTS = new HashSet<>(Arrays.asList(
            "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME", "NOW()", "NOW"
    ));
    private String formatDefault(String defaultValue, String dataType) {
        String trimmed = defaultValue.trim();

        // If the source JSON already wrapped the value in single quotes
        // (e.g. "'ACTIVE'"), unwrap it and un-escape doubled quotes so we
        // don't end up re-quoting an already-quoted literal.
        String unwrapped = trimmed;
        if (unwrapped.length() >= 2 && unwrapped.startsWith("'") && unwrapped.endsWith("'")) {
            unwrapped = unwrapped.substring(1, unwrapped.length() - 1).replace("''", "'");
        }

        String upper = unwrapped.toUpperCase();
        String baseType = dataType.toLowerCase().replaceAll("\\(.*\\)", "").trim();

        // SQL functions / keywords are never quoted.
        if (FUNCTION_DEFAULTS.contains(upper)) {
            if (upper.equals("NOW")) return "NOW()";
            return upper;
        }

        // Boolean defaults are never quoted.
        if (baseType.equals("boolean") || baseType.equals("bool")) {
            boolean boolVal = upper.equals("TRUE") || upper.equals("1");
            if (dialect == Dialect.MYSQL) {
                return boolVal ? "1" : "0";
            }
            return boolVal ? "TRUE" : "FALSE";
        }

        // Numeric defaults are never quoted.
        boolean numericType = baseType.matches("^(bigint|int|integer|smallint|numeric.*|decimal.*|float|double|real).*");
        if (numericType) {
            try {
                Double.parseDouble(unwrapped);
                return unwrapped;
            } catch (NumberFormatException ignored) {
                // Not actually numeric despite the column type -> fall through and quote as string.
            }
        }

        // Everything else is a string literal: quote exactly once, escaping
        // any embedded single quotes by doubling them.
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

            String constraintName = "fk_" + tableName + "_" + column + "_" + idx;
            String refFqName = qualifiedName(schemaName, refTable);

            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE ").append(fqName)
                    .append(" ADD CONSTRAINT ").append(quoteIdent(constraintName))
                    .append(" FOREIGN KEY (").append(quoteIdent(column)).append(")")
                    .append(" REFERENCES ").append(refFqName)
                    .append(" (").append(quoteIdent(refColumn)).append(")");

            if (!onDelete.isEmpty()) sb.append(" ON DELETE ").append(onDelete);
            if (!onUpdate.isEmpty()) sb.append(" ON UPDATE ").append(onUpdate);
            sb.append(";");

            statements.add(sb.toString());
        }
        return statements;
    }
    private String qualifiedName(String schemaName, String tableName) {
        if (dialect == Dialect.MYSQL || schemaName == null || schemaName.isEmpty()) {
            return quoteIdent(tableName);
        }
        return quoteIdent(schemaName) + "." + quoteIdent(tableName);
    }
    private String quoteIdent(String ident) {
        // Identifiers are emitted as-is (no surrounding quotes/backticks).
        // Basic safety check: only allow typical identifier characters so a
        // rogue value can't break the generated SQL.
        if (ident == null || !ident.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid identifier: " + ident);
        }
        return ident;
    }
    private List<String> quoteAll(List<String> idents) {
        List<String> out = new ArrayList<>();
        for (String i : idents) {
            out.add(quoteIdent(i));
        }
        return out;
    }
    /** Thrown when the input JSON is malformed or missing required schema fields. */
    public static class InvalidSchemaException extends Exception {
        public InvalidSchemaException(String message) {
            super(message);
        }
        public InvalidSchemaException(String message, Throwable cause) {
            super(message, cause);
        }
    }


}