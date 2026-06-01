---
name: add-new-database-field-and-pipeline
description: Workflow command scaffold for add-new-database-field-and-pipeline in Lumina.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /add-new-database-field-and-pipeline

Use this workflow when working on **add-new-database-field-and-pipeline** in `Lumina`.

## Goal

Add new fields to database tables and propagate them through the backend pipeline (entities, mappers, services, DTOs, API, and frontend).

## Common Files

- `src/main/resources/db/migration/*.sql`
- `src/main/java/com/lumina/entity/*.java`
- `src/main/java/com/lumina/dto/*.java`
- `src/main/java/com/lumina/mapper/*.java`
- `src/main/resources/mapper/*.xml`
- `src/main/java/com/lumina/service/**/*.java`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Add new columns to database schema (SQL migrations for MySQL and SQLite).
- Add fields to entity classes and DTOs.
- Update mappers (Java and XML) to handle new fields.
- Update service and accumulator logic to process new fields.
- Expose new fields in API responses.

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.