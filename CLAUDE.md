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
    TracingFilter                     Reads/generates X-Correlation-Id; propagates via MDC
  mappers/
    CaseDetailMapper                  Maps domain DTOs to API response model
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

## Repo-Specific Architecture Rules

- **Two-stage lookup**: `CaseDetailService` first calls `CaseUrnMapperService` to resolve the URN to a case ID, then uses that ID to call `ProgressionClient`. Both calls must succeed; either failure propagates as a 502.
- **CJSCPPUID header**: Both `CaseUrnMapperClient` and `ProgressionClient` must set the `CJSCPPUID` header on every backend request.
- **Mappers are pure**: `CaseDetailMapper` transforms domain types to API response — no business logic, no HTTP calls.

## Debugging

| Symptom | Cause / Fix |
|---|---|
| 404 on valid URN | URN not found in mapper backend; check `CP_BACKEND_URL` and that case exists in backend |
| 502 on case details | `ProgressionClient` cannot reach `AMP_BACKEND_URL`; check connectivity and env var |
| Empty response body | Check `CaseDetailMapper` — null fields are excluded by `@JsonInclude(NON_NULL)` |

## Repo-Specific Notes

- `ci-build-publish.yml` present alongside standard `ci-draft.yml` / `ci-released.yml`.
- No database; fully stateless.
