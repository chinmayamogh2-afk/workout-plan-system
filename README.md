# 🏋️ Workout Plan Management System

A full-stack DevOps college project demonstrating the complete software delivery lifecycle — from a Spring Boot web application through containerisation, CI/CD, configuration management, automated testing, and rollback.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Features](#features)
- [Local Development](#local-development)
- [Running with Docker Compose](#running-with-docker-compose)
- [Jenkins CI/CD Pipeline](#jenkins-cicd-pipeline)
- [Ansible Provisioning](#ansible-provisioning)
- [Testing](#testing)
- [Rollback](#rollback)
- [Default Credentials](#default-credentials)
- [API / Endpoints](#api--endpoints)
- [Project Structure](#project-structure)

---

## Overview

The **Workout Plan Management System** (FitFlow) is a web application that lets fitness trainers create, manage, and publish workout plans through a structured role-based approval workflow. An admin dashboard provides real-time statistics across plan statuses and difficulty levels.

---

## Architecture

```
Browser
  │
  ▼
Spring Boot (Thymeleaf MVC)  <── Spring Security (TRAINER / ADMIN)
  │
  ▼
WorkoutPlanService  (business rules, status-transition guard)
  │
  ▼
WorkoutPlanRepository  (Spring Data JPA)
  │
  ▼
MySQL 8 (Docker volume: mysql_data)
```

### Status Workflow

```
DRAFT ──► SUBMITTED ──► APPROVED ──► PUBLISHED
  ▲             │
  └─────────────┘  (admin reject / return for revision)
```

| Transition | Allowed Roles |
|---|---|
| DRAFT → SUBMITTED | Plan owner (TRAINER or ADMIN) |
| SUBMITTED → APPROVED | ADMIN only |
| APPROVED → PUBLISHED | ADMIN only |
| SUBMITTED → DRAFT | ADMIN only (reject/revise) |

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| ORM | Spring Data JPA / Hibernate |
| Database | MySQL 8 (prod) / H2 (tests) |
| Templating | Thymeleaf + Spring Security dialect |
| Authentication | Spring Security (form login) |
| Build | Maven 3.9 |
| Container | Docker (multi-stage) + Docker Compose |
| CI/CD | Jenkins declarative pipeline |
| Config Mgmt | Ansible 2.x (4 roles) |
| Testing | JUnit 5 · Mockito · TestNG · Selenium 4 |
| Monitoring | Spring Boot Actuator (/actuator/health) |

---

## Features

- Full CRUD — create, view, edit, and delete workout plans
- Role-based access — TRAINER and ADMIN roles with distinct permissions
- Status workflow — four-stage approval pipeline with enforced transitions
- Search and filter — full-text keyword search + status/difficulty filters
- Dashboard — live statistics: total plans, counts per status, counts per difficulty
- Validation — server-side bean validation on all inputs
- Error pages — custom 400, 404, 500 pages
- Health endpoint — /actuator/health for Docker/Jenkins health checks
- Automated tests — unit, integration, and Selenium end-to-end

---

## Local Development

### Prerequisites

- Java 21+
- Maven 3.9+
- MySQL 8 running locally (or use Docker Compose below)

### Configure the database

```sql
CREATE DATABASE workout_db;
CREATE USER 'workout_user'@'localhost' IDENTIFIED BY 'workout_pass';
GRANT ALL ON workout_db.* TO 'workout_user'@'localhost';
```

### Run the application

```bash
mvn spring-boot:run
```

The application starts on **http://localhost:8080**.

---

## Running with Docker Compose

```bash
# Build and start MySQL + application
docker-compose up -d --build

# Follow logs
docker-compose logs -f app

# Stop everything
docker-compose down
```

The `app` service waits for MySQL to pass its health check before starting.

### Environment variables (override defaults)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://db:3306/workout_db` | JDBC connection string |
| `DB_USERNAME` | `workout_user` | Database username |
| `DB_PASSWORD` | `workout_pass` | Database password |
| `DB_ROOT_PASSWORD` | `root_secret` | MySQL root password |
| `SERVER_PORT` | `8080` | Application HTTP port |

---

## Jenkins CI/CD Pipeline

The `Jenkinsfile` defines an 8-stage declarative pipeline:

| # | Stage | Description |
|---|---|---|
| 1 | Checkout | Clone source from SCM, print last 5 commits |
| 2 | Build | `mvn clean compile` |
| 3 | Unit Test | `mvn test` (Surefire, JUnit results published) |
| 4 | Package | `mvn package -DskipTests`, archives JAR |
| 5 | Docker Build | Builds and tags Docker image |
| 6 | Selenium Test | Spins up the stack, runs Failsafe/Selenium tests, tears down |
| 7 | Docker Push | Pushes versioned + latest tags to Docker Hub (main branch only) |
| 8 | Deploy | Replaces running container on target host (main branch only) |
| post | Health Check | Polls /actuator/health until UP or fails the build |

### Required Jenkins credentials

| Credential ID | Type | Purpose |
|---|---|---|
| `docker-hub-credentials` | Username + Password | Docker Hub push/pull |

### Required Jenkins plugins

See `jenkins/plugins.txt` for the full list.

---

## Ansible Provisioning

Automates full server setup from zero to running application.

```bash
# Run against localhost (demo / dev)
ansible-playbook ansible/playbook.yml -i ansible/inventory.ini

# Run against a remote server
ansible-playbook ansible/playbook.yml -i ansible/inventory.ini \
  -e "ansible_host=YOUR_SERVER_IP" --become
```

### Roles

| Role | What it installs / configures |
|---|---|
| `common` | System packages, time zone, firewall |
| `java` | OpenJDK 21 |
| `docker` | Docker CE + Docker Compose plugin |
| `app` | Copies docker-compose.yml, generates .env, starts the stack, waits for health |

The playbook is **idempotent** — running it twice produces no changes on an already-provisioned server.

---

## Testing

### Unit tests (Mockito, no database)

```bash
mvn test
```

Covers:
- `WorkoutPlanServiceTest` — CRUD, search, dashboard stats, exception paths
- `WorkoutStatusTransitionTest` — all valid and invalid status-transition cases

### Integration tests (H2 in-memory database)

```bash
mvn test -Dtest=WorkoutPlanRepositoryTest
```

Covers repository queries: findByCreatedBy, searchByKeyword, filterPlans, countByStatus, countByDifficulty.

### Selenium end-to-end tests (requires running application)

```bash
# Headless (CI)
mvn failsafe:integration-test failsafe:verify \
  -Dapp.base.url=http://localhost:8080 \
  -Dselenium.headless=true

# Headed (local inspection)
mvn failsafe:integration-test failsafe:verify \
  -Dapp.base.url=http://localhost:8080
```

Test cases:
1. Login success
2. Login failure (invalid credentials)
3. Create a workout plan
4. Search workout plans
5. Edit a workout plan
6. Full status workflow (DRAFT → SUBMITTED → APPROVED → PUBLISHED)
7. Dashboard statistics display

Screenshots of failures (and key steps) are saved to `target/selenium-screenshots/`.

---

## Rollback

To roll back to a previous Docker image version:

```bash
./scripts/rollback.sh <version>
# e.g.
./scripts/rollback.sh 1.0.5
```

The script:
1. Pulls the target versioned image (if not cached locally)
2. Stops and removes the running container
3. Starts a new container from the rollback image
4. Polls /actuator/health until UP (12 retries x 10 s)
5. Reports success or failure with container logs

---

## Default Credentials

Seeded by `DataInitializer` on first startup.

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | ADMIN |
| `trainer` | `Trainer@123` | TRAINER |

> **Change these credentials before deploying to production.**

---

## API / Endpoints

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | /login | Login page | Public |
| POST | /login | Authenticate | Public |
| GET | /logout | Logout | Authenticated |
| GET | /dashboard | Statistics dashboard | Authenticated |
| GET | /workouts | List / search / filter plans | Authenticated |
| GET | /workouts/new | Create plan form | Authenticated |
| POST | /workouts | Save new plan | Authenticated |
| GET | /workouts/{id} | Plan detail | Authenticated |
| GET | /workouts/{id}/edit | Edit plan form | Authenticated |
| POST | /workouts/{id} | Save updated plan | Authenticated |
| POST | /workouts/{id}/delete | Delete plan | Authenticated |
| POST | /workouts/{id}/status | Change plan status | Authenticated |
| GET | /actuator/health | Health check | Public |

---

## Project Structure

```
workout-plan-system/
├── .github/
│   ├── ISSUE_TEMPLATE/          # Bug report and feature request templates
│   └── workflows/
│       └── ci.yml               # GitHub Actions CI pipeline
├── ansible/
│   ├── group_vars/all.yml
│   ├── inventory.ini
│   ├── playbook.yml
│   └── roles/
│       ├── common/
│       ├── java/
│       ├── docker/
│       └── app/
├── docs/
│   ├── architecture.md
│   ├── api.md
│   └── deployment.md
├── jenkins/
│   └── plugins.txt
├── scripts/
│   ├── init.sql
│   └── rollback.sh
├── src/
│   ├── main/java/com/devops/workout/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── exception/
│   │   ├── model/
│   │   ├── repository/
│   │   ├── security/
│   │   └── service/
│   ├── main/resources/
│   │   ├── application.properties
│   │   ├── static/
│   │   └── templates/
│   └── test/
├── Dockerfile
├── Jenkinsfile
├── docker-compose.yml
└── pom.xml
```
