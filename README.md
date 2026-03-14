# Azure Deployment Guide — Latina App

This guide covers deploying the **Latina App** to Azure Kubernetes Service (AKS) using the ARM template and Azure DevOps (ADO) pipelines.

**Repositories:**
- App source: https://github.com/jremo25/latina_app
- Pipeline templates: https://github.com/jremo25/latina_app_template

---

## Architecture Overview

A single shared AKS cluster with namespace-based environment separation (`dev`, `test`, `prod`). All three services are routed through a single public IP via an NGINX ingress:

```
Internet
    │
    ▼
AKS Ingress (nginx)
    ├── /api/image  →  image-service:80
    ├── /api/phrase →  phrase-service:80
    └── /           →  frontend-service:80
```

### Resources deployed by the ARM template

| Resource | Name |
|----------|------|
| Resource Group | `rg-latina` |
| AKS Cluster | `aks-latina-shared` |
| Container Registry | `acrlatina<uniqueSuffix>` |
| Virtual Network | `vnet-latina` (10.0.0.0/16) |
| Subnet | `snet-aks` (10.0.0.0/22) |
| NSG | `nsg-latina` (allows HTTP/HTTPS inbound) |

---

## Prerequisites

- Azure subscription with Owner or Contributor access
- [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli) installed and logged in (`az login`)
- [kubectl](https://kubernetes.io/docs/tasks/tools/) installed
- Azure DevOps organisation with permissions to create pipelines and service connections

---

## Step 1 — Deploy Azure Infrastructure

### 1.1 Dowload the main.json

```bash
curl -fsSL https://raw.githubusercontent.com/denisdbell/latina_app/refs/heads/main/azure/arm/main.json -o main.json

```

### 1.2 Create a resource group

```bash
az group create --name rg-latina --location westus3
```

### 1.3 Deploy the ARM template

```bash
az deployment group create \
  --resource-group rg-latina \
  --template-file main.json 
```

### 1.4 Note the deployment outputs

```bash
az deployment group show \
  --resource-group rg-latina \
  --name main \
  --query properties.outputs
```

Keep a note of `acrLoginServer` and `aksName` — you will need them in later steps.

### 1.5 Connect kubectl to the cluster

```bash
az aks get-credentials --resource-group rg-latina --name aks-latina-shared
```

### 1.6 Create Kubernetes namespaces

```bash
curl -fsSL https://raw.githubusercontent.com/denisdbell/latina_app/refs/heads/main/azure/arm/namespaces.yaml -o namespaces.yaml

kubectl apply -f namespaces.yaml
```

This creates the `dev`, `test`, and `prod` namespaces with network isolation policies and resource quotas.

### 1.7 Apply the ingress rules

```bash

curl -fsSL https://raw.githubusercontent.com/denisdbell/latina_app/refs/heads/main/azure/arm/ingress.yaml -o ingress.yaml

kubectl apply -f ingress.yaml
```

---

## Step 2 — Configure Azure DevOps

### 2.1 Import the repositories

In your Azure DevOps project, import both GitHub repos:

1. **latina_app** — the main application (source of the pipeline YAML files)
2. **latina_app_template** — the shared pipeline templates (referenced as `petclinic-pipeline-template` in the pipeline files)

To import: **Repos → Import repository → GitHub → paste the URL**.

> The pipeline files reference the template repo as:
> ```yaml
> repository: templates
> name: <project name>/latinac_app_template
> ref: main
> ```
> Make sure the imported template repo is named accordingly in ADO, or update this reference in each pipeline file.

### 2.2 Create service connections

Go to **Project Settings → Service connections**.

#### Kubernetes connection

| Setting | Value |
|---------|-------|
| Type | Kubernetes |
| Name | `aks-service-connection` |
| Authentication method | Azure Subscription |
| Cluster | `aks-latina-shared` |

#### Container Registry connection

| Setting | Value |
|---------|-------|
| Type | Docker Registry → Azure Container Registry |
| Name | `acr-service-connection` |
| ACR | `acrlatinalatina` (or your suffix) |

> The pipeline files reference three separate ACR connections (`dev-acr-service-connection`, `test-acr-service-connection`, `prod-acr-service-connection`). For a single shared ACR, create one service connection and set all three variables to the same connection name, or create three connections pointing to the same ACR.

### 2.3 Update the `uniqueSuffix` variable

In each pipeline file (`frontend-service.yml`, `image-service.yml`, `phrase-service.yml`), update the `uniqueSuffix` variable to match your ARM deployment:

```yaml
variables:
  uniqueSuffix: 'latina'   # ← must match your ARM deployment
```

---

## Step 3 — Create the ADO Pipelines

For each of the three services, create a pipeline pointing to its YAML file:

1. **Pipelines → New pipeline**
2. Source: **Azure Repos Git** → select `latina_app`
3. **Existing Azure Pipelines YAML file**
4. Select the file path:

| Service | Pipeline file |
|---------|--------------|
| Image Service | `azure/pipelines/image-service.yml` |
| Phrase Service | `azure/pipelines/phrase-service.yml` |
| Frontend Service | `azure/pipelines/frontend-service.yml` |

5. Click **Save** (do not run yet).

---

## Step 4 — Run the Pipelines

Run the pipelines in this order so backend services are available before the frontend deploys:

1. `image-service`
2. `phrase-service`
3. `frontend-service`

Each pipeline runs through the following stages automatically, with manual approval gates between environments:

```
Build → Deploy Dev → [Approve] → Deploy Test → [Approve] → Deploy Prod
```

To trigger: **Pipelines → select pipeline → Run pipeline → Run**.

---

## Step 5 — Access the Application

After the frontend pipeline completes, get the ingress public IP:

```bash
kubectl get ingress latina-ingress -n dev
```

| Path | Service |
|------|---------|
| `http://<EXTERNAL-IP>/` | Frontend UI |
| `http://<EXTERNAL-IP>/api/image` | Image Service API |
| `http://<EXTERNAL-IP>/api/phrase` | Phrase Service API |

---

## Verify the Deployment

```bash
# Check all pods are running
kubectl get pods -n dev

# Check services
kubectl get svc -n dev

# Check ingress
kubectl get ingress -n dev

# Stream logs for a service
kubectl logs -f <pod-name> -n dev
```

---

## Troubleshooting

### Image pull errors
```bash
# Verify AKS has AcrPull access (already set by ARM template, but check if broken)
AKS_IDENTITY=$(az aks show -g rg-latina -n aks-latina-shared \
  --query identityProfile.kubeletidentity.objectId -o tsv)
ACR_ID=$(az acr show --name acrlatinalatina --query id -o tsv)
az role assignment create --assignee $AKS_IDENTITY --role AcrPull --scope $ACR_ID
```

### Pod not starting
```bash
kubectl describe pod <pod-name> -n dev
kubectl get events -n dev --sort-by='.lastTimestamp'
```

### Ingress not routing correctly
```bash
# Confirm the web app routing addon is enabled
az aks show -g rg-latina -n aks-latina-shared \
  --query addonProfiles.webAppRouting.enabled
```

---

## Clean Up

```bash
# Remove all app resources
kubectl delete all --all -n dev
kubectl delete all --all -n test
kubectl delete all --all -n prod

# Remove Azure infrastructure entirely
az group delete --name rg-latina --yes --no-wait
```

---

## Cost Tips

```bash
# Stop the cluster when not in use (saves compute costs)
az aks stop --resource-group rg-latina --name aks-latina-shared

# Restart when needed
az aks start --resource-group rg-latina --name aks-latina-shared
```
