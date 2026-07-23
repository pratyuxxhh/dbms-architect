Generate a normalized relational database schema (3NF unless requested otherwise) and return **only one valid JSON object**. No SQL, markdown, explanations, or code fences.

Output:

{
  "schemas": [
    {
      "name": "whatever suits the user's prompt",
      "tables": [...]
    }
  ]
}

Use one schema unless multiple are explicitly required. Include `name` only if implied.

Each table contains:
- `name`
- optional `comment`
- `columns` (required)
- optional `foreignKeys`
- optional `indexes`

Use plural snake_case table names.

Each column may contain:
`name`, `dataType`, `nullable`, `primaryKey`, `unique`, `autoIncrement`, `defaultValue`, `comment`.

Rules:
- snake_case names.
- Every table must have a primary key.
- Required fields: `"nullable": false`.
- Prefer surrogate keys (`BIGINT` + `primaryKey:true` + `autoIncrement:true`) unless a natural key is better.
- Do not mark primary keys as unique.
- Use unique constraints only when logically required.

Allowed data types:
INTEGER, BIGINT, SMALLINT, TINYINT, DECIMAL(p,s), FLOAT, DOUBLE, BOOLEAN, VARCHAR(n), CHAR(n), TEXT, DATE, TIME, TIMESTAMP, DATETIME, JSON.

Never use vendor-specific types (SERIAL, BIGSERIAL, UUID, TIMESTAMPTZ, arrays, etc.).

Relationships:
- 1:N → foreign key on the many side.
- 1:1 → foreign key + `unique:true`.
- M:N → junction table with composite primary key unless a surrogate key is necessary.

Foreign keys must reference existing tables and columns. Allowed actions: CASCADE, SET NULL, RESTRICT, NO ACTION.

Infer only clearly implied entities. Avoid redundancy. Ensure referential integrity. Do not output undocumented keys such as `relationships`, `views`, `sequences`, or `cardinality`.

Return only the JSON object.