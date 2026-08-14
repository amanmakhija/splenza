# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# curl needed for the HEALTHCHECK below; libheif-tools provides heif-convert,
# used by HeicImageConverter to normalize HEIC/HEIF receipt photos to JPEG
# before they reach the vision API (which doesn't accept HEIC directly)
RUN apk add --no-cache curl libheif-tools

# Create a dedicated non-root user/group to run the app. Running containers
# as root is a common security finding - if the JVM or a dependency is ever
# compromised, a non-root process significantly limits what an attacker can
# do inside the container (can't install packages, can't modify system
# files, etc.)
RUN addgroup -g 1000 -S spring && adduser -u 1000 -S spring -G spring

COPY --from=build /app/target/*.jar app.jar

# Make sure the app directory and jar are owned by the non-root user, not root
RUN chown -R spring:spring /app

USER spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM flags for container awareness and predictable memory behavior:
# - MaxRAMPercentage instead of a hardcoded -Xmx: lets the JVM size its heap
#   as a percentage of whatever memory limit the container actually gets
#   (e.g. via `docker run -m 512m` or an ECS/K8s memory limit), rather than
#   a fixed number that's wrong the moment you resize the container.
# - UseContainerSupport: on by default in modern JDKs but explicit here for
#   clarity - makes the JVM read cgroup limits instead of host machine specs.
# - ExitOnOutOfMemoryError: if the JVM truly runs out of heap, kill the
#   process outright rather than limping along in a corrupted state -  your
#   orchestrator (Docker/ECS/K8s restart policy) will then restart it cleanly.
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "app.jar"]