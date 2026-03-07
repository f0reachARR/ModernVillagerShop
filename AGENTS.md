# Repository Guidelines

## Project Structure & Module Organization
- Core plugin code lives in `src/main/java/me/f0reach/vshop`.
- Packages are organized by responsibility: `command`, `config`, `economy`, `listener`, `locale`, `model`, `shop`, `storage`, and `ui`.
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
- There is currently no `src/test` suite. Add tests under `src/test/java` for new business logic where practical.
- Recommended stack: JUnit 5 + MockBukkit for plugin behavior and listener/service tests.
- Test naming: `<ClassName>Test` and method names describing behavior (e.g., `createsShopWhenVillagerIsValid`).
- Run checks with `./gradlew test` (once tests are present) and always run `./gradlew build` before opening a PR.

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
