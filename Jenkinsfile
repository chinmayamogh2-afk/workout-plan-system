// =============================================================
// Jenkinsfile – Workout Plan Management System CI/CD Pipeline
// Stages: Checkout → Build → Test → Package → Docker → Deploy
// =============================================================

pipeline {
    agent any

    environment {
        APP_NAME         = 'workout-plan-system'
        APP_VERSION      = "1.0.${BUILD_NUMBER}"
        DOCKER_IMAGE     = "${DOCKER_HUB_USER ?: 'devopsproject'}/${APP_NAME}"
        DOCKER_TAG       = "${APP_VERSION}"
        DOCKER_LATEST    = "${DOCKER_IMAGE}:latest"
        CONTAINER_NAME   = 'workout-app'
        DB_CONTAINER     = 'workout-db'
        APP_PORT         = '8080'
        HEALTH_URL       = "http://localhost:${APP_PORT}/actuator/health"
        MAVEN_OPTS       = '-Xmx512m'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }

    stages {

        // ----------------------------------------------------------
        stage('Checkout') {
        // ----------------------------------------------------------
            steps {
                echo "📥 Checking out source code from branch: ${GIT_BRANCH}"
                checkout scm
                sh 'git log --oneline -5'
            }
        }

        // ----------------------------------------------------------
        stage('Build') {
        // ----------------------------------------------------------
            steps {
                echo "🔨 Compiling the application..."
                sh 'mvn clean compile -q'
            }
            post {
                failure { echo "❌ Build FAILED – fix compile errors before proceeding." }
            }
        }

        // ----------------------------------------------------------
        stage('Unit Test') {
        // ----------------------------------------------------------
            steps {
                echo "🧪 Running unit and integration tests..."
                sh 'mvn test -q'
            }
            post {
                always {
                    // Publish JUnit test results to Jenkins
                    junit allowEmptyResults: true,
                          testResults: '**/target/surefire-reports/*.xml'
                }
                failure { echo "❌ Unit Tests FAILED – deployment blocked." }
            }
        }

        // ----------------------------------------------------------
        stage('Package') {
        // ----------------------------------------------------------
            steps {
                echo "📦 Packaging the application JAR..."
                sh 'mvn package -DskipTests -q'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                echo "✅ JAR archived: target/${APP_NAME}-1.0.0.jar"
            }
        }

        // ----------------------------------------------------------
        stage('Docker Build') {
        // ----------------------------------------------------------
            steps {
                echo "🐳 Building Docker image: ${DOCKER_IMAGE}:${DOCKER_TAG}"
                sh "docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} -t ${DOCKER_LATEST} ."
                sh "docker images ${DOCKER_IMAGE}"
            }
        }

        // ----------------------------------------------------------
        stage('Selenium Test') {
        // ----------------------------------------------------------
            steps {
                echo "🌐 Starting application stack for Selenium tests..."
                sh """
                    # Start MySQL and app for integration testing
                    docker-compose up -d --wait || true

                    # Wait for app to be healthy (up to 120 seconds)
                    timeout=120
                    elapsed=0
                    until curl -sf ${HEALTH_URL} > /dev/null 2>&1 || [ \$elapsed -ge \$timeout ]; do
                        sleep 5
                        elapsed=\$((elapsed + 5))
                        echo "Waiting for app... (\${elapsed}s)"
                    done

                    if ! curl -sf ${HEALTH_URL}; then
                        echo "Application health check failed!"
                        exit 1
                    fi
                """
                sh "mvn failsafe:integration-test failsafe:verify -Dapp.base.url=http://localhost:${APP_PORT} -Dselenium.headless=true"
            }
            post {
                always {
                    junit allowEmptyResults: true,
                          testResults: '**/target/failsafe-reports/*.xml'
                    archiveArtifacts artifacts: 'target/selenium-screenshots/**/*.png',
                                      allowEmptyArchive: true
                    sh 'docker-compose down --remove-orphans || true'
                }
                failure { echo "❌ Selenium Tests FAILED – deployment blocked." }
            }
        }

        // ----------------------------------------------------------
        stage('Docker Tag & Push') {
        // ----------------------------------------------------------
            when {
                branch 'main'
            }
            steps {
                echo "🏷️  Tagging and pushing Docker image to registry..."
                withCredentials([usernamePassword(
                        credentialsId: 'docker-hub-credentials',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS')]) {
                    sh """
                        echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin
                        docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                        docker push ${DOCKER_LATEST}
                        docker logout
                    """
                }
            }
        }

        // ----------------------------------------------------------
        stage('Deploy') {
        // ----------------------------------------------------------
            when {
                branch 'main'
            }
            steps {
                echo "🚀 Deploying new container version ${DOCKER_TAG}..."
                sh """
                    # Stop and remove old container if exists
                    docker stop ${CONTAINER_NAME} 2>/dev/null || true
                    docker rm   ${CONTAINER_NAME} 2>/dev/null || true

                    # Start MySQL if not already running
                    docker start ${DB_CONTAINER} 2>/dev/null || \
                        docker run -d --name ${DB_CONTAINER} \
                            --network workout-network \
                            -e MYSQL_DATABASE=workout_db \
                            -e MYSQL_USER=workout_user \
                            -e MYSQL_PASSWORD=workout_pass \
                            -e MYSQL_ROOT_PASSWORD=root_secret \
                            -v mysql_data:/var/lib/mysql \
                            mysql:8.0

                    # Create network if not exists
                    docker network create workout-network 2>/dev/null || true

                    # Start new application container
                    docker run -d \
                        --name ${CONTAINER_NAME} \
                        --network workout-network \
                        -p ${APP_PORT}:8080 \
                        -e DB_URL="jdbc:mysql://${DB_CONTAINER}:3306/workout_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
                        -e DB_USERNAME=workout_user \
                        -e DB_PASSWORD=workout_pass \
                        --restart unless-stopped \
                        ${DOCKER_IMAGE}:${DOCKER_TAG}
                """
            }
        }

        // ----------------------------------------------------------
        stage('Health Check') {
        // ----------------------------------------------------------
            when {
                branch 'main'
            }
            steps {
                echo "🏥 Verifying deployment health..."
                sh """
                    sleep 30
                    MAX_RETRIES=10
                    COUNT=0
                    until curl -sf ${HEALTH_URL} | grep -q '"status":"UP"'; do
                        COUNT=\$((COUNT + 1))
                        if [ \$COUNT -ge \$MAX_RETRIES ]; then
                            echo "❌ Health check failed after \${MAX_RETRIES} retries!"
                            docker logs ${CONTAINER_NAME} --tail 50
                            exit 1
                        fi
                        echo "Retry \${COUNT}/\${MAX_RETRIES}..."
                        sleep 15
                    done
                    echo "✅ Application is healthy!"
                    curl -s ${HEALTH_URL}
                """
            }
        }

    } // end stages

    post {
        success {
            echo "🎉 Pipeline completed successfully! Version: ${APP_VERSION}"
        }
        failure {
            echo "💥 Pipeline FAILED. Check stage logs above."
        }
        always {
            echo "Pipeline finished. Build: #${BUILD_NUMBER}"
            // Clean up dangling Docker images
            sh 'docker image prune -f || true'
        }
    }
}
