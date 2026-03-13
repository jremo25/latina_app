# Azure Deployment Guide — Latina App

This guide covers deploying the **Latina App** to Azure Kubernetes Service (AKS) using the ARM template and Azure DevOps (ADO) pipelines.

**Repositories:**
- App source: https://github.com/denisdbell/latina_app
- Pipeline templates: https://github.com/denisdbell/latina_app_template

> **Important:** The blue-green deployment feature is on the `blue-green` branch. Make sure to use this branch when working with blue-green deployments.

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

1. **Branch Requirement**: Use the `blue-green` branch from both repositories:
   ```bash
   # Clone and checkout the blue-green branch
   git clone https://github.com/denisdbell/latina_app.git
   cd latina_app
   git checkout blue-green

   # For pipeline templates
   git clone https://github.com/denisdbell/latina_app_template.git
   cd latina_app_template
   git checkout blue-green
   ```

2. **Service connections for each environment**:
   - `dev-acr-service-connection` - Dev ACR
   - `test-acr-service-connection` - Test ACR
   - `prod-acr-service-connection` - Prod ACR
   - `aks-service-connection` - Kubernetes cluster connection

---

## Step 6 — Create Blue-Green Deployment Pipeline

### 6.1 Import Repositories

Import both repositories into Azure DevOps (if not already done in Step 2):

| Repository | URL |
|------------|-----|
| latina_app | https://github.com/denisdbell/latina_app |
| latina_app_template | https://github.com/denisdbell/latina_app_template |

> **Critical:** After importing, ensure you checkout the `blue-green` branch in both repos. The pipeline files reference templates from the template repo.

### 6.2 Update Pipeline Template Reference

In `azure/pipelines/frontend-service-bluegreen.yml`, verify the template repository reference:

```yaml
resources:
  repositories:
    - repository: templates
      type: git
      name: <project>/latina_app_template  # Your ADO project name
      ref: blue-green                        # Must use blue-green branch
```

### 6.3 Configure Environment Variables

Update the `uniqueSuffix` variable in the pipeline file to match your ARM deployment:

```yaml
variables:
  uniqueSuffix: 'latina'   # ← must match your ARM deployment output
```

### 6.4 Create the Pipeline

1. **Pipelines → New pipeline**
2. Source: **Azure Repos Git** → select `latina_app`
3. **Existing Azure Pipelines YAML file**
4. Select: `azure/pipelines/frontend-service-bluegreen.yml`
5. Click **Save** (do not run yet)

### 6.5 Configure Environment Approvals

For production blue-green deployments, configure environment approvals:

1. Go to **Environments** under Pipelines
2. Find the `prod` environment
3. Click **⋯** → **Approvals and checks**
4. Add **Approvals** with appropriate approvers

---

## Step 7 — Run Blue-Green Deployment

### 7.1 Deployment Sequence

Run pipelines in this order:

1. `image-service` (standard deployment)
2. `phrase-service` (standard deployment)
3. `frontend-service-bluegreen` (blue-green deployment)

### 7.2 Blue-Green Pipeline Stages

```
Build → Deploy Dev → [Approve] → Deploy Test → [Approve] → Deploy Prod Blue → Deploy Prod Green → [Test & Approve] → Traffic Switch
```

| Stage | Description |
|-------|-------------|
| Build | Builds and pushes Docker image to ACR |
| Deploy Dev | Standard deployment to dev namespace |
| Deploy Test | Standard deployment to test namespace (with image promotion) |
| Deploy Prod Green | Deploys to Green slot (inactive) |
| Deploy Prod Blue | Deploys to Blue slot |
| Test Blue/Green | Manual validation of both deployments |
| Traffic Switch | Switches service selector to new slot |

### 7.3 Testing Before Traffic Switch

After the blue and green deployments are complete, test both slots before approving traffic switch:

```bash
# Check both deployments are running
kubectl get deployments -n prod -l app=frontend-service

# Expected output:
# NAME                    READY   UP-TO-DATE   AVAILABLE   AGE
# frontend-service-blue   2/2     2            2           5m
# frontend-service-green  2/2     2            2           10m

# Check pods for each slot
kubectl get pods -n prod -l app=frontend-service

# Check service selector (shows which slot receives traffic)
kubectl get svc frontend-service -n prod -o jsonpath='{.spec.selector}'
```

### 7.4 Manual Traffic Switch Approval

During the `TestProdBlueGreen` stage:

1. ADO displays test URLs for both blue and green deployments
2. Manually test both deployments:
   - Health endpoints
   - API functionality
   - Performance metrics
3. Review the checklist displayed in the approval task
4. Approve to switch traffic to the new slot

### 7.5 Instant Rollback

If issues are discovered after traffic switch:

```bash
# Get current active slot
kubectl get svc frontend-service -n prod -o jsonpath='{.spec.selector.slot}'

# Switch back to previous slot instantly (no pod restarts)
# If current is "blue", switch to "green" (or vice versa)
kubectl patch svc frontend-service -n prod -p '{"spec":{"selector":{"slot":"green"}}}'
```

Or trigger the manual rollback stage in the pipeline.

---

## Blue-Green Deployment Checklist

Before approving traffic switch:

- [ ] Blue deployment health check passes
- [ ] Green deployment health check passes
- [ ] Smoke tests completed on new deployment
- [ ] Performance metrics within acceptable limits
- [ ] No critical errors in logs
- [ ] Previous deployment still running (fallback ready)
- [ ] Stakeholder sign-off obtained
- [ ] Rollback plan confirmed

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
