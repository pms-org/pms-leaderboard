pipeline {
    agent any

    environment {
        // Docker Hub credentials configured in Jenkins (Username + PAT)
        DOCKER_HUB_CREDENTIALS = credentials('dockerhub-credentials')
        DOCKER_HUB_USERNAME    = 'gayathriemj'
        // Image name for your backend service (aligned with credential username)
        BACKEND_IMAGE = "${DOCKER_HUB_CREDENTIALS_USR}/pms-leaderboard-backend"
        // Use Jenkins build number as image version
        VERSION = "${BUILD_NUMBER}"
        IMAGE_TAG = "${VERSION}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out code from GitHub...'
                checkout scm
            }
        }

        stage('Build Backend JAR') {
            steps {
                echo 'Building Spring Boot backend with Maven...'
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo 'Building Docker image for backend...'
                sh """
                  docker build \
                    -t ${BACKEND_IMAGE}:${VERSION} \
                    -t ${BACKEND_IMAGE}:latest \
                    .
                """
            }
        }

        stage('Push Docker Image to Docker Hub') {
            steps {
                echo 'Pushing backend image to Docker Hub...'
                sh """
                  echo ${DOCKER_HUB_CREDENTIALS_PSW} | docker login -u ${DOCKER_HUB_CREDENTIALS_USR} --password-stdin
                  docker push ${BACKEND_IMAGE}:${VERSION}
                  docker push ${BACKEND_IMAGE}:latest
                """
            }
        }

        stage('Deploy Locally with Docker Compose') {
            steps {
                echo 'Deploying locally using docker-compose.yml...'
                sh """
                  # Stop any existing stack (ignore errors on first run)
                  docker compose down || true

                  # Pull the pushed backend image and recreate stack
                  docker compose pull backend
                  docker compose up -d

                  echo "Deployment complete. Running containers:"
                  docker ps
                """
            }
        }
    }

    post {
        always {
            echo 'Pipeline finished. Cleaning up Docker login (if any)...'
            sh 'docker logout || true'
        }
        success {
            echo '=========================================='
            echo ' PMS LEADERBOARD - DEPLOYMENT SUCCESSFUL '
            echo '=========================================='
        }
        failure {
            echo '======================================'
            echo ' PMS LEADERBOARD - DEPLOYMENT FAILED '
            echo ' Check Jenkins console output for logs'
            echo '======================================'
        }
    }
}
