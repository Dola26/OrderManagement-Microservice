# Week 2: Docker + AWS ECR Integration

## What Changed from Week 1

### Pipeline Enhancements
- **Renamed**: `ci.yml` → `cicd.yml` (reflects full CI/CD scope)
- **New Job**: `build-and-push-docker` - Builds Docker images and pushes to AWS ECR
- **Artifact Upload**: JAR files are now uploaded as artifacts between jobs
- **Docker Buildx**: Added for multi-platform builds (linux/amd64)

### Workflow Flow
```
Week 1: Test → Build JAR ✅
Week 2: Test → Build JAR → Build Docker → Push to ECR ✅
```

### Dockerfile Optimizations
- **Changed from**: Multi-stage builds (built JAR inside Docker)
- **Changed to**: Single-stage builds (use pre-built JAR from CI/CD)
- **Added**: EXPOSE statements for ports (8081, 8082, 8083)
- **Added**: HEALTHCHECK for container health monitoring
- **Changed to**: Alpine-based images (smaller size: ~150MB vs ~400MB)
- **Added**: JVM optimization flags for containers

### New Features
1. **Immutable Tags**: Each image tagged with git commit SHA (`${{ github.sha }}`)
2. **Latest Tag**: Convenience tag for development
3. **Parallel Builds**: All 3 services build Docker images simultaneously
4. **Platform Lock**: Images built for linux/amd64 (EKS compatibility)

---

## AWS Resources Setup

### Step 1: Create ECR Repositories

You need to create 3 ECR repositories in AWS (one per service).

#### Option A: Using AWS Console

1. Navigate to **ECR** in AWS Console
2. Click **Create repository**
3. Create these repositories:
   - `order-service`
   - `user-service`
   - `notification-service`
4. Region: **us-east-2** (Ohio)
5. Settings:
   - Tag immutability: **Disabled** (allows overwriting 'latest' tag)
   - Scan on push: **Enabled** (recommended for security)
   - KMS encryption: **Disabled** (use default AES-256)

#### Option B: Using AWS CLI

```bash
# Configure AWS CLI first
aws configure
# AWS Access Key ID: [your key]
# AWS Secret Access Key: [your secret]
# Default region: us-east-2
# Default output format: json

# Create repositories
aws ecr create-repository \
    --repository-name order-service \
    --region us-east-2

aws ecr create-repository \
    --repository-name user-service \
    --region us-east-2

aws ecr create-repository \
    --repository-name notification-service \
    --region us-east-2
```

#### Verify Repositories

```bash
aws ecr describe-repositories --region us-east-2 --output table
```

Expected output:
```
-------------------------------------------------------------------------
|                        DescribeRepositories                           |
+-----------------------------------------------------------------------+
||                           Repositories                              ||
|+------------------------------------+--------------------------------+|
||  repositoryName                    | repositoryUri                  ||
|+------------------------------------+--------------------------------+|
||  order-service                     | 279715468177.dkr.ecr.us-ea...  ||
||  user-service                      | 279715468177.dkr.ecr.us-ea...  ||
||  notification-service              | 279715468177.dkr.ecr.us-ea...  ||
|+------------------------------------+--------------------------------+|
```

---

### Step 2: Create IAM User for GitHub Actions

GitHub Actions needs AWS credentials to push images to ECR.

#### Create IAM User

1. Go to **IAM** → **Users** → **Create user**
2. Username: `github-actions-ecr-push`
3. **DO NOT** enable console access (programmatic access only)
4. Click **Next**

#### Attach Policies

Attach this custom inline policy for minimal permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:PutImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload"
      ],
      "Resource": [
        "arn:aws:ecr:us-east-2:279715468177:repository/order-service",
        "arn:aws:ecr:us-east-2:279715468177:repository/user-service",
        "arn:aws:ecr:us-east-2:279715468177:repository/notification-service"
      ]
    }
  ]
}
```

**Save the policy as**: `ECRPushPolicy`

#### Create Access Keys

1. Select the user → **Security credentials** tab
2. Click **Create access key**
3. Use case: **Third-party service**
4. Click **Create access key**
5. **IMPORTANT**: Save these credentials securely - you won't see them again!
   - Access Key ID: `AKIA...`
   - Secret Access Key: `wJalr...`

---

## GitHub Secrets Setup

Add these 4 secrets to your GitHub repository.

### Navigate to Secrets

1. Go to your repository: https://github.com/Dola26/OrderManagement-Microservice
2. Click **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**

### Add These Secrets

| Secret Name              | Value                                    | Example                    |
|--------------------------|------------------------------------------|----------------------------|
| `AWS_ACCESS_KEY_ID`      | Your IAM user access key ID              | `AKIAIOSFODNN7EXAMPLE`     |
| `AWS_SECRET_ACCESS_KEY`  | Your IAM user secret access key          | `wJalrXUtnFEMI/K7MDENG/...`|
| `AWS_REGION`             | AWS region                               | `us-east-2`                |
| `AWS_ACCOUNT_ID`         | Your AWS account ID (12 digits)          | `279715468177`             |

### How to Find Your AWS Account ID

```bash
aws sts get-caller-identity --query Account --output text
```

Or in AWS Console: Click your username → Account

---

## Testing the Pipeline

### Method 1: Push to Main Branch

```bash
cd ~/Documents/order-management-microservices

# Make a small change (e.g., update README)
echo "# Testing Week 2 Pipeline" >> test.md
git add .
git commit -m "Test Week 2: Docker + ECR integration"
git push origin main
```

### Method 2: Manual Workflow Trigger

1. Go to: https://github.com/Dola26/OrderManagement-Microservice/actions
2. Click **CI/CD Pipeline**
3. Click **Run workflow** → **Run workflow**

### Expected Pipeline Execution

```
┌─────────────────────────────────────────────┐
│ 1. Test (parallel)                          │
│    ✓ order-service tests                    │
│    ✓ user-service tests                     │
│    ✓ notification-service tests             │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│ 2. Build JAR (parallel)                     │
│    ✓ order-service JAR                      │
│    ✓ user-service JAR                       │
│    ✓ notification-service JAR               │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│ 3. Build & Push Docker (parallel)           │
│    ✓ order-service → ECR                    │
│    ✓ user-service → ECR                     │
│    ✓ notification-service → ECR             │
└─────────────────────────────────────────────┘
```

**Total Time**: ~5-7 minutes (parallel execution)

---

## Verifying Images in ECR

### Method 1: AWS Console

1. Navigate to **ECR** → **Repositories**
2. Click on a repository (e.g., `order-service`)
3. You should see images with tags:
   - `latest`
   - `abc123...` (git commit SHA)

### Method 2: AWS CLI

```bash
# List all images in order-service
aws ecr list-images \
    --repository-name order-service \
    --region us-east-2 \
    --output table

# Get detailed image info
aws ecr describe-images \
    --repository-name order-service \
    --region us-east-2 \
    --output json
```

### Method 3: Pull and Test Image Locally

```bash
# Login to ECR
aws ecr get-login-password --region us-east-2 | \
    docker login --username AWS --password-stdin \
    279715468177.dkr.ecr.us-east-2.amazonaws.com

# Pull the image
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Run it locally
docker run -p 8082:8082 \
    --name order-service-test \
    279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Test it
curl http://localhost:8082/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

---

## Pipeline Output Reference

After a successful run, you'll see output like this:

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📦 Service: order-service
🏷️  Tags:
   - a3f5d9c8b2e1f4a6d7c9e0b1a2f3d4e5c6b7a8d9
   - latest
📍 Registry: 279715468177.dkr.ecr.us-east-2.amazonaws.com
🔗 Image URI: 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:a3f5d9c8b2e1f4a6d7c9e0b1a2f3d4e5c6b7a8d9
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Rollback Procedures

### Scenario 1: Rollback to Previous Image

If the latest image has issues, rollback to a previous commit's image:

```bash
# 1. Find previous image tags
aws ecr list-images \
    --repository-name order-service \
    --region us-east-2 \
    --query 'imageIds[*].imageTag' \
    --output table

# 2. Re-tag the good image as 'latest'
# Get the manifest of the good image
aws ecr batch-get-image \
    --repository-name order-service \
    --region us-east-2 \
    --image-ids imageTag=<GOOD_COMMIT_SHA> \
    --query 'images[].imageManifest' \
    --output text > manifest.json

# Put it back as 'latest'
aws ecr put-image \
    --repository-name order-service \
    --region us-east-2 \
    --image-tag latest \
    --image-manifest file://manifest.json

# 3. Update Kubernetes deployment (if deployed)
kubectl set image deployment/order-service \
    order-service=279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:<GOOD_COMMIT_SHA>
```

### Scenario 2: Delete Bad Image

```bash
# Delete specific image by tag
aws ecr batch-delete-image \
    --repository-name order-service \
    --region us-east-2 \
    --image-ids imageTag=<BAD_COMMIT_SHA>
```

### Scenario 3: Rollback Code and Re-trigger Pipeline

```bash
# Revert the last commit
git revert HEAD

# Push to trigger new pipeline
git push origin main

# This will build and push a new 'latest' image with the reverted code
```

---

## Troubleshooting

### Issue 1: Authentication Error

**Error**: `Error response from daemon: Get https://279715468177.dkr.ecr.us-east-2.amazonaws.com/v2/: denied`

**Solution**:
1. Verify GitHub secrets are set correctly
2. Check IAM user has ECR permissions
3. Verify AWS account ID is correct (279715468177)

### Issue 2: No Such File or Directory (JAR)

**Error**: `COPY failed: file not found in build context`

**Solution**:
1. Ensure `build-jar` job completed successfully
2. Check `upload-artifact` and `download-artifact` steps
3. Verify JAR filename matches in Dockerfile COPY command

### Issue 3: Platform Mismatch

**Error**: `WARNING: The requested image's platform (linux/amd64) does not match`

**Solution**: This is just a warning if you're on ARM Mac. The image will still work on EKS (which is linux/amd64).

### Issue 4: Buildx Not Found

**Error**: `docker: 'buildx' is not a docker command`

**Solution**: Update Docker to latest version. Buildx is included in Docker 19.03+.

---

## Cost Considerations

### ECR Costs (us-east-2)
- **Storage**: $0.10 per GB/month
- **Data Transfer**: $0.09 per GB (egress)

### Estimated Costs for This Project
- **3 services** × **~150MB** = ~450MB total
- **Storage cost**: ~$0.05/month
- **Data transfer** (development): ~$0.50/month
- **Total**: < $1/month

### Cost Optimization Tips
1. **Delete old images**: Keep only last 10 tags
   ```bash
   # List images sorted by push date, delete old ones
   aws ecr describe-images \
       --repository-name order-service \
       --region us-east-2 \
       --query 'sort_by(imageDetails,&imagePushedAt)[:-10].[imageDigest]' \
       --output text | \
   xargs -I {} aws ecr batch-delete-image \
       --repository-name order-service \
       --region us-east-2 \
       --image-ids imageDigest={}
   ```

2. **Enable lifecycle policy**: Auto-delete images older than 30 days
   ```json
   {
     "rules": [{
       "rulePriority": 1,
       "description": "Keep last 10 images",
       "selection": {
         "tagStatus": "any",
         "countType": "imageCountMoreThan",
         "countNumber": 10
       },
       "action": { "type": "expire" }
     }]
   }
   ```

---

## Next Steps (Week 3 Preview)

- ✅ Week 1: CI/CD foundation (tests + JAR builds)
- ✅ Week 2: Docker + ECR integration
- ⬜ Week 3: Kubernetes deployment (EKS)
- ⬜ Week 4: Monitoring (Prometheus + Grafana)
- ⬜ Week 5: GitOps (ArgoCD)

---

## Quick Reference Commands

```bash
# Login to ECR
aws ecr get-login-password --region us-east-2 | docker login --username AWS --password-stdin 279715468177.dkr.ecr.us-east-2.amazonaws.com

# Pull latest image
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Run image locally
docker run -p 8082:8082 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Check image size
docker images | grep order-service

# List all ECR repositories
aws ecr describe-repositories --region us-east-2 --output table

# Delete repository (CAUTION!)
aws ecr delete-repository --repository-name order-service --region us-east-2 --force
```

---

## Resources

- [AWS ECR Documentation](https://docs.aws.amazon.com/ecr/)
- [Docker Buildx Documentation](https://docs.docker.com/buildx/working-with-buildx/)
- [GitHub Actions - AWS ECR](https://github.com/aws-actions/amazon-ecr-login)
- [GitHub Actions - Configure AWS Credentials](https://github.com/aws-actions/configure-aws-credentials)

---

**Questions?** Check the GitHub Actions logs for detailed error messages and stack traces.
