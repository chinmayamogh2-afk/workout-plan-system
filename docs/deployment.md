# Deployment Guide – Workout Plan Management System

## Contents

1. [Prerequisites](#1-prerequisites)
2. [Option A – Local Development](#2-option-a--local-development)
3. [Option B – Docker Compose (recommended)](#3-option-b--docker-compose-recommended)
4. [Option C – Ansible Automated Provisioning](#4-option-c--ansible-automated-provisioning)
5. [Option D – Jenkins CI/CD Pipeline](#5-option-d--jenkins-cicd-pipeline)
6. [Environment Variables Reference](#6-environment-variables-reference)
7. [Post-Deployment Verification](#7-post-deployment-verification)
8. [Rollback Procedure](#8-rollback-procedure)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Prerequisites

| Tool | Minimum Version | Purpose |
|---|---|---|
| Java (JDK) | 21 | Local development |
| Maven | 3.9 | Build tool |
| Docker | 24.0 | Container runtime |
| Docker Compose | v2 (plugin) | Multi-container orchestration |
| Ansible | 2.14 | Automated server provisioning |
| Jenkins | 2.440 | CI/CD pipeline execution |
| Git | 2.x | Source control |
| MySQL | 8.0 | Production database |
| Chrome / Chromium | Latest | Selenium E2E tests |

---

## 2. Option A – Local Development

### Step 1: Clone the repository

```bash
git clone <your-repo-url>
cd workout-plan-system
```

### Step 2: Set up MySQL

```sql
CREATE DATABASE workout_db;
CREATE USER 'workout_user'@'localhost' IDENTIFIED BY 'workout_pass';
GRANT ALL PRIVILEGES ON workout_db.* TO 'workout_user'@'localhost';
FLUSH PRIVILEGES;
```

### Step 3: Run the application

```bash
mvn spring-boot:run
```

Visit: http://localhost:8080

Default (development-only) admin credentials: `admin` / `Admin@123`; override with `APP_ADMIN_PASSWORD`

---

## 3. Option B – Docker Compose (recommended)

```bash
cp .env.example .env     # set DB_PASSWORD, DB_ROOT_PASSWORD, APP_ADMIN_PASSWORD, APP_TRAINER_PASSWORD
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build --wait   # build from source
docker compose ps                                                                       # both services "healthy"
curl -fsS http://localhost:8080/actuator/health                                         # {"status":"UP"}
docker compose logs -f app
docker compose down            # keep data;  docker compose down -v  deletes the database volume
```

Missing secrets abort `docker compose up` with an explanatory error. MySQL is not published to the host.
To run an already-built image instead of building: `APP_IMAGE=<namespace>/workout-plan-system APP_VERSION=<tag> docker compose up -d --wait`.

---

## 4. Option C – Ansible Automated Provisioning

See *Ansible Provisioning* in the README for commands. Summary of behaviour:

1. `preflight` aborts before any change if secrets or image coordinates are missing.
2. `common`/`java`/`docker` install prerequisites with native modules (idempotent; Docker install is skipped when present).
3. `app` writes `.env` (mode 0600), installs `docker-compose.yml` and `scripts/init.sql`, starts the DB, deploys the image, checks `/actuator/health` for `UP`.
4. If health verification fails, the previously running version is restored and the play fails with an actionable message.

`ansible-playbook playbook.yml --syntax-check` validates syntax; a second run on an unchanged host reports `changed=0`.

---

## 5. Option D – Jenkins CI/CD Pipeline

See *Jenkins CI/CD Pipeline* in the README for stages, credentials (`docker-hub-credentials`, `workout-deploy-env`) and agent prerequisites.
Deployment is performed by `scripts/deploy.sh`: validate → start DB → deploy → verify health → restore previous version on failure.

---

## 6. Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | local MySQL | Full JDBC connection string |
| `DB_USERNAME` | `workout_user` | Database user |
| `DB_PASSWORD` | required in Docker | Database password |
| `DB_ROOT_PASSWORD` | required in Docker | MySQL root password |
| `APP_ADMIN_PASSWORD` / `APP_TRAINER_PASSWORD` | required in Docker | Seeded user passwords (dev defaults only outside Docker) |
| `APP_IMAGE` / `APP_VERSION` | `devopsproject/workout-plan-system` / `latest` | Image to run |
| `APP_PORT` / `SERVER_PORT` | `8080` | Host port / container port |
| `THYMELEAF_CACHE` | `true` | Template caching |

---

## 7. Post-Deployment Verification

### Health check

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Expected response:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

### Login verification

1. Navigate to http://localhost:8080
2. Log in with `admin` / `Admin@123`
3. Verify the dashboard loads with statistics

### Database connectivity

```bash
docker exec -it workout-db sh -c 'MYSQL_PWD=$MYSQL_PASSWORD mysql -u$MYSQL_USER workout_db -e "SHOW TABLES;"'
```

---

## 8. Rollback Procedure

* **Automatic:** `scripts/deploy.sh` (used by Jenkins) and the Ansible `app` role restore the previous version if the new one is unhealthy.
* **Manual (script):** `ENV_FILE=/path/to/secrets.env ./scripts/rollback.sh [version]` (defaults to `.deploy/previous_version`).
* **Manual (Ansible):** `ansible-playbook rollback.yml -e rollback_version=1.0.41 -e @secrets.yml`.

Only the application image is rolled back; database changes are not reverted. The switch recreates the container, so there is a brief outage.

---

## 9. Troubleshooting

### Application fails to start – database connection refused

**Symptom:** `Communications link failure` in logs

**Cause:** MySQL is not yet ready

**Fix:** When using Docker Compose, the `depends_on: db: condition: service_healthy` ensures the app waits. For bare-metal, wait until `mysqladmin ping` succeeds before starting the app.

---

### Health check endpoint returns 503

**Symptom:** `{"status":"OUT_OF_SERVICE"}`

**Cause:** Database is unreachable

**Fix:**
```bash
# Check MySQL container
docker logs workout-db --tail 20
docker exec workout-db sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqladmin ping -uroot'
```

---

### Port 8080 already in use

```bash
# Find the process occupying 8080
lsof -i :8080
# Kill it
kill -9 <PID>
```

---

### Selenium tests fail with "Chrome not found"

**Fix:** Install Google Chrome or Chromium:

```bash
# Ubuntu / Debian
sudo apt-get install -y google-chrome-stable

# macOS
brew install --cask google-chrome
```

WebDriverManager (`io.github.bonigarcia`) automatically downloads the matching ChromeDriver.

---

### Maven build fails with "Compiler version" error

**Fix:** Ensure `JAVA_HOME` points to JDK 21:

```bash
java -version      # must show 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
```

---

### Docker image build fails – "cannot find JAR"

**Cause:** The first Maven stage in the Dockerfile may have failed silently.

**Fix:**
```bash
docker build --no-cache -t workout-plan-system:debug .
```

This forces Maven to re-download dependencies and shows full build output.
