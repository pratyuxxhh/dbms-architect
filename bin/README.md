# 🧠 AI Database Architect

> Turn a natural language description into a production-ready SQL database design package — schema, seed data, ER diagram, docs, and sample queries, all in one export.

![status](https://img.shields.io/badge/status-planning-yellow) ![stack](https://img.shields.io/badge/stack-Java%2021%20%2B%20Spring%20Boot-blue) ![license](https://img.shields.io/badge/license-MIT-green)

---

## 🎯 What It Does

Give it a plain-English description of a system ("a library management app with books, authors, and members") and a target database engine. It returns a complete, downloadable design package — not just a schema dump.

```
"I need a database for an e-commerce store"
        │
        ▼
   📦 project.zip
   ├── schema.sql
   ├── seed.sql
   ├── queries.sql
   ├── diagram.svg
   ├── README.md
   ├── data_dictionary.md
   └── metadata.json
```

---

## 🏗️ Architecture

```
Client
  │
  ▼
Spring Boot API
  │
  ▼
Requirement Analyzer ──▶ LLM ──▶ Database Model (JSON)
                                        │
                    ┌───────────┬───────┼───────────┬────────────┐
                    ▼           ▼       ▼            ▼            ▼
                 SQL Gen    Seed Gen  Diagram Gen  Docs Gen   Query Gen
                    │           │       │            │            │
                    └───────────┴───────┴────────────┴────────────┘
                                        │
                                        ▼
                                  ZIP Export
```

The **Database Model (JSON)** is the single source of truth. Every artifact (SQL, diagram, docs) is generated *from* it — nothing is generated independently, which keeps everything in sync.

---

## 🔄 Pipeline

| Phase | Name | Produces |
|---|---|---|
| 1 | **Requirement Analysis** | Structured summary of entities, rules, and gaps |
| 2 | **Database Model** | Canonical JSON model (tables, columns, relationships, constraints, indexes) |
| 3 | **SQL Generation** | `schema.sql` — CREATE TABLE, PK/FK, UNIQUE, CHECK, DEFAULT, indexes |
| 4 | **Seed Data** | `seed.sql` — realistic INSERTs, configurable row counts |
| 5 | **ER Diagram** | `diagram.svg` (via Mermaid/Graphviz → SVG) |
| 6 | **Documentation** | `README.md` — overview, tables, relationships, assumptions |
| 7 | **Data Dictionary** | `data_dictionary.md` — table/column/type/description reference |
| 8 | **Sample Queries** | `queries.sql` — search, joins, aggregates, reports |
| 9 | **Validation** | Warnings for missing PKs, broken FKs, naming issues, 3NF violations |
| 10 | **Export** | Final `project.zip` bundle |

<details>
<summary>Example: Phase 1 output</summary>

```
System: Library Management
Entities:
- Book
- Author
- Member
- BorrowRecord
Business Rules:
- One member can borrow many books.
- One book has one author.
```
</details>

<details>
<summary>Example: Phase 9 validation warnings</summary>

```
Warnings
• Customer has no primary key.
• Orders references missing table Payment.
```
</details>

---

## 🧰 Tech Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot, Spring AI, Jackson, Lombok |
| AI | GPT-5 / Gemini / Claude (via Spring AI) |
| Diagrams | Mermaid or Graphviz → SVG |
| Output | ZIP, Markdown, SQL, SVG |

---

## ✅ MVP Scope

Build these first — everything else is a fast-follow:

- [ ] Natural language prompt input
- [ ] Database engine selection (MySQL / PostgreSQL / MariaDB)
- [ ] `schema.sql` generation
- [ ] `seed.sql` generation
- [ ] `diagram.svg` generation
- [ ] `README.md` generation
- [ ] ZIP download

### Post-MVP
- Data dictionary generation
- Sample query generation
- Validation report (3NF, orphaned FKs, naming conventions)
- Multi-database output from a single model
- Versioned schema migrations

---

## 💡 Why This Scope

This stays a focused **AI-powered database design tool**, not a generic code generator. The JSON model as single source of truth is what makes the project defensible as solid engineering — every output is deterministic and traceable back to one structure, not re-prompted independently.

---

## 📄 License

MIT
