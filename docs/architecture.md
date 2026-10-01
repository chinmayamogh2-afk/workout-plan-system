# Architecture – Workout Plan Management System

## 1. High-Level Overview

The system is a Spring Boot MVC web application backed by MySQL, containerised with Docker, deployed via Jenkins CI/CD and Ansible, and tested with JUnit, Mockito, and Selenium.

```
┌─────────────────────────────────────────────────────────────────┐
│                         Browser (User)                          │
└───────────────────────────────┬─────────────────────────────────┘
                                │  HTTP (port 8080)
┌───────────────────────────────▼─────────────────────────────────┐
│             Spring Boot Application (Tomcat embedded)           │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Spring Security Filter Chain                            │   │
│  │  - Form login  (/login)                                  │   │
│  │  - TRAINER and ADMIN roles                               │   │
│  │  - CSRF protection                                       │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  MVC Controllers                                         │   │
│  │  - AuthController      (/login, /logout)                 │   │
│  │  - DashboardController (/dashboard)                      │   │
│  │  - WorkoutPlanController (/workouts/**)                  │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                   │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │  WorkoutPlanService                                      │   │
│  │  - CRUD logic             - Status transition guard      │   │
│  │  - Search / filter        - Role-based access control    │   │
│  │  - Dashboard stats        - DTO <-> Entity mapping       │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │                                   │
│  ┌──────────────────────────▼───────────────────────────────┐   │
│  │  Spring Data JPA Repositories                            │   │
│  │  - WorkoutPlanRepository (custom JPQL queries)           │   │
│  │  - AppUserRepository                                     │   │
│  └──────────────────────────┬───────────────────────────────┘   │
│                             │  JPA / JDBC                       │
└─────────────────────────────┼───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                  MySQL 8 (Docker container)                      │
│              Volume: mysql_data  Network: workout-network        │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Package Structure

```
com.devops.workout
├── WorkoutPlanApplication          Spring Boot entry point
├── config/
│   ├── DataInitializer             Seeds default users (admin, trainer) on startup
│   └── SecurityConfig              Form login, role config, public paths
├── controller/
│   ├── AuthController              /login redirect handling
│   ├── DashboardController         /dashboard stats
│   └── WorkoutPlanController       Full CRUD + status-change endpoints
├── dto/
│   ├── WorkoutPlanDto              Input/output DTO (also carries validation annotations)
│   └── DashboardStatsDto           Aggregated statistics DTO
├── exception/
│   ├── ResourceNotFoundException   Thrown when entity lookup fails → 404
│   ├── InvalidStatusTransitionException  Thrown on illegal workflow step → 400
│   └── GlobalExceptionHandler      @ControllerAdvice mapping exceptions to error views
├── model/
│   ├── AppUser                     Spring Security user entity (username, password, role)
│   └── WorkoutPlan                 Core domain entity; embeds DifficultyLevel and WorkoutStatus enums
├── repository/
│   ├── AppUserRepository           findByUsername()
│   └── WorkoutPlanRepository       Custom JPQL: searchByKeyword, filterPlans, countByStatus, countByDifficulty
├── security/
│   └── AppUserDetailsService       Loads UserDetails from AppUserRepository
└── service/
    └── WorkoutPlanService          All business rules and orchestration
```

---

## 3. Data Model

### AppUser

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-generated |
| username | VARCHAR(50) | Unique |
| password | VARCHAR(255) | BCrypt encoded |
| role | VARCHAR(20) | TRAINER or ADMIN |

### WorkoutPlan

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-generated |
| name | VARCHAR(150) | Required, 3–150 chars |
| description | TEXT | Required, max 1000 chars |
| exercise_details | TEXT | Required |
| duration_minutes | INT | 1–600 |
| difficulty | VARCHAR(20) | BEGINNER / INTERMEDIATE / ADVANCED |
| target_muscle_group | VARCHAR(100) | Required |
| trainer_name | VARCHAR(100) | Required |
| status | VARCHAR(20) | DRAFT / SUBMITTED / APPROVED / PUBLISHED |
| created_by | VARCHAR(100) | Spring Security principal username |
| created_at | DATETIME | Auto-set on insert |
| updated_at | DATETIME | Auto-updated on save |

---

## 4. Status-Transition State Machine

```
          DRAFT
            │
            │  owner submits (TRAINER or ADMIN)
            ▼
        SUBMITTED
         │     │
  admin  │     │  admin rejects
approves │     │  (returns to DRAFT)
         │     ▼
         │   DRAFT
         ▼
       APPROVED
            │
            │  admin publishes
            ▼
        PUBLISHED  (terminal – no further transitions)
```

Implementation: `WorkoutPlan.WorkoutStatus.canTransitionTo()` is the single source of truth. `WorkoutPlanService.changeStatus()` enforces it and applies role-based permission on top.

---

## 5. Security Model

| Endpoint Pattern | Access Rule |
|---|---|
| /login, /actuator/health | Public |
| /workouts/new, POST /workouts | Authenticated (any role) |
| /workouts/{id}/delete | Owner or ADMIN |
| /workouts/{id}/status (APPROVED, PUBLISHED) | ADMIN only |
| /dashboard | Authenticated (any role) |

Spring Security uses `AppUserDetailsService` to load users from MySQL. Passwords are BCrypt-encoded (strength 10). CSRF protection is enabled.

---

## 6. Test Architecture

```
Tests
├── Unit (Mockito)
│   ├── WorkoutPlanServiceTest          12 tests – service logic, no DB
│   └── WorkoutStatusTransitionTest     canTransitionTo() exhaustive cases
├── Integration (H2 in-memory)
│   └── WorkoutPlanRepositoryTest       Custom JPQL query verification
└── Selenium E2E (ChromeDriver)
    └── WorkoutPlanSeleniumTest         7 end-to-end browser tests
```

The Maven Surefire plugin runs unit + integration tests on `mvn test`.
Selenium tests are isolated under `mvn failsafe:integration-test` to avoid requiring a running application during normal builds.

---

## 7. Docker Architecture

```
Dockerfile (multi-stage)
  Stage 1: maven:3.9-eclipse-temurin-21  →  builds JAR
  Stage 2: eclipse-temurin:21-jre-jammy  →  minimal runtime

docker-compose.yml
  Services:
    db:  mysql:8.0  (health-checked, named volume mysql_data)
    app: workout-plan-system (waits for db healthy, HEALTHCHECK on /actuator/health)
  Network: workout-network (bridge)
```

The application image runs as non-root user `fitflow` for security.

---

## 8. CI/CD Architecture

```
Developer pushes to GitHub
         │
         ├─► GitHub Actions (ci.yml)
         │       build → test → package → docker-build → docker-push (main only)
         │
         └─► Jenkins (Jenkinsfile) — when configured with GitHub webhook
                 Checkout → Build → Test → Package → Docker Build
                 → Selenium Test → Docker Push → Deploy → Health Check
```

Both pipelines mirror each other. GitHub Actions provides fast feedback without needing a Jenkins server; Jenkins provides the full deployment capability.

---

## 9. Ansible Provisioning Architecture

```
Control Node (developer machine)
    │
    │  ansible-playbook
    ▼
Target Host (remote Ubuntu server or localhost)
    │
    ├── Role: common   – apt update, curl, wget, git, ufw
    ├── Role: java     – openjdk-21-jdk
    ├── Role: docker   – docker-ce, docker-compose-plugin
    └── Role: app      – docker-compose.yml + .env → docker compose up -d
                         health-check via uri module until /actuator/health = 200
```
