# ===========================================================================================
# Dockerfile for Order Service - CI/CD Optimized
# ===========================================================================================
# WHY: Single-stage build using pre-built JAR from CI/CD pipeline
# This is faster than multi-stage builds because the JAR is already built and tested
# ===========================================================================================

# Base Image
# WHY: eclipse-temurin:21-jre-alpine provides Java 21 runtime in a minimal Alpine Linux image
# Alpine images are ~40MB vs ~200MB for standard images, reducing pull/push times
# JRE (not JDK) since we only need to run Java, not compile it
FROM eclipse-temurin:21-jre-alpine

# Metadata
# WHY: Labels help identify and manage images in registries
LABEL maintainer="order-service"
LABEL service="order-service"
LABEL version="1.0"

# Working Directory
# WHY: Set /app as the working directory for all subsequent commands
WORKDIR /app

# Copy JAR File
# WHY: Copy the pre-built JAR from the target/ directory
# In CI/CD, this JAR is downloaded from the build-jar job artifact
# Renamed to app.jar for consistency across all services
COPY target/order-service-0.0.1-SNAPSHOT.jar app.jar

# Expose Port
# WHY: Document that this service listens on port 8082
# This doesn't actually publish the port - it's documentation for developers
# Actual port mapping happens at runtime with -p or in Kubernetes
EXPOSE 8082

# Health Check (Optional but recommended)
# WHY: Allows Docker/Kubernetes to verify the service is healthy
# Checks if the application is responding on the actuator health endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# Container Entry Point
# WHY: Defines the command to run when container starts
# -XX:+UseContainerSupport ensures JVM respects container memory limits
# -Djava.security.egd=file:/dev/./urandom speeds up startup (SecureRandom)
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", \
            "app.jar"]

# ===========================================================================================
# Build Instructions for Local Testing:
# ===========================================================================================
# 1. Build JAR:
#    cd order-service
#    ./mvnw clean package -DskipTests
#
# 2. Build Docker image:
#    docker build -t order-service:local .
#
# 3. Run container:
#    docker run -p 8082:8082 --name order-service order-service:local
#
# 4. Test health:
#    curl http://localhost:8082/actuator/health
# ===========================================================================================
