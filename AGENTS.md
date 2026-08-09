# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Java 21 Spring Boot application. Production code lives under `src/main/java/top/yuxs/springbootdev`. Shared configuration, authorization, database, exception, and utility code belongs in `core/`; business features belong in `modules/system` or `modules/file`. Keep controllers, services, entities, and MyBatis-Plus mappers in the matching feature package. Configuration is in `src/main/resources`; tests mirror production packages under `src/test/java`, with test settings in `src/test/resources/application.yml`. Put architecture notes and maintenance SQL in `docs/`. Do not commit `target/`, `logs/`, or `uploads/` output.

## Build, Test, and Development Commands

Use the checked-in Maven wrapper so contributors run the same Maven version:

- `./mvnw test` (Windows: `.\mvnw.cmd test`) runs the JUnit 5 test suite with the H2 test datasource.
- `./mvnw clean package` compiles, tests, and creates the executable JAR in `target/`.
- `./mvnw spring-boot:run` starts the API locally on port `8080`.
- `./mvnw -DskipTests package` performs a fast packaging check only.

Local runtime expects MySQL 8+ and Redis 6+. Configure connections through environment variables such as `DB_HOST`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD`.

## Coding Style & Naming Conventions

Use four-space indentation, UTF-8, `PascalCase` classes, `camelCase` members, and lowercase packages. Preserve the `top.yuxs.springbootdev` hierarchy and layer suffixes such as `Controller`, `Service`, `ServiceImpl`, `Mapper`, and `Test`. Keep REST paths and capability names consistent with nearby code. Lombok is supported. No formatter or linter is configured, so match surrounding import, annotation, and brace style; avoid unrelated reformatting.

### Generated Code Requirements

- All generated code must follow every existing project convention. Before adding a helper or common operation, search the repository and reuse an existing method with the same responsibility. Do not duplicate equivalent logic unless reuse is demonstrably unsuitable.
- Generated code must contain clear Chinese comments, including method-level Javadoc and necessary inline explanations for non-obvious logic. Keep formatting readable and structured; never compress declarations, branches, queries, or complete methods onto one line.
- Application log messages must be written in Chinese. English technical terms may appear where necessary, but complete English or other-language log sentences are prohibited.
- Import every external type at the top of the source file. Do not embed fully qualified dependency names inside executable code. For example, import `LambdaQueryWrapper` and use `new LambdaQueryWrapper<SysOrgClosure>()`; do not write `new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<...>()` inline.

### Entity and Automatic Schema Rules

Every persistent entity must extend the shared `BaseEntity` and participate in the project's automatic table-creation system. Declare the table with `@TableName` and `@TableComment`. Each persistent field must explicitly declare its mapped column and schema metadata with `@TableField`, `@ColumnComment`, and `@ColumnType`; add `@DefaultValue`, `@Index`, `@ForeignKey`, or related annotations when the database semantics require them. Import these annotations at the file header. New or changed entity definitions must keep table names, column names, types, and Chinese comments synchronized with the intended database schema.

## Testing Guidelines

Tests use JUnit Jupiter, Spring Boot Test, Mockito, and H2. Name files `*Test.java`, mirror the tested package, and prefer focused unit tests; use `@SpringBootTest` only for application wiring. Add regression tests for authorization, serialization, persistence, and storage changes. No coverage threshold is enforced, but behavior changes should include relevant tests. Run `./mvnw test` before submitting.

## Commit & Pull Request Guidelines

History generally follows Conventional Commit subjects, for example `feat(file): add storage strategy` or `fix(oauth): handle callback`. Use `feat`, `fix`, `refactor`, `test`, or `docs` with an optional scope. Pull requests should explain the change, list verification commands, link issues, and call out schema, configuration, API, or security impacts. Include request/response examples for endpoint changes.

## Security & Configuration

Never commit real database, Redis, OAuth, MinIO, or encryption credentials. Override development defaults with environment variables. Treat changes under `core/authz`, tenant interception, login flows, and physical file deletion as security-sensitive and test both allowed and denied paths.
