# This Dockerfile creates a Docker image that can be used to run JPhyloRef.
# It is based on https://medium.com/@ramanamuttana/build-a-docker-image-using-maven-and-spring-boot-418e24c00776, which
# suggests a multi-stage build: the JAR and the webhook binary are built in their own
# images, and only the results are copied into the runtime image.
#
# Most of what a vulnerability scan finds in this image comes from three places, and each
# needs a different fix:
#   - OS packages from the runtime base image: rebuild the image, which picks up the
#     current eclipse-temurin base and then applies any newer Ubuntu security updates.
#   - Java libraries, one JAR per library under /app/lib: upgrade them in pom.xml (see
#     its <dependencyManagement> section).
#   - The webhook binary: Go links its standard library statically, so rebuild it with a
#     current Go (WEBHOOK_GO_VERSION below). apt-get upgrade cannot fix it.
# Rebuilding regularly fixes the first and third without any change to this file.

# The Go release used to build webhook (step 2). An ARG used in a FROM line has to be
# declared before the first FROM.
ARG WEBHOOK_GO_VERSION=1.27

# Step 1. Set up an image to build the JAR files.
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src

# Build the application using Maven. The docker profile (see pom.xml) builds a thin
# target/JPhyloRef.jar plus its runtime dependencies in target/lib, rather than the
# shaded JAR that the default build makes for command-line use.
RUN mvn -B -Pdocker clean package -DskipTests -Dmaven.javadoc.skip=true

# Step 2. Build webhook (https://github.com/adnanh/webhook) from source.
# Ubuntu's webhook package is compiled with the Go release that was current when that
# Ubuntu release was made (Go 1.22 for 24.04) and is not rebuilt when Go fixes its standard
# library, so scanners report every Go security fix since then against it. Building it
# here with a current Go avoids that. webhook's tags have no "v" prefix, so they are not
# valid Go module versions and `go install ...@2.8.3` will not resolve them; clone instead.
FROM golang:${WEBHOOK_GO_VERSION} AS webhook
ARG WEBHOOK_VERSION=2.8.3
RUN git clone --depth 1 --branch ${WEBHOOK_VERSION} https://github.com/adnanh/webhook.git /src/webhook
WORKDIR /src/webhook
# webhook 2.8.3 asks for chi v5.0.12 and x/sys v0.18.0, which have moderate advisories
# (chi: IP spoofing via X-Forwarded-For, an open redirect; x/sys: Windows-only). Drop
# these overrides once a webhook release asks for these versions or newer.
# webhook commits a vendor/ directory, which no longer matches go.mod after this, so
# build with -mod=mod to use the module cache instead.
RUN go get github.com/go-chi/chi/v5@v5.5.0 golang.org/x/sys@v0.50.0 && go mod tidy
RUN CGO_ENABLED=0 go build -mod=mod -trimpath -o /usr/local/bin/webhook .

# Step 3. Set up an image to run the built file in webserver mode.
FROM eclipse-temurin:25-jre

# Configuration for runner.
ARG APPDIR=/app
ARG DATADIR=/data
ARG PORT=8080
ARG MEMORY=16G

# These environmental variables will be used by start.sh and exec_jphyloref.sh.
ENV PORT=$PORT
ENV MEMORY=$MEMORY

# Apply any Ubuntu security updates published since the base image was built.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get -y upgrade \
    && rm -rf /var/lib/apt/lists/*

# Install webhook.
COPY --from=webhook /usr/local/bin/webhook /usr/local/bin/webhook

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

# Copy the necessary files into the image, least frequently changed first. The libraries
# get their own layer, so a release that changes only JPhyloRef's code leaves that layer's
# digest unchanged and deployments don't download the libraries again. JPhyloRef.jar comes
# last: each layer records /app's timestamp, so anything copied after it would get a new
# layer whenever it changed, even with the same contents. JPhyloRef.jar's manifest names
# each library on its Class-Path, so `java -jar` finds them in lib/ beside it.
COPY --from=build /build/target/lib ${APPDIR}/lib
COPY ./webhook/hooks.json ${APPDIR}/hooks.json
COPY ./webhook/start.sh ${APPDIR}/webhook-start.sh
COPY --chmod=755 ./webhook/exec_jphyloref.sh ${APPDIR}/exec_jphyloref.sh
COPY --from=build /build/target/JPhyloRef.jar ${APPDIR}/JPhyloRef.jar

EXPOSE ${PORT}/tcp
CMD ["/usr/bin/bash", "webhook-start.sh"]
