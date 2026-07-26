# Repository Guidelines

## Project Structure & Module Organization

- Core plugin code lives in `src/main/java/me/f0reach/vshop`.
- Runtime resources are in `src/main/resources`:
  - `paper-plugin.yml` for plugin metadata
  - `config.yml` for plugin settings
  - `lang/messages_*.yml` for localized text — keep `messages_en.yml` and `messages_ja.yml` in sync, and never print a domain enum with `name()`: route it through `locale/EnumLabels`, which reads `enum.<kebab-class>.<kebab-value>`
- Build output is generated under `build/` (do not commit generated artifacts).
- Documentation:
  - `README.md` is the English project page (also used as the Modrinth description) — refresh it when commands, permissions, requirements or config defaults change.
  - `docs/guide/*.md` are the Japanese guides and `docs/guide/en/*.md` the English ones. **Both languages must stay in sync**: a change to a Japanese guide requires the matching change in `docs/guide/en/`, and vice versa.
  - Guides quote UI button labels and error text. Take the wording from the matching locale file (`lang/messages_ja.yml` / `lang/messages_en.yml`) rather than translating the other guide, so each guide matches what that locale actually renders.
  - `spec.md` stays Japanese and is the authoritative behavior spec — update it first when behavior changes, then the guides.

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

- Commit format is [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): short summary` (example: `feat(ui): add listing pagination controls`). This is not cosmetic — release-please derives the version bump and the CHANGELOG from it, so `feat` means a minor bump, `fix` a patch, and `!` or a `BREAKING CHANGE:` footer a major.
- Because merges are squashed, **the PR title becomes the commit message on `main`** and must follow the same convention. `.github/workflows/pr-title.yml` enforces it.
- Keep commits focused; avoid mixing refactors with feature work.
- PRs should include:
  - What changed and why
  - Related issue/ticket (if available)
  - Validation steps (`./gradlew build`, local `runServer` checks)
  - Screenshots/GIFs for UI or dialog flow changes

## Releasing

Releases are fully automated; nothing is bumped or tagged by hand.

1. Merge PRs into `main` as usual. `.github/workflows/release.yml` runs release-please, which opens (or updates) a **"chore: release x.y.z"** PR containing the CHANGELOG entries and the new version in `gradle.properties`.
2. Review that PR — the CHANGELOG is a normal file, so hand-edit it there if the generated wording needs help.
3. Merge it. release-please then creates the `vx.y.z` tag and the GitHub release, and the same workflow builds and publishes to GitHub Packages and to [Modrinth](https://modrinth.com/plugin/modernvillagershop), attaches the shaded JAR to the GitHub release, and syncs `README.md` to the Modrinth project description.

Notes:

- The version lives only in `gradle.properties`, inside the `x-release-please-start-version` block. `processResources` expands it into `paper-plugin.yml` and Minotaur uses it as the Modrinth version number.
- To force a specific version (e.g. going 0.x → 1.0.0), land an empty commit with a `Release-As: 1.0.0` footer: `git commit --allow-empty -m "chore: release 1.0.0" -m "Release-As: 1.0.0"`.
- The Minecraft versions advertised on Modrinth come from `modrinth.gameVersions` in `gradle.properties`. Keep them aligned with what BedrockDialog supports, since it is a hard dependency.
- Because `README.md` is pushed as the Modrinth description, every link in it must be an absolute URL.
- Re-publishing an existing tag (e.g. after a transient Modrinth failure): run the **Release** workflow manually with the tag name as input.
- Required repository setup: the `MODRINTH_TOKEN` secret, and *Settings → Actions → General → Allow GitHub Actions to create and approve pull requests*.

## Security & Configuration Tips

- Never commit real database credentials or server secrets.
- Validate config defaults in `config.yml` and ensure fail-safe behavior when values are missing/invalid.
