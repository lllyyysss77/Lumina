```markdown
# Lumina Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill provides a comprehensive guide to the development patterns and workflows used in the Lumina Java codebase. Lumina is a backend system (with a TypeScript/React frontend) that tracks and exposes various metrics, with a strong focus on maintainable code structure, clear commit practices, and consistent coding conventions. This guide covers file naming, import/export styles, commit conventions, and detailed step-by-step instructions for common development workflows such as adding new database fields and propagating them through the stack.

## Coding Conventions

### File Naming
- **Java files:** Use PascalCase for class and file names.
  - Example: `UserStats.java`, `TokenCache.java`
- **Frontend (TypeScript/React):** Use PascalCase for components, camelCase for services and types.
  - Example: `TokenChart.tsx`, `metricsService.ts`, `types.ts`

### Import Style
- **Java:** Use relative imports within the `com.lumina` package structure.
  ```java
  import com.lumina.entity.UserStats;
  import com.lumina.service.metrics.TokenCacheService;
  ```
- **TypeScript:** Use relative imports.
  ```typescript
  import { fetchMetrics } from './services/metricsService';
  import { MetricType } from './types';
  ```

### Export Style
- **Java:** Use named exports (public classes).
  ```java
  public class UserStats { ... }
  ```
- **TypeScript:** Use named exports.
  ```typescript
  export interface MetricType { ... }
  export function fetchMetrics() { ... }
  ```

### Commit Conventions
- **Type:** Conventional commits with `feat` prefix for new features.
  - Example: `feat: add hitCount field to UserStats entity`
- **Average commit message length:** ~67 characters

## Workflows

### Add New Database Field and Pipeline
**Trigger:** When you need to track a new metric or data field end-to-end (e.g., cache tokens, hit count).
**Command:** `/add-field-pipeline`

1. **Add new columns to the database schema**
   - Update SQL migrations for both MySQL and SQLite.
   - Example (MySQL):
     ```sql
     ALTER TABLE user_stats ADD COLUMN hit_count INT DEFAULT 0;
     ```
   - Place migration files in: `src/main/resources/db/migration/*.sql`

2. **Add fields to entity classes and DTOs**
   - Update the relevant Java entity and DTO classes.
   - Example (`UserStats.java`):
     ```java
     public class UserStats {
         private int hitCount;
         // getters and setters
     }
     ```

3. **Update mappers (Java and XML) to handle new fields**
   - Update Java mappers and corresponding XML files.
   - Example (`UserStatsMapper.xml`):
     ```xml
     <result property="hitCount" column="hit_count"/>
     ```

4. **Update service and accumulator logic to process new fields**
   - Update service classes to handle the new field.
   - Example (`UserStatsService.java`):
     ```java
     public void incrementHitCount(Long userId) {
         // logic to increment hitCount
     }
     ```

5. **Expose new fields in API responses**
   - Update controller or API classes to include the new field in responses.

6. **Update frontend types and services to consume new fields**
   - Update TypeScript types and service methods.
   - Example (`types.ts`):
     ```typescript
     export interface UserStats {
         hitCount: number;
         // other fields
     }
     ```

7. **Display new metrics in the frontend UI**
   - Update React components to display the new metric.
   - Example (`UserStatsCard.tsx`):
     ```tsx
     <div>Hit Count: {userStats.hitCount}</div>
     ```

## Testing Patterns

- **Framework:** Unknown (not detected in repository)
- **Test file pattern:** Files named with `.test.` in their name (e.g., `UserStatsService.test.java`)
- **Location:** Follows the same package structure as source files.
- **Best Practice:** Mirror the structure of the code under test and use descriptive test names.

## Commands

| Command             | Purpose                                                                 |
|---------------------|-------------------------------------------------------------------------|
| /add-field-pipeline | Add a new field to the database and propagate through the entire stack.  |
```
