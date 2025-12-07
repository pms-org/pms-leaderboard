pipeline {
    agent any

    environment {
        IMAGE_NAME = "kanishka/leaderboard-backend"
        IMAGE_TAG = "latest"
    }

    stages {
        stage('Checkout') {
            steps { git branch: 'main', url: 'https://github.com/your/repo.git' }
        }

        stage('Build') {
            steps { sh 'mvn clean package -DskipTests' }
        }

        stage('Docker Build') {
            steps { sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ." }
        }

        stage('Docker Push') {
            steps {
                withCredentials([string(credentialsId: 'dockerhub-token', variable: 'TOKEN')]) {
                    sh "echo $TOKEN | docker login -u kanishka --password-stdin"
                }
                sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
            }
        }

        stage('Deploy') {
            steps {
                sshagent(['deployment-server']) {
                    sh '''
                      ssh ubuntu@server "docker pull ${IMAGE_NAME}:${IMAGE_TAG}"
                      ssh ubuntu@server "docker compose -f /deploy/docker-compose.yml up -d backend"
                    '''
                }
            }
        }
    }
}
