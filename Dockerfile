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

# Configuration for runner.
ARG APPDIR=/app
ARG DATADIR=/data
ARG PORT=8080
ARG MEMORY=16G

# These environmental variables will be used by start.sh and exec_jphyloref.sh.
ENV PORT=$PORT
ENV MEMORY=$MEMORY

# Install webhook.
RUN apt update
RUN apt install webhook

# Set the workdir.
WORKDIR ${APPDIR}

# Create a user account and switch to it.
RUN useradd --home ${APPDIR} nru

# Create the data directory with the right permissions (so we can mount an external volume here).
RUN mkdir ${DATADIR}
RUN chown nru ${DATADIR}
VOLUME ${DATADIR}

# Change to the nru user.
USER nru

# Copy the necessary files into the image.
COPY --from=build /build/target/JPhyloRef.jar ${APPDIR}/JPhyloRef.jar
COPY ./webhook/hooks.json ${APPDIR}/hooks.json
COPY ./webhook/start.sh ${APPDIR}/webhook-start.sh
COPY --chmod=755 ./webhook/exec_jphyloref.sh ${APPDIR}/exec_jphyloref.sh

EXPOSE ${PORT}/tcp
CMD ["/usr/bin/bash", "webhook-start.sh"]
