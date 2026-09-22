Absolutely. I think this is a **very strong project for your profile** because it sits exactly at the intersection you're trying to build: **Java backend + distributed systems + big data + security/AI**.

I would **not** try to clone HeroDevs. Instead, build a focused open-source prototype around the problem:

> **“Given a Java/Maven project, understand its dependency health, identify EOL software and known vulnerabilities, determine the actual risk, and recommend safe remediation.”**

# Project: Dependency Intelligence Platform

Working name: **DepGuard**

### Core idea

```text
                 ┌─────────────────────┐
                 │   Java/Maven Repo   │
                 │      pom.xml        │
                 └──────────┬──────────┘
                            │
                            ▼
                 ┌─────────────────────┐
                 │ Dependency Analyzer │
                 └──────────┬──────────┘
                            │
             ┌──────────────┼──────────────┐
             ▼              ▼              ▼
       Dependency       EOL Dataset     CVE Dataset
          Graph             │              │
             │              │              │
             └──────────────┼──────────────┘
                            ▼
                 ┌─────────────────────┐
                 │     Risk Engine     │
                 └──────────┬──────────┘
                            │
               ┌────────────┼────────────┐
               ▼            ▼            ▼
          EOL Report    CVE Report   Risk Score
                            │
                            ▼
                 ┌─────────────────────┐
                 │ Remediation Engine  │
                 └──────────┬──────────┘
                            │
                ┌───────────┴───────────┐
                ▼                       ▼
          Upgrade Advice         GitHub PR
```

The important thing is that **the first version should not attempt to do everything**.

---

# 1. MVP

Start with one very clear workflow:

```text
User submits Git repository
             ↓
Clone repository
             ↓
Find pom.xml
             ↓
Parse dependencies
             ↓
Resolve dependency tree
             ↓
Check EOL status
             ↓
Check known vulnerabilities
             ↓
Generate dependency health report
```

For example:

```text
Project: payment-service

Dependency Health
──────────────────────────────────────────────

Spring Boot       2.7.18    EOL       HIGH
Spring Framework  5.3.31    EOL       HIGH
Hibernate         5.6.15    EOL       MEDIUM
Tomcat            9.0.83    Supported LOW
Jackson           2.15.2    CVE       HIGH

Overall Health:  HIGH RISK
```

That's already a useful project.

---

# 2. Architecture

I'd use **Java 21 + Spring Boot** because it reinforces the exact engineering profile you're trying to develop.

```text
                        ┌───────────────┐
                        │   Frontend    │
                        │ React / Vite  │
                        └───────┬───────┘
                                │
                              REST
                                │
                                ▼
                    ┌─────────────────────┐
                    │    API Gateway      │
                    │    Spring Boot      │
                    └──────────┬──────────┘
                               │
               ┌───────────────┼────────────────┐
               │               │                │
               ▼               ▼                ▼
       ┌─────────────┐ ┌──────────────┐ ┌──────────────┐
       │ Repo Service│ │ Dependency   │ │ Report       │
       │             │ │ Analyzer     │ │ Service      │
       └──────┬──────┘ └──────┬───────┘ └──────┬───────┘
              │               │                │
              └───────────────┼────────────────┘
                              │
                            Kafka
                              │
               ┌──────────────┼──────────────┐
               ▼              ▼              ▼
        ┌────────────┐ ┌─────────────┐ ┌──────────────┐
        │ CVE Worker │ │ EOL Worker  │ │ Risk Worker  │
        └─────┬──────┘ └──────┬──────┘ └──────┬───────┘
              │               │                │
              └───────────────┼────────────────┘
                              ▼
                        ┌───────────┐
                        │PostgreSQL │
                        └───────────┘
                              │
                           Redis
```

But **don't actually deploy all these as separate microservices initially**.

Start as a **modular monolith**.

That's important.

---

# 3. Backend modules

Your Spring Boot project can initially look like:

```text
depguard/
│
├── api/
│   ├── ProjectController
│   ├── ScanController
│   └── ReportController
│
├── project/
│   ├── Project
│   ├── ProjectService
│   └── ProjectRepository
│
├── repository/
│   ├── GitRepositoryService
│   └── RepositoryMetadata
│
├── dependency/
│   ├── DependencyAnalyzer
│   ├── MavenDependencyResolver
│   ├── Dependency
│   └── DependencyGraph
│
├── vulnerability/
│   ├── VulnerabilityService
│   ├── Cve
│   └── CveRepository
│
├── eol/
│   ├── EolService
│   ├── EolProduct
│   └── EolVersion
│
├── risk/
│   ├── RiskEngine
│   ├── RiskScore
│   └── RiskRule
│
├── remediation/
│   ├── RemediationService
│   └── UpgradeRecommendation
│
└── infrastructure/
    ├── KafkaConfig
    ├── RedisConfig
    └── SecurityConfig
```

This lets you later extract modules into services if scale requires it.

---

# 4. Dependency model

This is one of the most important parts.

Don't store dependencies as just:

```text
spring-core → 5.3.31
```

You need relationships.

Example:

```text
my-payment-service
       │
       ├── spring-boot-starter-web
       │          │
       │          ├── spring-core
       │          ├── spring-web
       │          └── tomcat
       │
       ├── spring-data-jpa
       │          │
       │          ├── spring-core
       │          └── hibernate
       │
       └── jackson
```

Your database therefore needs something like:

```text
projects
dependencies
dependency_relationships
versions
products
eol_records
vulnerabilities
dependency_vulnerabilities
scans
scan_dependencies
risk_assessments
remediation_recommendations
```

---

# 5. EOL intelligence

This is where the HeroDevs inspiration becomes interesting.

You need to distinguish:

```text
SUPPORTED
MAINTENANCE
EOL
UNKNOWN
```

For example:

```text
Spring Boot 2.7
        │
        ├── release: 2022
        ├── maintenance ended
        ├── EOL: 2023/2024
        └── current status: EOL
```

Don't hardcode this into Java.

Create an **EOL dataset ingestion pipeline**.

```text
External EOL Dataset
        ↓
Kafka
        ↓
EOL Ingestion Worker
        ↓
Normalize
        ↓
PostgreSQL
```

That gives you a legitimate distributed-data component.

---

# 6. Vulnerability ingestion

Same idea.

```text
CVE sources
    │
    ▼
Ingestion
    │
    ▼
Normalization
    │
    ▼
Kafka
    │
    ▼
Vulnerability database
```

Eventually:

```text
NVD
GitHub Advisories
OSV
Vendor advisories
```

You don't need all four initially.

Start with **OSV** because its API/data model is developer-friendly.

---

# 7. The Risk Engine

This is where your project becomes much more interesting than a simple dependency scanner.

Don't simply say:

> CVE = HIGH

Instead:

```text
Risk =
    vulnerability severity
  + exploitability
  + dependency exposure
  + dependency reachability
  + EOL status
  + application context
```

For example:

```text
Dependency:

Spring Framework 5.3.x

CVE severity: HIGH
EOL: YES
Direct dependency: YES
Runtime reachable: YES

→ CRITICAL
```

Whereas:

```text
Dependency:

Old library

CVE severity: HIGH
EOL: YES
Transitive dependency: YES
Not reachable from application code

→ MEDIUM
```

This is where you eventually introduce **VEX-style reasoning**.

---

# 8. Reachability analysis

This should be a later milestone, not MVP.

Imagine:

```text
CVE
 │
 ▼
Affected library
 │
 ▼
Affected class
 │
 ▼
Application dependency
 │
 ▼
Application code
```

You want to determine:

> Is the vulnerable functionality actually reachable by this application?

For example:

```text
Jackson CVE
     ↓
ObjectMapper
     ↓
Application uses ObjectMapper
     ↓
YES
     ↓
Higher risk
```

Versus:

```text
Jackson CVE
     ↓
Affected XML parser
     ↓
Application only uses JSON
     ↓
Likely not reachable
     ↓
Lower risk
```

You don't need perfect static analysis initially. Even a **rule-based reachability prototype** would be interesting.

---

# 9. Remediation Engine

Once you've identified a problem:

```text
Spring Boot 2.7.18
       ↓
EOL
       ↓
Known vulnerabilities
       ↓
Find supported version
       ↓
Spring Boot 3.x
```

Produce:

```text
Recommendation

Current:
Spring Boot 2.7.18

Recommended:
Spring Boot 3.x

Reason:
- Current version is EOL
- Known vulnerabilities detected
- Supported replacement available

Potential breaking changes:
- javax → jakarta
- configuration changes
- dependency compatibility

Confidence:
82%
```

Notice the wording:

**recommendation**, not automatic upgrade.

That's important.

---

# 10. AI layer

Don't start with AI.

Once the deterministic engine works, add AI.

The AI receives structured information:

```json
{
  "dependency": "spring-framework",
  "version": "5.3.31",
  "eol": true,
  "cves": [
    {
      "id": "CVE-XXXX",
      "severity": "HIGH"
    }
  ],
  "usage": [
    "org.springframework.web",
    "org.springframework.http"
  ]
}
```

Then produces:

```text
Explain:
- Why this is risky
- Whether the application appears exposed
- What upgrade path is appropriate
- Potential breaking changes
```

This fits your **Backend × Big Data × AI** direction naturally.

---

# 11. GitHub integration

This should be one of the later killer features.

User connects GitHub:

```text
GitHub Repository
       ↓
DepGuard
       ↓
Scan
       ↓
Problem detected
       ↓
Generate remediation branch
       ↓
Create Pull Request
```

Example:

```text
🤖 DepGuard

Upgrade Spring Boot 2.7.18 → 3.4.x

Reason:
✓ EOL
✓ Security vulnerabilities
✓ Supported release available

Changes:
pom.xml
application.properties

Tests:
187 passed
3 failed

Review required.
```

**Never automatically merge.**

---

# 12. Dashboard

Your frontend doesn't need to be complicated.

Main dashboard:

```text
┌──────────────────────────────────────────────┐
│ Dependency Health                            │
├──────────────────────────────────────────────┤
│                                              │
│  Projects        12                          │
│  Dependencies    347                         │
│  EOL             21                          │
│  Vulnerabilities 14                          │
│  Critical        3                           │
│                                              │
├──────────────────────────────────────────────┤
│ Risk Distribution                            │
│                                              │
│ Critical ███                                 │
│ High     ███████                             │
│ Medium   ███████████                         │
│ Low      █████████████████                   │
│                                              │
└──────────────────────────────────────────────┘
```

Then project detail:

```text
Payment Service

Health: HIGH RISK

Dependency             Version    Status
------------------------------------------------
Spring Boot             2.7.18     EOL
Spring Framework         5.3.31    EOL
Hibernate                5.6.15    EOL
Kafka                    3.6.x     Supported
Jackson                  2.15.x    Vulnerable
```

---

# 13. Event-driven architecture

Once your MVP works, introduce Kafka.

Example events:

```text
PROJECT_SCAN_REQUESTED
DEPENDENCY_DISCOVERED
EOL_STATUS_UPDATED
VULNERABILITY_DISCOVERED
RISK_ASSESSMENT_REQUESTED
REMEDIATION_REQUESTED
SCAN_COMPLETED
```

Example:

```text
POST /projects/{id}/scan

        ↓

PROJECT_SCAN_REQUESTED

        ↓

Kafka

        ↓
Dependency Analyzer

        ↓

DEPENDENCY_DISCOVERED

        ↓

Kafka

        ↓
Vulnerability Worker
EOL Worker

        ↓

RISK_ASSESSMENT_REQUESTED

        ↓

Risk Engine

        ↓

SCAN_COMPLETED
```

Now you're demonstrating the same distributed-system concepts you've worked with professionally.

---

# 14. Database design

I'd start PostgreSQL.

Core tables:

```text
projects
---------
id
name
repository_url
default_branch
created_at


scans
---------
id
project_id
status
started_at
completed_at


dependencies
---------
id
ecosystem
group_id
artifact_id
version


project_dependencies
---------
project_id
dependency_id
scope
direct


eol_records
---------
id
product
version
release_date
eol_date
status
source


vulnerabilities
---------
id
cve_id
severity
cvss_score
summary
published_at


dependency_vulnerabilities
---------
dependency_id
vulnerability_id


risk_assessments
---------
id
scan_id
dependency_id
risk_level
reason
confidence


remediation_recommendations
---------
id
dependency_id
current_version
recommended_version
reason
breaking_changes
confidence
```

---

# 15. Technology stack

I'd deliberately keep it close to what you already know:

### Backend

**Java 21**

**Spring Boot 3.x**

**Spring Data JPA**

**PostgreSQL**

**Kafka**

**Redis**

**Docker**

### Dependency analysis

**Maven Resolver**

This is important because you're building a Java dependency intelligence platform. Don't parse `pom.xml` manually if Maven already gives you the machinery.

### Security data

Start with:

**OSV**

Then add:

**NVD**

### Frontend

**React + TypeScript + Vite**

### AI

Initially:

**OpenAI-compatible API**

Later make the AI provider pluggable.

### GitHub

GitHub REST API / GitHub App.

---

# 16. Development roadmap

Don't start by building 15 services.

### Phase 1 — Dependency Scanner

Build:

```text
Git repo
 ↓
pom.xml
 ↓
Maven dependency tree
 ↓
PostgreSQL
 ↓
REST API
```

Goal:

```http
GET /projects/{id}/dependencies
```

---

### Phase 2 — EOL Intelligence

Add:

```text
EOL dataset
 ↓
Ingestion
 ↓
Dependency matching
 ↓
EOL report
```

Now:

```text
Spring Boot 2.7.18 → EOL
```

---

### Phase 3 — CVE Intelligence

Add OSV.

```text
Dependency
 ↓
CVE lookup
 ↓
Vulnerability report
```

---

### Phase 4 — Risk Engine

Combine:

```text
EOL
+
CVE
+
Direct/transitive
+
Severity
+
Reachability
```

Produce a meaningful risk assessment.

---

### Phase 5 — Dashboard

Build the UI.

---

### Phase 6 — Kafka

Move scans and enrichment to asynchronous processing.

---

### Phase 7 — GitHub

Automatically create remediation PRs.

---

### Phase 8 — AI

Add:

```text
CVE explanation
+
risk explanation
+
upgrade recommendation
+
breaking-change analysis
```

---

### Phase 9 — Advanced reachability

This becomes your research/engineering challenge.

---

# 17. The project should have an RFC

Given your existing **RFC-0001 Idempotent Payment Processing**, I'd actually make this another serious engineering project rather than just a GitHub demo.

For example:

```text
RFC-0003: Dependency Intelligence and Automated
           Remediation Platform
```

Document:

```text
Problem
Goals
Non-goals
Architecture
Dependency model
EOL detection
CVE correlation
Risk model
Event model
Data model
Security
Failure handling
Scalability
AI boundaries
```

This will make the project much more credible.

---

# 18. The really interesting research question

I would make this the central question of the project:

> **Can we distinguish between a dependency that contains a vulnerability and an application that is actually exposed to that vulnerability?**

That moves you beyond:

```text
"Here are your CVEs."
```

toward:

```text
"Here are the vulnerabilities that actually matter
to your application and why."
```

And that is where **dependency graphs + static analysis + vulnerability intelligence + VEX + AI** become interesting.

---

# 19. Your eventual architecture

If this grows significantly, you could evolve it into:

```text
                    ┌───────────────┐
                    │ React Console │
                    └───────┬───────┘
                            │
                            ▼
                     API Gateway
                            │
        ┌───────────────────┼────────────────────┐
        │                   │                    │
        ▼                   ▼                    ▼
 Repository            Dependency           Reporting
 Service               Intelligence          Service
        │                   │                    │
        └─────────────┬─────┴────────────────────┘
                      │
                    Kafka
                      │
        ┌─────────────┼──────────────┐
        ▼             ▼              ▼
     EOL Worker    CVE Worker    Risk Engine
        │             │              │
        └─────────────┼──────────────┘
                      ▼
                 PostgreSQL
                      │
                    Redis
                      │
                      ▼
              Remediation Engine
                      │
                      ▼
                 GitHub App
                      │
                      ▼
                 Pull Request

                      +
                 AI Analysis
```

But **earn that architecture**. Start modular monolith → introduce Kafka when you have an actual asynchronous workload → extract services only where there's a reason.

That itself becomes something you can discuss in interviews.

### Your first concrete milestone

Don't start coding Kafka, React, AI, or GitHub integration yet.

Start with:

> **A Java CLI that accepts a Git repository containing a `pom.xml`, resolves the complete Maven dependency graph, and outputs every direct/transitive dependency with its group ID, artifact ID, version, and scope.**

Once that works, we'll build the **EOL matching engine** on top of it.

That gives you a clean foundation and lets us evolve the project incrementally rather than over-engineering it from day one.
