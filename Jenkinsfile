// =============================================================
// Jenkinsfile – Workout Plan Management System CI/CD Pipeline
// Stages: Checkout → Build → Unit/Integration Test → Package →
//         Docker Build → Selenium (against the built image) →
//         Push (main) → Deploy (main) → Verify
//
// One image is built once (tagged with APP_VERSION) and that exact image is
// tested by Selenium, pushed and deployed. Any failing stage fails the build
// and blocks everything after it.
//
// Agent prerequisites: JDK 21, Maven 3.9+, Docker Engine with the Compose v2
// plugin (`docker compose`), curl, openssl, Chrome/Chromium (for Selenium).
//
// Jenkins credentials used (none are stored in this repository):
//   docker-hub-credentials  Username/password  – registry push
//   workout-deploy-env      Secret file        – KEY=VALUE lines: DB_PASSWORD,
//                           DB_ROOT_PASSWORD, APP_ADMIN_PASSWORD, APP_TRAINER_PASSWORD
// =============================================================

pipeline {
    agent any

    parameters {
        string(name: 'DOCKER_NAMESPACE', defaultValue: 'chinmay206',
               description: 'Docker Hub namespace (user/org) the image is published under')
        booleanParam(name: 'PUSH_IMAGE', defaultValue: true,
                     description: 'Push image to Docker Hub (requires docker-hub-credentials in Jenkins)')
        booleanParam(name: 'DEPLOY', defaultValue: false,
                     description: 'Deploy after a successful build on the main branch (requires workout-deploy-env)')
    }

    environment {
        APP_NAME     = 'workout-plan-system'
        APP_VERSION  = "1.0.${BUILD_NUMBER}"
        APP_IMAGE    = "${params.DOCKER_NAMESPACE}/workout-plan-system"
        APP_PORT     = '8080'
        CI_PROJECT   = "wps-ci-${BUILD_NUMBER}"
        MAVEN_OPTS   = '-Xmx512m'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 40, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log --oneline -5'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B -q clean compile'
            }
        }

        stage('Unit & Integration Test') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    // allowEmptyResults is deliberately false: no reports means the tests did not run
                    junit testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B -q package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build --build-arg APP_VERSION="${APP_VERSION}" -t "${APP_IMAGE}:${APP_VERSION}" .
                    docker image inspect "${APP_IMAGE}:${APP_VERSION}" --format 'Built {{.RepoTags}} {{.Id}}'
                '''
            }
        }

        stage('Selenium Test') {
            steps {
                // Throw-away stack: random credentials, random localhost port, no volumes.
                sh '''
                    set -eu
                    umask 077
                    {
                      echo "CI_DB_PASSWORD=$(openssl rand -hex 12)"
                      echo "CI_DB_ROOT_PASSWORD=$(openssl rand -hex 12)"
                      echo "CI_ADMIN_PASSWORD=Ci-$(openssl rand -hex 8)"
                      echo "CI_TRAINER_PASSWORD=Ci-$(openssl rand -hex 8)"
                    } > .ci.env

                    CI="docker compose -p ${CI_PROJECT} -f docker-compose.ci.yml --env-file .ci.env"
                    # --wait fails (non-zero) if a service never becomes healthy
                    $CI up -d --wait --wait-timeout 240

                    PORT=$($CI port app 8080 | sed 's/.*://')
                    curl -fsS "http://127.0.0.1:${PORT}/actuator/health" | grep -q '"status":"UP"'

                    . ./.ci.env
                    mvn -B test-compile failsafe:integration-test failsafe:verify \
                        -Dapp.base.url="http://127.0.0.1:${PORT}" \
                        -Dselenium.headless=true \
                        -Dselenium.admin.password="${CI_ADMIN_PASSWORD}" \
                        -Dselenium.trainer.password="${CI_TRAINER_PASSWORD}"
                '''
            }
            post {
                always {
                    junit testResults: 'target/failsafe-reports/*.xml', allowEmptyResults: true
                    archiveArtifacts artifacts: 'target/selenium-screenshots/**/*.png', allowEmptyArchive: true
                    // Keep logs for diagnosis, then always tear the stack down (even on failure)
                    sh '''
                        CI="docker compose -p ${CI_PROJECT} -f docker-compose.ci.yml --env-file .ci.env"
                        [ -f .ci.env ] && $CI logs --no-color > ci-stack.log 2>&1 || true
                        [ -f .ci.env ] && $CI down -v --remove-orphans || true
                        rm -f .ci.env
                    '''
                    archiveArtifacts artifacts: 'ci-stack.log', allowEmptyArchive: true
                }
            }
        }

        stage('Docker Push') {
            when {
                allOf {
                    expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH in ['origin/main', 'main'] }
                    expression { params.PUSH_IMAGE }
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials',
                                                  usernameVariable: 'DOCKER_USER',
                                                  passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin
                        docker push "${APP_IMAGE}:${APP_VERSION}"
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            when {
                allOf {
                    expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH in ['origin/main', 'main'] }
                    expression { params.DEPLOY }
                }
            }
            steps {
                // deploy.sh: validate → start DB → replace app → verify health → auto-restore on failure
                withCredentials([file(credentialsId: 'workout-deploy-env', variable: 'ENV_FILE')]) {
                    sh './scripts/deploy.sh'
                }
            }
        }

        stage('Verify Deployment') {
            when {
                allOf {
                    expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH in ['origin/main', 'main'] }
                    expression { params.DEPLOY }
                }
            }
            steps {
                sh '''
                    curl -fsS "http://localhost:${APP_PORT}/actuator/health" | grep -q '"status":"UP"'
                    test "$(docker inspect --format '{{.Config.Image}}' workout-app)" = "${APP_IMAGE}:${APP_VERSION}"
                    echo "Deployed ${APP_IMAGE}:${APP_VERSION} and healthy"
                '''
            }
        }

        stage('Promote latest') {
            // `latest` only moves after the new version was deployed AND verified healthy
            when {
                allOf {
                    expression { env.BRANCH_NAME == 'main' || env.GIT_BRANCH in ['origin/main', 'main'] }
                    expression { params.PUSH_IMAGE }
                    expression { params.DEPLOY }
                }
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials',
                                                  usernameVariable: 'DOCKER_USER',
                                                  passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin
                        docker tag "${APP_IMAGE}:${APP_VERSION}" "${APP_IMAGE}:latest"
                        docker push "${APP_IMAGE}:latest"
                        docker logout
                    '''
                }
            }
        }
    }

    post {
        success { echo "Pipeline succeeded. Version: ${APP_VERSION}" }
        failure { echo "Pipeline FAILED. See the failing stage's log and archived artifacts." }
        always  { sh 'docker image prune -f --filter "until=168h" || true' }
    }
}
