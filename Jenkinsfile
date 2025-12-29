pipeline {
    agent any
    tools {
        jdk 'java-21'
        maven 'maven-3.9.12'
    }
    environment {
        DOCKER_HUB_CREDENTIALS = credentials('leaderboard-dockerhub-creds')
        BACKEND_IMAGE = "${DOCKER_HUB_CREDENTIALS_USR}/pms-leaderboard-backend"
        VERSION = "${BUILD_NUMBER}"
        IMAGE_TAG = "${VERSION}"
        EC2_IP="3.149.5.40"
        EC2_HOST="ubuntu@${EC2_IP}"
        SERVER_URL="http://${EC2_IP}:8000"
    }

    stages {

        stage('Clean Workspace') {
            steps {
                echo 'Cleaning Jenkins workspace...'
                cleanWs()
            }
        }

        stage('Git Checkout') {
            steps {
                echo 'Checking out code from GitHub...'
                git branch: 'main', 
                    url: 'https://github.com/pms-org/pms-leaderboard.git'
            }
        }

        stage('Maven Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }


        stage('Build Docker Image') {
            steps {
                echo 'Building Docker image for backend...'
                sh """
                  docker build --no-cache \
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

        // stage('Deploy with Docker Compose') {
        //     steps {
        //         echo 'Deploying locally using docker-compose.yml...'
        //         sh """
        //           docker compose down || true
        //           docker compose pull backend
        //           docker compose up -d

        //           echo "Deployment complete. Running containers:"
        //           docker ps
        //         """
        //     }
        // }


        stage('Deploy to EC2') {
            steps {
                sshagent(['leaderboard-ssh-key']) {
                    withCredentials([file(credentialsId: 'leaderboard-env', variable: 'ENV_FILE')]) {

                        // Copy compose file
                        sh '''
                        scp -o StrictHostKeyChecking=no \
                            docker-compose.yml \
                            $EC2_HOST:/home/ubuntu/docker-compose.yml
                        '''

                        // Copy .env inside EC2 from Jenkins secret file
                        sh '''
                        scp -o StrictHostKeyChecking=no "$ENV_FILE" "$EC2_HOST:/home/ubuntu/.env"
                        '''

                        // Deploy containers
                        sh """
                        ssh -o StrictHostKeyChecking=no $EC2_HOST "
                            docker compose down || true
                            docker compose pull backend
                            docker compose up -d

                            echo "Deployment complete. Running containers:"
                            docker ps
                        "
                        """
                    }
                }
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
