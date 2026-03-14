# Azure Deployment Guide — Latina App

This guide covers deploying the **Latina App** to Azure Kubernetes Service (AKS) using the ARM template and Azure DevOps (ADO) pipelines.

**Repositories:**
- App source: https://github.com/jremo25/latina_app (branch: `blue-green`)
- Pipeline templates: https://github.com/jremo25/latina_app_template (branch: `main`)

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

### 1.1 Download the main.json

```bash
curl -fsSL https://raw.githubusercontent.com/jremo25/latina_app/refs/heads/blue-green/azure/arm/main.json -o main.json
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

Keep a note of the `uniqueSuffix` value — you will need it to update the pipeline files.

### 1.5 Connect kubectl to the cluster

```bash
az aks get-credentials --resource-group rg-latina --name aks-latina-shared
```

### 1.6 Create Kubernetes namespaces

```bash
curl -fsSL https://raw.githubusercontent.com/jremo25/latina_app/refs/heads/blue-green/azure/arm/namespaces.yaml -o namespaces.yaml

kubectl apply -f namespaces.yaml
```

This creates the `dev`, `test`, and `prod` namespaces with network isolation policies and resource quotas.

### 1.7 Apply the ingress rules

```bash
curl -fsSL https://raw.githubusercontent.com/jremo25/latina_app/refs/heads/blue-green/azure/arm/ingress.yaml -o ingress.yaml

kubectl apply -f ingress.yaml
```

---

## Step 2 — Configure Azure DevOps

### 2.1 Import the repositories

In your Azure DevOps project, import both GitHub repos:

1. **latina_app** — the main application (branch: `blue-green`)
2. **latina_app_template** — the shared pipeline templates (branch: `main`)

To import: **Repos → Import repository → GitHub → paste the URL**.

> The pipeline files reference the template repo as:
> ```yaml
> resources:
>   repositories:
>     - repository: templates
>       type: git
>       name: <YourADOProject>/latina_app_template
>       ref: main
> ```
> Replace `<YourADOProject>` with your Azure DevOps project name (e.g., `PetClinic`).

### 2.2 Create service connections

Go to **Project Settings → Service connections**.

#### Kubernetes connection

| Setting | Value |
|---------|-------|
| Type | Kubernetes |
| Name | `aks-service-connection` |
| Authentication method | Azure Subscription |
| Cluster | `aks-latina-shared` |

#### Container Registry connections

The ARM template creates 3 separate ACRs (dev, test, prod). Create a service connection for each:

| Name | ACR |
|------|-----|
| `dev-acr-service-connection` | `acrlatinadev<uniqueSuffix>` |
| `test-acr-service-connection` | `acrlatinatest<uniqueSuffix>` |
| `prod-acr-service-connection` | `acrlatinaprod<uniqueSuffix>` |

Type: **Docker Registry → Azure Container Registry** for each.

### 2.3 Update the `uniqueSuffix` variable

In each pipeline file (`frontend-service-bluegreen.yml`, `image-service.yml`, `phrase-service.yml`), update the `uniqueSuffix` variable to match the value from your ARM deployment output:

```yaml
variables:
  uniqueSuffix: 'abcd1234'   # ← replace with your ARM deployment output value
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
| Frontend Service (Blue-Green) | `azure/pipelines/frontend-service-bluegreen.yml` |

5. Click **Save** (do not run yet).

> **Note:** Only 1 pipeline is needed for the frontend blue-green deployment. Each run automatically detects the active slot and deploys to the opposite one.

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

## Blue-Green Deployment (Production)

Blue-green deployment is a release strategy that reduces downtime and risk by running two identical production environments called **Blue** and **Green**. At any time, only one of these environments is live and receiving traffic.

### How It Works

```
┌─────────────────────────────────────────────────────────────────────┐
│                    PRODUCTION NAMESPACE                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   ┌─────────────────┐         ┌─────────────────┐                   │
│   │   BLUE SLOT     │         │   GREEN SLOT    │                   │
│   │   (v1.0)        │         │   (v2.0)        │                   │
│   │   - Deployment  │         │   - Deployment  │                   │
│   │   - Pods        │         │   - Pods        │                   │
│   └─────────────────┘         └─────────────────┘                   │
│           ▲                           ▲                             │
│           │                           │                             │
│           └───────────┬───────────────┘                             │
│                       │                                              │
│              ┌────────┴────────┐                                     │
│              │     SERVICE     │                                     │
│              │  selector: slot │                                     │
│              │    = green      │  ◄── Traffic routes to Green        │
│              └─────────────────┘                                     │
│                       │                                              │
│                       ▼                                              │
│                  ┌─────────┐                                         │
│                  │  INGRESS│                                         │
│                  └─────────┘                                         │
│                       │                                              │
└───────────────────────┼─────────────────────────────────────────────┘
                        ▼
                   User Traffic
```

### Blue-Green Deployment Flow

1. **Detect Active Slot** - The pipeline checks which slot (blue or green) is currently receiving traffic
2. **Deploy to Inactive Slot** - New version deploys to the inactive slot without affecting live traffic
3. **Health Verification** - Pipeline verifies all pods are healthy before proceeding
4. **Manual Approval** - Requires approval to switch traffic
5. **Traffic Switch** - Service selector is updated to route traffic to the new slot
6. **Verification** - Confirms traffic is routing correctly
7. **Instant Rollback** - If issues occur, traffic can be instantly switched back

### Prerequisites for Blue-Green

In addition to the standard prerequisites:

1. **ADO Environments**: Create the following environments in **Pipelines → Environments**:
   - `dev`
   - `testing`
   - `prod`
   - `prod-rollback`

2. **Service connections** (created in Step 2):
   - `dev-acr-service-connection` — Dev ACR
   - `test-acr-service-connection` — Test ACR
   - `prod-acr-service-connection` — Prod ACR
   - `aks-service-connection` — Kubernetes cluster connection

---

## Step 6 — Blue-Green Pipeline Setup

The blue-green pipeline is already created in Step 3 (`frontend-service-bluegreen.yml`). Verify the following before running:

### 6.1 Verify Template Reference

In `azure/pipelines/frontend-service-bluegreen.yml`, confirm the template repo reference matches your ADO project:

```yaml
resources:
  repositories:
    - repository: templates
      type: git
      name: PetClinic/latina_app_template   # ← your ADO project name
      ref: main
```

### 6.2 Create Required ADO Environments

Go to **Pipelines → Environments** and create:
- `dev`
- `testing`
- `prod`
- `prod-rollback`

> **Important:** The `prod-rollback` environment is required for the rollback stage. The pipeline will fail if it doesn't exist.

---

## Step 7 — Run Blue-Green Deployment

### 7.1 Deployment Sequence

Run pipelines in this order:

1. `image-service` (standard deployment)
2. `phrase-service` (standard deployment)
3. `frontend-service-bluegreen` (blue-green deployment)

### 7.2 Blue-Green Pipeline Stages

Only **1 pipeline** is needed. Each run auto-detects the active slot and deploys to the opposite one:

```
Build → Deploy Dev → [Approve] → Deploy Test → [Approve] → Deploy Prod (Inactive Slot) → [Test & Approve] → Switch Traffic → [Rollback if needed]
```

| Stage | Description |
|-------|-------------|
| Build | Builds and pushes Docker image to Dev ACR |
| Deploy Dev | Standard deployment to dev namespace |
| Approve Testing | Manual gate before promoting to test |
| Deploy Test | Deploys to test namespace (promotes image from Dev ACR to Test ACR) |
| Approve Prod | Manual gate before production deployment |
| Deploy Prod (Inactive Slot) | Detects active slot, deploys to the opposite slot (promotes image from Test ACR to Prod ACR) |
| Test & Approve Traffic Switch | Manual validation — new version is running but not receiving traffic yet |
| Switch Traffic | Patches the service selector to route traffic to the new slot (instant, no pod restarts) |
| Rollback | Manual gate — approve only if you need to switch traffic back to the previous slot |

### 7.3 How the Slot Alternation Works

- **Run 1** (first ever): No slot exists → deploys to `blue` → switches traffic to `blue`
- **Run 2**: Detects `blue` active → deploys to `green` → switches traffic to `green`
- **Run 3**: Detects `green` active → deploys to `blue` → switches traffic to `blue`
- ...and so on, alternating automatically.

### 7.4 Testing Before Traffic Switch

After the inactive slot deployment completes, verify before approving the traffic switch:

```bash
# Check deployments
kubectl get deployments -n prod -l app=frontend-service

# Check pods for each slot
kubectl get pods -n prod -l app=frontend-service

# Check which slot currently receives traffic
kubectl get svc frontend-service -n prod -o jsonpath='{.spec.selector}'
```

### 7.5 Instant Rollback

If issues are discovered after switching traffic, you have 3 options:

1. **Approve the Rollback stage** in the current pipeline run — it will switch traffic back to the previous slot

2. **Run the pipeline again** — it will deploy to the other slot and switch traffic after approval

3. **Manually via kubectl**:
```bash
# Check current active slot
kubectl get svc frontend-service -n prod -o jsonpath='{.spec.selector.slot}'

# Switch to the other slot (replace "blue" with the target slot)
kubectl patch svc frontend-service -n prod -p '{"spec":{"selector":{"slot":"blue"}}}' --type=strategic
```

Rollback is instant — no pod restarts. Both slots remain running.

---

## Blue-Green Deployment Checklist

Before approving traffic switch:

- [ ] New slot deployment health check passes
- [ ] Smoke tests completed on new deployment
- [ ] Performance metrics within acceptable limits
- [ ] No critical errors in logs
- [ ] Previous slot still running (rollback ready)
- [ ] Stakeholder sign-off obtained

---

## Troubleshooting

### Image pull errors
```bash
# Verify AKS has AcrPull access (already set by ARM template, but check if broken)
AKS_IDENTITY=$(az aks show -g rg-latina -n aks-latina-shared \
  --query identityProfile.kubeletidentity.objectId -o tsv)
ACR_ID=$(az acr show --name acrlatinaprod<uniqueSuffix> --query id -o tsv)
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
