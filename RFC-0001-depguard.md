# RFC-0001: DepGuard — Dependency Intelligence Platform

| Field | Value |
|-------|-------|
| RFC | 0001 |
| Title | Dependency Intelligence Platform |
| Status | Draft |
| Author | DepGuard Engineering |
| Created | 2026-09-21 |
| Last Updated | 2026-09-21 |

---

## Table of Contents

1. [Problem](#1-problem)
2. [Goals](#2-goals)
3. [Non-Goals](#3-non-goals)
4. [Architecture](#4-architecture)
5. [Dependency Graph Model](#5-dependency-graph-model)
6. [EOL Detection](#6-eol-detection)
7. [Advisory Correlation](#7-advisory-correlation)
8. [Dependency Risk Heuristic](#8-dependency-risk-heuristic)
9. [Scan Reproducibility](#9-scan-reproducibility)
10. [Data Model](#10-data-model)
11. [API Contract](#11-api-contract)
12. [Failure Handling](#12-failure-handling)
13. [Security](#13-security)
14. [Scalability](#14-scalability)
15. [Future Roadmap](#15-future-roadmap)
16. [Alternatives Considered](#16-alternatives-considered)

---

## 1. Problem

Java/Maven applications routinely carry dozens to hundreds of transitive dependencies. Over time
these dependencies fall out of active support, reach End-of-Life (EOL), or acquire known security
advisories. The consequences range from unpatched vulnerabilities in production to breaking changes
that block future upgrades.

Existing tooling falls into two categories:

**Dependency auditors** (OWASP Dependency-Check, Dependabot, Snyk) report *which* dependencies
have advisories, but do not reason about *how exposed the application actually is*. A transitive
dependency with a CVSS 9.8 advisory that is never reachable from application code is treated the
same as a direct dependency that exercises the vulnerable code path on every request.

**Dependency update bots** (Renovate, Dependabot) propose version bumps without assessing whether
an upgrade is safe, whether it crosses a major breaking-change boundary, or whether the dependency
is already unmaintained at the target version.

Neither category gives engineers a clear answer to the question that actually matters:

> *Given this specific application and its dependency tree, what is the real risk of each
> dependency, and what is the safest upgrade path?*

DepGuard is built to answer that question.

---

## 2. Goals

| ID | Goal |
|----|------|
| G1 | Accept a public GitHub repository URL, validate it, and resolve the full Maven dependency tree (direct + transitive, all scopes) |
| G2 | Determine the EOL status of each dependency using authoritative lifecycle data with an extensible product mapping strategy |
| G3 | Identify known security advisories for each dependency version using the OSV database |
| G4 | Compute a Dependency Risk Heuristic per dependency — accounting for EOL status, advisory severity, and whether the dependency is direct or transitive — with a confidence indicator that reflects data completeness |
| G5 | Generate compatible upgrade recommendations for HIGH and CRITICAL risk dependencies, preferring same-major upgrades and clearly annotating cross-major migrations |
| G6 | Record scan reproducibility metadata (commit SHA, branch, data-source fetch timestamps) on every scan |
| G7 | Expose all results through a REST API returning a complete, structured health report |
| G8 | Be runnable locally with a single `docker compose up` command |

---

## 3. Non-Goals

| ID | Non-Goal | Rationale |
|----|----------|-----------|
| NG1 | Reachability analysis (call-graph / static analysis) | Requires bytecode instrumentation; deferred to Phase 7 |
| NG2 | Automated dependency upgrades / PR creation | Deferred to Phase 8 |
| NG3 | Gradle, npm, pip, or other ecosystems | Maven only in MVP; ecosystem abstraction is designed in |
| NG4 | Private GitHub repositories | OAuth / GitHub App integration deferred to Phase 8 |
| NG5 | Real-time streaming of scan progress | Scan status polling via `GET /api/scans/{id}` is sufficient for MVP |
| NG6 | AI-assisted explanation | Deferred to Phase 9 |
| NG7 | Kafka event-driven pipeline | Deferred to Phase 5; MVP uses `@Async` within a single JVM |
| NG8 | React dashboard | Deferred to Phase 6 |

---

## 4. Architecture

### 4.1 System Overview

DepGuard is implemented as a **modular monolith** using Spring Boot 4.x + Java 21. All domain
modules (project, scan, dependency, eol, vulnerability, risk, remediation) run in a single
Spring Boot process with a shared PostgreSQL database. Module boundaries are enforced by package
structure and explicit service interfaces — there are no direct cross-module repository calls.

This architecture is chosen deliberately for the MVP timeline. The module boundaries are designed
to extract into independent services backed by a Kafka event bus in a future phase without
requiring logic rewrites.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DepGuard Monolith (Spring Boot 4.x / Java 21)    │
│                                                                     │
│  ┌──────────┐   ┌────────────┐   ┌────────────┐   ┌─────────────┐  │
│  │ project  │   │    scan    │   │ dependency │   │     eol     │  │
│  │  module  │   │   module   │   │   module   │   │    module   │  │
│  └──────────┘   └────────────┘   └────────────┘   └─────────────┘  │
│                                                                     │
│  ┌──────────────┐   ┌───────────┐   ┌────────────────────────────┐  │
│  │vulnerability │   │   risk    │   │        remediation         │  │
│  │   module     │   │   module  │   │          module            │  │
│  └──────────────┘   └───────────┘   └────────────────────────────┘  │
│                                                                     │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                          PostgreSQL 16
```

### 4.2 Scan Pipeline

`POST /api/projects/{id}/scans` returns `202 Accepted` immediately. The scan pipeline runs
asynchronously via Spring's `@Async`, backed by a dedicated `ThreadPoolTaskExecutor`.

```
POST /api/projects/{id}/scans
          │
          ├─── Validate projectId → 404 if not found
          ├─── Create Scan record (status: PENDING)
          └─── Return 202 Accepted { "scanId": "..." }

[Background — @Async scanExecutor thread pool]
          │
          ├─── 1. GitCloneService.clone(repositoryUrl)
          │              │
          │              ├── Validate URL (GitHub HTTPS only)
          │              ├── JGit clone to temp dir (read-only, no credentials)
          │              └── Returns CloneResult { localPath, commitSha, branch }
          │
          ├─── 2. Store commitSha + branch on Scan record; set status: RUNNING
          │
          ├─── 3. MavenDependencyResolver.resolve(localPath)
          │              │
          │              └── Maven Resolver API → full dependency tree
          │                  (reads POM metadata only — no build execution)
          │
          ├─── 4. Persist Dependency + ScanDependency records
          │
          ├─── 5. [Parallel via CompletableFuture.allOf()]
          │         ├── EolService.enrichScan(scanId)      → EolRecord per dependency
          │         └── VulnerabilityService.enrichScan(scanId) → VulnerabilityRecord per advisory
          │
          ├─── 6. RiskScoringService.scoreScan(scanId)
          │              └── RiskAssessment per dependency (heuristicScore, riskLevel, confidence)
          │
          ├─── 7. RemediationService.generateRecommendations(scanId)
          │              └── RemediationRecommendation for each HIGH/CRITICAL dependency
          │
          └─── 8. Mark scan COMPLETED (or FAILED on exception)
```

Steps 5a and 5b (EOL + advisory enrichment) run concurrently. Steps 6 and 7 depend on step 5.

### 4.3 External Integrations

| Integration | Purpose | Protocol | Failure Behaviour |
|-------------|---------|----------|-------------------|
| endoflife.date API | EOL lifecycle data | `GET /api/{product}/{cycle}.json` | Fall back to embedded YAML |
| OSV API | Advisory data | `POST /v1/querybatch` | Skip enrichment; record empty advisory list |
| Maven Central Search | Candidate upgrade versions | `GET /solrsearch/select` | Skip recommendation for that dependency |
| GitHub | Repository clone | JGit HTTPS (read-only) | Fail scan immediately with descriptive error |

All external HTTP calls use Spring `WebClient` with 5-second timeouts and one retry (500ms delay).

---

## 5. Dependency Graph Model

### 5.1 Why a Graph

A Maven project's dependencies form a directed acyclic graph (DAG), not a flat list. The same
artifact can appear as both a direct dependency and as a transitive dependency via multiple paths.
Maven's nearest-wins algorithm resolves version conflicts by selecting the version declared closest
to the root.

DepGuard models this accurately: each `ScanDependency` record captures both the `direct` flag
and the post-conflict-resolution version.

### 5.2 Example

```
payment-service (root)
    │
    ├── [direct, compile] spring-boot-starter-web:3.3.0
    │       │
    │       ├── [transitive] spring-webmvc:6.1.0
    │       ├── [transitive] spring-core:6.1.0
    │       └── [transitive] tomcat-embed-core:10.1.18
    │
    ├── [direct, compile] spring-data-jpa:3.3.0
    │       │
    │       ├── [transitive] spring-core:6.1.0   ← same artifact, nearest-wins resolves it
    │       └── [transitive] hibernate-core:6.4.4
    │
    └── [direct, compile] jackson-databind:2.13.0
```

### 5.3 Risk Implication

The `direct` flag is a key input to the Dependency Risk Heuristic. A direct dependency is more
actionable because the application owner explicitly declared it and can upgrade it without waiting
for an upstream library. Transitive dependency scores are multiplied by 0.6 to reflect reduced
control.

---

## 6. EOL Detection

### 6.1 Data Source

Primary source: **endoflife.date** (`https://endoflife.date`), a community-maintained database of
product lifecycle dates.

```
GET https://endoflife.date/api/spring-boot/2.7.json
→ {
    "cycle": "2.7",
    "releaseDate": "2022-05-19",
    "eol": "2023-11-24",
    "latest": "2.7.18",
    "lts": false,
    "support": "2023-05-19"
  }
```

The `eol` field is a date string. DepGuard compares it against the current date:

| Condition | Status |
|-----------|--------|
| `eol` date is in the past | `EOL` |
| `eol` date is within 90 days | `MAINTENANCE` |
| `eol` date is more than 90 days away | `SUPPORTED` |
| Product not found in endoflife.date (404) | `UNKNOWN` |

### 6.2 Extensible Mapping Strategy

Maven coordinates must be mapped to endoflife.date product names. This mapping is implemented as
a strategy, not as hardcoded `if/else` logic.

```java
interface EolMappingStrategy {
    Optional<ProductCycle> resolve(String groupId, String artifactId, String version);
}
```

`DefaultEolMappingStrategy` loads mappings from `eol-mappings.yml` at startup. New products are
added by editing the YAML — no code changes required:

```yaml
mappings:
  - groupIdPrefix: "org.springframework.boot"
    product: "spring-boot"
  - groupIdPrefix: "org.springframework"
    product: "spring-framework"
  - groupIdPrefix: "org.hibernate"
    product: "hibernate-orm"
  - groupIdPrefix: "org.apache.tomcat"
    product: "tomcat"
  - groupIdPrefix: "org.apache.kafka"
    product: "apache-kafka"
```

Cycle is extracted from `major.minor` of the version string (e.g., `2.7.18` → `2.7`).

If no mapping is found for a `groupId`, EOL status is recorded as `UNKNOWN` with
`source = NO_MAPPING`. This is surfaced in the report — it is never silently treated as LOW risk.

### 6.3 Fallback Dataset

An embedded `eol-fallback.yml` is used when the endoflife.date API is unreachable. It covers the
same products as the mapping configuration with hardcoded EOL dates as of the last update.
`EolRecord.source` is set to `FALLBACK` in this case so reports remain reproducibility-auditable.

---

## 7. Advisory Correlation

### 7.1 Data Source

**OSV** (`https://osv.dev`) aggregates data from GitHub Security Advisories, NVD, Google
OSS-Fuzz, and dozens of ecosystem-specific advisories. It uses the `"Maven"` ecosystem identifier
and returns advisory IDs in `GHSA-*`, `CVE-*`, and `OSV-*` formats.

This is why the entity is named `VulnerabilityRecord` rather than `CveRecord` — the canonical
ID is an OSV ID, which may or may not correspond to a CVE.

### 7.2 Batch Query Strategy

All scan dependencies are batched into a single `POST /v1/querybatch` call (up to 1,000 per
batch), reducing external HTTP calls from O(N) to O(1) per scan:

```json
{
  "queries": [
    { "package": { "name": "org.springframework.boot:spring-boot-starter", "ecosystem": "Maven" }, "version": "2.7.18" },
    { "package": { "name": "com.fasterxml.jackson.core:jackson-databind", "ecosystem": "Maven" }, "version": "2.13.0" }
  ]
}
```

The response preserves input order, so each result maps directly to the queried dependency.

### 7.3 Severity Derivation

Severity is derived from the OSV response in the following priority order:

| Priority | Source | Example |
|----------|--------|---------|
| 1 | `severity[].score` (CVSS vector string) | `"CVSS:3.1/AV:N/AC:L/..."` |
| 2 | `database_specific.severity` (string label) | `"HIGH"` |
| 3 | Numerical CVSS score in `affected[].ecosystem_specific` | `7.5` |
| 4 | None of the above | `Severity.UNKNOWN` |

**`UNKNOWN` severity is never silently treated as MEDIUM or ignored.** It is stored and surfaces
in the report and risk engine with a penalty of +20 points and a `confidence = MEDIUM` flag on
the risk assessment. This is the conservative choice — an advisory with unknown severity may still
be serious.

CVSS numerical score → four-tier severity mapping (used only when the vector string is unavailable):

| CVSS | Severity |
|------|----------|
| 0.0 – 3.9 | LOW |
| 4.0 – 6.9 | MEDIUM |
| 7.0 – 8.9 | HIGH |
| 9.0 – 10.0 | CRITICAL |

---

## 8. Dependency Risk Heuristic

### 8.1 Design Principle

This engine is called a **Dependency Risk Heuristic** deliberately — not a "score" or "rating".
It is a weighted approximation that combines available signal into a single actionable level. It
is not a precise measurement of exploitability or actual exposure.

The heuristic is designed to be:
- **Transparent** — every output includes a `reasons` list explaining which factors contributed
- **Honest about uncertainty** — a `confidence` field explicitly signals when input data was
  incomplete or missing
- **Conservative** — unknown data is never silently treated as safe

### 8.2 Heuristic Formula

```
base_score  = sum of all applicable factor points (additive)
final_score = base_score × position_multiplier
```

**Factor points:**

| Factor | Points | Notes |
|--------|--------|-------|
| EOL | +30 | |
| MAINTENANCE | +10 | Within 90 days of EOL |
| Advisory CRITICAL | +40 | Per advisory |
| Advisory HIGH | +30 | Per advisory |
| Advisory MEDIUM | +15 | Per advisory |
| Advisory LOW | +5 | Per advisory |
| Advisory UNKNOWN severity | +20 | Conservative; flags `confidence = MEDIUM` |
| EOL status UNKNOWN | +0 | Missing data; flags `confidence = LOW` |

**Position multiplier:**

| Position | Multiplier |
|----------|------------|
| Direct dependency | × 1.0 |
| Transitive dependency | × 0.6 |

**Risk level buckets:**

| Score | Risk Level |
|-------|------------|
| 0 – 20 | LOW |
| 21 – 40 | MEDIUM |
| 41 – 70 | HIGH |
| ≥ 71 | CRITICAL |

### 8.3 Confidence

Every `RiskAssessment` includes a `confidence` field:

| Confidence | Meaning |
|------------|---------|
| `HIGH` | All EOL and advisory data was resolved successfully |
| `MEDIUM` | At least one advisory had `UNKNOWN` severity |
| `LOW` | EOL status was `UNKNOWN` — heuristic score may be understated |

A `LOW` confidence assessment with a `LOW` risk level does **not** mean the dependency is safe —
it means DepGuard lacked sufficient data to assess it. The report surfaces this explicitly so
engineers can investigate manually.

### 8.4 Example Calculations

**Example A — EOL direct dependency + one HIGH advisory:**
```
EOL (+30) + Advisory HIGH (+30) = 60 × 1.0 = 60 → HIGH, confidence: HIGH
reasons: ["EOL (+30)", "Advisory GHSA-xxxx HIGH (+30)", "direct (×1.0)"]
```

**Example B — EOL transitive dependency + one HIGH advisory:**
```
(30 + 30) × 0.6 = 36 → MEDIUM, confidence: HIGH
reasons: ["EOL (+30)", "Advisory GHSA-xxxx HIGH (+30)", "transitive (×0.6)"]
```

**Example C — Supported direct dependency + CRITICAL + HIGH advisories:**
```
(40 + 30) × 1.0 = 70 → HIGH, confidence: HIGH
reasons: ["Advisory GHSA-yyyy CRITICAL (+40)", "Advisory GHSA-zzzz HIGH (+30)", "direct (×1.0)"]
```

**Example D — EOL status unknown, one MEDIUM advisory, direct:**
```
MEDIUM (+15) × 1.0 = 15 → LOW, confidence: LOW
reasons: ["EOL status UNKNOWN — manual review recommended", "Advisory GHSA-aaaa MEDIUM (+15)", "direct (×1.0)"]
```

Note: Example D scores LOW but carries `confidence: LOW` — the missing EOL data means the true
score may be higher.

**Example E — Advisory with unknown severity, direct:**
```
UNKNOWN (+20) × 1.0 = 20 → LOW, confidence: MEDIUM
reasons: ["Advisory GHSA-bbbb UNKNOWN severity (+20, conservative)", "direct (×1.0)"]
```

### 8.5 Overall Project Health

The overall project health is the highest risk level present across all `RiskAssessment` records
for the scan. If any assessment has `confidence = LOW`, the overall report includes a warning
flag: `"containsUnknownEolDependencies": true`.

---

## 9. Scan Reproducibility

A scan result must be reproducible and auditable. Two people running DepGuard against the same
repository must be able to verify that they saw the same dependency tree and the same advisory
data at the time of the scan.

### 9.1 Repository Snapshot

When `GitCloneService` clones a repository, it captures:
- `commitSha` — the HEAD commit SHA of the cloned ref
- `branch` — the branch name at clone time

These are stored on the `Scan` record and returned in all report responses. A user can reproduce
the exact scan by checking out the same commit SHA.

### 9.2 Data-Source Metadata

Each enrichment record stores `dataSourceFetchedAt` — the timestamp at which the data was
retrieved from the external API. The scan report's `dataSources` field exposes:

```json
{
  "dataSources": {
    "eolApi": "endoflife.date",
    "eolFetchedAt": "2026-09-21T16:01:12Z",
    "advisoryApi": "api.osv.dev",
    "advisoryFetchedAt": "2026-09-21T16:01:15Z"
  }
}
```

This is important because advisory databases are updated continuously. A dependency that had no
known advisories at scan time may have advisories recorded against it a week later. The timestamps
allow users to understand the freshness of the data and trigger re-scans when needed.

---

## 10. Data Model

### 10.1 Tables

**projects**
```
id              UUID        PK
name            VARCHAR     NOT NULL
repository_url  VARCHAR     NOT NULL
default_branch  VARCHAR     DEFAULT 'main'
created_at      TIMESTAMP   NOT NULL
```

**scans**
```
id              UUID        PK
project_id      UUID        FK → projects.id
status          VARCHAR     NOT NULL  (PENDING|RUNNING|COMPLETED|FAILED)
commit_sha      VARCHAR
branch          VARCHAR
error_message   TEXT
started_at      TIMESTAMP
completed_at    TIMESTAMP
```

**dependencies**
```
id              UUID        PK
group_id        VARCHAR     NOT NULL
artifact_id     VARCHAR     NOT NULL
version         VARCHAR     NOT NULL
ecosystem       VARCHAR     NOT NULL  DEFAULT 'MAVEN'
UNIQUE (group_id, artifact_id, version, ecosystem)
```

**scan_dependencies**
```
scan_id         UUID        FK → scans.id
dependency_id   UUID        FK → dependencies.id
scope           VARCHAR     NOT NULL  (compile|test|provided|runtime)
direct          BOOLEAN     NOT NULL
PRIMARY KEY (scan_id, dependency_id)
```

**eol_records**
```
id                    UUID        PK
scan_id               UUID        FK → scans.id
dependency_id         UUID        FK → dependencies.id
status                VARCHAR     NOT NULL  (SUPPORTED|MAINTENANCE|EOL|UNKNOWN)
eol_date              DATE
source                VARCHAR     NOT NULL  (API|FALLBACK|NO_MAPPING)
data_source_fetched_at TIMESTAMP
UNIQUE (scan_id, dependency_id)
```

**vulnerability_records** *(replaces cve_records — covers GHSA, CVE, OSV IDs)*
```
id                    UUID        PK
osv_id                VARCHAR     NOT NULL UNIQUE
summary               TEXT
severity              VARCHAR     NOT NULL  (LOW|MEDIUM|HIGH|CRITICAL|UNKNOWN)
cvss_score            DECIMAL(3,1)
cvss_vector           VARCHAR
published_at          TIMESTAMP
data_source_fetched_at TIMESTAMP
```

**dependency_vulnerabilities**
```
dependency_id         UUID        FK → dependencies.id
vulnerability_id      UUID        FK → vulnerability_records.id
PRIMARY KEY (dependency_id, vulnerability_id)
```

**risk_assessments**
```
id              UUID        PK
scan_id         UUID        FK → scans.id
dependency_id   UUID        FK → dependencies.id
risk_level      VARCHAR     NOT NULL  (LOW|MEDIUM|HIGH|CRITICAL)
heuristic_score INTEGER     NOT NULL
confidence      VARCHAR     NOT NULL  (HIGH|MEDIUM|LOW)
reasons         JSONB       NOT NULL  (array of strings)
UNIQUE (scan_id, dependency_id)
```

**remediation_recommendations**
```
id                      UUID        PK
scan_id                 UUID        FK → scans.id
dependency_id           UUID        FK → dependencies.id
current_version         VARCHAR     NOT NULL
recommended_version     VARCHAR     NOT NULL
upgrade_type            VARCHAR     NOT NULL  (PATCH|MINOR|MAJOR)
breaking_change_summary TEXT
reason                  TEXT
confidence              VARCHAR     NOT NULL  (HIGH|MEDIUM)
UNIQUE (scan_id, dependency_id)
```

### 10.2 Indexes

```sql
CREATE INDEX idx_scans_project_id           ON scans(project_id);
CREATE INDEX idx_scan_deps_scan_id          ON scan_dependencies(scan_id);
CREATE INDEX idx_eol_records_scan_id        ON eol_records(scan_id);
CREATE INDEX idx_risk_scan_id               ON risk_assessments(scan_id);
CREATE INDEX idx_dep_vulns_dep_id           ON dependency_vulnerabilities(dependency_id);
CREATE INDEX idx_vuln_records_osv_id        ON vulnerability_records(osv_id);
```

---

## 11. API Contract

### 11.1 Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Health check |
| `POST` | `/api/projects` | Register a project (public GitHub URL required) |
| `GET` | `/api/projects` | List all projects |
| `GET` | `/api/projects/{id}` | Get a project |
| `POST` | `/api/projects/{id}/scans` | Trigger a scan (returns 202 immediately) |
| `GET` | `/api/scans/{id}` | Get scan status and dependency list |
| `GET` | `/api/scans/{id}/report` | Full dependency health report |
| `GET` | `/api/scans/{id}/recommendations` | Remediation recommendations |

### 11.2 Key Request/Response Shapes

**POST /api/projects**
```json
// Request
{ "name": "payment-service", "repositoryUrl": "https://github.com/user/payment-service" }

// Response 201
{ "id": "550e8400-...", "name": "payment-service",
  "repositoryUrl": "https://github.com/user/payment-service", "createdAt": "2026-09-21T16:00:00Z" }

// Response 400 — non-GitHub URL
{ "error": "VALIDATION_ERROR", "message": "Only public GitHub URLs are accepted (https://github.com/owner/repo)", "timestamp": "..." }
```

**POST /api/projects/{id}/scans**
```json
// Response 202 — immediate
{ "scanId": "7c9e6679-7425-40de-944b-e07fc1f90ae7" }
```

**GET /api/scans/{id}/report**
```json
{
  "scanId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "projectName": "payment-service",
  "repositoryUrl": "https://github.com/user/payment-service",
  "commitSha": "a3f9d1c8b2e4f6a0d5c7e9f1b3a5d7e9c1a3b5d7",
  "branch": "main",
  "scannedAt": "2026-09-21T16:00:00Z",
  "overallHealth": "HIGH",
  "containsUnknownEolDependencies": true,
  "dataSources": {
    "eolApi": "endoflife.date",
    "eolFetchedAt": "2026-09-21T16:01:12Z",
    "advisoryApi": "api.osv.dev",
    "advisoryFetchedAt": "2026-09-21T16:01:15Z"
  },
  "summary": {
    "totalDependencies": 47,
    "eolCount": 5,
    "vulnerableCount": 3,
    "criticalCount": 1,
    "highCount": 2,
    "mediumCount": 4,
    "lowCount": 35,
    "unknownEolCount": 8
  },
  "dependencies": [
    {
      "groupId": "org.springframework.boot",
      "artifactId": "spring-boot-starter",
      "version": "2.7.18",
      "direct": true,
      "scope": "compile",
      "eolStatus": "EOL",
      "eolDate": "2023-11-24",
      "advisories": [
        { "osvId": "GHSA-xxxx-yyyy-zzzz", "summary": "...", "severity": "HIGH", "cvssScore": 7.5 }
      ],
      "riskLevel": "CRITICAL",
      "heuristicScore": 72,
      "confidence": "HIGH",
      "riskReasons": ["EOL (+30)", "Advisory GHSA-xxxx HIGH (+30)", "direct (×1.0)"],
      "recommendation": {
        "recommendedVersion": "3.4.1",
        "upgradeType": "MAJOR",
        "breakingChangeSummary": "javax → jakarta namespace migration required; Java 17+ required",
        "reason": "Current version is EOL. No supported 2.x release available.",
        "confidence": "MEDIUM"
      }
    },
    {
      "groupId": "com.example",
      "artifactId": "some-unknown-lib",
      "version": "1.0.0",
      "direct": false,
      "scope": "compile",
      "eolStatus": "UNKNOWN",
      "advisories": [],
      "riskLevel": "LOW",
      "heuristicScore": 0,
      "confidence": "LOW",
      "riskReasons": ["EOL status UNKNOWN — manual review recommended"]
    }
  ]
}
```

### 11.3 Error Responses

```json
{
  "error": "NOT_FOUND",
  "message": "Scan not found: 7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "timestamp": "2026-09-21T16:00:00Z"
}
```

| HTTP Status | Error Code | Condition |
|-------------|------------|-----------|
| 400 | VALIDATION_ERROR | Invalid request body or non-GitHub URL |
| 404 | NOT_FOUND | Project or scan not found |
| 409 | SCAN_IN_PROGRESS | Report requested while scan is RUNNING or PENDING |
| 500 | INTERNAL_ERROR | Unexpected server error |

---

## 12. Failure Handling

| Failure | Behaviour |
|---------|-----------|
| Non-GitHub URL submitted | `400 Bad Request` at project creation; scan never starts |
| GitHub clone fails (network error, repo not found) | Scan marked `FAILED` with descriptive error; no partial data stored |
| `pom.xml` not found in cloned repo | Scan marked `FAILED`; temp dir cleaned up |
| endoflife.date API unavailable | Fall back to embedded YAML; `EolRecord.source = FALLBACK` |
| endoflife.date product not found (404) | `EolRecord.status = UNKNOWN`, `source = NO_MAPPING`; surfaces in report |
| OSV API unavailable | Advisory enrichment skipped; dependency recorded with empty advisory list; scan still COMPLETED |
| Advisory with no severity data | `Severity.UNKNOWN` stored; +20 risk points; `confidence = MEDIUM` |
| Maven Central unreachable | No recommendation generated for that dependency; scan still COMPLETED |
| Exception mid-scan (any step) | Scan marked `FAILED` with error message; temp dir cleaned up |

---

## 13. Security

| Concern | Approach |
|---------|----------|
| Repository URL validation | `GitHubUrlValidator` enforces `https://github.com/{owner}/{repo}` pattern at project creation. Non-GitHub URLs, `http://`, and other hosts are rejected with `400`. |
| Repository cloning | JGit `CloneCommand` over HTTPS only. No credentials — public repos only. No shell execution. |
| No build execution | `MavenDependencyResolver` uses the Maven Resolver API to read POM metadata from Maven Central. It never runs `mvn`, never executes plugins, and never compiles or runs code from the cloned repository. |
| Temp directory isolation | Each scan clones to a uniquely named temp directory. The directory is deleted in a `finally` block after resolution completes, regardless of success or failure. |
| SQL injection | All database access via Spring Data JPA with parameterised queries. No string-concatenated SQL. |
| Input validation | `@Valid` + `@NotBlank` + URL pattern validation on all request DTOs. |
| Secrets | Database credentials via environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). Never in source code or Docker images. |
| Outbound only | No inbound webhooks in MVP. All external calls are outbound HTTP to public APIs. |

---

## 14. Scalability

The MVP runs as a single-process application with `@Async` scan execution. The architecture is
designed to evolve in three explicit steps — each motivated by a concrete workload, not an
architectural preference.

### Step 1 — @Async (MVP, already in scope)

`ScanService.triggerScan()` is annotated `@Async`, backed by a `ThreadPoolTaskExecutor`
(core pool: 4, max: 10). The HTTP request returns `202 Accepted` immediately. The client polls
`GET /api/scans/{id}` for completion.

This supports multiple concurrent scans without blocking request threads, and is the correct
scope for the 2–4 week MVP.

### Step 2 — Kafka Event Bus (Phase 5)

When concurrent scan volume justifies it, replace the in-process pipeline with Kafka topics:

```
SCAN_REQUESTED
    → DEPENDENCIES_RESOLVED
        → EOL_ENRICHED (parallel with CVE_ENRICHED)
        → CVE_ENRICHED (parallel with EOL_ENRICHED)
            → RISK_SCORED
                → SCAN_COMPLETED
```

Each consumer scales independently. EOL and advisory workers can fan out to handle large
dependency trees concurrently across multiple scan requests.

### Step 3 — Service Extraction (Phase 7+)

Module boundaries defined in the monolith map directly to candidate service boundaries if
resource usage ever justifies extraction.

---

## 15. Future Roadmap

Phases are ordered by value delivered, not by technical complexity. The core scanner must be
complete before any of these phases begin.

### Phase 5 — Kafka Event-Driven Pipeline

Replace the `@Async` in-process pipeline with Kafka topics and independent consumer workers.
Enables horizontal scaling of enrichment steps.

### Phase 6 — React Dashboard

A React + TypeScript + Vite frontend providing project registration, scan triggering, dependency
health table (sortable by risk level), risk distribution chart, and per-dependency CVE detail panel.

### Phase 7 — Static Reachability Analysis

The central research question:

> *Can we distinguish between a dependency that contains a vulnerability and an application that
> is actually exposed to that vulnerability?*

Approach:
1. For each advisory, identify the affected class and method from the OSV record
2. Use bytecode analysis (ASM) to build a call graph of the application
3. Determine whether the vulnerable method is reachable from any application entry point
4. Adjust the heuristic: unreachable vulnerable methods receive a significantly lower score

This moves DepGuard toward **VEX (Vulnerability Exploitability eXchange)** reasoning.

### Phase 8 — GitHub App and Automated Remediation PRs

1. User installs DepGuard as a GitHub App on their repository
2. DepGuard scans on push / PR / schedule
3. For each HIGH/CRITICAL dependency, DepGuard creates a remediation branch and PR:
   - Updates `pom.xml` with the recommended version
   - PR description includes risk justification, upgrade type, and breaking change summary
   - Links to the full DepGuard report
4. **PRs are never auto-merged** — human review is always required

### Phase 9 — AI-Assisted Analysis

An AI layer receives structured scan data and produces:
- Plain-language explanation of why a dependency is risky
- Assessment of whether the application appears actually exposed
- Migration guide for cross-major upgrades (e.g., Spring Boot 2.x → 3.x)
- Confidence-ranked remediation options

The AI provider is pluggable — any OpenAI-compatible API can be configured. The deterministic
heuristic engine runs first; AI output is supplementary, not a replacement.

---

## 16. Alternatives Considered

### Why not parse pom.xml manually?

Maven's dependency model is significantly more complex than the raw `pom.xml` suggests. Version
ranges, property interpolation, dependency management inheritance, BOMs, and the nearest-wins
conflict resolution algorithm all interact. Manual parsing would produce an inaccurate dependency
list. The Maven Resolver API handles all of this correctly.

### Why not run `mvn dependency:tree` as a subprocess?

Running a subprocess requires Maven to be installed in the container, couples resolution to the
Maven binary version, and — critically — executes plugin lifecycle code from the cloned repository.
This is a security boundary violation: we must not execute code from untrusted repositories.
The Maven Resolver API reads POM metadata only, with no code execution.

### Why OSV over NVD?

OSV aggregates NVD data in addition to GitHub Security Advisories, Google OSS-Fuzz, and
ecosystem-specific advisories. Its API requires no key, its batch query endpoint reduces
HTTP overhead significantly, and its data model maps cleanly to DepGuard's use case. NVD can
be added as a supplementary source in a future phase.

### Why a modular monolith over microservices?

Microservices require orchestration infrastructure (Kubernetes), service mesh concerns, and
distributed tracing — significant operational overhead for a project of this scale. The modular
monolith delivers the same domain separation and testability at a fraction of the setup cost.
Kafka is introduced in Phase 5 because there is a concrete workload that benefits from it
(concurrent enrichment workers), not to satisfy an architectural preference.

### Why @Async over synchronous MVP?

`POST /api/projects/{id}/scans` triggering a scan that takes 10–60 seconds should not hold an
HTTP connection open for that duration. `@Async` allows the endpoint to return `202 Accepted`
immediately with a `scanId`, and the client polls status — which is the standard pattern for
long-running operations. This is also a more honest justification for the `202` status code.
