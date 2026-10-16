# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo: service-cp-crime-prosecution-case-details

Spring Boot service that aggregates prosecution case details by calling two CP backends — a URN-to-case-ID mapper and a prosecution progression API — then maps the combined response to the API contract.

**Pattern**: Stateless proxy (multi-client aggregation)
**Spring Boot version**: 4.0.1 (target 4.0.6+ per upgrade cycle)
**Implements**: `api-cp-crime-prosecution-case-details`

## Infrastructure

| Component | Technology | Purpose |
|---|---|---|
| CP Backend (case mapper) | External HTTP | Resolves case URN to case ID |
| CP Backend (progression) | External HTTP | Provides prosecution case progression details |
| WireMock | 3.6.0 (docker) | Stubs both backends for local dev and API tests |

## Source Structure

```
uk.gov.hmcts.cp/
  Application.java                    @SpringBootApplication
  clients/
    CaseUrnMapperClient               RestTemplate → case-mapper backend; sets CJSCPPUID header
    ProgressionClient                 RestTemplate → prosecution-progression backend
  config/
    AuthProperties                          @Value auth.*; fails startup on incomplete/non-enforcing config
    EntraAuthConfig                         @Bean cached, rate-limited JWKSource
    AppConfig                         @Bean RestTemplate
    AppPropertiesBackend              @Value AMP_BACKEND_URL, CP_BACKEND_URL
  controllers/
    CaseDetailController              Implements generated API; delegates to CaseDetailService
    GlobalExceptionHandler            @RestControllerAdvice; maps domain exceptions to HTTP codes
    RootController                    Returns 200 on GET /
  domain/
    CaseMapperResponse                Internal DTO for case mapper response
    ProgressionResponse               Internal DTO for progression backend response
  exceptions/
    GlobalExceptionHandler            Maps EntityNotFoundException → 404; ValidationException → 400
  filters/
    auth/EntraAuthenticationFilter          Validates Entra app-only tokens; ordered after TracingFilter
    TracingFilter                     Reads/generates X-Correlation-Id; propagates via MDC
  mappers/
    CaseDetailMapper                  Maps domain DTOs to API response model
  security/
    EntraTokenValidator                     RS256-pinned signature + claims validation
    ExemptPathPolicy                        Exact-match exemption list for /actuator paths
    AuthMode / TokenRejectionReason         Rollout mode; coarse rejection reasons mapped to 401/403
    TokenValidationException                Checked; carries a reason, never token material
    CallerIdentity                          azp-derived caller, plus a verified flag
  services/
    CaseDetailService                 Orchestrates: call mapper → resolve ID → call progression → map
    CaseUrnMapperService              Wraps CaseUrnMapperClient with error handling
```

## Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `CP_BACKEND_URL` | Base URL of the URN mapper backend | `http://localhost:8081` |
| `AMP_BACKEND_URL` | Base URL of the prosecution progression backend | `http://localhost:8081` |
| `CJSCPPUID` | User UUID header on all backend calls | `00000000-0000-0000-0000-000000000000` |
| `rpe.AppInsightsInstrumentationKey` | Azure Application Insights key | `00000000-0000-0000-0000-000000000000` |
| `AUTH_MODE` | `OFF`/`OBSERVE`/`ENFORCE`; non-enforcing fails startup outside `local`/`test` | `ENFORCE` |
| `AUTH_TENANT_ID` | Token-issuing Entra tenant | _(none - startup fails)_ |
| `AUTH_AUDIENCE` | This API's own audience | _(none - startup fails)_ |
| `AUTH_ROLES` | Comma-separated app roles accepted (`app.read`) | _(none - startup fails)_ |
| `AUTH_CLOCK_SKEW_SECONDS` | Skew for `exp`/`nbf`, capped at 300 | `60` |
| `AUTH_JWKS_CACHE_TTL_SECONDS` | JWKS cache lifetime | `3600` |

## Repo-Specific Architecture Rules

- **Two-stage lookup**: `CaseDetailService` first calls `CaseUrnMapperService` to resolve the URN to a case ID, then uses that ID to call `ProgressionClient`. Both calls must succeed; either failure propagates as a 502.
- **CJSCPPUID header**: Both `CaseUrnMapperClient` and `ProgressionClient` must set the `CJSCPPUID` header on every backend request.
- **Mappers are pure**: `CaseDetailMapper` transforms domain types to API response — no business logic, no HTTP calls.

- **Token validation is enumerated, not prefix-matched**: `ExemptPathPolicy` holds an exact-match list of `/actuator` paths; `ExemptPathPolicyTest` enumerates the OpenAPI contract so a new endpoint fails the build until classified. See `docs/Authentication.md`.
- **Filter order is load-bearing**: `TracingFilter` is `LOWEST_PRECEDENCE - 20` and `EntraAuthenticationFilter` `- 10`, so auth-failure logs still carry the trace id.
- **PMD is pinned to 7.27.0** in `gradle/pmd.gradle` to match the `code-analysis` workflow; the Gradle default lags and misses rules that fail CI.

## Debugging

| Symptom | Cause / Fix |
|---|---|
| 404 on valid URN | URN not found in mapper backend; check `CP_BACKEND_URL` and that case exists in backend |
| 502 on case details | `ProgressionClient` cannot reach `AMP_BACKEND_URL`; check connectivity and env var |
| Empty response body | Check `CaseDetailMapper` — null fields are excluded by `@JsonInclude(NON_NULL)` |
| Startup fails on `auth.audience`/`auth.tenant-id`/`auth.roles` | Deliberate - the deployment is missing `AUTH_*` values; set them in `cp-vp-aks-deploy` |
| Every token rejected with `INVALID_AUDIENCE` | `AUTH_AUDIENCE` is a sibling API's GUID, or `requestedAccessTokenVersion` drifted to `1`. Do not widen the audience |
| Every token rejected with `MISSING_ROLE` (403) | App role declared but not assigned, or no admin consent - the token silently omits `roles` |

## Smoke Testing

`src/smokeTest` (Gradle source set, `./gradlew smokeTest`) verifies `getCaseDetailsByCaseUrn`
against a real deployed instance. Not part of `build`/`check` — invoked explicitly. Design
rationale: `docs/superpowers/specs/2026-07-02-smoke-testing-framework-design.md` (note: written
before the WAF/Entra pivot below — the code is the source of truth for current behaviour).

```
uk.gov.hmcts.cp.smoketest/
  config/
    SmokeTestConfig               Standalone @Configuration + @ComponentScan — deliberately
                                   not Application.class; never boots controllers/web server
    SmokeTestProperties           @Value-injected from application.yaml's smoke: block
  clients/
    SpiInDataFixtureClient        Creates the prerequisite case via SPI-IN against the raw CP
                                   backend ingress (progression-client.url); CJSCPPUID auth
    CaseDetailsSmokeClient        Calls this service's own contract through the Azure APIM/WAF
                                   gateway (smoke.service-base-url); Entra bearer token +
                                   Ocp-Apim-Subscription-Key auth — doubles as the data-
                                   readiness poll, no separate backend polling needed
    EntraTokenClient               Entra ID client-credentials grant; fetched fresh per call
  fixtures/SpiInMessageBuilder    Owned copy of the minimal SPI-IN SOAP template; no
                                   cpp-apitests dependency
  evidence/EvidenceRecorder      Writes request/response/timestamps to build/smoke-evidence/
```

- **Two distinct auth paths — don't conflate them.** SPI-IN data-prep and the actual
  `getCaseDetailsByCaseUrn` read go through completely different gateways with different
  identity mechanisms (see the tree above). Adding `CJSCPPUID` to `CaseDetailsSmokeClient` or
  Entra/subscription headers to `SpiInDataFixtureClient` is wrong for both.
- Required env vars are documented as commented placeholders in `.envrc` — never put real
  credentials there (it's committed to git). Use a local gitignored `.env` (`.envrc` already
  sources it via `dotenv`) for manual runs; Vault for CI (mount path TBC with platform team).
- `SMOKE_INSECURE_TLS=true` is needed only for raw `steccm*`/`steamp*`/`devamp*` ingress hosts
  (self-signed/internal certs). The WAF (`amp.dev.cjscp.org.uk`) has a real CA-signed cert.
- Known open item: `GET /cases/{urn}` via the WAF currently 404s at the APIM routing layer —
  this repo has never been onboarded via `apim-gateway-configure.yml`, so the registered
  `apim_path` (or whether onboarding has happened at all) needs confirming.

## Repo-Specific Notes

- `ci-build-publish.yml` present alongside standard `ci-draft.yml` / `ci-released.yml`.
- No database; fully stateless.
