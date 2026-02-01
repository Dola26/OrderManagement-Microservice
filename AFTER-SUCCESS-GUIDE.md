# What to Do After GitHub Actions Success

This guide explains exactly what happens after your CI/CD pipeline succeeds and what changes you need to make.

---

## Table of Contents
1. [Understanding Success](#understanding-success)
2. [Immediate Actions](#immediate-actions)
3. [Verification Steps](#verification-steps)
4. [Using the Images](#using-the-images)
5. [Making Code Changes](#making-code-changes)
6. [Troubleshooting](#troubleshooting)

---

## 1. Understanding Success

### What "Success" Means

When GitHub Actions shows ✅ **Success**, it means:

```
✅ All tests passed (3 services)
✅ All JARs built successfully (3 services)
✅ All Docker images built (3 services)
✅ All images pushed to AWS ECR (3 services)
```

### What You Get

After a successful run, you have:

**In GitHub**:
- ✅ Green checkmark on your commit
- ✅ Detailed logs in Actions tab
- ✅ Artifacts (JARs) stored temporarily (1 day)

**In AWS ECR**:
- ✅ 3 repositories with images:
  ```
  order-service:latest
  order-service:abc123...

  user-service:latest
  user-service:def456...

  notification-service:latest
  notification-service:ghi789...
  ```

### Timeline of Events

```
You: git push
  ↓ (10 seconds)
GitHub: Workflow triggered
  ↓ (1-2 minutes)
Test Job: All tests pass ✅
  ↓ (30-45 seconds)
Build JAR Job: All JARs built ✅
  ↓ (1-2 minutes)
Docker Job: Images pushed to ECR ✅
  ↓
You receive: Email notification (if enabled)
```

---

## 2. Immediate Actions

### Step 1: Check GitHub Actions Tab

1. **Go to Actions page**:
   ```
   https://github.com/Dola26/OrderManagement-Microservice/actions
   ```

2. **Click on latest workflow run**

3. **Verify all jobs are green**:
   ```
   Test
   ├── Test order-service ✅
   ├── Test user-service ✅
   └── Test notification-service ✅

   Build JAR
   ├── Build JAR - order-service ✅
   ├── Build JAR - user-service ✅
   └── Build JAR - notification-service ✅

   Build & Push Docker
   ├── Docker Build & Push - order-service ✅
   ├── Docker Build & Push - user-service ✅
   └── Docker Build & Push - notification-service ✅
   ```

4. **Note the commit SHA**:
   - Example: `a3f5d9c8b2e1f4a6d7c9e0b1a2f3d4e5c6b7a8d9`
   - This is your image tag in ECR

### Step 2: Verify Images in AWS ECR

#### Option A: AWS Console

1. Login to AWS Console
2. Navigate to **ECR** service
3. Region: **us-east-2**
4. You should see 3 repositories:
   ```
   order-service
   user-service
   notification-service
   ```
5. Click on each repository
6. Verify 2 tags exist:
   - `latest`
   - `<commit-sha>`

#### Option B: AWS CLI

```bash
# List all repositories
aws ecr describe-repositories --region us-east-2 --output table

# Check order-service images
aws ecr list-images \
    --repository-name order-service \
    --region us-east-2 \
    --output table

# Expected output:
--------------------------------------------
|             ListImages                   |
+------------------------------------------+
||            imageIds                    ||
|+----------------+-----------------------+|
||  imageDigest   |      imageTag         ||
|+----------------+-----------------------+|
||  sha256:abc... |  latest               ||
||  sha256:abc... |  a3f5d9c8b2e1f4a6d... ||
|+----------------+-----------------------+|
```

---

## 3. Verification Steps

### Verify 1: Pull Image Locally

Test that you can pull and run the image:

```bash
# 1. Login to ECR
aws ecr get-login-password --region us-east-2 | \
    docker login --username AWS --password-stdin \
    279715468177.dkr.ecr.us-east-2.amazonaws.com

# 2. Pull the image
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# 3. Check image exists
docker images | grep order-service

# Expected output:
279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service   latest    abc123    2 minutes ago    157MB
```

### Verify 2: Run Container Locally

```bash
# Run order-service
docker run -d \
    --name order-test \
    -p 8082:8082 \
    279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Wait 30 seconds for startup
sleep 30

# Test health endpoint
curl http://localhost:8082/actuator/health

# Expected output:
{"status":"UP"}

# Check logs
docker logs order-test

# Expected log snippets:
# Started OrderServiceApplication in X.XX seconds
# Tomcat started on port 8082
```

### Verify 3: Test All Services

Run all three services together:

```bash
# Pull all images
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/user-service:latest
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/notification-service:latest

# Run all services (no dependencies - they'll fail to connect to PostgreSQL/Kafka, but we're just testing containers)
docker run -d --name order-test -p 8082:8082 \
    279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

docker run -d --name user-test -p 8081:8081 \
    279715468177.dkr.ecr.us-east-2.amazonaws.com/user-service:latest

docker run -d --name notification-test -p 8083:8083 \
    279715468177.dkr.ecr.us-east-2.amazonaws.com/notification-service:latest

# Check all are running
docker ps

# Clean up
docker stop order-test user-test notification-test
docker rm order-test user-test notification-test
```

### Verify 4: Image Size

Check that images are optimized:

```bash
docker images | grep -E "(order|user|notification)"

# Expected sizes (Alpine-based):
# order-service:        ~150-180 MB
# user-service:         ~150-180 MB
# notification-service: ~150-180 MB

# If sizes are > 300 MB, something is wrong (probably not using Alpine)
```

---

## 4. Using the Images

### Scenario 1: Local Development with Docker Compose

Create `docker-compose.dev.yml`:

```yaml
version: '3.8'

services:
  order-service:
    image: 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest
    ports:
      - "8082:8082"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/orderdb
    depends_on:
      - postgres

  user-service:
    image: 279715468177.dkr.ecr.us-east-2.amazonaws.com/user-service:latest
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/userdb
    depends_on:
      - postgres

  notification-service:
    image: 279715468177.dkr.ecr.us-east-2.amazonaws.com/notification-service:latest
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/notificationdb
    depends_on:
      - postgres
      - kafka

  postgres:
    image: postgres:15-alpine
    environment:
      - POSTGRES_PASSWORD=postgres
    ports:
      - "5432:5432"

  kafka:
    image: confluentinc/cp-kafka:latest
    environment:
      - KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181
    ports:
      - "9092:9092"

  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      - ZOOKEEPER_CLIENT_PORT=2181
```

**Run it**:
```bash
# Login to ECR first
aws ecr get-login-password --region us-east-2 | \
    docker login --username AWS --password-stdin \
    279715468177.dkr.ecr.us-east-2.amazonaws.com

# Start all services
docker-compose -f docker-compose.dev.yml up -d

# Check logs
docker-compose -f docker-compose.dev.yml logs -f
```

### Scenario 2: Deploy to Kubernetes (Week 3 Preview)

You'll create Kubernetes deployment files:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: order-service
        image: 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:a3f5d9c8b2e1f4a6d7c9e0b1a2f3d4e5c6b7a8d9
        ports:
        - containerPort: 8082
```

**Deploy**:
```bash
kubectl apply -f kubernetes-manifests/order-service-deployment.yml
```

### Scenario 3: Manual Testing

```bash
# Pull specific version (by commit SHA)
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:a3f5d9c

# Run it
docker run -p 8082:8082 \
    279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:a3f5d9c

# Test API endpoints
curl http://localhost:8082/actuator/health
curl http://localhost:8082/orders
```

---

## 5. Making Code Changes

### Workflow for Changes

```
1. Make code changes locally
   ↓
2. Test locally
   cd order-service
   ./mvnw test
   ↓
3. Commit and push
   git add .
   git commit -m "Fix bug in order processing"
   git push origin main
   ↓
4. GitHub Actions runs automatically
   ↓
5. New images pushed to ECR with new commit SHA
   ↓
6. Update Kubernetes to use new image (if deployed)
```

### Example: Fixing a Bug

**Step 1: Fix the code**
```java
// order-service/src/main/java/com/dola/orderservice/services/OrderService.java
public Order createOrder(OrderDTO dto) {
    // BUG FIX: Validate quantity is positive
    if (dto.getQuantity() <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }

    Order order = new Order();
    order.setQuantity(dto.getQuantity());
    return orderRepository.save(order);
}
```

**Step 2: Add test**
```java
// order-service/src/test/java/com/dola/orderservice/OrderServiceTests.java
@Test
void shouldRejectNegativeQuantity() {
    OrderDTO dto = new OrderDTO();
    dto.setQuantity(-5);

    assertThrows(IllegalArgumentException.class, () -> {
        orderService.createOrder(dto);
    });
}
```

**Step 3: Test locally**
```bash
cd order-service
./mvnw test

# Expected:
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Step 4: Commit and push**
```bash
git add .
git commit -m "Fix: Validate order quantity is positive

- Add validation in OrderService.createOrder()
- Add test case for negative quantity
- Fixes issue #123"

git push origin main
```

**Step 5: Wait for pipeline**
- Go to GitHub Actions
- Wait ~5-7 minutes
- Verify ✅ Success

**Step 6: New image is now in ECR**
```bash
# List images to see new tag
aws ecr list-images --repository-name order-service --region us-east-2

# Output will show NEW commit SHA:
# - latest (points to new commit)
# - b4c6d8e... (new commit SHA)
# - a3f5d9c... (old commit SHA - still there for rollback)
```

### Rollback if Needed

If the new version has issues:

```bash
# Option 1: Revert in Git
git revert HEAD
git push origin main
# This triggers new pipeline with reverted code

# Option 2: Use old Docker image
# In Kubernetes
kubectl set image deployment/order-service \
    order-service=279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:a3f5d9c
```

---

## 6. Troubleshooting

### Issue 1: Image Not in ECR

**Symptom**: Pipeline shows success, but image not in ECR

**Debug**:
1. Check GitHub Actions logs for Docker job
2. Look for errors in "Build and push Docker image" step
3. Common causes:
   - AWS credentials expired
   - ECR repository doesn't exist
   - Network timeout during push

**Fix**:
```bash
# Verify ECR repository exists
aws ecr describe-repositories \
    --repository-name order-service \
    --region us-east-2

# If not, create it
aws ecr create-repository \
    --repository-name order-service \
    --region us-east-2
```

### Issue 2: Image Won't Run Locally

**Symptom**: `docker run` fails with error

**Debug**:
```bash
# Check image exists
docker images | grep order-service

# Try running with detailed logs
docker run --rm \
    279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Common errors:
# - "No database connection" → Expected (PostgreSQL not running)
# - "Port already in use" → Stop other container: docker stop <name>
# - "JAR not found" → Build issue, check GitHub Actions logs
```

### Issue 3: Tests Pass Locally But Fail in CI/CD

**Causes**:
1. Environment differences (Java version, dependencies)
2. Test depends on local state
3. Race conditions in tests

**Fix**:
```bash
# Use same Java version
sdk use java 21.0.9-tem

# Clean and test
./mvnw clean test

# Check test isolation
./mvnw test -Dtest=OrderServiceTests#contextLoads
```

### Issue 4: Cache Issues

**Symptom**: Builds take 5+ minutes even though dependencies haven't changed

**Debug**:
1. Check cache status in GitHub Actions logs:
   ```
   Cache Maven packages
   Cache not found for input keys: Linux-maven-abc123...
   ```

2. Check if `pom.xml` changed (invalidates cache)

**Fix**:
```yaml
# In cicd.yml, ensure cache key is correct
- uses: actions/cache@v3
  with:
    path: ~/.m2/repository
    key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
```

---

## 7. Monitoring and Maintenance

### Monitor ECR Storage

```bash
# Check image sizes
aws ecr describe-images \
    --repository-name order-service \
    --region us-east-2 \
    --query 'imageDetails[*].[imagePushedAt,imageSizeInBytes,imageTags]' \
    --output table

# Delete old images (keep last 10)
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

### Set Up ECR Lifecycle Policy

Create `lifecycle-policy.json`:
```json
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep last 10 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 10
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
```

Apply it:
```bash
aws ecr put-lifecycle-policy \
    --repository-name order-service \
    --region us-east-2 \
    --lifecycle-policy-text file://lifecycle-policy.json
```

---

## 8. Next Steps (Week 3)

After verifying your images work, you're ready for:

### Kubernetes Deployment

1. **Create EKS cluster**
2. **Configure kubectl**
3. **Create Kubernetes manifests**:
   - Deployments
   - Services
   - Ingress
4. **Deploy services**
5. **Set up autoscaling**

### Continuous Deployment

Add to `cicd.yml`:
```yaml
deploy:
  needs: build-and-push-docker
  steps:
    - name: Deploy to Kubernetes
      run: |
        kubectl set image deployment/order-service \
          order-service=279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:${{ github.sha }}
```

---

## Summary Checklist

After pipeline success:

- ✅ Check GitHub Actions - all jobs green
- ✅ Verify images in AWS ECR Console
- ✅ Pull and test image locally
- ✅ Test container runs and responds
- ✅ Note commit SHA for deployments
- ✅ Set up ECR lifecycle policy
- ✅ Ready for Kubernetes deployment

---

## Quick Reference Commands

```bash
# Check latest workflow status
gh run list --limit 5

# View workflow logs
gh run view

# List ECR images
aws ecr list-images --repository-name order-service --region us-east-2

# Pull latest image
aws ecr get-login-password --region us-east-2 | docker login --username AWS --password-stdin 279715468177.dkr.ecr.us-east-2.amazonaws.com
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Run image locally
docker run -p 8082:8082 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Test health
curl http://localhost:8082/actuator/health
```

---

**You're all set!** Your images are in ECR and ready for deployment. Next week: Kubernetes! 🚀
