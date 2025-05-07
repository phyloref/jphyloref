# This Dockerfile creates a Docker image that can be used to run JPhyloRef.
# It is based on https://medium.com/@ramanamuttana/build-a-docker-image-using-maven-and-spring-boot-418e24c00776, which
# suggests a two step process:

# Configuration.
ARG APPDIR=/app

# Step 1. Set up an image to build the JAR files.
FROM maven:eclipse-temurin-21 AS build
WORKDIR ${APPDIR}
COPY pom.xml .
COPY src ./src

# Build the application using Maven
RUN mvn clean package -DskipTests

# Step 2. Set up an image to store the built file.
FROM eclipse-temurin:21

WORKDIR ${APPDIR}

COPY --from=build ${APPDIR}/target/jphyloref.jar .
CMD ["java", "-jar", "jphyloref.jar"]
