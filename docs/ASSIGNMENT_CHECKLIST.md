# Assignment Checklist – what the repository provides vs. what you must evidence yourself

Nothing below was fabricated: items marked **MANUAL** need your GitHub/Jenkins/Docker Hub/server accounts.

| Area | In the repository | You must do / capture (MANUAL) |
|------|-------------------|--------------------------------|
| Problem definition, Agile plan, MVP | `README.md` (overview, features, workflow), `docs/architecture.md`, issue templates in `.github/ISSUE_TEMPLATE` | Create the product backlog / sprint board (GitHub Projects/Jira) and screenshot it; write user stories |
| Architecture, CRUD, search | Spring Boot MVC app, `docs/architecture.md`, `docs/api.md` | – |
| Git/GitHub collaboration, branching | `.github/workflows/ci.yml`, branch triggers (`main`, `develop`) | Create feature branches, open **pull requests**, and produce/resolve a **merge conflict** with teammates; screenshot PRs and history |
| Jenkins CI, pipeline as code | `Jenkinsfile`, `jenkins/plugins.txt` | Install Jenkins + plugins, add credentials (`docker-hub-credentials`, `workout-deploy-env`), create a Multibranch Pipeline, run it; screenshot the **stage view**, test report and console log |
| Deployment target | `docker-compose.yml`, `scripts/deploy.sh` (Docker/Compose target) | Run on your server/VM; if the assignment demands Tomcat/Nginx specifically, confirm with your instructor that the Docker target is acceptable |
| Selenium / continuous testing | `selenium/WorkoutPlanSeleniumTest.java`, Jenkins "Selenium Test" stage, screenshots archived to `target/selenium-screenshots` | Run it with Chrome available; screenshot the Jenkins test report |
| Docker image + lifecycle | `Dockerfile`, `.dockerignore`, `docker-compose*.yml` | Run `docker build`, `docker run`, `stop`, `rm`; screenshot `docker ps` and image list |
| Docker Hub publication | Push stage in Jenkins / GitHub Actions | Create the Docker Hub repo/token, set `DOCKER_NAMESPACE`, run the pipeline; screenshot the repo with its tags |
| Jenkins-driven Docker deployment | `Deploy` + `Verify Deployment` stages, `scripts/deploy.sh` | Run on `main`; screenshot the successful deploy and `curl /actuator/health` |
| Ansible configuration management | `ansible/` (roles, `playbook.yml`, `rollback.yml`) | Run `ansible-playbook playbook.yml -e @secrets.yml` against a real host **twice** and save both PLAY RECAPs (second must show `changed=0`) |
| Health checks and rollback | Image `HEALTHCHECK`, `deploy.sh` auto-restore, `rollback.sh`, `rollback.yml` | Deploy a good version, then deliberately deploy a broken tag and capture the automatic restore log; run `rollback.sh` and capture it |

Pre-submission commands to run on your own machine (they need Maven Central / Docker):

```bash
mvn test                                   # expect > 0 tests per class; open target/surefire-reports
docker build -t devopsproject/workout-plan-system:local .
docker compose -f docker-compose.yml -f docker-compose.build.yml --env-file .env up -d --wait
ansible-playbook ansible/playbook.yml --syntax-check
```
