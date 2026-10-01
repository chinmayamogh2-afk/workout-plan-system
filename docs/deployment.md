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

Default admin credentials: `admin` / `Admin@123`

---

## 3. Option B – Docker Compose (recommended)

This is the simplest way to run the full stack with MySQL and the application together.

### Step 1: Build and start

```bash
docker-compose up -d --build
```

### Step 2: Monitor startup

```bash
# Follow application logs
docker-compose logs -f app

# Check health status
docker-compose ps
```

The `app` container starts only after MySQL passes its health check (`mysqladmin ping`). The application health check polls `/actuator/health` every 30 seconds.

### Step 3: Access the application

- Application: http://localhost:8080
- Health: http://localhost:8080/actuator/health

### Step 4: Stop

```bash
docker-compose down

# Remove volumes (deletes database data)
docker-compose down -v
```

### Using custom credentials

Create a `.env` file in the project root:

```env
DB_USERNAME=my_user
DB_PASSWORD=my_secure_password
DB_ROOT_PASSWORD=my_root_password
```

Docker Compose automatically picks up `.env` variables.

---

## 4. Option C – Ansible Automated Provisioning

Provisions a fresh Ubuntu server end-to-end: installs Java, Docker, and runs the application.

### Step 1: Edit the inventory

```ini
# ansible/inventory.ini
[local]
localhost ansible_connection=local

[remote]
YOUR_SERVER_IP ansible_user=ubuntu ansible_ssh_private_key_file=~/.ssh/id_rsa
```

### Step 2: Review variables

```bash
cat ansible/group_vars/all.yml
```

### Step 3: Run the playbook

```bash
# Against localhost (demo)
ansible-playbook ansible/playbook.yml -i ansible/inventory.ini

# Against remote server
ansible-playbook ansible/playbook.yml -i ansible/inventory.ini \
  --limit remote --become

# Dry run (check mode)
ansible-playbook ansible/playbook.yml -i ansible/inventory.ini --check
```

### Step 4: Verify

The playbook ends with a post-task that polls `/actuator/health` and prints the JSON response. A green `ok` means the deployment succeeded.

### Idempotency

Running the playbook a second time on an already-provisioned host produces no changes (all tasks report `ok`, none report `changed`).

---

## 5. Option D – Jenkins CI/CD Pipeline

### Step 1: Install Jenkins plugins

```bash
# Using Jenkins CLI
jenkins-plugin-cli --plugin-file jenkins/plugins.txt
```

Or install manually via **Manage Jenkins → Plugins → Available**.

### Step 2: Add credentials

In **Manage Jenkins → Credentials → System → Global → Add Credentials**:

| ID | Type | Value |
|---|---|---|
| `docker-hub-credentials` | Username with password | Docker Hub login |

### Step 3: Create a Pipeline job

1. New Item → Pipeline
2. Configure SCM: Git → paste repository URL
3. Script Path: `Jenkinsfile`
4. Save and Build Now

### Step 4: Interpret the stages

| Stage | Success indicator |
|---|---|
| Build | `mvn clean compile` exits 0 |
| Unit Test | All JUnit tests pass; results published |
| Package | `target/workout-plan-system-1.0.0.jar` archived |
| Docker Build | Image tagged with version and `latest` |
| Selenium Test | All 7 Selenium tests pass; screenshots archived on failure |
| Docker Push | Image pushed to Docker Hub (main branch only) |
| Deploy | New container running on target host (main branch only) |
| Health Check | `/actuator/health` returns `{"status":"UP"}` |

### Multibranch pipeline (recommended)

For GitHub integration with automatic branch detection and PR checks, use a **Multibranch Pipeline** job pointing at the repository. The `Jenkinsfile` `when { branch 'main' }` gates ensure Docker Push and Deploy only run on the main branch.

---

## 6. Environment Variables Reference

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/workout_db?...` | Full JDBC connection string |
| `DB_USERNAME` | `workout_user` | Database user |
| `DB_PASSWORD` | `workout_pass` | Database password |
| `DB_ROOT_PASSWORD` | `root_secret` | MySQL root password (Docker only) |
| `SERVER_PORT` | `8080` | HTTP port the app listens on |
| `JAVA_OPTS` | `-Xmx512m -Xms256m` | JVM flags |
| `DOCKER_HUB_USER` | `devopsproject` | Docker Hub organisation / username |

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
docker exec -it workout-db mysql -uworkout_user -pworkout_pass workout_db -e "SHOW TABLES;"
```

---

## 8. Rollback Procedure

If a deployment introduces a regression:

```bash
# Syntax
./scripts/rollback.sh <previous-version>

# Example: roll back to version 1.0.5
./scripts/rollback.sh 1.0.5
```

The script:
1. Attempts to pull the versioned image from Docker Hub if not cached locally
2. Stops and removes the current running container
3. Starts a new container using the rollback image
4. Polls `/actuator/health` until `UP` (12 × 10 s = 2 minutes max)
5. Prints `ROLLBACK SUCCESSFUL` or exits with error and prints recent logs

### Git rollback

```bash
# Find the last known-good commit
git log --oneline

# Create a revert commit (preserves history)
git revert <bad-commit-hash>
git push origin main

# Jenkins will automatically pick up the revert and redeploy
```

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
docker exec workout-db mysqladmin ping -uroot -proot_secret
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
