# GitHub Actions Deep Dive - How Everything Works

## Table of Contents
1. [Workflow Triggers](#workflow-triggers)
2. [Environment Variables](#environment-variables)
3. [Jobs and Dependencies](#jobs-and-dependencies)
4. [Matrix Strategy](#matrix-strategy)
5. [Steps and Actions](#steps-and-actions)
6. [Secrets and Context](#secrets-and-context)
7. [Artifacts](#artifacts)
8. [How Testing Works](#how-testing-works)
9. [What Happens After Success](#what-happens-after-success)

---

## 1. Workflow Triggers

```yaml
on:
  push:
    branches:
      - main
```

**What this means**:
- **`on:`** = "when should this workflow run?"
- **`push:`** = "when code is pushed to GitHub"
- **`branches: - main`** = "only on the main branch"

**Other trigger options** (not used in our workflow, but good to know):
```yaml
on:
  pull_request:        # Runs on PRs
  schedule:            # Runs on a schedule (cron)
    - cron: '0 0 * * *'  # Daily at midnight
  workflow_dispatch:   # Manual trigger button in GitHub
```

**Why we chose `push` to `main`**:
- Automatically tests every change to main
- Ensures main branch always has working Docker images
- Fast feedback loop for developers

---

## 2. Environment Variables

```yaml
env:
  AWS_REGION: us-east-2
```

**What this does**:
- Defines variables available to ALL jobs in the workflow
- Reference them with `${{ env.AWS_REGION }}`

**DRY Principle** (Don't Repeat Yourself):
```yaml
# ❌ BAD - Repeating the region everywhere
run: aws ecr login --region us-east-2
run: docker push 123.dkr.ecr.us-east-2.amazonaws.com/service

# ✅ GOOD - Define once, use everywhere
env:
  AWS_REGION: us-east-2
run: aws ecr login --region ${{ env.AWS_REGION }}
```

**Scope Levels**:
```yaml
# Global (all jobs)
env:
  AWS_REGION: us-east-2

jobs:
  build:
    # Job level (only this job)
    env:
      BUILD_ENV: production

    steps:
      # Step level (only this step)
      - name: Build
        env:
          SPECIFIC_VAR: value
```

---

## 3. Jobs and Dependencies

### Job Definition

```yaml
jobs:
  test:
    name: Test ${{ matrix.service }}
    runs-on: ubuntu-latest
    steps: [...]
```

**Breaking it down**:
- **`test:`** = Job ID (used by other jobs to reference this one)
- **`name:`** = Human-readable name (shows in GitHub UI)
- **`runs-on:`** = What OS to use (ubuntu-latest = Ubuntu 22.04)
- **`steps:`** = List of commands to execute

### Job Dependencies

```yaml
jobs:
  test:
    # No dependencies - runs immediately

  build-jar:
    needs: test     # Waits for 'test' to finish

  build-and-push-docker:
    needs: build-jar  # Waits for 'build-jar' to finish
```

**Visual Flow**:
```
Start
  ↓
Test (runs immediately)
  ↓ (waits until complete)
Build JAR (runs after test succeeds)
  ↓ (waits until complete)
Build Docker (runs after build-jar succeeds)
  ↓
Done
```

**Why this matters**:
- **Quality Gates**: If tests fail, nothing else runs (saves time and money)
- **Sequential**: Ensures correct order (can't build Docker without JAR)
- **Fail Fast**: Stops immediately when something breaks

---

## 4. Matrix Strategy

### What is Matrix Strategy?

Instead of writing 3 separate jobs for 3 services, matrix strategy runs the SAME job 3 times with different values.

```yaml
strategy:
  fail-fast: false
  matrix:
    service:
      - order-service
      - user-service
      - notification-service
```

### How It Works

**Without Matrix** (repetitive):
```yaml
jobs:
  test-order:
    steps:
      - run: cd order-service && ./mvnw test

  test-user:
    steps:
      - run: cd user-service && ./mvnw test

  test-notification:
    steps:
      - run: cd notification-service && ./mvnw test
```

**With Matrix** (DRY):
```yaml
jobs:
  test:
    strategy:
      matrix:
        service: [order-service, user-service, notification-service]
    steps:
      - run: cd ${{ matrix.service }} && ./mvnw test
```

**Matrix Variables**:
- Access with `${{ matrix.service }}`
- GitHub creates 3 parallel jobs automatically
- Each job gets one value from the matrix

### Fail-Fast Behavior

```yaml
strategy:
  fail-fast: false  # ← This is important!
```

**With `fail-fast: true`** (default):
```
Job 1: order-service ✅
Job 2: user-service ❌ FAILED
Job 3: notification-service 🛑 CANCELLED (never ran)
```

**With `fail-fast: false`**:
```
Job 1: order-service ✅
Job 2: user-service ❌ FAILED
Job 3: notification-service ✅
```

**Why we use `fail-fast: false`**:
- See ALL failures in one run
- Better debugging (know which services are broken)
- Don't waste time running the workflow multiple times

---

## 5. Steps and Actions

### Two Types of Steps

#### Type 1: `uses` - Pre-built Actions

```yaml
- name: Checkout code
  uses: actions/checkout@v3
```

**What this does**:
- Downloads pre-written code from GitHub Marketplace
- `actions/checkout@v3` = Official action by GitHub
- `@v3` = Version 3 (pinned for stability)

**Common Actions**:
```yaml
# Clone your repository
uses: actions/checkout@v3

# Install Java
uses: actions/setup-java@v3
with:
  java-version: '21'

# Cache dependencies
uses: actions/cache@v3
with:
  path: ~/.m2/repository
  key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}

# Upload files
uses: actions/upload-artifact@v3
with:
  name: my-artifact
  path: target/*.jar
```

#### Type 2: `run` - Shell Commands

```yaml
- name: Build JAR
  run: |
    cd order-service
    chmod +x mvnw
    ./mvnw clean package -DskipTests
```

**What this does**:
- Runs bash commands directly
- `|` = Multi-line YAML string (allows multiple commands)
- Each line is a separate command

### Step Order Matters

```yaml
steps:
  # Step 1: Must checkout first (nothing exists otherwise)
  - uses: actions/checkout@v3

  # Step 2: Install Java (needed to run Maven)
  - uses: actions/setup-java@v3

  # Step 3: NOW we can run Maven commands
  - run: ./mvnw test
```

**Common Mistake**:
```yaml
# ❌ WRONG - Will fail!
steps:
  - run: ./mvnw test  # Can't run - code not checked out yet!
  - uses: actions/checkout@v3
```

---

## 6. Secrets and Context

### GitHub Secrets

Secrets are encrypted values stored in GitHub (NOT in code).

**Adding Secrets**:
1. Go to: `Settings → Secrets and variables → Actions`
2. Click: `New repository secret`
3. Add secret name and value

**Using Secrets in Workflow**:
```yaml
- name: Configure AWS credentials
  uses: aws-actions/configure-aws-credentials@v2
  with:
    aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
    aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
    aws-region: ${{ env.AWS_REGION }}
```

### Context Variables

GitHub provides built-in variables:

```yaml
${{ github.sha }}          # Git commit hash (abc123...)
${{ github.ref }}          # Branch name (refs/heads/main)
${{ github.actor }}        # User who triggered workflow
${{ github.repository }}   # Repo name (Dola26/OrderManagement-Microservice)
${{ runner.os }}           # OS (Linux, macOS, Windows)
${{ matrix.service }}      # Current matrix value
${{ env.AWS_REGION }}      # Environment variable
${{ secrets.AWS_ACCESS_KEY_ID }}  # Secret value
```

### Step Outputs

Steps can produce outputs for other steps:

```yaml
- name: Login to ECR
  id: login-ecr   # ← Give the step an ID
  uses: aws-actions/amazon-ecr-login@v1

- name: Build Docker
  env:
    ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}  # ← Use its output
  run: docker build -t $ECR_REGISTRY/service:latest .
```

---

## 7. Artifacts

Artifacts are files passed between jobs.

### Upload Artifact

```yaml
- name: Upload JAR artifact
  uses: actions/upload-artifact@v3
  with:
    name: order-service-jar      # ← Name to reference later
    path: order-service/target/*.jar
    retention-days: 1             # ← Auto-delete after 1 day
```

**What happens**:
1. GitHub zips the files
2. Stores them in GitHub's artifact storage
3. Makes them available to other jobs

### Download Artifact

```yaml
- name: Download JAR artifact
  uses: actions/download-artifact@v3
  with:
    name: order-service-jar       # ← Same name as upload
    path: order-service/target/   # ← Where to extract
```

### Why We Use Artifacts

**Problem**: Jobs run in separate environments (fresh VM each time)

**Without Artifacts**:
```
Job 1: Build JAR → JAR created
Job 2: Build Docker → ❌ JAR doesn't exist (different VM!)
```

**With Artifacts**:
```
Job 1: Build JAR → Upload JAR
Job 2: Download JAR → Build Docker ✅
```

**Benefits**:
- Avoid rebuilding (faster, consistent)
- Use exact same JAR that was tested
- Share data between jobs

---

## 8. How Testing Works

### Test Job Structure

```yaml
test:
  strategy:
    matrix:
      service: [order-service, user-service, notification-service]

  steps:
    - uses: actions/checkout@v3
    - uses: actions/setup-java@v3
      with:
        java-version: '21'

    - uses: actions/cache@v3
      with:
        path: ~/.m2/repository
        key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}

    - run: |
        cd ${{ matrix.service }}
        chmod +x mvnw
        ./mvnw test
```

### Step-by-Step Execution

**For `order-service`** (same for other services):

1. **Checkout** (`uses: actions/checkout@v3`)
   - Clones your repository
   - Creates directory structure:
     ```
     /home/runner/work/OrderManagement-Microservice/
     ├── order-service/
     ├── user-service/
     └── notification-service/
     ```

2. **Setup Java** (`uses: actions/setup-java@v3`)
   - Downloads and installs Java 21
   - Sets JAVA_HOME environment variable
   - Adds `java` to PATH

3. **Cache Maven** (`uses: actions/cache@v3`)
   - **First run**: No cache exists
     - Maven downloads ALL dependencies (~500MB)
     - GitHub caches `~/.m2/repository`
     - Takes ~3-5 minutes

   - **Subsequent runs**: Cache exists
     - Restores from cache (10-20 seconds)
     - Only downloads NEW dependencies
     - Takes ~30 seconds

   - **Cache key**: `Linux-maven-abc123...`
     - `runner.os` = Linux
     - `hashFiles('**/pom.xml')` = Hash of all pom.xml files
     - If pom.xml changes → Hash changes → Cache invalidated → Re-download

4. **Run Tests** (`./mvnw test`)
   ```bash
   cd order-service
   chmod +x mvnw           # Make Maven wrapper executable
   ./mvnw test             # Run JUnit tests
   ```

   **What Maven does**:
   ```
   [INFO] Scanning for projects...
   [INFO] Building order-service
   [INFO] Compiling 5 source files
   [INFO] Running tests...
   [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
   [INFO] BUILD SUCCESS
   ```

### Test Files Structure

Let's look at our test files:

```java
package com.dola.orderservice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
class OrderServiceTests {

    @Test
    void contextLoads() {
        // This test passes if Spring Boot can start
        // It loads all beans, configuration, database connections
    }

    @Test
    void basicAssertionTest() {
        String expected = "order-service";
        String actual = "order-service";
        assertEquals(expected, actual, "Service name should match");

        String serviceName = "order-service";
        assertNotNull(serviceName, "Service name should not be null");

        boolean isServiceActive = true;
        assertTrue(isServiceActive, "Service should be active");
    }
}
```

**How Tests Execute**:

1. **JUnit discovers tests**
   - Scans for `@Test` annotations
   - Finds 2 test methods

2. **Spring Boot starts** (`@SpringBootTest`)
   - Reads `src/test/resources/application.properties`
   - Configures H2 in-memory database
   - Excludes Kafka (not needed for tests)
   - Loads all Spring beans
   - If this fails → Tests fail

3. **Runs `contextLoads()`**
   - Empty test body
   - Success = Spring Boot started successfully
   - Validates: Configuration is valid, Beans can be created, No circular dependencies

4. **Runs `basicAssertionTest()`**
   - Executes each assertion
   - `assertEquals()` → Compares values
   - `assertNotNull()` → Checks not null
   - `assertTrue()` → Checks boolean
   - If ANY assertion fails → Test fails

### Test Configuration Files

**`src/test/resources/application.properties`**:
```properties
# Use H2 in-memory database (not PostgreSQL)
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver

# Disable Kafka (not needed for unit tests)
spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration

# Random port (avoid conflicts)
server.port=0
```

**Why this matters**:
- Tests run FAST (H2 is in-memory)
- Tests are ISOLATED (no external dependencies)
- Tests run ANYWHERE (no PostgreSQL/Kafka needed)
- CI/CD can run tests without infrastructure

---

## 9. What Happens After Success

### Success Flow

```
Push to main
  ↓
GitHub Actions triggered
  ↓
┌─────────────────────────┐
│ Job 1: Test (parallel)  │
│  ✓ order-service        │
│  ✓ user-service         │
│  ✓ notification-service │
└─────────────────────────┘
  ↓ All tests passed
┌─────────────────────────┐
│ Job 2: Build JAR        │
│  ✓ order-service.jar    │
│  ✓ user-service.jar     │
│  ✓ notification-service │
│  ✓ Upload artifacts     │
└─────────────────────────┘
  ↓ All JARs built
┌─────────────────────────┐
│ Job 3: Docker + ECR     │
│  ✓ Download artifacts   │
│  ✓ Build Docker images  │
│  ✓ Push to ECR          │
└─────────────────────────┘
  ↓
SUCCESS! 🎉
```

### What Gets Created in ECR

After successful pipeline:

```
AWS ECR Registry:
├── order-service/
│   ├── abc123... (commit SHA)
│   └── latest
├── user-service/
│   ├── abc123... (commit SHA)
│   └── latest
└── notification-service/
    ├── abc123... (commit SHA)
    └── latest
```

### Image Details

```bash
# Full image URI
279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:abc123...

# Breakdown:
279715468177                       # AWS Account ID
.dkr.ecr                           # ECR service
.us-east-2                         # Region
.amazonaws.com                     # AWS domain
/order-service                     # Repository name
:abc123...                         # Tag (commit SHA)
```

### What to Do After Success

#### Option 1: Verify in AWS Console

1. Go to AWS Console → ECR
2. Click on `order-service`
3. You should see 2 image tags:
   - `latest`
   - `abc123...` (your commit hash)

#### Option 2: Verify with AWS CLI

```bash
# List all images
aws ecr describe-images \
    --repository-name order-service \
    --region us-east-2

# Expected output:
{
  "imageDetails": [
    {
      "imageDigest": "sha256:abc123...",
      "imageTags": ["latest", "abc123..."],
      "imagePushedAt": "2024-01-31T21:00:00-06:00",
      "imageSizeInBytes": 157000000
    }
  ]
}
```

#### Option 3: Pull and Test Locally

```bash
# Login to ECR
aws ecr get-login-password --region us-east-2 | \
    docker login --username AWS --password-stdin \
    279715468177.dkr.ecr.us-east-2.amazonaws.com

# Pull the image
docker pull 279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Run it
docker run -d -p 8082:8082 \
    --name order-service-test \
    279715468177.dkr.ecr.us-east-2.amazonaws.com/order-service:latest

# Test health endpoint
curl http://localhost:8082/actuator/health
# Expected: {"status":"UP"}

# Check logs
docker logs order-service-test

# Stop and remove
docker stop order-service-test
docker rm order-service-test
```

---

## 10. GitHub Actions UI

### How to View Workflow Runs

1. **Go to Actions tab**:
   - https://github.com/Dola26/OrderManagement-Microservice/actions

2. **See all workflow runs**:
   ```
   CI/CD Pipeline
   ├── Run #5 - ✅ Success (3m 45s)
   ├── Run #4 - ❌ Failed (2m 10s)
   ├── Run #3 - ✅ Success (3m 50s)
   └── Run #2 - ✅ Success (4m 05s)
   ```

3. **Click on a run to see jobs**:
   ```
   Test
   ├── order-service ✅ (1m 20s)
   ├── user-service ✅ (1m 15s)
   └── notification-service ✅ (1m 25s)

   Build JAR
   ├── order-service ✅ (45s)
   ├── user-service ✅ (40s)
   └── notification-service ✅ (50s)

   Build & Push Docker
   ├── order-service ✅ (1m 30s)
   ├── user-service ✅ (1m 25s)
   └── notification-service ✅ (1m 35s)
   ```

4. **Click on a job to see logs**:
   ```
   > Set up job
   > Run actions/checkout@v3
   > Set up JDK 21
   > Cache Maven packages
     Cache restored from key: Linux-maven-abc123
   > Run tests for order-service
     [INFO] Running com.dola.orderservice.OrderServiceTests
     [INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
     [INFO] BUILD SUCCESS
   > Complete job
   ```

### Debugging Failed Workflows

When a test fails:

```
Test order-service ❌ Failed (1m 20s)
  ↓
Click to see logs
  ↓
[ERROR] Tests run: 3, Failures: 1, Errors: 0, Skipped: 0
[ERROR] Failures:
[ERROR]   OrderServiceTests.contextLoads:25
    Expected: Spring context to load
    But was: Failed to configure a DataSource
  ↓
Fix the issue in code
  ↓
Push to main again
  ↓
Workflow runs again
```

---

## 11. Common Patterns and Best Practices

### Pattern 1: Conditional Steps

```yaml
steps:
  - name: Run only on main branch
    if: github.ref == 'refs/heads/main'
    run: echo "This is main branch"

  - name: Run only on PRs
    if: github.event_name == 'pull_request'
    run: echo "This is a PR"
```

### Pattern 2: Reusable Workflows

Instead of duplicating YAML, use reusable workflows:

```yaml
# .github/workflows/test.yml
on:
  workflow_call:
    inputs:
      service:
        required: true
        type: string

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - run: cd ${{ inputs.service }} && ./mvnw test

# .github/workflows/main.yml
jobs:
  test-order:
    uses: ./.github/workflows/test.yml
    with:
      service: order-service
```

### Pattern 3: Job Outputs

Pass data between jobs:

```yaml
jobs:
  build:
    outputs:
      version: ${{ steps.get-version.outputs.version }}
    steps:
      - id: get-version
        run: echo "version=1.2.3" >> $GITHUB_OUTPUT

  deploy:
    needs: build
    steps:
      - run: echo "Deploying version ${{ needs.build.outputs.version }}"
```

---

## 12. Quick Reference

### Syntax Cheat Sheet

```yaml
# String
key: value

# Multi-line string
key: |
  line 1
  line 2

# List
key:
  - item1
  - item2

# Map
key:
  subkey: value

# Expression
key: ${{ expression }}

# Condition
if: expression

# Reference
${{ github.sha }}
${{ secrets.SECRET_NAME }}
${{ env.VAR_NAME }}
${{ matrix.value }}
```

### Common Actions

| Action | Purpose |
|--------|---------|
| `actions/checkout@v3` | Clone repository |
| `actions/setup-java@v3` | Install Java |
| `actions/cache@v3` | Cache dependencies |
| `actions/upload-artifact@v3` | Upload files |
| `actions/download-artifact@v3` | Download files |
| `aws-actions/configure-aws-credentials@v2` | Configure AWS |
| `aws-actions/amazon-ecr-login@v1` | Login to ECR |
| `docker/setup-buildx-action@v2` | Setup Docker Buildx |

### Exit Codes

- **0** = Success (continue to next step)
- **1-255** = Failure (stop workflow, mark as failed)

```bash
# Success
exit 0

# Failure
exit 1
```

---

## Summary

### Key Takeaways

1. **Workflows** = Automated processes triggered by events
2. **Jobs** = Groups of steps that run on same machine
3. **Steps** = Individual commands or actions
4. **Matrix** = Run same job multiple times with different values
5. **Artifacts** = Files passed between jobs
6. **Secrets** = Encrypted values for sensitive data
7. **Cache** = Speed up workflows by reusing dependencies

### Our Workflow in One Sentence

> "When code is pushed to main, GitHub Actions runs tests for all 3 services in parallel, then builds JARs in parallel, then builds Docker images and pushes them to AWS ECR in parallel."

### After Success Checklist

- ✅ View workflow run in GitHub Actions tab
- ✅ Verify images exist in AWS ECR
- ✅ Pull and test images locally
- ✅ Update Kubernetes deployments (Week 3)
- ✅ Monitor application performance
- ✅ Clean up old images (lifecycle policy)

---

**Need more details?** Check the official docs:
- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Workflow Syntax](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)
- [GitHub Actions Marketplace](https://github.com/marketplace?type=actions)
