# Blue-Green Deployment Guide

This guide covers the blue-green deployment implementation for the Latina application on Azure Kubernetes Service (AKS). Blue-green deployment enables zero-downtime releases by maintaining two identical production environments and switching traffic instantly.

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [How It Works](#how-it-works)
4. [File Structure](#file-structure)
5. [Prerequisites](#prerequisites)
6. [Deployment Workflow](#deployment-workflow)
7. [Rollback Procedure](#rollback-procedure)
8. [Azure DevOps Pipeline](#azure-devops-pipeline)
9. [Kubernetes Resources](#kubernetes-resources)
10. [Testing & Verification](#testing--verification)
11. [Troubleshooting](#troubleshooting)
12. [Lesson Plan Reference](#lesson-plan-reference)

---

## Overview

### What is Blue-Green Deployment?

Blue-green deployment is a release strategy that maintains two identical production environments:

- **Blue**: The currently active environment serving live traffic
- **Green**: The inactive environment where new releases are deployed

When a new version is ready, traffic is switched from blue to green instantaneously. If issues arise, you can instantly roll back by switching back to blue.

### Benefits

| Benefit | Description |
|---------|-------------|
| **Zero Downtime** | No service interruption during deployment |
| **Instant Rollback** | Switch back to previous version in seconds |
| **Production Testing** | Test in production environment before going live |
| **Reduced Risk** | New version runs in production-like conditions before receiving traffic |

### Trade-offs

| Trade-off | Consideration |
|-----------|---------------|
| **Resource Cost** | Requires 2x resources during deployment (both slots active) |
| **Complexity** | Additional pipeline logic for slot detection and switching |
| **State Management** | Databases and stateful services need careful handling |

---

## Architecture

### Traditional Rolling Update vs Blue-Green

```
┌─────────────────────────────────────────────────────────────────┐
│                    ROLLING UPDATE                               │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                  │
│  │  Pod v1  │ -> │  Pod v2  │ -> │  Pod v2  │  (Gradual)      │
│  │  (old)   │    │  (new)   │    │  (new)   │                  │
│  └──────────┘    └──────────┘    └──────────┘                  │
│  Traffic split between versions during rollout                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    BLUE-GREEN                                   │
│                                                                  │
│  ┌──────────────────────┐    ┌──────────────────────┐          │
│  │     BLUE SLOT        │    │     GREEN SLOT       │          │
│  │  ┌────────────────┐  │    │  ┌────────────────┐  │          │
│  │  │   Pod v1.0     │  │    │  │   Pod v1.1     │  │          │
│  │  │   (active)     │  │    │  │   (inactive)   │  │          │
│  │  └────────────────┘  │    │  └────────────────┘  │          │
│  └──────────────────────┘    └──────────────────────┘          │
│           ↑                            ↑                        │
│           │                            │                        │
│  ┌────────┴────────────────────────────┴────────┐              │
│  │              Kubernetes Service               │              │
│  │         selector: {slot: blue}                │              │
│  └───────────────────────────────────────────────┘              │
│                     Switch → selector: {slot: green}            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Service Selector Switching

The traffic switch is achieved by updating the Kubernetes Service selector:

```yaml
# Before switch - traffic routes to blue
apiVersion: v1
kind: Service
metadata:
  name: frontend-service
spec:
  selector:
    app: frontend-service
    slot: blue    # ← Traffic goes here

# After switch - traffic routes to green
apiVersion: v1
kind: Service
metadata:
  name: frontend-service
spec:
  selector:
    app: frontend-service
    slot: green   # ← Traffic switched here
```

---

## How It Works

### Step-by-Step Deployment Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                     BLUE-GREEN DEPLOYMENT FLOW                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Step 1: DETECT ACTIVE SLOT                                         │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  kubectl get svc frontend-service -o jsonpath='{.spec.    │     │
│  │  selector.slot}'                                           │     │
│  │  → Returns: "blue"                                         │     │
│  └────────────────────────────────────────────────────────────┘     │
│                          ↓                                           │
│  Step 2: DEPLOY TO INACTIVE SLOT (green)                           │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  kubectl apply -f deployment-green.yaml                    │     │
│  │  → Creates: frontend-service-green deployment              │     │
│  │  → Pods start with new version                            │     │
│  │  → Readiness probes pass                                   │     │
│  └────────────────────────────────────────────────────────────┘     │
│                          ↓                                           │
│  Step 3: VERIFY HEALTH                                              │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  kubectl rollout status deployment/frontend-service-green  │     │
│  │  kubectl get pods -l slot=green                            │     │
│  │  → All pods ready                                          │     │
│  └────────────────────────────────────────────────────────────┘     │
│                          ↓                                           │
│  Step 4: SWITCH TRAFFIC                                             │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  kubectl patch svc frontend-service -p \                   │     │
│  │    '{"spec":{"selector":{"slot":"green"}}}'                │     │
│  │  → Instant switch (milliseconds)                           │     │
│  │  → Traffic now routes to green pods                        │     │
│  └────────────────────────────────────────────────────────────┘     │
│                          ↓                                           │
│  Step 5: VERIFY TRAFFIC ROUTING                                    │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │  kubectl get endpoints frontend-service                    │     │
│  │  → Endpoints point to green pods                           │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Timeline Comparison

| Event | Rolling Update | Blue-Green |
|-------|----------------|------------|
| Start deployment | v1 pods begin terminating | v2 pods created in green |
| During deployment | Mixed traffic (v1 and v2) | All traffic on v1 (blue) |
| Duration | 5-15 minutes (gradual) | Seconds (instant switch) |
| Rollback time | Re-deploy (5-15 min) | Switch selector (<1 second) |
| Resource usage | Normal | 2x during transition |

---

## File Structure

```
latina_app/
├── frontend-service/
│   └── k8s/
│       ├── deployment.yaml              # Original deployment
│       ├── deployment-bluegreen.yaml    # Blue-green deployment (NEW)
│       └── service.yaml                # Service with slot selector (MODIFIED)
├── image-service/
│   └── k8s/
│       ├── deployment.yaml
│       ├── deployment-bluegreen.yaml    # (NEW)
│       └── service.yaml                # (MODIFIED)
├── phrase-service/
│   └── k8s/
│       ├── deployment.yaml
│       ├── deployment-bluegreen.yaml    # (NEW)
│       └── service.yaml                # (MODIFIED)
└── azure/
    ├── templates/
    │   ├── deploy.yaml                 # Original deployment template
    │   ├── deploy-bluegreen.yaml       # Blue-green template (NEW)
    │   └── rollback.yaml               # Rollback template (NEW)
    └── pipelines/
        ├── frontend-service.yml        # Original pipeline
        └── frontend-service-bluegreen.yml  # Blue-green pipeline (NEW)
```

---

## Prerequisites

### Kubernetes Requirements

1. **Services with Slot Selector**

   All services must include the `slot` label in their selector:

   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: frontend-service
   spec:
     selector:
       app: frontend-service
       slot: blue  # Required for blue-green
   ```

2. **Deployments with Slot Label**

   Deployment pods must have the `slot` label:

   ```yaml
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: frontend-service-blue  # or frontend-service-green
   spec:
     selector:
       matchLabels:
         app: frontend-service
         slot: blue
     template:
       metadata:
         labels:
           app: frontend-service
           slot: blue
   ```

3. **Readiness Probes**

   Required for health verification before traffic switch:

   ```yaml
   readinessProbe:
     httpGet:
       path: /actuator/health
       port: 8080
     initialDelaySeconds: 15
     periodSeconds: 10
     failureThreshold: 3
   ```

4. **Resource Quota**

   Namespace must allow 2x replicas during transition:

   ```yaml
   apiVersion: v1
   kind: ResourceQuota
   metadata:
     name: latina-quota
     namespace: prod
   spec:
     hard:
       requests.cpu: "2"      # Doubled for blue-green
       requests.memory: "2Gi" # Doubled for blue-green
       pods: "30"             # Allow for both slots
   ```

### Azure DevOps Requirements

1. **Service Connections**
   - `aks-service-connection`: Kubernetes service connection
   - `{env}-acr-service-connection`: Container registry connections

2. **Environments**
   - `dev`: Development environment
   - `testing`: Testing environment
   - `prod`: Production environment
   - `prod-rollback`: Production rollback environment

---

## Deployment Workflow

### Using the Pipeline

The blue-green pipeline is defined in `azure/pipelines/frontend-service-bluegreen.yml`:

```yaml
# Trigger on main branch changes to frontend-service
trigger:
  branches:
    include:
      - main
  paths:
    include:
      - frontend-service/**

# Pipeline stages
stages:
  - Build          # Build and push Docker image
  - DeployDev      # Deploy to dev (blue-green)
  - ApproveTesting # Manual approval gate
  - DeployTest     # Deploy to test (blue-green)
  - ApproveProd    # Manual approval gate
  - DeployProd     # Deploy to prod (blue-green)
  - RollbackProd   # Optional rollback stage
```

### Manual Deployment Commands

```bash
# 1. Check current active slot
kubectl get svc frontend-service -n prod -o jsonpath='{.spec.selector.slot}'
# Output: blue

# 2. Deploy to inactive slot (green)
kubectl apply -f frontend-service/k8s/deployment-bluegreen.yaml
# (with SLOT=green substituted)

# 3. Wait for rollout
kubectl rollout status deployment/frontend-service-green -n prod

# 4. Verify health
kubectl get pods -n prod -l app=frontend-service,slot=green
kubectl logs -n prod -l app=frontend-service,slot=green

# 5. Switch traffic
kubectl patch svc frontend-service -n prod \
  -p '{"spec":{"selector":{"slot":"green"}}}'

# 6. Verify traffic routing
kubectl get endpoints frontend-service -n prod
```

---

## Rollback Procedure

### Instant Rollback

When issues are detected after deployment, rollback is instantaneous:

```bash
# Current state: traffic on green, issues detected
# Rollback: switch back to blue

kubectl get svc frontend-service -n prod -o jsonpath='{.spec.selector.slot}'
# Output: green

# Instant rollback (no pod restarts)
kubectl patch svc frontend-service -n prod \
  -p '{"spec":{"selector":{"slot":"blue"}}}'

# Verify
kubectl get endpoints frontend-service -n prod
# Endpoints should now point to blue pods
```

### Using Azure DevOps Rollback Template

The rollback template (`azure/templates/rollback.yaml`) can be invoked:

1. **Manual Stage Trigger**: Navigate to the pipeline in Azure DevOps
2. **Run RollbackProd Stage**: Select the failed deployment run
3. **Approve Rollback**: Confirm the rollback operation

```yaml
# Rollback stage in pipeline
- stage: RollbackProd
  displayName: 'Rollback Production'
  jobs:
  - deployment: WaitForRollbackDecision
    environment: 'prod-rollback'
    steps:
    - task: ManualValidation@0
      inputs:
        instructions: 'This will instantly switch traffic back to the previous slot.'
  - template: rollback.yaml
    parameters:
      environmentName: 'prod'
      namespace: 'prod'
      aksServiceConnection: '$(aksServiceConnection)'
      serviceName: 'frontend-service'
```

### Rollback Timeline

| Step | Duration | Description |
|------|----------|-------------|
| Detect issue | Variable | Monitoring alerts or user reports |
| Decide rollback | < 1 min | Manual approval in Azure DevOps |
| Switch selector | < 1 sec | `kubectl patch` executes |
| Traffic routing | < 5 sec | Kubernetes updates endpoints |
| **Total** | **< 2 min** | From decision to restored service |

---

## Azure DevOps Pipeline

### Pipeline Stages

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        BLUE-GREEN PIPELINE                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────┐   ┌───────────┐   ┌──────────┐   ┌───────────┐          │
│  │  Build   │ → │ DeployDev  │ → │ Approve  │ → │ DeployTest│          │
│  │          │   │ (Blue-Green)│  │ Testing  │   │(Blue-Green)│          │
│  └──────────┘   └───────────┘   └──────────┘   └───────────┘          │
│        │              │                               │                  │
│        │              ↓                               ↓                  │
│        │         ┌────────┐                      ┌────────┐             │
│        │         │ Detect │                      │ Detect │             │
│        │         │ Active │                      │ Active │             │
│        │         │  Slot  │                      │  Slot  │             │
│        │         └────────┘                      └────────┘             │
│        │              │                               │                  │
│        │              ↓                               ↓                  │
│        │         ┌────────┐                      ┌────────┐             │
│        │         │ Deploy │                      │ Deploy │             │
│        │         │Inactive│                      │Inactive│             │
│        │         └────────┘                      └────────┘             │
│        │              │                               │                  │
│        │              ↓                               ↓                  │
│        │         ┌────────┐                      ┌────────┐             │
│        │         │ Switch │                      │ Switch │             │
│        │         │Traffic │                      │Traffic │             │
│        │         └────────┘                      └────────┘             │
│        │              │                               │                  │
│        ↓              ↓                               ↓                  │
│  ┌───────────┐   ┌──────────┐   ┌──────────┐   ┌───────────┐           │
│  │  Approve  │ → │ DeployProd│ → │ (Optional)│  │ Rollback  │           │
│  │   Prod    │   │(Blue-Green)│   │          │   │   Prod    │           │
│  └───────────┘   └──────────┘   └──────────┘   └───────────┘           │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Template Parameters

#### deploy-bluegreen.yaml

```yaml
parameters:
- name: environmentName    # Environment: dev, test, prod
- name: namespace           # Kubernetes namespace
- name: aksServiceConnection # Azure DevOps service connection
- name: targetAcrConnection # ACR service connection
- name: targetAcrLoginServer # ACR login server URL
- name: imageRepository     # Image name (e.g., frontend-service)
- name: imageTag            # Image tag (e.g., Build.BuildId)
- name: serviceName         # Service name (e.g., frontend-service)
- name: promoteImage        # Whether to promote image between ACRs
- name: imageUrl            # URL for dependent service (frontend only)
- name: phraseUrl           # URL for dependent service (frontend only)
```

#### rollback.yaml

```yaml
parameters:
- name: environmentName    # Environment to rollback
- name: namespace           # Kubernetes namespace
- name: aksServiceConnection # Azure DevOps service connection
- name: serviceName         # Service to rollback
```

---

## Kubernetes Resources

### Deployment Manifest (Blue-Green)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend-service-#{SLOT}#   # Parameterized: blue or green
  namespace: inspire
  labels:
    app: frontend-service
    slot: #{SLOT}#                   # Parameterized label
spec:
  replicas: 1
  selector:
    matchLabels:
      app: frontend-service
      slot: #{SLOT}#                 # Must match pod template
  template:
    metadata:
      labels:
        app: frontend-service
        slot: #{SLOT}#               # Pod label for selector
    spec:
      containers:
      - name: frontend-service
        image: <ACR_LOGIN_SERVER>/frontend-service:#{IMAGE_TAG}#
        ports:
        - containerPort: 8080
        readinessProbe:              # Critical for blue-green
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 15
          periodSeconds: 10
```

### Service Manifest

```yaml
apiVersion: v1
kind: Service
metadata:
  name: frontend-service        # Service name stays constant
  namespace: inspire
  labels:
    app: frontend-service
    slot: blue                  # Current active slot (informational)
spec:
  selector:
    app: frontend-service
    slot: blue                  # This gets patched to switch traffic
  ports:
  - name: http
    port: 80
    targetPort: 8080
  type: ClusterIP
```

### Key Points

1. **Deployment Name**: Includes slot suffix (`-blue` or `-green`)
2. **Service Name**: Constant (does not change)
3. **Service Selector**: Only the `slot` value changes during switch
4. **Both Deployments Exist**: After switch, both blue and green deployments remain

---

## Testing & Verification

### Pre-Deployment Verification

```bash
# Check current active slot
kubectl get svc frontend-service -n prod -o jsonpath='{.spec.selector.slot}'

# Check existing deployments
kubectl get deployments -n prod -l app=frontend-service

# Check resource quota
kubectl describe resourcequota -n prod
```

### During Deployment Monitoring

```bash
# Watch new deployment rollout
kubectl rollout status deployment/frontend-service-green -n prod --timeout=300s

# Monitor pod status
kubectl get pods -n prod -l app=frontend-service -w

# Check readiness probes
kubectl describe pod -n prod -l app=frontend-service,slot=green | grep -A5 "Readiness"
```

### Post-Switch Verification

```bash
# Verify service selector updated
kubectl get svc frontend-service -n prod -o yaml | grep -A5 "selector:"

# Check endpoints are populated
kubectl get endpoints frontend-service -n prod

# Verify endpoints point to new pods
kubectl get endpoints frontend-service -n prod -o jsonpath='{.subsets[0].addresses[*].ip}'
kubectl get pods -n prod -l app=frontend-service,slot=green -o jsonpath='{.items[*].status.podIP}'

# Test the application
curl -s http://<ingress-ip>/actuator/health | jq .
```

### Zero-Downtime Test Script

```bash
#!/bin/bash
# continuous-health-check.sh
# Run during deployment to verify zero-downtime

URL="http://<your-ingress-ip>/actuator/health"
FAILURES=0

echo "Starting continuous health check..."
echo "Press Ctrl+C to stop"

while true; do
  RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$URL")
  TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

  if [ "$RESPONSE" -eq 200 ]; then
    echo "[$TIMESTAMP] ✓ HTTP $RESPONSE"
  else
    echo "[$TIMESTAMP] ✗ HTTP $RESPONSE"
    ((FAILURES++))
    if [ $FAILURES -ge 3 ]; then
      echo "FAILURE: Too many errors, stopping"
      exit 1
    fi
  fi

  sleep 0.5
done
```

### Expected Output During Switch

```
[2024-01-15 14:30:00] ✓ HTTP 200
[2024-01-15 14:30:00] ✓ HTTP 200
[2024-01-15 14:30:01] ✓ HTTP 200
[2024-01-15 14:30:01] ✓ HTTP 200  ← Traffic switch occurs here
[2024-01-15 14:30:02] ✓ HTTP 200  ← No failed requests
[2024-01-15 14:30:02] ✓ HTTP 200
[2024-01-15 14:30:03] ✓ HTTP 200
```

---

## Troubleshooting

### Common Issues

#### 1. Endpoints Not Updating After Switch

**Symptom**: Service selector changed but endpoints still point to old pods

**Solution**:
```bash
# Check endpoint readiness
kubectl get endpoints frontend-service -n prod -o yaml

# Verify pod labels match selector
kubectl get pods -n prod -l app=frontend-service,slot=green -o wide

# Manual endpoint refresh (rarely needed)
kubectl patch svc frontend-service -n prod -p '{"spec":{"selector":{"slot":"green"}}}'
```

#### 2. Health Check Failures

**Symptom**: Pods not becoming ready after deployment

**Solution**:
```bash
# Check readiness probe status
kubectl describe pod -n prod -l app=frontend-service,slot=green

# View container logs
kubectl logs -n prod -l app=frontend-service,slot=green --tail=100

# Check application health endpoint
kubectl exec -n prod <pod-name> -- curl -s http://localhost:8080/actuator/health
```

#### 3. Resource Quota Exceeded

**Symptom**: Pods stuck in Pending state

**Solution**:
```bash
# Check quota usage
kubectl describe resourcequota -n prod

# Check pod resource requests
kubectl get pods -n prod -o custom-columns=NAME:.metadata.name,CPU:.spec.containers[*].resources.requests.cpu,MEMORY:.spec.containers[*].resources.requests.memory

# Delete old slot deployment to free resources
kubectl delete deployment/frontend-service-blue -n prod
```

#### 4. Rollback Fails - No Previous Deployment

**Symptom**: Cannot rollback because previous slot doesn't exist

**Solution**:
```bash
# Check available deployments
kubectl get deployments -n prod -l app=frontend-service

# If only one slot exists, rollback not possible
# Must re-deploy previous version or fix current deployment
```

### Debugging Commands

```bash
# Full service diagnostics
kubectl get all -n prod -l app=frontend-service

# Check service details
kubectl describe svc frontend-service -n prod

# Check deployment details
kubectl describe deployment frontend-service-blue -n prod
kubectl describe deployment frontend-service-green -n prod

# View events
kubectl get events -n prod --sort-by='.lastTimestamp' | grep frontend-service

# Check pod logs
kubectl logs -n prod -l app=frontend-service --tail=50

# Execute into pod for debugging
kubectl exec -it -n prod <pod-name> -- /bin/sh
```

---

## Lesson Plan Reference

### Hour 1: Concepts and Implementation

| Time | Topic | Activity |
|------|-------|----------|
| 0:00-0:15 | **Introduction to Blue-Green** | - What is blue-green deployment?<br>- Benefits: zero-downtime, instant rollback<br>- Trade-offs: resource cost, complexity |
| 0:15-0:35 | **Kubernetes Labels & Selectors** | - Hands-on: Examine current deployment<br>- Add slot label to deployment manifest<br>- Add slot to service selector |
| 0:35-0:45 | **Service Selector Switching** | - Demo: Deploy to inactive slot<br>- Demo: `kubectl patch service` to switch traffic<br>- Observe instant traffic redirect |
| 0:45-1:00 | **Health Checks** | - Demo: Readiness probes<br>- Watch pods become ready<br>- Verify endpoints update |

### Hour 2: Pipeline and Rollback

| Time | Topic | Activity |
|------|-------|----------|
| 1:00-1:20 | **Azure DevOps Pipeline** | - Walkthrough: Blue-green stages<br>- Approval gates between environments<br>- Slot detection logic |
| 1:20-1:35 | **Traffic Switch with Approval** | - Demo: Manual intervention before switch<br>- Verify new deployment health<br>- Approve traffic switch |
| 1:35-1:50 | **Rollback Demonstration** | - Live demo: Deploy broken version<br>- Observe failures in monitoring<br>- Execute instant rollback |
| 1:50-2:00 | **Hands-on Exercise** | - Students deploy broken version<br>- Identify issues<br>- Perform rollback |

### Lab Exercises

#### Exercise 1: Manual Blue-Green Deployment

```bash
# 1. Check current state
kubectl get svc,deployments -n dev -l app=frontend-service

# 2. Deploy to inactive slot
cat frontend-service/k8s/deployment-bluegreen.yaml | \
  sed 's/#{SLOT}#/green/g' | \
  sed 's/#{IMAGE_TAG}#/v1.2.0/g' | \
  kubectl apply -n dev -f -

# 3. Wait for health checks
kubectl rollout status deployment/frontend-service-green -n dev

# 4. Switch traffic
kubectl patch svc frontend-service -n dev -p '{"spec":{"selector":{"slot":"green"}}}'

# 5. Verify
kubectl get endpoints frontend-service -n dev
```

#### Exercise 2: Rollback Scenario

```bash
# 1. Deploy a "broken" version
# (Modify deployment to have failing health check)

# 2. Observe issues
kubectl logs -n dev -l app=frontend-service,slot=green --tail=20

# 3. Rollback instantly
kubectl patch svc frontend-service -n dev -p '{"spec":{"selector":{"slot":"blue"}}}'

# 4. Verify service restored
kubectl get endpoints frontend-service -n dev
curl http://<ingress>/actuator/health
```

---

## Summary

Blue-green deployment provides:

- **Zero-downtime releases** through parallel deployment slots
- **Instant rollback** by switching service selectors
- **Production testing** before traffic switch
- **Reduced deployment risk** with clear rollback path

Key implementation points:

1. Use `slot` label (`blue`/`green`) on deployments and service selectors
2. Deploy to inactive slot, verify health, then switch traffic
3. Keep previous deployment for instant rollback
4. Ensure ResourceQuota allows 2x replicas during transition
5. Use readiness probes for health verification

For questions or issues, refer to the troubleshooting section or contact the DevOps team.