# DepGuard

**Dependency Intelligence Platform for Java/Maven projects.**

DepGuard analyzes a GitHub repository's Maven dependency tree, determines which dependencies are
End-of-Life or have known security advisories, computes a Dependency Risk Heuristic for each one,
and recommends safe upgrade paths — all through a single REST API call.

---

## The Problem

Most dependency scanners answer: *"Which of your dependencies have CVEs?"*

DepGuard answers: *"Which of your dependencies are actually risky, why, and what should you do about it?"*

A transitive Tomcat advisory that cannot be reached from your application code is a different
problem from a direct Spring Boot dependency that is EOL, has a CVSS 8.9 advisory, and is called
on every request. DepGuard's risk engine accounts for that difference.

---

## Quick Start

**Prerequisites:** Docker, Docker Compose

```bash
git clone https://github.com/your-username/depguard.git
cd depguard
docker compose up
```

The API is ready when you see:
```
depguard  | Started DepGuardApplication in X.XXX seconds
```

---

## Example Usage

### 1. Register a project

Only public GitHub repositories are accepted.

```bash
curl -s -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "payment-service",
    "repositoryUrl": "https://github.com/your-org/payment-service"
  }' | jq .
```

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "payment-service",
  "repositoryUrl": "https://github.com/your-org/payment-service",
  "createdAt": "2026-09-21T16:00:00Z"
}
```

### 2. Trigger a scan

Returns `202 Accepted` immediately — scan runs asynchronously.

```bash
curl -s -X POST \
  http://localhost:8080/api/projects/550e8400-e29b-41d4-a716-446655440000/scans | jq .
```

```json
{
  "scanId": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
}
```

### 3. Poll scan status

```bash
curl -s http://localhost:8080/api/scans/7c9e6679-7425-40de-944b-e07fc1f90ae7 | jq .status
# "COMPLETED"
```

### 4. Get the health report

```bash
curl -s http://localhost:8080/api/scans/7c9e6679-7425-40de-944b-e07fc1f90ae7/report | jq .
```

```json
{
  "projectName": "payment-service",
  "commitSha": "a3f9d1c8b2e4f6a0d5c7e9f1b3a5d7e9c1a3b5d7",
  "branch": "main",
  "scannedAt": "2026-09-21T16:00:00Z",
  "overallHealth": "HIGH",
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
    "unknownEolCount": 8
  },
  "dependencies": [
    {
      "groupId": "org.springframework.boot",
      "artifactId": "spring-boot-starter",
      "version": "2.7.18",
      "direct": true,
      "eolStatus": "EOL",
      "eolDate": "2023-11-24",
      "advisories": [
        { "osvId": "GHSA-xxxx-yyyy-zzzz", "severity": "HIGH", "cvssScore": 7.5 }
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
    }
  ]
}
```

### 5. Get remediation recommendations only

```bash
curl -s http://localhost:8080/api/scans/7c9e6679.../recommendations | jq .
```

```json
[
  {
    "artifactId": "spring-boot-starter",
    "currentVersion": "2.7.18",
    "recommendedVersion": "3.4.1",
    "upgradeType": "MAJOR",
    "breakingChangeSummary": "javax → jakarta namespace migration required; Java 17+ required",
    "reason": "Current version is EOL. No supported 2.x release available.",
    "confidence": "MEDIUM"
  },
  {
    "artifactId": "jackson-databind",
    "currentVersion": "2.13.0",
    "recommendedVersion": "2.17.1",
    "upgradeType": "MINOR",
    "breakingChangeSummary": "",
    "reason": "Known advisory GHSA-xxxx. Version 2.17.x resolves all known advisories.",
    "confidence": "HIGH"
  }
]
```

---

## API Reference

Full interactive documentation available at **http://localhost:8080/swagger-ui.html** after startup.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/health` | Health check |
| `POST` | `/api/projects` | Register a public GitHub repository |
| `GET` | `/api/projects` | List registered projects |
| `GET` | `/api/projects/{id}` | Get a project |
| `POST` | `/api/projects/{id}/scans` | Trigger a dependency scan (202 Accepted) |
| `GET` | `/api/scans/{id}` | Get scan status and raw dependency list |
| `GET` | `/api/scans/{id}/report` | Full dependency health report with reproducibility metadata |
| `GET` | `/api/scans/{id}/recommendations` | Remediation recommendations |

---

## Architecture

```
POST /api/projects/{id}/scans  →  202 Accepted immediately
              │
              ▼  [Background — @Async thread pool]
        ScanService
              │
    ┌─────────┴──────────┐
    ▼                    ▼
GitCloneService      captures commitSha + branch
(JGit HTTPS)         for scan reproducibility
    │
    ▼
MavenDependencyResolver
(Maven Resolver API — reads POM metadata only,
 never executes Maven builds)
    │
    ▼
Dependency Graph
    │
    ├──────────────────────────────┐
    ▼ (parallel)                   ▼ (parallel)
EolService                    VulnerabilityService
(endoflife.date API)          (OSV /v1/querybatch)
(+ extensible YAML mapping)   (GHSA/CVE/OSV IDs)
    │                              │
    └──────────────┬───────────────┘
                   ▼
        RiskScoringService
        (Dependency Risk Heuristic
         — with confidence flags for
           unknown EOL / advisory data)
                   │
                   ▼
        RemediationService
        (compatible upgrade paths,
         not blindly latest version)
                   │
                   ▼
              PostgreSQL
                   │
                   ▼
    GET /api/scans/{id}/report
```

**Module boundaries:**

```
src/main/java/io/depguard/
├── project/        Register and validate GitHub repositories
├── scan/           Scan lifecycle orchestration (@Async)
├── dependency/     Maven tree resolution + Git clone (public GitHub only)
├── eol/            EOL enrichment (strategy-based product mapping)
├── vulnerability/  Advisory enrichment via OSV (VulnerabilityRecord)
├── risk/           Dependency Risk Heuristic engine
├── remediation/    Compatible upgrade recommendations
└── infrastructure/ WebClient config, exception handling
```

---

## Dependency Risk Heuristic

DepGuard computes a heuristic score per dependency combining:

| Factor | Points |
|--------|--------|
| Dependency is EOL | +30 |
| Dependency is in maintenance window | +10 |
| Advisory CRITICAL (CVSS ≥ 9.0) | +40 |
| Advisory HIGH (CVSS 7.0–8.9) | +30 |
| Advisory MEDIUM (CVSS 4.0–6.9) | +15 |
| Advisory LOW (CVSS < 4.0) | +5 |
| Advisory with unknown severity | +20 (conservative) |

Position multiplier: direct dependency **× 1.0**, transitive dependency **× 0.6**

| Score | Risk Level |
|-------|------------|
| 0–20 | 🟢 LOW |
| 21–40 | 🟡 MEDIUM |
| 41–70 | 🟠 HIGH |
| ≥ 71 | 🔴 CRITICAL |

Every assessment includes:
- `riskReasons` — exactly which factors contributed and by how much
- `confidence` — `HIGH` (all data resolved), `MEDIUM` (advisory severity unknown), or `LOW`
  (EOL status unknown — score may be understated)

Unknown EOL or advisory data is never silently treated as LOW risk. It surfaces explicitly in
the report so engineers can investigate manually.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| Persistence | Spring Data JPA + PostgreSQL 16 |
| Migrations | Flyway |
| Dependency resolution | Apache Maven Resolver API (no Maven build execution) |
| Git operations | JGit (HTTPS, public GitHub repos only) |
| HTTP client | Spring WebClient |
| EOL data | endoflife.date API + embedded YAML fallback (extensible mapping strategy) |
| Advisory data | OSV API (`api.osv.dev`) — covers GHSA, CVE, OSV IDs |
| API docs | springdoc-openapi / Swagger UI |
| Async execution | Spring `@Async` + `ThreadPoolTaskExecutor` |
| Containerisation | Docker + Docker Compose |
| Testing | JUnit 5 + MockMvc + MockWebServer |

---

## Project Structure

```
depguard/
├── src/
│   ├── main/
│   │   ├── java/io/depguard/       Java source
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── eol-mappings.yml    Extensible EOL product mapping (edit to add products)
│   │       ├── eol-fallback.yml    Embedded EOL data (used when API is unavailable)
│   │       └── db/migration/       Flyway SQL migrations
│   └── test/
│       ├── java/                   Unit + integration tests
│       └── resources/
│           └── test-pom.xml        Test fixture for dependency resolution
├── docker-compose.yml
├── pom.xml
├── IMPLEMENTATION_PLAN.md
├── RFC-0001-depguard.md            Engineering RFC
└── README.md
```

---

## Development

**Run locally (without Docker):**

Requirements: Java 21, Maven 3.9+, PostgreSQL 16 on localhost:5432

```bash
export DB_URL=jdbc:postgresql://localhost:5432/depguard
export DB_USERNAME=depguard
export DB_PASSWORD=secret

mvn spring-boot:run
```

**Run tests:**

```bash
mvn test
```

**Build:**

```bash
mvn package -DskipTests
docker compose build
```

---

## Roadmap

| Phase | Status | Description |
|-------|--------|-------------|
| 1 | ✅ MVP | Spring Boot 4.x skeleton, project API, Docker Compose, GitHub URL validation |
| 2 | ✅ MVP | Maven dependency tree resolution (Maven Resolver API, no build execution) |
| 3 | ✅ MVP | EOL enrichment (endoflife.date + extensible mapping strategy + YAML fallback) |
| 4 | ✅ MVP | Advisory enrichment (OSV API, VulnerabilityRecord, UNKNOWN severity handling) |
| 5 | ✅ MVP | Dependency Risk Heuristic (confidence flags, UNKNOWN data handling) |
| 6 | ✅ MVP | Compatible upgrade recommendations (not blindly latest version) |
| 7 | ✅ MVP | Polished report API + reproducibility metadata + Swagger UI |
| 8 | 🔜 Next | Kafka event-driven scan pipeline |
| 9 | 🔜 Next | React + TypeScript dashboard |
| 10 | 🔜 Future | Static reachability analysis (VEX-style) |
| 11 | 🔜 Future | GitHub App + automated remediation PRs |
| 12 | 🔜 Future | AI-assisted advisory explanation and upgrade guidance |

---

## Engineering Design

See [RFC-0001-depguard.md](./RFC-0001-depguard.md) for the full engineering design document
covering architecture decisions, the dependency graph model, Dependency Risk Heuristic rationale,
unknown data handling, scan reproducibility, Git security controls, data model, API contract,
failure handling, scalability evolution (sync → `@Async` → Kafka), and future roadmap.

---

## License

MIT
