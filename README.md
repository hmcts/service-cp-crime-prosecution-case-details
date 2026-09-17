# Service CP Crime Prosecution Case Details

The Prosecution Case Details API returns the details of a crime prosecution case for a given case URN — `GET /cases/{case_urn}`.

## Architecture overview

The service is a stateless aggregator with no database. A request is served by a two-stage lookup: the case-URN mapper backend resolves the URN to a case ID, then the prosecution progression query API is called with that ID, and the combined result is mapped to the API contract. Both calls must succeed — either failure surfaces as a 502. Every backend call carries the `CJSCPPUID` header.

Every request except an enumerated list of `/actuator` paths must present a valid Microsoft Entra app-only access token — see [Authentication](docs/Authentication.md).

## Software required (macOS)

- **Java 25** – required to build and run the service.
  Check with `java -version`. Install via [SDKMAN](https://sdkman.io/), [Homebrew](https://brew.sh/) (`brew install openjdk@25`), or from [Adoptium](https://adoptium.net/).

- **Docker** – required to run the WireMock stub and the API tests.
  Install from [Docker Desktop for Mac](https://docs.docker.com/desktop/install/mac-install/). Check with `docker --version`.

- **direnv** (optional but recommended) – loads environment variables from `.envrc` when you `cd` into the project.
  Install with `brew install direnv` and [hook it into your shell](https://direnv.net/docs/hook.html).

- **Gradle** – not required on your machine; the project uses the Gradle wrapper (`./gradlew`).

There is no database — the service holds no state.

## Running the service on a local machine

### 1. Start the local stack

The `docker-compose.yml` in the project root defines a WireMock stub for the CP backends and the service image itself:

```bash
# Stub backend only — enough to run the app from Gradle
docker compose up -d wiremock

# Full stack (WireMock + the service in a container, on port 4550)
docker compose up -d

# Stop everything
docker compose down
```

WireMock listens on `localhost:8080` and serves the mappings in `src/apiTest/resources/mappings`.

### 2. Build and run the service

From the project root:

```bash
# Build without Docker
./gradlew build -x apiTest

# Run — the local profile is what permits AUTH_MODE=OFF
SPRING_PROFILES_ACTIVE=local AUTH_MODE=OFF ./gradlew bootRun
```

`./gradlew clean build` also runs `apiTest`, which starts the Docker stack — use `-x apiTest` when Docker is not running.

Without `AUTH_MODE=OFF` and the `local` profile the service **will not start**: `auth.mode` defaults to `ENFORCE` and `AUTH_TENANT_ID` / `AUTH_AUDIENCE` / `AUTH_ROLES` have no defaults. That startup failure is deliberate — see [Authentication](docs/Authentication.md).

The service starts on **http://localhost:4550**. Note that the committed `.envrc` exports `SERVER_PORT=8082`, so with direnv active the service binds to 8082 instead.

### 3. Override configuration with .envrc

The project includes a committed `.envrc` holding local defaults. If you use **direnv**, run `direnv allow` in the project root and the variables load when you `cd` into the project or run `direnv reload`. Otherwise, export the variables in your shell before running `./gradlew bootRun`.

The configuration the service itself reads:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `4550` | HTTP port (`.envrc` overrides this to `8082`) |
| `AMP_BACKEND_URL` | `http://localhost:8081` | Case URN mapper backend base URL |
| `CP_BACKEND_URL` | `http://localhost:8081` | Prosecution progression backend base URL |
| `CJSCPPUID` | `00000000-0000-0000-0000-000000000000` | User UUID header sent on every backend call |
| `rpe.AppInsightsInstrumentationKey` | `00000000-0000-0000-0000-000000000000` | Azure Application Insights key |

Authentication variables are listed under [Authentication](#authentication) below.

### 4. Check the service is running

- Health: `curl http://localhost:4550/actuator/health`
- Build/version info: `curl http://localhost:4550/actuator/info`

These actuator paths are exempt from token validation; `/cases/{case_urn}` is not.

### 5. Running tests

| Command | What it runs | Docker required |
|---------|-------------|-----------------|
| `./gradlew test` | Unit and Spring slice tests | No |
| `./gradlew apiTest` | API tests against the compose stack | Yes — builds the image, auto-starts and tears down the stack |
| `./gradlew build` | Both of the above | Yes |

`./gradlew apiTest` manages the compose lifecycle for you: it builds the service image and starts `wiremock` and `app` before the tests, then stops and removes them afterwards. The API tests currently cover the actuator endpoints only — exercising `/cases/{case_urn}` end to end needs real backends or WireMock mappings for both of them.

---

## Authentication

The service validates Entra app-only access tokens on every non-exempt request. `OFF` and `OBSERVE` are rejected at startup unless the `local` or `test` profile is active.

| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_MODE` | `ENFORCE` | `OFF`, `OBSERVE` or `ENFORCE` |
| `AUTH_TENANT_ID` | _(none — startup fails)_ | Token-issuing Entra tenant; derives issuer and JWKS URI |
| `AUTH_AUDIENCE` | _(none — startup fails)_ | This API's own audience |
| `AUTH_ROLES` | _(none — startup fails)_ | Comma-separated app roles accepted (`app.read`) |
| `AUTH_CLOCK_SKEW_SECONDS` | `60` | Skew for `exp`/`nbf`, capped at 300 |
| `AUTH_JWKS_CACHE_TTL_SECONDS` | `3600` | JWKS cache lifetime |

Full details, Entra prerequisites and rejection-reason troubleshooting: [docs/Authentication.md](docs/Authentication.md).

---

## Documentation

- [Authentication](docs/Authentication.md) — Entra access token validation, configuration and Entra prerequisites
- [Logging](docs/Logging.md) — logback configuration, log fields and collecting logs in Azure
- [Release](docs/release.md) — the dev and SIT release tracks, and how to cut a release

Further documentation see the [HMCTS Marketplace Springboot template readme](https://github.com/hmcts/service-hmcts-marketplace-springboot-template/blob/main/README.md) and its [supporting docs](https://github.com/hmcts/service-hmcts-marketplace-springboot-template/blob/main/docs).

## Contribute to this repository

Contributions are welcome. See [CONTRIBUTING.md](.github/CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the [MIT License](LICENSE).
