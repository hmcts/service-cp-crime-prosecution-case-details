# Authentication

The service validates Microsoft Entra **app-only** (client credentials) access tokens on every
request except the enumerated infrastructure paths below. This duplicates the APIM `validate-jwt`
policy deliberately: it also covers in-cluster callers, port-forwards and misrouted ingress, which
never traverse the gateway.

## Configuration

| Property | Environment variable | Default | Purpose |
|---|---|---|---|
| `auth.mode` | `AUTH_MODE` | `ENFORCE` | `OFF`, `OBSERVE` or `ENFORCE`. `OFF`/`OBSERVE` fail startup unless the `local` or `test` profile is active |
| `auth.tenant-id` | `AUTH_TENANT_ID` | _(none)_ | The token-**issuing** tenant. Derives the issuer and JWKS URI |
| `auth.audience` | `AUTH_AUDIENCE` | _(none)_ | This API's own audience |
| `auth.roles` | `AUTH_ROLES` | _(none)_ | Comma-separated application roles this API accepts (`app.read`) |
| `auth.issuer` | — | derived from tenant | Override |
| `auth.jwks-uri` | — | derived from tenant | Override |
| `auth.clock-skew-seconds` | `AUTH_CLOCK_SKEW_SECONDS` | `60` | Applied to `exp`/`nbf`; capped at 300 |
| `auth.jwks-cache-ttl-seconds` | `AUTH_JWKS_CACHE_TTL_SECONDS` | `3600` | JWKS cache lifetime |

Startup fails when the mode is enforcing and the tenant, audience or roles are blank. A blank
audience never means "accept any audience".

## Claims validated

| Claim | Requirement |
|---|---|
| Signature | RS256 only, against the tenant JWKS. The algorithm is pinned in configuration, never read from the token header |
| `aud` | Single-valued and equal to `auth.audience` |
| `iss` | Exact string match against the configured issuer |
| `exp` | Required, and in the future within the configured skew |
| `nbf` | Checked when present, same skew |
| `tid` | Equal to `auth.tenant-id` |
| `ver` | `2.0` |
| `azp` | Present and a well-formed UUID. This is the caller's identity, not `oid`/`sub` |
| `sub` == `oid` | Proves the token is app-only. `idtyp` is **not** required — Entra omits it unless it is enabled as an optional claim |
| `scp` | Must be absent; its presence means a delegated (user) token |
| `roles` | Non-empty and containing at least one role from `auth.roles` |

## Exempt paths

Matched exactly, so `/actuator/health/x` and `/actuator/healthx` remain protected:

`/`, `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/prometheus`

All carry no case data. Adding an entry is a security change and must be reviewed as one.
`ExemptPathPolicyTest` enumerates the OpenAPI contract's paths, so a new endpoint fails the build
until someone classifies it.

## Errors

`401` with `WWW-Authenticate: Bearer error="invalid_token"` when the caller cannot be identified;
`403` with `error="insufficient_scope"` when the token is valid but carries no recognised role. The
response body is the contract's `ErrorResponse`, whose `message` is a coarse reason. Token material
is never logged or returned.

## Metrics

`auth.token.validation.success`, `auth.token.validation.failure{reason}` and, in `OBSERVE` mode,
`auth.token.validation.observed{reason}` — scraped from `/actuator/prometheus`.

## Entra prerequisites

These cannot be fixed in code. Without them the service starts but rejects every token.

| Item | Owner |
|---|---|
| App registration exposing this API's audience, per environment | Platform identity |
| App roles declared **and assigned, with admin consent** — a declared role is not an assigned one, and a role without consent produces a token that silently omits `roles`. Verify by minting a token | Entra admin |
| `requestedAccessTokenVersion` pinned to `2` | Entra admin |
| `AUTH_TENANT_ID` / `AUTH_AUDIENCE` / `AUTH_ROLES` set per environment in `cp-vp-aks-deploy` | Deployment |

Clients request `scope=<this API's audience>/.default` against
`https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token`.

## Running locally

Set `SPRING_PROFILES_ACTIVE=local` and `AUTH_MODE=OFF` in `.envrc` so local runs need no token.
To exercise validation locally, set `AUTH_MODE=ENFORCE` with a real tenant, audience and role.
