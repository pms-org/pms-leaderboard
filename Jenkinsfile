pipeline {
    agent any

    environment {
        DOCKER_IMAGE = "kanishkapriya/pms-leaderboard-backend"
        DOCKER_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'jenkins-pipeline-fix', url: 'https://github.com/pms-org/pms-leaderboard.git'
            }
        }

        stage('Maven Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker compose build --no-cache'
            }
        }

        stage('Push Docker Image') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                        usernameVariable: 'DOCKERHUB_USER',
                        passwordVariable: 'DOCKERHUB_PASS')]) {

                    sh 'echo $DOCKERHUB_PASS | docker login -u $DOCKERHUB_USER --password-stdin'

                    sh "docker tag pms-leaderboard-backend ${DOCKER_IMAGE}:${DOCKER_TAG}"
                    sh "docker push ${DOCKER_IMAGE}:${DOCKER_TAG}"
                }
            }
        }

        stage('Deploy') {
            steps {
                sh "docker compose pull backend || true"
                sh "docker compose up -d --force-recreate backend"
                sh "docker ps"
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
