# Inspire App — Three-Tier Spring Boot · TDD · AKS

A three-tier web application built with Spring Boot and Test-Driven Development, deployable to Azure Kubernetes Service.

```
Browser → frontend-service (LoadBalancer)
              ├── image-service  (ClusterIP)
              └── phrase-service (ClusterIP)
```

---

## Repository Structure

```
inspire-app/
├── pom.xml                          Parent Maven POM
├── image-service/                   Returns a random image URL
│   ├── Dockerfile
│   ├── k8s/
│   └── src/
├── phrase-service/                  Returns a random phrase
│   ├── Dockerfile
│   ├── k8s/
│   └── src/
├── frontend-service/                Serves the UI + /api/config
│   ├── Dockerfile
│   ├── k8s/
│   └── src/
└── k8s/
    └── namespace.yaml
```

---

## Running Tests

```bash
# Run all tests across all three modules from the repo root
mvn clean test
```

Tests must pass before any build or deployment step.

---

## Running Locally

Each service runs on a different port to avoid conflicts.

```bash
# Terminal 1 — image-service on port 8081
cd image-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"

# Terminal 2 — phrase-service on port 8082
cd phrase-service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8082"

# Terminal 3 — frontend-service on port 8080
cd frontend-service
mvn spring-boot:run -Dspring-boot.run.arguments="--image.service.url=http://localhost:8081 --phrase.service.url=http://localhost:8082"
```

Open `http://localhost:8080` in your browser.

---

## Deploying to AKS

### Prerequisites

- AKS cluster with `kubectl` connected (`az aks get-credentials --resource-group <rg> --name <cluster>`)
- Azure Container Registry (ACR) with AcrPull granted to the AKS kubelet identity
- Docker logged in to ACR (`az acr login --name <ACR_NAME>`)

### 1. Build all JARs and run tests

```bash
mvn clean package
```

### 2. Build and push Docker images

```bash
# Set your ACR login server
ACR=<your-acr-login-server>   # e.g. acrpetclinicdevoyyzir.azurecr.io

# Build context is always the repo root so Stage 1 can see the parent pom.xml
docker build -f image-service/Dockerfile    -t $ACR/image-service:latest    .
docker build -f phrase-service/Dockerfile   -t $ACR/phrase-service:latest   .
docker build -f frontend-service/Dockerfile -t $ACR/frontend-service:latest .

docker push $ACR/image-service:latest
docker push $ACR/phrase-service:latest
docker push $ACR/frontend-service:latest
```

### 3. Substitute ACR placeholder in manifests

```bash
sed -i "s|<ACR_LOGIN_SERVER>|$ACR|g" \
  image-service/k8s/deployment.yaml \
  phrase-service/k8s/deployment.yaml \
  frontend-service/k8s/deployment.yaml
```

### 4. Apply to AKS

```bash
# Namespace first
kubectl apply -f k8s/namespace.yaml

# Then services before deployments so DNS names exist when pods start
kubectl apply -f image-service/k8s/service.yaml
kubectl apply -f image-service/k8s/deployment.yaml

kubectl apply -f phrase-service/k8s/service.yaml
kubectl apply -f phrase-service/k8s/deployment.yaml

kubectl apply -f frontend-service/k8s/service.yaml
kubectl apply -f frontend-service/k8s/deployment.yaml
```

### 5. Verify

```bash
# Watch pods become ready
kubectl get pods -n inspire --watch

# Get the public IP (may take 1-2 minutes to provision)
kubectl get svc frontend-service -n inspire

# Open http://<EXTERNAL-IP> in your browser
```

---

## Adding Images

Place new `.jpg` or `.png` files in:

```
image-service/src/main/resources/images/
```

Rebuild and redeploy. No code changes required.

---

## Adding Phrases

Open `phrase-service/src/main/java/com/inspire/phrase/PhraseService.java` and add strings to the `PHRASES` list. Rebuild and redeploy.

---

## Environment Variables

| Variable | Service | Default | Description |
|---|---|---|---|
| `IMAGE_SERVICE_URL` | frontend-service | `http://localhost:8081` | URL of image-service |
| `PHRASE_SERVICE_URL` | frontend-service | `http://localhost:8082` | URL of phrase-service |
| `SERVER_PORT` | any | `8080` | Override the HTTP port |

In Kubernetes these are set in each Deployment manifest and map automatically to `application.properties` via Spring Boot relaxed binding.
