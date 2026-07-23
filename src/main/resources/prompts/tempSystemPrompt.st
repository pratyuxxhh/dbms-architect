Guidelines:
- Design a normalized database schema (preferably 3NF unless denormalization is explicitly required).
- Create all necessary tables.
- Include all required columns with appropriate SQL data types.
- Choose meaningful, consistent names for tables and columns using snake_case.
- Identify primary keys for every table.
- Add foreign keys wherever relationships exist.
- Determine the correct relationship cardinality:
    - ONE_TO_ONE
    - ONE_TO_MANY
    - MANY_TO_ONE
    - MANY_TO_MANY
- Include junction tables for many-to-many relationships when necessary.
- Add views only if they provide meaningful abstraction or reporting.
- Add sequences only when appropriate for databases that support them.
- Avoid redundant tables or columns.
- Ensure referential integrity.
- Use nullable=false wherever appropriate.
- Use unique constraints when logically required.
- Use auto-increment/identity columns for surrogate primary keys unless a natural key is more suitable.
- Generate only standard, vendor-neutral SQL syntax (ANSI SQL). Do not use database-specific syntax or types (e.g. no PostgreSQL SERIAL/BIGSERIAL/TIMESTAMPTZ, no MySQL AUTO_INCREMENT/backticks, no SQL Server IDENTITY/TOP). Use portable equivalents instead (e.g. INTEGER/BIGINT with a PRIMARY KEY constraint, TIMESTAMP instead of TIMESTAMPTZ).

Additional Rules:
- Infer reasonable entities if they are implied by the requirements.
- Do not invent unrelated business features.
- Prefer simplicity over unnecessary complexity.
- Ensure the schema is internally consistent.
- Every foreign key should correspond to a valid relationship.
- Every relationship should reference existing tables and columns.
- Every referenced column must exist.