# Release Design

## Overview

This document describes the release pipeline for `service-cp-crime-prosecution-case-details`. There are two separate deployment tracks — a continuous dev track triggered by every PR merge, and a deliberate release track triggered from the GitHub Releases UI. SIT deployment is fully automated once a release is published.

---

## Two tracks, two purposes

| Track | Trigger | Environment | Version format | Example tag |
|---|---|---|---|---|
| **Dev CI** | PR merge to `main` | devamp01 | `{semver}-{githash}_{DDMMYY}` | `1.2.5-d54722d_290526` |
| **Release** | GitHub Releases UI → publish | sitamp01 | `{semver}_{DDMMYY}` | `1.2.6_010626` |

Dev gets every merged commit automatically. SIT only gets deliberate, tagged releases.

---

## Release flow

```
GitHub Releases UI
    │
    │  1. Navigate to:
    │     https://github.com/hmcts/service-cp-crime-prosecution-case-details/releases/new
    │  2. Type new tag in the tag field  e.g. v1.2.6
    │  3. Click "Generate release notes"  (auto-fills PR titles as changelog)
    │  4. Edit notes if needed, set title
    │  5. Click "Publish release"
    │
    │  GitHub emits: release: published (checked out at tag v1.2.6)
    │
    ▼
ci-released.yml
    │   is_release:    true
    │   deploy_dev:    false   ← dev is NOT touched by the release
    │   deploy_sit:    true
    │
    ▼
ci-build-publish.yml
    ├── Artefact-Version  (artefact-version-action reads git tag v1.2.6 → "1.2.6")
    ├── Build             service-cp-crime-prosecution-case-details-1.2.6.jar
    ├── Build-Docker      ghcr.io/hmcts/service-cp-crime-prosecution-case-details:1.2.6
    ├── Deploy            ADO pipeline 460: GHCR → ACR  tag: 1.2.6_010626
    ├── Wait-For-ACR-Push
    │
    ├── deploy-dev        SKIPPED  (deploy_dev: false)
    │
    └── deploy-sit
            writes cp-vp-aks-deploy / env/sit / vp-config/services_values.yml:
              service-cp-crime-prosecution-case-details.image.tag = "1.2.6_010626"
            triggers ADO pipeline 434 (ref: env/sit)
            → deploys to sitamp01
```

---

## Dev CI flow

```
PR merge to main
    │
    ▼
ci-draft.yml
    │   is_release:    false
    │   deploy_dev:    true  (default)
    │   deploy_sit:    false (default)
    │
    ▼
ci-build-publish.yml
    ├── Artefact-Version  (draft_version = "1.2.5-d54722d")
    ├── Build
    ├── Build-Docker      ghcr.io/...:1.2.5-d54722d
    ├── Deploy            ADO pipeline 460 → ACR  tag: 1.2.5-d54722d_290526
    ├── Wait-For-ACR-Push
    │
    ├── deploy-dev
    │       writes env/dev services_values.yml: tag = "1.2.5-d54722d_290526"
    │       triggers ADO pipeline 434 (ref: env/dev) → deploys to devamp01
    │
    └── deploy-sit        SKIPPED  (deploy_sit: false)

    ── manual testing on devamp01 ──
```

---

## Workflow inputs controlling the two tracks

Defined in `ci-build-publish.yml`:

| Input | Type | Default | Purpose |
|---|---|---|---|
| `is_release` | boolean | `false` | Switches version from `draft_version` (includes git hash) to `release_version` (clean semver from git tag) |
| `trigger_deploy` | boolean | — | Gates ADO pipeline 460 (GHCR → ACR copy) and all deploy jobs |
| `deploy_dev` | boolean | `true` | Controls `deploy-dev` job. Set to `false` in `ci-released.yml` so releases do not overwrite the dev environment |
| `deploy_sit` | boolean | `false` | Controls `deploy-sit` job. Set to `true` in `ci-released.yml` |

---

## ACR tag format

The date suffix (`_DDMMYY`) is appended by ADO pipeline 460 when it copies the image from GHCR to ACR. Our workflow reconstructs the same suffix at deploy time using `date -u +'%d%m%y'` so that `action-ado-deploy@v1` writes the correct matching tag to `services_values.yml`.

| Build type | `artefact_version` | `tag_suffix` | Final ACR tag |
|---|---|---|---|
| Dev CI | `1.2.5-d54722d` | `_290526` | `1.2.5-d54722d_290526` |
| Release | `1.2.6` | `_010626` | `1.2.6_010626` |

---

## Environment promotion after SIT

SIT receives automatic deployment on every release. Promotion from SIT to PRP and PRD is a separate batch operation managed in `cp-vp-aks-deploy`:

- **SIT → PRP**: `promote-environment.yml` (`workflow_dispatch`, source: `sit`, target: `prp`)
- **PRP → PRD**: `promote-environment.yml` (`workflow_dispatch`, source: `prp`, target: `prd`) — creates a PR requiring change approval
- Release cycle name (e.g. `rel_amp_2607`) is the PR title used when promoting to PRD

---

## How to create a release (step by step)

1. Open the Releases page for this repo:
   `https://github.com/hmcts/service-cp-crime-prosecution-case-details/releases/new`

2. In **"Choose a tag"**, type the next version and select **"Create new tag on publish"**
   - Patch bump (bug fixes): `v1.2.5` → `v1.2.6`
   - Minor bump (new features): `v1.2.5` → `v1.3.0`
   - Major bump (breaking change): `v1.2.5` → `v2.0.0`

3. Set the release title to match the tag, e.g. `v1.2.6`

4. Click **"Generate release notes"** — GitHub auto-fills the changelog from PR titles merged since the last tag

5. Review and edit the notes if needed

6. Click **"Publish release"**

The SIT deployment starts automatically — no further action needed. Track progress at:
`https://github.com/hmcts/service-cp-crime-prosecution-case-details/actions`
