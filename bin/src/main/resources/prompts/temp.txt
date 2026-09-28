Generate a normalized relational database schema (3NF unless requested otherwise). Return **only one valid JSON object**. No SQL, markdown, explanations, comments, or code fences.

Output:
{
  "schemas": [
    {
      "name": "schema_name",
      "tables": [...]
    }
  ]
}

Use one schema unless multiple are explicitly requested.

Each table:
- name
- optional comment
- columns (required)
- optional foreignKeys
- optional indexes

Use plural snake_case table names.

Each column may contain:
- name
- dataType
- nullable
- primaryKey
- unique
- autoIncrement
- defaultValue
- comment

Rules:
- Every table must have a primary key.
- Required columns: `"nullable": false`.
- Prefer surrogate primary keys:
  - BIGINT
  - primaryKey: true
  - autoIncrement: true
  unless a natural key is clearly better.
- Never set `"unique": true` on primary keys.
- Use unique only when logically required.
- Avoid redundant tables/columns.
- Infer only entities clearly implied by the user's request.
- Use vendor-neutral design compatible with ANSI SQL.

Allowed data types only:
INTEGER, BIGINT, SMALLINT, TINYINT, DECIMAL(p,s), FLOAT, DOUBLE, BOOLEAN, CHAR(n), VARCHAR(n), TEXT, DATE, TIME, DATETIME, TIMESTAMP, JSON.

Do not use vendor-specific types or features (SERIAL, BIGSERIAL, UUID, IDENTITY, TIMESTAMPTZ, JSONB, NVARCHAR, VARCHAR2, CLOB, BLOB, XML, ENUM, arrays, triggers, procedures, views, sequences, partitions, generated columns, etc.).

Relationships:
- 1:N → foreign key on the many side.
- 1:1 → foreign key + unique.
- M:N → junction table.

Foreign keys:
- column
- referencesTable
- referencesColumn
- optional onDelete
- optional onUpdate

Allowed actions:
CASCADE, SET NULL, RESTRICT, NO ACTION.

Indexes:
- name
- columns
- optional unique

Do not output undocumented fields such as relationships, cardinality, views, procedures, triggers, sequences, ER diagrams, or SQL.

Return exactly one valid JSON object.