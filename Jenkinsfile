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
            agent {
                docker {
                    image 'maven:3.9.6-eclipse-temurin-21'
                    args '-v /root/.m2:/root/.m2'
                }
            }
            steps {
                sh 'java -version'
                sh 'mvn -version'
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Docker Clean Containers') {
            steps {
                sh 'docker compose down -v || true'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker compose build --no-cache"
            }
        }

        stage('Push Docker Image') {
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

        stage('Deploy') {
            steps {
                sh "docker compose up -d"
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
