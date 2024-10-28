pipeline {
    agent any

    tools {
        jdk 'JDK 17'
        maven 'Maven 3'
    }

    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials-id')
        DOCKER_IMAGE = 'lookabj/myapp:latest'
        SONAR_PROJECT_KEY = 'devops_project'
        NEXUS_VERSION = "nexus3"
        NEXUS_PROTOCOL = "http"
        NEXUS_URL = "10.0.2.15:8081"
        NEXUS_REPOSITORY = "maven-releases-rep"
        NEXUS_CREDENTIAL_ID = "nexus-credentials"
        KUBECONFIG = credentials('kubeconfig-credentials-id')
        APP_NAMESPACE = "myapp"
        GRAFANA_URL = 'http://localhost:3000'
        PROMETHEUS_URL = 'http://localhost:9090'
        GRAFANA_CREDS = credentials('grafana-admin-credentials')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Maven Clean') {
            steps {
                echo 'Running mvn clean...'
                sh 'mvn clean'
            }
        }

        stage('Maven Compile') {
            steps {
                script {
                    env.JAVA_HOME = tool name: 'JDK 17', type: 'jdk'
                    env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
                    sh 'java -version'
                    sh 'mvn compile'
                }
            }
        }

        stage('Run Tests with JaCoCo') {
            steps {
                sh 'mvn test org.jacoco:jacoco-maven-plugin:report'
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    script {
                        env.JAVA_HOME = tool name: 'JDK 17', type: 'jdk'
                        env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"
                        sh 'java -version'
                        sh """
                        mvn sonar:sonar \
                          -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                          -Dsonar.java.coveragePlugin=jacoco \
                          -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        """
                    }
                }
            }
        }

        stage('Build Package') {
            steps {
                sh 'mvn package'
            }
        }

        stage('Push to Nexus Repository') {
            steps {
                script {
                    pom = readMavenPom file: "pom.xml"
                    filesByGlob = findFiles(glob: "target/*.${pom.packaging}")
                    echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"
                    artifactPath = filesByGlob[0].path
                    artifactExists = fileExists artifactPath

                    if(artifactExists) {
                        echo "*** File: ${artifactPath}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}"
                        nexusArtifactUploader(
                            nexusVersion: NEXUS_VERSION,
                            protocol: NEXUS_PROTOCOL,
                            nexusUrl: NEXUS_URL,
                            groupId: pom.groupId,
                            version: pom.version,
                            repository: NEXUS_REPOSITORY,
                            credentialsId: NEXUS_CREDENTIAL_ID,
                            artifacts: [
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: artifactPath,
                                type: pom.packaging],
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: "pom.xml",
                                type: "pom"]
                            ]
                        )
                    } else {
                        error "*** File: ${artifactPath}, could not be found"
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    echo 'Building Docker image...'
                    sh 'docker build -t $DOCKER_IMAGE .'
                }
            }
        }

        stage('Push Docker Image to Docker Hub') {
            steps {
                script {
                    echo 'Pushing Docker image to Docker Hub...'
                    sh 'echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin'
                    sh 'docker push $DOCKER_IMAGE'
                }
            }
        }

        stage('Test Kubernetes Access') {
            steps {
                script {
                    sh '''
                        kubectl --kubeconfig=${KUBECONFIG} get nodes
                        kubectl --kubeconfig=${KUBECONFIG} cluster-info
                    '''
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                script {
                    sh '''
                        # Apply the deployment and service
                        kubectl --kubeconfig=$KUBECONFIG apply -f k8s-deployment.yaml
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                script {
                    sh '''
                        kubectl --kubeconfig=$KUBECONFIG get deployments -n $APP_NAMESPACE
                        kubectl --kubeconfig=$KUBECONFIG get pods -n $APP_NAMESPACE
                        kubectl --kubeconfig=$KUBECONFIG get services -n $APP_NAMESPACE
                    '''
                }
            }
        }

        stage('Configure Prometheus Scraping') {
            steps {
                script {
                    // Create Prometheus RBAC configuration
                    writeFile file: 'prometheus-rbac.yaml', text: """
                    apiVersion: rbac.authorization.k8s.io/v1
                    kind: ClusterRole
                    metadata:
                    name: prometheus-server
                    rules:
                    - apiGroups: [""]
                        resources:
                        - nodes
                        - nodes/proxy
                        - nodes/metrics
                        - services
                        - endpoints
                        - pods
                        verbs: ["get", "list", "watch"]
                    - apiGroups: ["extensions"]
                        resources:
                        - ingresses
                        verbs: ["get", "list", "watch"]
                    - nonResourceURLs: ["/metrics"]
                        verbs: ["get"]
                    ---
                    apiVersion: rbac.authorization.k8s.io/v1
                    kind: ClusterRoleBinding
                    metadata:
                    name: prometheus-server
                    roleRef:
                    apiGroup: rbac.authorization.k8s.io
                    kind: ClusterRole
                    name: prometheus-server
                    subjects:
                    - kind: ServiceAccount
                        name: prometheus
                        namespace: ${APP_NAMESPACE}
                    """

                    // Create Prometheus config
                    writeFile file: 'prometheus-additional.yml', text: """
                    global:
                    scrape_interval: 15s
                    scrape_timeout: 10s
                    evaluation_interval: 15s

                    scrape_configs:
                    - job_name: 'spring-boot'
                        kubernetes_sd_configs:
                        - role: endpoints
                            api_server: 'https://kubernetes.default.svc'
                            tls_config:
                            insecure_skip_verify: true
                            namespaces:
                            names:
                                - ${APP_NAMESPACE}
                        relabel_configs:
                        - source_labels: [__meta_kubernetes_service_label_app]
                            regex: myapp
                            action: keep
                        - source_labels: [__meta_kubernetes_endpoint_port_name]
                            regex: metrics
                            action: keep
                        - source_labels: [__meta_kubernetes_namespace]
                            target_label: kubernetes_namespace
                        - source_labels: [__meta_kubernetes_pod_name]
                            target_label: kubernetes_pod_name
                    """
                                
                        // Apply RBAC and create ServiceAccount
                        sh """
                            # Create namespace if it doesn't exist
                            kubectl --kubeconfig=\$KUBECONFIG create namespace ${APP_NAMESPACE} --dry-run=client -o yaml | kubectl --kubeconfig=\$KUBECONFIG apply -f -

                            # Apply RBAC configuration
                            kubectl --kubeconfig=\$KUBECONFIG apply -f prometheus-rbac.yaml
                            
                            # Create service account and token for Prometheus
                            cat <<EOF | kubectl --kubeconfig=\$KUBECONFIG apply -f -
                            apiVersion: v1
                            kind: ServiceAccount
                            metadata:
                            name: prometheus
                            namespace: ${APP_NAMESPACE}
                            ---
                            apiVersion: v1
                            kind: Secret
                            metadata:
                            name: prometheus-token
                            namespace: ${APP_NAMESPACE}
                            annotations:
                                kubernetes.io/service-account.name: prometheus
                            type: kubernetes.io/service-account-token
                            EOF
                            
                            # Wait for token to be created
                            sleep 5
                            
                            # Get the token
                            PROMETHEUS_TOKEN=\$(kubectl --kubeconfig=\$KUBECONFIG get secret prometheus-token -n ${APP_NAMESPACE} -o jsonpath='{.data.token}' | base64 --decode)
                            
                            # Update Prometheus configuration
                            # Ensure prometheus container exists before copying
                            if docker ps | grep -q prometheus; then
                                docker cp prometheus-additional.yml prometheus:/etc/prometheus/prometheus.yml
                                docker exec prometheus kill -HUP 1
                            else
                                echo "Warning: Prometheus container not found"
                            fi
                        """
                    }
                }
            }
        
        stage('Configure Grafana Dashboard') {
            steps {
                script {
                    // Configure Prometheus as a data source in Grafana
                    sh """
                        # Add Prometheus data source
                        curl -X POST \
                            -H 'Content-Type: application/json' \
                            -u '${GRAFANA_CREDS_USR}:${GRAFANA_CREDS_PSW}' \
                            ${GRAFANA_URL}/api/datasources \
                            -d '{
                                "name": "Prometheus",
                                "type": "prometheus",
                                "url": "http://prometheus:9090",
                                "access": "proxy",
                                "basicAuth": false
                            }'
                            
                        # Create dashboard
                        curl -X POST \
                            -H 'Content-Type: application/json' \
                            -u '${GRAFANA_CREDS_USR}:${GRAFANA_CREDS_PSW}' \
                            ${GRAFANA_URL}/api/dashboards/db \
                            -d @grafana-dashboard.json
                    """
                }
            }
        }
        
        stage('Verify Monitoring Setup') {
            steps {
                script {
                    sh """
                        # Check if Prometheus can scrape the targets
                        curl -s ${PROMETHEUS_URL}/api/v1/targets | grep myapp
                        
                        # Verify Grafana datasource
                        curl -s -u '${GRAFANA_CREDS_USR}:${GRAFANA_CREDS_PSW}' \
                            ${GRAFANA_URL}/api/datasources/name/Prometheus
                    """
                }
            }
        }
    }
    

    post {
        always {
            // Clean up temporary files
            sh 'rm -f prometheus-additional.yml'
        }
        failure {
            echo 'Monitoring setup failed! Check the logs for details.'
        }
        success {
            echo 'Monitoring setup completed successfully!'
        }
    }
}