# This Dockerfile creates a Docker image that can be used to run JPhyloRef.
# It is based on https://medium.com/@ramanamuttana/build-a-docker-image-using-maven-and-spring-boot-418e24c00776, which
# suggests a two step process:

# Step 1. Set up an image to build the JAR files.
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src

# Build the application using Maven
RUN mvn clean package -DskipTests -Djar.finalName=jphyloref

# Step 2. Set up an image to run the built file in webserver mode.
FROM eclipse-temurin:21

# Configuration for runner.
ARG APPDIR=/app
ARG PORT=8080

WORKDIR ${APPDIR}

COPY --from=build /build/target/JPhyloRef.jar .

EXPOSE ${PORT}/tcp
CMD ["java", "-jar", "jphyloref.jar", "webserver", "--host", "localhost", "--port", "${PORT}"]
