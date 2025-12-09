pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "kanishkapriya/pms-leaderboard-backend"
        DOCKER_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/pms-org/pms-leaderboard.git'
            }
        }

        stage('Maven Build') {
            steps {
                // Run Maven inside Docker to build the jar
                sh "docker run --rm -v ${WORKSPACE}:/app -w /app maven:3.9.6-eclipse-temurin-21 mvn clean package -DskipTests"
            }
        }

        stage('Stop & Clean Containers') {
            steps {
                sh 'docker compose down -v || true'
            }
        }

        stage('Build & Deploy Containers') {
            steps {
                sh 'docker compose build --no-cache'
                sh 'docker compose up -d'
                sh 'docker ps'
            }
        }

        stage('Optional: Push Backend Image') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DOCKERHUB_USER',
                        passwordVariable: 'DOCKERHUB_PASS')]) {
                    sh "echo $DOCKERHUB_PASS | docker login -u $DOCKERHUB_USER --password-stdin"
                    sh "docker tag pms-leaderboard-backend ${DOCKER_IMAGE}:${DOCKER_TAG}"
                    sh "docker push ${DOCKER_IMAGE}:${DOCKER_TAG}"
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }

        success {
            echo "Build and deployment succeeded!"
        }

        failure {
            echo "Build failed."
        }
    }
}
