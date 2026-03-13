# Azure DevOps Setup Guide for Blue-Green Deployment

This guide provides step-by-step instructions for setting up Azure DevOps pipelines for blue-green deployment on Azure Kubernetes Service (AKS).

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Step 1: Create Azure DevOps Project](#step-1-create-azure-devops-project)
3. [Step 2: Set Up Service Connections](#step-2-set-up-service-connections)
4. [Step 3: Create Environments](#step-3-create-environments)
5. [Step 4: Configure Variable Groups](#step-4-configure-variable-groups)
6. [Step 5: Create the Pipeline](#step-5-create-the-pipeline)
7. [Step 6: Configure Pipeline YAML](#step-6-configure-pipeline-yaml)
8. [Step 7: Set Up Approval Gates](#step-7-set-up-approval-gates)
9. [Step 8: Run the Pipeline](#step-8-run-the-pipeline)
10. [Step 9: Monitor and Rollback](#step-9-monitor-and-rollback)

---

## Prerequisites

Before starting, ensure you have:

- [ ] Azure subscription with Owner/Contributor access
- [ ] Azure Kubernetes Service (AKS) cluster deployed
- [ ] Azure Container Registry (ACR) created
- [ ] Azure DevOps organization created
- [ ] Kubernetes manifests prepared with `slot` labels
- [ ] Docker images ready for deployment

---

## Step 1: Create Azure DevOps Project

### 1.1 Navigate to Azure DevOps

1. Go to https://dev.azure.com
2. Sign in with your Microsoft account
3. Select your organization or create a new one

### 1.2 Create New Project

1. Click **New project**
2. Enter project details:
   - **Name**: `Latina App`
   - **Description**: Blue-green deployment for Latina application
   - **Visibility**: Private
   - **Version control**: Git
   - **Work item process**: Agile (or Basic)
3. Click **Create**

### 1.3 Import Repository (if needed)

If you have an existing repository:

1. Go to **Repos** > **Files**
2. Click **Import a repository**
3. Enter your repository URL
4. Click **Import**

---

## Step 2: Set Up Service Connections

### 2.1 Create AKS Service Connection

1. Go to **Project Settings** (gear icon in bottom left)
2. Navigate to **Pipelines** > **Service connections**
3. Click **New service connection**
4. Select **Kubernetes**
5. Choose **Azure Service Connection** authentication method
6. Configure:
   - **Connection name**: `aks-service-connection`
   - **Subscription**: Select your Azure subscription
   - **Cluster**: Select your AKS cluster
   - **Namespace**: Select or enter namespace (e.g., `prod`)
7. Click **Save**

### 2.2 Create ACR Service Connection (per environment)

For each environment (dev, test, prod):

1. Click **New service connection**
2. Select **Docker Registry**
3. Choose **Azure Container Registry**
4. Configure:
   - **Connection name**: `dev-acr-service-connection`
   - **Subscription**: Select your Azure subscription
   - **Azure container registry**: Select your ACR
5. Click **Save**

6. Repeat for test and prod environments:
   - `test-acr-service-connection`
   - `prod-acr-service-connection`

### 2.3 Verify Service Connections

```
Service connections created:
├── aks-service-connection       (AKS cluster access)
├── dev-acr-service-connection   (Dev ACR)
├── test-acr-service-connection  (Test ACR)
└── prod-acr-service-connection  (Prod ACR)
```

---

## Step 3: Create Environments

### 3.1 Navigate to Environments

1. Go to **Pipelines** > **Environments**
2. Click **New environment**

### 3.2 Create Development Environment

1. Enter **Name**: `dev`
2. Enter **Description**: `Development environment`
3. Select **Resource type**: Kubernetes
4. Click **Create**
5. Add Kubernetes resource:
   - **Cluster**: Select your AKS cluster
   - **Namespace**: `dev`
6. Click **Add**

### 3.3 Create Testing Environment

1. Click **New environment**
2. Enter **Name**: `testing`
3. Enter **Description**: `Testing environment`
4. Select **Resource type**: Kubernetes
5. Click **Create**
6. Add Kubernetes resource:
   - **Cluster**: Select your AKS cluster
   - **Namespace**: `testing`

### 3.4 Create Production Environment

1. Click **New environment**
2. Enter **Name**: `prod`
3. Enter **Description**: `Production environment`
4. Select **Resource type**: Kubernetes
5. Click **Create**
6. Add Kubernetes resource:
   - **Cluster**: Select your AKS cluster
   - **Namespace**: `prod`

### 3.5 Create Rollback Environment

1. Click **New environment**
2. Enter **Name**: `prod-rollback`
3. Enter **Description**: `Production rollback approval`
4. Select **Resource type**: Kubernetes (or "Virtual machine" for approval-only)
5. Click **Create**

### 3.6 Verify Environments

```
Environments created:
├── dev            (Development)
├── testing        (Testing)
├── prod           (Production)
└── prod-rollback  (Rollback approval)
```

---

## Step 4: Configure Variable Groups

### 4.1 Create Variable Group

1. Go to **Pipelines** > **Library**
2. Click **+ Variable group**
3. Enter **Name**: `latina-pipeline-variables`

### 4.2 Add Variables

Add the following variables:

| Variable Name | Value | Description |
|---------------|-------|-------------|
| `aksServiceConnection` | `aks-service-connection` | AKS service connection name |
| `devAcrConnection` | `dev-acr-service-connection` | Dev ACR connection |
| `testAcrConnection` | `test-acr-service-connection` | Test ACR connection |
| `prodAcrConnection` | `prod-acr-service-connection` | Prod ACR connection |
| `devAcrLoginServer` | `devacr.azurecr.io` | Dev ACR login server URL |
| `testAcrLoginServer` | `testacr.azurecr.io` | Test ACR login server URL |
| `prodAcrLoginServer` | `prodacr.azurecr.io` | Prod ACR login server URL |
| `namespace` | `inspire` | Default Kubernetes namespace |
| `replicas` | `1` | Number of replicas |

### 4.3 Save Variable Group

1. Click **Save**

---

## Step 5: Create the Pipeline

### 5.1 Navigate to Pipelines

1. Go to **Pipelines** > **Pipelines**
2. Click **New pipeline**

### 5.2 Select Repository Source

1. Select **Azure Repos Git**
2. Select your repository
3. Click **Continue**

### 5.3 Select Pipeline Template

1. Select **Existing Azure Pipelines YAML file**
2. Select the branch (e.g., `main`)
3. Select the path: `azure/pipelines/frontend-service-bluegreen.yml`
4. Click **Continue**

### 5.4 Review and Save

1. Review the pipeline YAML
2. Click **Save** (not "Run")

---

## Step 6: Configure Pipeline YAML

### 6.1 Pipeline Structure

The blue-green pipeline should have the following structure:

```yaml
# azure/pipelines/frontend-service-bluegreen.yml

trigger:
  branches:
    include:
      - main
  paths:
    include:
      - frontend-service/**

variables:
  - group: latina-pipeline-variables

stages:
  # Stage 1: Build
  - stage: Build
    displayName: 'Build and Push Image'
    jobs:
    - job: Build
      displayName: 'Build Docker Image'
      pool:
        vmImage: 'ubuntu-latest'
      steps:
      - task: Docker@2
        displayName: 'Build and Push to ACR'
        inputs:
          containerRegistry: $(devAcrConnection)
          repository: 'frontend-service'
          command: 'buildAndPush'
          Dockerfile: 'frontend-service/Dockerfile'
          tags: |
            $(Build.BuildId)
            latest

  # Stage 2: Deploy to Dev
  - stage: DeployDev
    displayName: 'Deploy to Development'
    dependsOn: Build
    jobs:
    - deployment: DeployDev
      displayName: 'Blue-Green Deploy to Dev'
      environment: 'dev'
      strategy:
        runOnce:
          deploy:
            steps:
            - template: ../templates/deploy-bluegreen.yaml
              parameters:
                environmentName: 'dev'
                namespace: $(namespace)
                aksServiceConnection: $(aksServiceConnection)
                targetAcrConnection: $(devAcrConnection)
                targetAcrLoginServer: $(devAcrLoginServer)
                imageRepository: 'frontend-service'
                imageTag: $(Build.BuildId)
                serviceName: 'frontend-service'

  # Stage 3: Approve Testing
  - stage: ApproveTesting
    displayName: 'Approve Testing Deployment'
    dependsOn: DeployDev
    jobs:
    - job: WaitForApproval
      pool: server
      steps:
      - task: ManualValidation@0
        inputs:
          instructions: 'Approve deployment to testing environment'
          onTimeout: 'reject'

  # Stage 4: Deploy to Test
  - stage: DeployTest
    displayName: 'Deploy to Testing'
    dependsOn: ApproveTesting
    jobs:
    - deployment: DeployTest
      displayName: 'Blue-Green Deploy to Test'
      environment: 'testing'
      strategy:
        runOnce:
          deploy:
            steps:
            - template: ../templates/deploy-bluegreen.yaml
              parameters:
                environmentName: 'testing'
                namespace: $(namespace)
                aksServiceConnection: $(aksServiceConnection)
                targetAcrConnection: $(testAcrConnection)
                targetAcrLoginServer: $(testAcrLoginServer)
                imageRepository: 'frontend-service'
                imageTag: $(Build.BuildId)
                serviceName: 'frontend-service'
                promoteImage: true

  # Stage 5: Approve Production
  - stage: ApproveProd
    displayName: 'Approve Production Deployment'
    dependsOn: DeployTest
    jobs:
    - job: WaitForApproval
      pool: server
      steps:
      - task: ManualValidation@0
        inputs:
          instructions: 'Approve deployment to production environment'
          onTimeout: 'reject'

  # Stage 6: Deploy to Production
  - stage: DeployProd
    displayName: 'Deploy to Production'
    dependsOn: ApproveProd
    jobs:
    - deployment: DeployProd
      displayName: 'Blue-Green Deploy to Prod'
      environment: 'prod'
      strategy:
        runOnce:
          deploy:
            steps:
            - template: ../templates/deploy-bluegreen.yaml
              parameters:
                environmentName: 'prod'
                namespace: $(namespace)
                aksServiceConnection: $(aksServiceConnection)
                targetAcrConnection: $(prodAcrConnection)
                targetAcrLoginServer: $(prodAcrLoginServer)
                imageRepository: 'frontend-service'
                imageTag: $(Build.BuildId)
                serviceName: 'frontend-service'
                promoteImage: true

  # Stage 7: Rollback (Optional)
  - stage: RollbackProd
    displayName: 'Rollback Production'
    dependsOn: DeployProd
    condition: failed()
    jobs:
    - deployment: WaitForRollbackDecision
      environment: 'prod-rollback'
      strategy:
        runOnce:
          deploy:
            steps:
            - task: ManualValidation@0
              inputs:
                instructions: 'This will instantly switch traffic back to the previous slot.'
            - template: ../templates/rollback.yaml
              parameters:
                environmentName: 'prod'
                namespace: $(namespace)
                aksServiceConnection: $(aksServiceConnection)
                serviceName: 'frontend-service'
```

### 6.2 Deploy Template (deploy-bluegreen.yaml)

Create the deployment template at `azure/templates/deploy-bluegreen.yaml`:

```yaml
# azure/templates/deploy-bluegreen.yaml

parameters:
- name: environmentName
  type: string
- name: namespace
  type: string
- name: aksServiceConnection
  type: string
- name: targetAcrConnection
  type: string
- name: targetAcrLoginServer
  type: string
- name: imageRepository
  type: string
- name: imageTag
  type: string
- name: serviceName
  type: string
- name: promoteImage
  type: boolean
  default: false

steps:
# Step 1: Detect Active Slot
- task: Bash@3
  displayName: 'Detect Active Slot'
  name: detectSlot
  inputs:
    targetType: 'inline'
    script: |
      CURRENT_SLOT=$(kubectl get svc ${{ parameters.serviceName }} -n ${{ parameters.namespace }} -o jsonpath='{.spec.selector.slot}' 2>/dev/null || echo "blue")

      if [ "$CURRENT_SLOT" = "blue" ]; then
        echo "##vso[task.setvariable variable=inactiveSlot]green"
        echo "Active slot: blue, deploying to: green"
      else
        echo "##vso[task.setvariable variable=inactiveSlot]blue"
        echo "Active slot: green, deploying to: blue"
      fi

      echo "Current active slot: $CURRENT_SLOT"
    azureSubscription: ${{ parameters.aksServiceConnection }}

# Step 2: Promote Image (if needed)
- task: Docker@2
  displayName: 'Promote Image to Target ACR'
  condition: eq(${{ parameters.promoteImage }}, true)
  inputs:
    containerRegistry: ${{ parameters.targetAcrConnection }}
    repository: ${{ parameters.imageRepository }}
    command: 'push'
    tags: |
      ${{ parameters.imageTag }}

# Step 3: Deploy to Inactive Slot
- task: KubernetesManifest@1
  displayName: 'Deploy to Inactive Slot'
  inputs:
    action: 'deploy'
    connectionType: 'kubernetesServiceConnection'
    kubernetesServiceConnection: ${{ parameters.aksServiceConnection }}
    namespace: ${{ parameters.namespace }}
    manifests: |
      ${{ parameters.serviceName }}/k8s/deployment-bluegreen.yaml
    containers: |
      ${{ parameters.targetAcrLoginServer }}/${{ parameters.imageRepository }}:${{ parameters.imageTag }}
    arguments: |
      --set-string SLOT=$(inactiveSlot)

# Step 4: Wait for Rollout
- task: Bash@3
  displayName: 'Wait for Deployment Rollout'
  inputs:
    targetType: 'inline'
    script: |
      kubectl rollout status deployment/${{ parameters.serviceName }}-$(inactiveSlot) -n ${{ parameters.namespace }} --timeout=300s
    azureSubscription: ${{ parameters.aksServiceConnection }}

# Step 5: Verify Health
- task: Bash@3
  displayName: 'Verify Deployment Health'
  inputs:
    targetType: 'inline'
    script: |
      echo "Waiting for pods to be ready..."
      kubectl wait --for=condition=ready pod -l app=${{ parameters.serviceName }},slot=$(inactiveSlot) -n ${{ parameters.namespace }} --timeout=120s

      echo "Checking pod status..."
      kubectl get pods -n ${{ parameters.namespace }} -l app=${{ parameters.serviceName }},slot=$(inactiveSlot)

      echo "Verifying endpoints..."
      kubectl get endpoints ${{ parameters.serviceName }} -n ${{ parameters.namespace }}
    azureSubscription: ${{ parameters.aksServiceConnection }}

# Step 6: Switch Traffic
- task: Kubernetes@1
  displayName: 'Switch Traffic to New Slot'
  inputs:
    connectionType: 'Kubernetes Service Connection'
    kubernetesServiceConnection: ${{ parameters.aksServiceConnection }}
    namespace: ${{ parameters.namespace }}
    command: 'patch'
    arguments: 'svc ${{ parameters.serviceName }} -p "{\"spec\":{\"selector\":{\"slot\":\"$(inactiveSlot)\"}}}"'

# Step 7: Verify Traffic Switch
- task: Bash@3
  displayName: 'Verify Traffic Routing'
  inputs:
    targetType: 'inline'
    script: |
      echo "Verifying service selector..."
      kubectl get svc ${{ parameters.serviceName }} -n ${{ parameters.namespace }} -o jsonpath='{.spec.selector.slot}'

      echo ""
      echo "Verifying endpoints..."
      kubectl get endpoints ${{ parameters.serviceName }} -n ${{ parameters.namespace }}
    azureSubscription: ${{ parameters.aksServiceConnection }}
```

### 6.3 Rollback Template (rollback.yaml)

Create the rollback template at `azure/templates/rollback.yaml`:

```yaml
# azure/templates/rollback.yaml

parameters:
- name: environmentName
  type: string
- name: namespace
  type: string
- name: aksServiceConnection
  type: string
- name: serviceName
  type: string

steps:
# Step 1: Detect Current Active Slot
- task: Bash@3
  displayName: 'Detect Current Active Slot'
  name: detectSlot
  inputs:
    targetType: 'inline'
    script: |
      CURRENT_SLOT=$(kubectl get svc ${{ parameters.serviceName }} -n ${{ parameters.namespace }} -o jsonpath='{.spec.selector.slot}')

      if [ "$CURRENT_SLOT" = "blue" ]; then
        echo "##vso[task.setvariable variable=rollbackSlot]green"
        echo "Current slot: blue, rolling back to: green"
      else
        echo "##vso[task.setvariable variable=rollbackSlot]blue"
        echo "Current slot: green, rolling back to: blue"
      fi
    azureSubscription: ${{ parameters.aksServiceConnection }}

# Step 2: Verify Previous Slot Exists
- task: Bash@3
  displayName: 'Verify Previous Deployment Exists'
  inputs:
    targetType: 'inline'
    script: |
      PREVIOUS_DEPLOYMENT="${{ parameters.serviceName }}-$(rollbackSlot)"

      if ! kubectl get deployment $PREVIOUS_DEPLOYMENT -n ${{ parameters.namespace }} &>/dev/null; then
        echo "Error: Previous deployment $PREVIOUS_DEPLOYMENT not found"
        echo "Cannot rollback - no previous deployment exists"
        exit 1
      fi

      echo "Previous deployment found: $PREVIOUS_DEPLOYMENT"
    azureSubscription: ${{ parameters.aksServiceConnection }}

# Step 3: Switch Traffic Back
- task: Kubernetes@1
  displayName: 'Switch Traffic to Previous Slot'
  inputs:
    connectionType: 'Kubernetes Service Connection'
    kubernetesServiceConnection: ${{ parameters.aksServiceConnection }}
    namespace: ${{ parameters.namespace }}
    command: 'patch'
    arguments: 'svc ${{ parameters.serviceName }} -p "{\"spec\":{\"selector\":{\"slot\":\"$(rollbackSlot)\"}}}"'

# Step 4: Verify Rollback
- task: Bash@3
  displayName: 'Verify Rollback Success'
  inputs:
    targetType: 'inline'
    script: |
      echo "Verifying service selector..."
      kubectl get svc ${{ parameters.serviceName }} -n ${{ parameters.namespace }} -o jsonpath='{.spec.selector.slot}'

      echo ""
      echo "Verifying endpoints..."
      kubectl get endpoints ${{ parameters.serviceName }} -n ${{ parameters.namespace }}

      echo ""
      echo "Checking pod health..."
      kubectl get pods -n ${{ parameters.namespace }} -l app=${{ parameters.serviceName }},slot=$(rollbackSlot)
    azureSubscription: ${{ parameters.aksServiceConnection }}
```

---

## Step 7: Set Up Approval Gates

### 7.1 Configure Environment Approvals

1. Go to **Pipelines** > **Environments**
2. Click on `testing` environment
3. Click **More actions** (three dots) > **Approvals and checks**
4. Click **+ Add check**
5. Select **Approvals**
6. Add approvers:
   - Enter email addresses or select users
   - Set **Timeout**: 7 days
7. Click **Add**

### 7.2 Configure Production Approvals

1. Go to **Pipelines** > **Environments**
2. Click on `prod` environment
3. Click **More actions** > **Approvals and checks**
4. Click **+ Add check**
5. Select **Approvals**
6. Add production approvers:
   - Add team leads or DevOps engineers
   - Set **Timeout**: 7 days
7. Click **Add**

### 7.3 Add Additional Checks (Optional)

1. In the same environment, click **+ Add check**
2. Select **Invoke Azure Function** for custom validation
3. Or select **Run REST API** for external service checks

### 7.4 Configure Branch Policies

1. Go to **Repos** > **Branches**
2. Click **More options** (three dots) on `main` branch
3. Select **Branch policies**
4. Enable **Require minimum number of reviewers**
   - Set minimum reviewers: 1 or 2
5. Enable **Check for linked work items**
6. Enable **Build validation**:
   - Select your pipeline
   - Set trigger: Run only when new changes are pushed

---

## Step 8: Run the Pipeline

### 8.1 Trigger Pipeline

1. Go to **Pipelines** > **Pipelines**
2. Select your pipeline
3. Click **Run pipeline**
4. Select the branch (e.g., `main`)
5. Click **Run**

### 8.2 Monitor Build Stage

1. Watch the **Build** stage progress
2. Click on the stage to see detailed logs
3. Verify Docker image is built and pushed to ACR

### 8.3 Monitor Dev Deployment

1. Once Build completes, **DeployDev** starts automatically
2. Click on the stage to see:
   - Active slot detection
   - Deployment to inactive slot
   - Health verification
   - Traffic switch

### 8.4 Approve Testing Deployment

1. After Dev completes, pipeline pauses at **ApproveTesting**
2. Click **ApproveTesting** stage
3. Review Dev deployment logs
4. Click **Approve** or **Reject**
5. Add comments if needed

### 8.5 Monitor Test Deployment

1. After approval, **DeployTest** starts
2. Monitor the deployment through logs
3. Verify successful traffic switch

### 8.6 Approve Production Deployment

1. Pipeline pauses at **ApproveProd**
2. Review Test deployment results
3. Verify application functionality
4. Click **Approve** to proceed to production

### 8.7 Monitor Production Deployment

1. **DeployProd** starts after approval
2. Monitor:
   - Slot detection
   - Deployment to inactive slot
   - Health checks
   - Traffic switch
   - Verification

---

## Step 9: Monitor and Rollback

### 9.1 Monitor Deployment

**Via Azure DevOps:**

1. Go to **Pipelines** > **Pipelines**
2. Click on the running pipeline
3. View real-time logs for each stage

**Via kubectl:**

```bash
# Watch deployment status
kubectl rollout status deployment/frontend-service-green -n prod --timeout=300s

# Monitor pods
kubectl get pods -n prod -l app=frontend-service -w

# Check service endpoints
kubectl get endpoints frontend-service -n prod

# View deployment events
kubectl get events -n prod --sort-by='.lastTimestamp' | grep frontend-service
```

### 9.2 Rollback Procedure

**Automatic Rollback (via Pipeline):**

If the deployment fails, the pipeline automatically triggers the rollback stage.

**Manual Rollback (via Pipeline):**

1. Go to the completed pipeline run
2. Click **Run rollback** (if configured)
3. Or navigate to **RollbackProd** stage
4. Click **Run stage**

**Manual Rollback (via kubectl):**

```bash
# Check current active slot
kubectl get svc frontend-service -n prod -o jsonpath='{.spec.selector.slot}'

# Switch traffic to previous slot
kubectl patch svc frontend-service -n prod -p '{"spec":{"selector":{"slot":"blue"}}}'

# Verify rollback
kubectl get endpoints frontend-service -n prod
```

### 9.3 Post-Deployment Verification

```bash
# Verify service selector
kubectl get svc frontend-service -n prod -o yaml | grep -A5 "selector:"

# Check endpoints are populated
kubectl get endpoints frontend-service -n prod

# Verify application health
curl -s http://<ingress-ip>/actuator/health | jq .

# Check pod logs
kubectl logs -n prod -l app=frontend-service,slot=green --tail=50
```

---

## Summary

### Azure DevOps Setup Checklist

- [ ] Azure DevOps project created
- [ ] Repository imported
- [ ] AKS service connection created
- [ ] ACR service connections created (dev, test, prod)
- [ ] Environments created (dev, testing, prod, prod-rollback)
- [ ] Variable groups configured
- [ ] Pipeline YAML created
- [ ] Deploy template created
- [ ] Rollback template created
- [ ] Approval gates configured
- [ ] Branch policies enabled

### Pipeline Flow

```
Build → DeployDev → ApproveTesting → DeployTest → ApproveProd → DeployProd
                                                    ↓
                                              (if failed)
                                                    ↓
                                              RollbackProd
```

### Key Points

1. **Service Connections**: Required for AKS and ACR access
2. **Environments**: Required for approvals and Kubernetes deployments
3. **Variable Groups**: Centralize configuration across stages
4. **Templates**: Reusable YAML for deployment logic
5. **Approval Gates**: Manual intervention before test and prod
6. **Rollback Stage**: Automatic trigger on failure

For questions or issues, refer to the [blue-green-deployment.md](./blue-green-deployment.md) guide or contact the DevOps team.