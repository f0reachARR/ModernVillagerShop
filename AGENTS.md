# Repository Guidelines

## Project Structure & Module Organization

- Core plugin code lives in `src/main/java/me/f0reach/vshop`.
- Runtime resources are in `src/main/resources`:
  - `paper-plugin.yml` for plugin metadata
  - `config.yml` for plugin settings
  - `lang/messages_*.yml` for localized text
- Build output is generated under `build/` (do not commit generated artifacts).

## Build, Test, and Development Commands

- `./gradlew build` compiles Java 21 sources and creates the shaded plugin JAR.
- `./gradlew shadowJar` builds only the fat JAR (used for server deployment).
- `./gradlew runServer` launches a local Paper 1.21 test server using the run-paper plugin.
- `./gradlew clean` removes generated build outputs.

## Coding Style & Naming Conventions

- Language: Java 21, UTF-8 source encoding.
- Indentation: 4 spaces, no tabs.
- Keep package names lowercase (`me.f0reach.vshop.*`), class names `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE_CASE`.
- Prefer small, focused classes by domain (e.g., repositories in `storage`, UI concerns in `ui`).
- Keep YAML keys stable and descriptive; add new user-facing messages to both English and Japanese files when applicable.

## Testing Guidelines

- Tests live under `src/test/java`. Stack is JUnit 5 + MockBukkit (`mockbukkit-v1.21`).
- Storage tests follow a "contract per repository, two thin subclasses" pattern: the SQL-agnostic checks live in `me.f0reach.vshop.storage.<Repo>Contract`, and each backend has a `Sqlite<Repo>Test` / `Mysql<Repo>Test` subclass that picks the data source via `AbstractRepositoryContract`. MySQL subclasses are annotated `@EnabledIfEnvironmentVariable("VSHOP_TEST_MYSQL_URL")` so they skip cleanly when no MySQL is available.
- Test naming: `<ClassName>Test` and method names describing behavior (e.g., `createsShopWhenVillagerIsValid`).
- Default: `./gradlew test` runs SQLite-backed tests only. Always run `./gradlew build` before opening a PR.
- Locally exercising MySQL tests: start MySQL (`docker run --rm -p 3307:3306 -e MYSQL_ROOT_PASSWORD=rootpw -e MYSQL_USER=vshop -e MYSQL_PASSWORD=vshop mysql:8.4`), grant the user `CREATE`/`DROP` plus `ALL` on `vshop_test_%`.*, and run with `VSHOP_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:3307 VSHOP_TEST_MYSQL_USER=vshop VSHOP_TEST_MYSQL_PASSWORD=vshop ./gradlew test`. CI does the same via `.github/workflows/ci.yml` and a `mysql:8.4` service container.

## Commit & Pull Request Guidelines

- Existing history is minimal (`initial commit`), so use clear imperative commits going forward.
- Suggested commit format: `type(scope): short summary` (example: `feat(ui): add listing pagination controls`).
- Keep commits focused; avoid mixing refactors with feature work.
- PRs should include:
  - What changed and why
  - Related issue/ticket (if available)
  - Validation steps (`./gradlew build`, local `runServer` checks)
  - Screenshots/GIFs for UI or dialog flow changes

## Security & Configuration Tips

- Never commit real database credentials or server secrets.
- Validate config defaults in `config.yml` and ensure fail-safe behavior when values are missing/invalid.
