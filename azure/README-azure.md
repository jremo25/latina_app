# Azure Deployment Guide - Inspire App

This guide covers deploying the three-tier Inspire App to Azure Kubernetes Service (AKS) using ARM templates and Azure DevOps (ADO) pipelines.

## Architecture Overview

### Shared Infrastructure Model

This deployment uses a **single shared AKS cluster** with **namespace-based environment separation**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Azure Resource Group: rg-inspire                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────┐     ┌──────────────────────────────────────┐  │
│  │   Azure Container       │     │     Azure Kubernetes Service         │  │
│  │   Registry (ACR)        │────▶│         (aks-inspire-shared)         │  │
│  │   acrinspire<suffix>     │     │                                       │  │
│  └─────────────────────────┘     │  ┌─────────────────────────────────┐│  │
│                                  │  │ inspire namespace (dev)          ││  │
│                                  │  │  ├── image-service               ││  │
│                                  │  │  ├── phrase-service              ││  │
│                                  │  │  └── frontend-service (LoadBalancer)│
│                                  │  └─────────────────────────────────┘│  │
│                                  │  ┌─────────────────────────────────┐│  │
│                                  │  │ inspire-testing namespace        ││  │
│                                  │  │  └── ...services...              ││  │
│                                  │  └─────────────────────────────────┘│  │
│                                  │  ┌─────────────────────────────────┐│  │
│                                  │  │ inspire-prod namespace           ││  │
│                                  │  │  └── ...services...              ││  │
│                                  │  └─────────────────────────────────┘│  │
│                                  └──────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Resource Names (from ARM Template)

| Resource | Name | Notes |
|----------|------|-------|
| Resource Group | `rg-inspire` | Contains all resources |
| AKS Cluster | `aks-inspire-shared` | Single cluster, multiple namespaces |
| ACR | `acrinspire<suffix>` | Shared container registry |
| VNet | `vnet-inspire` | Virtual network (10.0.0.0/16) |
| Subnet | `snet-aks` | AKS subnet (10.0.0.0/22) |
| NSG | `nsg-inspire` | Allows HTTP/HTTPS inbound |

---

## Prerequisites

- Azure subscription with Owner or Contributor access
- Azure CLI installed (`az`)
- kubectl installed
- Docker installed
- Azure DevOps project with permissions to create pipelines and service connections

---

## Step 1: Create Resource Group and Deploy Infrastructure

### 1.1 Create Resource Group

```bash
# Create the resource group (use westus3 or your preferred region)
az group create --name rg-inspire --location westus3
```

### 1.2 Deploy ARM Template

```bash
# Deploy infrastructure
az deployment group create \
  --resource-group rg-inspire \
  --template-file azure/arm/main.json \
  --parameters uniqueSuffix=inspire1
```

### 1.3 Capture Deployment Outputs

After deployment completes, note the outputs:

```bash
# View all outputs
az deployment group show \
  --resource-group rg-inspire \
  --name main \
  --query properties.outputs

# Sample output:
# {
#   "acrLoginServer": {"value": "acrinspireinspire1.azurecr.io"},
#   "acrName": {"value": "acrinspireinspire1"},
#   "aksName": {"value": "aks-inspire-shared"},
#   "postDeploySteps": {"value": "az aks get-credentials --resource-group rg-inspire --name aks-inspire-shared && kubectl apply -f k8s/namespaces.yaml"}
# }
```

### 1.4 Get AKS Credentials and Create Namespaces

```bash
# Get AKS credentials (this merges into your ~/.kube/config)
az aks get-credentials --resource-group rg-inspire --name aks-inspire-shared

# Create the inspire namespace
kubectl apply -f k8s/namespace.yaml

# For testing and production namespaces, create additional namespaces:
kubectl create namespace inspire-testing
kubectl create namespace inspire-prod
```

---

## Step 2: Configure Azure DevOps

### 2.1 Create Service Connections

Navigate to **Project Settings > Service connections** and create:

#### Kubernetes Service Connection

| Setting | Value |
|---------|-------|
| Name | `aks-service-connection` |
| Type | Kubernetes |
| Authentication | Azure Subscription |
| Subscription | Your Azure subscription |
| Cluster | `aks-inspire-shared` |
| Namespace | `default` (or leave empty for all namespaces) |

#### Docker Registry Service Connection

| Setting | Value |
|---------|-------|
| Name | `acr-service-connection` |
| Type | Docker Registry |
| Registry | Azure Container Registry |
| Subscription | Your Azure subscription |
| ACR | `acrinspireinspire1` (or your ACR name) |

### 2.2 Create Pipeline Templates Repository

Create a repository named `pipeline-templates` in your Azure DevOps project:

```bash
# From the repository root
git init pipeline-templates
cd pipeline-templates
cp -r ../azure/templates/* .
git add .
git commit -m "Add pipeline templates"
git remote add origin https://dev.azure.com/YOUR-ORG/YOUR-PROJECT/_git/pipeline-templates
git push -u origin main
```

### 2.3 Update Pipeline Variables

Edit each pipeline file (`azure/pipelines/*.yml`) and update the `uniqueSuffix` variable to match your deployment:

```yaml
variables:
  # UPDATE THIS: Match the uniqueSuffix from your ARM deployment
  uniqueSuffix: 'inspire1'

  # Service connections (adjust names if different)
  aksServiceConnection: 'aks-service-connection'
```

**Important:** If using a single ACR (shared infrastructure model), also update the ACR connection variables:

```yaml
# For shared ACR model, use same ACR for all environments:
devAcrConnection: 'acr-service-connection'
devAcrLoginServer: 'acrinspire$(uniqueSuffix).azurecr.io'
testAcrConnection: 'acr-service-connection'
testAcrLoginServer: 'acrinspire$(uniqueSuffix).azurecr.io'
prodAcrConnection: 'acr-service-connection'
prodAcrLoginServer: 'acrinspire$(uniqueSuffix).azurecr.io'
```

---

## Step 3: Create ADO Pipelines

### 3.1 Create Pipelines for Each Service

1. Navigate to **Pipelines > New pipeline**
2. Select **Azure Repos Git** (or your repository source)
3. Select your repository
4. Choose **Existing Azure Pipelines YAML file**
5. Select the pipeline file:
   - `/azure/pipelines/image-service.yml`
   - `/azure/pipelines/phrase-service.yml`
   - `/azure/pipelines/frontend-service.yml`

6. Review and **Save** (don't run yet)

### 3.2 Pipeline Execution Order

Run pipelines in this order to ensure dependencies are available:

1. **image-service** - Build and deploy image service first
2. **phrase-service** - Build and deploy phrase service second
3. **frontend-service** - Build and deploy frontend service last (depends on other services)

---

## Step 4: Deploy and Access the Application

### 4.1 Trigger Deployments

Trigger each pipeline in order:

1. Go to Pipelines, select the `image-service` pipeline, click **Run pipeline**
2. Wait for completion, then trigger `phrase-service`
3. Wait for completion, then trigger `frontend-service`

### 4.2 Access the Application

The application is exposed via a **LoadBalancer service**. After deployment:

```bash
# Get the external IP address
kubectl get svc frontend-service -n inspire

# Output:
# NAME               TYPE           CLUSTER-IP     EXTERNAL-IP     PORT(S)        AGE
# frontend-service   LoadBalancer   10.1.123.456   20.123.45.67    80:31234/TCP   5m
```

#### Access URLs

| Environment | Namespace | Access Method | URL |
|-------------|-----------|---------------|-----|
| Development | `inspire` | LoadBalancer | `http://<EXTERNAL-IP>` |
| Testing | `inspire-testing` | LoadBalancer | `http://<EXTERNAL-IP>` (get via kubectl) |
| Production | `inspire-prod` | LoadBalancer | `http://<EXTERNAL-IP>` (get via kubectl) |

#### API Endpoints

Once the application is running, access:

| Endpoint | Description |
|----------|-------------|
| `http://<EXTERNAL-IP>/` | Frontend UI |
| `http://<EXTERNAL-IP>/api/image` | Image service API |
| `http://<EXTERNAL-IP>/api/phrase` | Phrase service API |
| `http://<EXTERNAL-IP>/api/config` | Frontend config API |

### 4.3 Verify Deployment

```bash
# Check all pods are running
kubectl get pods -n inspire

# Check all services
kubectl get svc -n inspire

# Check deployments
kubectl get deployments -n inspire

# View frontend service details
kubectl describe svc frontend-service -n inspire
```

---

## Pipeline Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Pipeline Stages                                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────┐    ┌──────────┐    ┌─────────┐    ┌──────────┐         │
│  │  Build  │───▶│ DeployDev│───▶│ Approval│───▶│DeployTest│         │
│  │         │    │(inspire) │    │ Testing │    │(inspire- │         │
│  └─────────┘    └──────────┘    └─────────┘    │ testing) │         │
│                                                 └──────────┘         │
│                                                       │              │
│                                                       ▼              │
│  ┌─────────┐    ┌──────────┐                           │             │
│  │ Deploy  │◀───│ Approval │◀──────────────────────────┘             │
│  │  Prod   │    │   Prod   │                                         │
│  │(inspire-│    │          │                                         │
│  │  prod)  │    │          │                                         │
│  └─────────┘    └──────────┘                                         │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

### Stage Descriptions

| Stage | Description | Namespace |
|-------|-------------|-----------|
| Build | Maven build + tests, Docker build & push to ACR | N/A |
| DeployDev | Deploy to development environment | `inspire` |
| ApproveTesting | Manual validation gate | N/A |
| DeployTest | Deploy to testing environment | `inspire-testing` |
| ApproveProd | Manual validation gate | N/A |
| DeployProd | Deploy to production environment | `inspire-prod` |

---

## Manual Deployment (Alternative)

If you prefer manual deployment without ADO pipelines:

### Build and Push Images

```bash
# Login to ACR
ACR_NAME=$(az acr list --resource-group rg-inspire --query '[0].name' -o tsv)
az acr login --name $ACR_NAME

# Set ACR login server
ACR_LOGIN_SERVER=$(az acr show --name $ACR_NAME --query loginServer -o tsv)

# Build and push all services
# Image Service
docker build -f image-service/Dockerfile -t $ACR_LOGIN_SERVER/image-service:latest .
docker push $ACR_LOGIN_SERVER/image-service:latest

# Phrase Service
docker build -f phrase-service/Dockerfile -t $ACR_LOGIN_SERVER/phrase-service:latest .
docker push $ACR_LOGIN_SERVER/phrase-service:latest

# Frontend Service
docker build -f frontend-service/Dockerfile -t $ACR_LOGIN_SERVER/frontend-service:latest .
docker push $ACR_LOGIN_SERVER/frontend-service:latest
```

### Update Kubernetes Manifests

Edit the deployment files to use your ACR:

```bash
# Update image references in deployments
sed -i "s|<ACR_LOGIN_SERVER>|$ACR_LOGIN_SERVER|g" image-service/k8s/deployment.yaml
sed -i "s|<ACR_LOGIN_SERVER>|$ACR_LOGIN_SERVER|g" phrase-service/k8s/deployment.yaml
sed -i "s|<ACR_LOGIN_SERVER>|$ACR_LOGIN_SERVER|g" frontend-service/k8s/deployment.yaml
```

### Deploy to AKS

```bash
# Apply namespace first
kubectl apply -f k8s/namespace.yaml

# Deploy services
kubectl apply -f image-service/k8s/service.yaml
kubectl apply -f image-service/k8s/deployment.yaml

kubectl apply -f phrase-service/k8s/service.yaml
kubectl apply -f phrase-service/k8s/deployment.yaml

kubectl apply -f frontend-service/k8s/service.yaml
kubectl apply -f frontend-service/k8s/deployment.yaml
```

### Get External IP

```bash
# Watch for external IP (may take 2-3 minutes)
kubectl get svc frontend-service -n inspire --watch

# Once EXTERNAL-IP appears, access the app:
# http://<EXTERNAL-IP>
```

---

## Using Ingress (Optional)

For production environments, consider using an Ingress Controller instead of LoadBalancer services.

### Install NGINX Ingress Controller

```bash
# Add NGINX Ingress Helm repo
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

# Install NGINX Ingress Controller
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

### Apply Ingress Rules

```bash
# Apply the ingress configuration
kubectl apply -f k8s/ingress.yaml
```

### Access via Ingress

```bash
# Get ingress controller IP
kubectl get svc ingress-nginx-controller -n ingress-nginx

# Add to /etc/hosts (for local testing)
# <INGRESS-IP> inspire.local

# Access: http://inspire.local/
```

---

## Troubleshooting

### Check Pod Status

```bash
# List pods in namespace
kubectl get pods -n inspire

# Describe pod for details
kubectl describe pod <pod-name> -n inspire

# View pod logs
kubectl logs <pod-name> -n inspire

# Stream logs
kubectl logs -f <pod-name> -n inspire
```

### Image Pull Errors

```bash
# Verify ACR images exist
az acr repository list --name $ACR_NAME

# Check if AKS has pull access
az aks show --resource-group rg-inspire --name aks-inspire-shared \
  --query identityProfile.kubeletidentity.objectId -o tsv

# If needed, assign AcrPull role
AKS_IDENTITY=$(az aks show --resource-group rg-inspire --name aks-inspire-shared --query identityProfile.kubeletidentity.objectId -o tsv)
ACR_ID=$(az acr show --name $ACR_NAME --query id -o tsv)
az role assignment create --assignee $AKS_IDENTITY --role AcrPull --scope $ACR_ID
```

### Service Not Accessible

```bash
# Check service type and external IP
kubectl get svc -n inspire

# For LoadBalancer, check if Azure LB is provisioning
kubectl describe svc frontend-service -n inspire

# Test internal connectivity
kubectl run test --rm -it --image=curlimages/curl -- curl http://frontend-service.inspire.svc.cluster.local
```

### Deployment Not Progressing

```bash
# Check deployment status
kubectl rollout status deployment/image-service -n inspire
kubectl rollout status deployment/phrase-service -n inspire
kubectl rollout status deployment/frontend-service -n inspire

# Check events for issues
kubectl get events -n inspire --sort-by='.lastTimestamp'
```

---

## Clean Up Resources

```bash
# Delete all Kubernetes resources in namespace
kubectl delete all --all -n inspire

# Delete namespace
kubectl delete namespace inspire

# Delete entire Azure infrastructure
az group delete --name rg-inspire --yes --no-wait
```

---

## Environment Variables

| Variable | Service | Default | Description |
|----------|---------|---------|-------------|
| `IMAGE_SERVICE_URL` | frontend-service | `http://image-service:8081` | URL of image-service |
| `PHRASE_SERVICE_URL` | frontend-service | `http://phrase-service:8082` | URL of phrase-service |
| `SERVER_PORT` | all services | `8080` | HTTP port |

---

## Useful Commands Reference

| Task | Command |
|------|---------|
| Get AKS credentials | `az aks get-credentials -g rg-inspire -n aks-inspire-shared` |
| Login to ACR | `az acr login --name <acr-name>` |
| List ACR images | `az acr repository list --name <acr-name>` |
| View pods | `kubectl get pods -n inspire` |
| View services | `kubectl get svc -n inspire` |
| View deployments | `kubectl get deployments -n inspire` |
| Stream logs | `kubectl logs -f <pod> -n inspire` |
| Get external IP | `kubectl get svc frontend-service -n inspire` |
| Describe resource | `kubectl describe <resource> <name> -n inspire` |
| Delete all resources | `kubectl delete all --all -n inspire` |

---

## Cost Optimization

### Scale Down AKS Nodes (Non-Production)

```bash
# Scale to 1 node for dev/test
az aks scale --resource-group rg-inspire --name aks-inspire-shared --node-count 1
```

### Stop AKS Cluster

```bash
# Stop the cluster (saves compute costs)
az aks stop --resource-group rg-inspire --name aks-inspire-shared

# Start when needed
az aks start --resource-group rg-inspire --name aks-inspire-shared
```

---

## Security Recommendations

1. **Use Managed Identity** - Already configured in ARM template for AKS to ACR access
2. **Enable Azure Policy** for AKS compliance
3. **Use Azure Key Vault** for secrets management
4. **Enable Azure Monitor** for logging and monitoring
5. **Configure Network Policies** to restrict pod-to-pod communication
6. **Use Private ACR** with Private Endpoints for production
7. **Enable Azure AD integration** for RBAC on AKS