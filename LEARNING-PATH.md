# Your CI/CD Learning Path - Week 2

I've created comprehensive documentation to help you understand everything. Here's what to read and in what order:

---

## 📚 Reading Order

### 1️⃣ **Start Here: GITHUB-ACTIONS-EXPLAINED.md**
**Purpose**: Understand how GitHub Actions works from scratch

**What you'll learn**:
- How YAML syntax works
- What are jobs, steps, and workflows
- Matrix strategies (running same job multiple times)
- Secrets and environment variables
- How artifacts work (passing files between jobs)
- How caching speeds up builds
- Complete breakdown of our `cicd.yml` file

**Read this if**: You want to understand WHY each line exists in `cicd.yml`

**Time**: 20-30 minutes

---

### 2️⃣ **README-WEEK2.md**
**Purpose**: Setup guide - what you need to DO

**What you'll learn**:
- What changed from Week 1
- How to create AWS ECR repositories
- How to create IAM user with correct permissions
- How to add GitHub secrets (4 required)
- How to verify images in ECR
- Rollback procedures
- Cost optimization

**Read this if**: You want to SET UP the pipeline

**Time**: 15-20 minutes (reading) + 20-30 minutes (doing the setup)

---

### 3️⃣ **AFTER-SUCCESS-GUIDE.md**
**Purpose**: What to do after pipeline runs successfully

**What you'll learn**:
- How to verify success
- How to pull and test images locally
- How to use images in Docker Compose
- How to make code changes and trigger new builds
- How to rollback if something breaks
- Troubleshooting common issues

**Read this if**: Your pipeline succeeded and you want to know "now what?"

**Time**: 15-20 minutes

---

## 🎯 Quick Start (If You Just Want to Get It Running)

### Step 1: Setup AWS (Required)
```bash
# Create ECR repositories
aws ecr create-repository --repository-name order-service --region us-east-2
aws ecr create-repository --repository-name user-service --region us-east-2
aws ecr create-repository --repository-name notification-service --region us-east-2

# Create IAM user and get credentials
# (see README-WEEK2.md section "Create IAM User")
```

### Step 2: Add GitHub Secrets (Required)
Go to: https://github.com/Dola26/OrderManagement-Microservice/settings/secrets/actions

Add these 4 secrets:
- `AWS_ACCESS_KEY_ID` - Your IAM user access key
- `AWS_SECRET_ACCESS_KEY` - Your IAM user secret key  
- `AWS_REGION` - `us-east-2`
- `AWS_ACCOUNT_ID` - `279715468177`

### Step 3: Push Changes
```bash
cd ~/Documents/order-management-microservices
git add .
git commit -m "Add Week 2: Docker + ECR integration"
git push origin main
```

### Step 4: Watch Pipeline
1. Go to: https://github.com/Dola26/OrderManagement-Microservice/actions
2. Click on latest workflow run
3. Watch it run (takes ~5-7 minutes)
4. ✅ Success? Read AFTER-SUCCESS-GUIDE.md

---

## 📁 Files Created for Week 2

### CI/CD Pipeline
```
.github/workflows/
└── cicd.yml          (renamed from ci.yml)
    - Job 1: Test (parallel for 3 services)
    - Job 2: Build JAR (parallel for 3 services)
    - Job 3: Build & Push Docker (parallel for 3 services)
```

### Dockerfiles (Optimized)
```
order-service/Dockerfile
user-service/Dockerfile
notification-service/Dockerfile
    - Single-stage builds (uses pre-built JAR)
    - Alpine-based (smaller images)
    - Added EXPOSE statements
    - Added HEALTHCHECK
```

### Documentation
```
README-WEEK2.md                 - Setup and configuration guide
GITHUB-ACTIONS-EXPLAINED.md     - Deep dive into how it all works
AFTER-SUCCESS-GUIDE.md          - What to do after success
LEARNING-PATH.md               - This file (reading order)
```

---

## 🔍 Understanding the Workflow

### Visual Flow
```
Push to main
  ↓
GitHub detects push
  ↓
Reads .github/workflows/cicd.yml
  ↓
┌────────────────────────────────┐
│ Job 1: Test (matrix: 3)       │
│  - Checkout code               │
│  - Setup Java 21               │
│  - Cache Maven dependencies    │
│  - Run tests                   │
│                                │
│  Runs 3 times in parallel:     │
│  ✓ order-service              │
│  ✓ user-service               │
│  ✓ notification-service       │
└────────────────────────────────┘
  ↓ (all tests passed)
┌────────────────────────────────┐
│ Job 2: Build JAR (matrix: 3)  │
│  - Checkout code               │
│  - Setup Java 21               │
│  - Cache Maven dependencies    │
│  - Build JAR                   │
│  - Upload JAR as artifact      │
│                                │
│  Runs 3 times in parallel:     │
│  ✓ order-service.jar → upload │
│  ✓ user-service.jar → upload  │
│  ✓ notification-service.jar   │
└────────────────────────────────┘
  ↓ (all JARs built)
┌────────────────────────────────┐
│ Job 3: Docker (matrix: 3)     │
│  - Checkout code               │
│  - Download JAR artifact       │
│  - Configure AWS credentials   │
│  - Login to ECR                │
│  - Setup Docker Buildx         │
│  - Build Docker image          │
│  - Push to ECR (2 tags)        │
│                                │
│  Runs 3 times in parallel:     │
│  ✓ order-service → ECR        │
│  ✓ user-service → ECR         │
│  ✓ notification-service → ECR │
└────────────────────────────────┘
  ↓
✅ SUCCESS!
  ↓
Images in ECR:
  279715468177.dkr.ecr.us-east-2.amazonaws.com/
    ├── order-service:latest
    ├── order-service:abc123...
    ├── user-service:latest
    ├── user-service:def456...
    ├── notification-service:latest
    └── notification-service:ghi789...
```

### Key Concepts

**Matrix Strategy** = Run same job multiple times
```yaml
strategy:
  matrix:
    service: [order-service, user-service, notification-service]

# Creates 3 parallel jobs automatically
# Each job gets ${{ matrix.service }} variable
```

**Job Dependencies** = Control execution order
```yaml
test:           # Runs first
  
build-jar:      # Runs after test
  needs: test
  
docker:         # Runs after build-jar
  needs: build-jar
```

**Artifacts** = Pass files between jobs
```yaml
# Job 1: Upload
- uses: actions/upload-artifact@v3
  with:
    name: order-service-jar
    path: target/*.jar

# Job 2: Download
- uses: actions/download-artifact@v3
  with:
    name: order-service-jar
    path: target/
```

---

## 🧪 How Testing Works

### Test Files Location
```
order-service/
├── src/
│   ├── main/java/           (Production code)
│   └── test/java/           (Test code)
│       └── com/dola/orderservice/
│           └── OrderServiceTests.java
└── src/test/resources/
    └── application.properties  (Test configuration)
```

### What Happens When Tests Run

1. **GitHub Actions runs**: `./mvnw test`
2. **Maven discovers tests**: Finds all classes with `@Test`
3. **Spring Boot starts**: Reads `application.properties` (test version)
4. **Uses H2 database**: In-memory (not PostgreSQL)
5. **Disables Kafka**: Not needed for unit tests
6. **Runs each test method**:
   - `contextLoads()` - Verifies Spring Boot can start
   - `basicAssertionTest()` - Tests basic functionality
7. **Reports results**:
   ```
   Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
   BUILD SUCCESS
   ```

### Test Configuration

**Production** (`src/main/resources/application.properties`):
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/orderdb
spring.kafka.bootstrap-servers=localhost:9092
```

**Test** (`src/test/resources/application.properties`):
```properties
spring.datasource.url=jdbc:h2:mem:testdb       # In-memory DB
spring.autoconfigure.exclude=...KafkaAutoConfiguration  # No Kafka
server.port=0                                   # Random port
```

**Why this matters**:
- Tests run FAST (H2 in-memory)
- Tests are ISOLATED (no external dependencies)
- Tests run in CI/CD (no PostgreSQL/Kafka needed)

---

## 🔑 Key Takeaways

### What You Built
✅ Automated testing for 3 microservices  
✅ Automated JAR building  
✅ Automated Docker image creation  
✅ Automated push to AWS ECR  
✅ Parallel execution (saves time)  
✅ Quality gates (tests must pass before building)  

### What You Learned
✅ GitHub Actions workflow syntax  
✅ YAML structure and indentation  
✅ Matrix strategies for DRY code  
✅ Job dependencies and execution order  
✅ Artifacts for passing files between jobs  
✅ Secrets management  
✅ Docker image building and tagging  
✅ AWS ECR integration  

### What You Can Do Now
✅ Push code → Automatic testing  
✅ Push code → Automatic Docker builds  
✅ Push code → Images in ECR ready for deployment  
✅ Rollback to previous versions (commit SHAs)  
✅ Test changes locally before pushing  
✅ Debug pipeline failures  

---

## 📖 Additional Resources

### Official Documentation
- [GitHub Actions](https://docs.github.com/en/actions)
- [Workflow Syntax](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)
- [AWS ECR](https://docs.aws.amazon.com/ecr/)
- [Docker Buildx](https://docs.docker.com/buildx/working-with-buildx/)

### Our Documentation
- `cicd.yml` - Workflow file (commented extensively)
- `Dockerfile` - Image definitions (commented)
- `OrderServiceTests.java` - Example tests (commented)

---

## 🎓 Learning Checklist

- [ ] Read GITHUB-ACTIONS-EXPLAINED.md
- [ ] Understand matrix strategies
- [ ] Understand job dependencies
- [ ] Understand artifacts
- [ ] Set up AWS ECR repositories
- [ ] Create IAM user with correct permissions
- [ ] Add 4 GitHub secrets
- [ ] Push changes and watch pipeline run
- [ ] Verify images in ECR
- [ ] Pull and test image locally
- [ ] Read AFTER-SUCCESS-GUIDE.md
- [ ] Make a code change and watch pipeline run again
- [ ] Understand rollback procedures

---

## ❓ FAQ

**Q: Do I need to run `docker build` manually?**  
A: No! GitHub Actions does it automatically when you push to main.

**Q: Do tests run every time I push?**  
A: Yes, on every push to main branch.

**Q: What if tests fail?**  
A: The pipeline stops. Docker images are NOT built. Fix tests and push again.

**Q: How long does the pipeline take?**  
A: ~5-7 minutes (with caching). First run takes ~10 minutes.

**Q: Can I run the pipeline manually?**  
A: Yes! Actions tab → CI/CD Pipeline → Run workflow

**Q: What does the commit SHA tag mean?**  
A: It's your exact Git commit hash. Use it for rollbacks and deployments.

**Q: Why two tags (latest and SHA)?**  
A: `latest` = convenience, always points to newest. `SHA` = immutable, for rollbacks.

**Q: How much does ECR cost?**  
A: ~$0.10/GB/month storage. Your 3 images = ~$0.05/month.

**Q: Can I delete old images?**  
A: Yes! See AFTER-SUCCESS-GUIDE.md for lifecycle policies.

---

## 🚀 Next Week (Week 3 Preview)

- Create AWS EKS cluster
- Write Kubernetes manifests (Deployments, Services)
- Deploy Docker images to Kubernetes
- Set up Ingress for external access
- Configure autoscaling
- Add health checks and readiness probes

---

**Ready to start?** Read the files in order, set up AWS, and push your changes! 🎉
