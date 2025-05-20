# This Dockerfile creates a Docker image that can be used to run JPhyloRef.
# It is based on https://medium.com/@ramanamuttana/build-a-docker-image-using-maven-and-spring-boot-418e24c00776, which
# suggests a two step process:

# Step 1. Set up an image to build the JAR files.
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src

# Build the application using Maven
RUN mvn clean package -DskipTests

# Step 2. Set up an image to run the built file in webserver mode.
FROM eclipse-temurin:21

# Set up a volume for storing temporary files.
VOLUME /data

# Configuration for runner.
ARG APPDIR=/app
ARG PORT=8080
ARG MEMORY=16G

# These environmental variables will be used by start.sh.
ENV PORT=$PORT
ENV MEMORY=$MEMORY

# Install webhook.
RUN apt update
RUN apt install webhook

# Set the workdir.
WORKDIR ${APPDIR}

# Create a user account and switch to it.
RUN useradd --home ${APPDIR} nru
USER nru

# Copy the necessary files into the image.
COPY --from=build /build/target/JPhyloRef.jar .
COPY ./webhook/hooks.json ./hooks.json
COPY ./webhook/start.sh ./webhook-start.sh
COPY ./webhook/exec_jphyloref.sh ./exec_jphyloref.sh

EXPOSE ${PORT}/tcp
CMD ["/usr/bin/bash", "webhook-start.sh"]
