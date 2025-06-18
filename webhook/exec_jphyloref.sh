#!/usr/bin/env bash

# Environmental variables:
# - JARFILE: The JAR file to load (defaults to /app/JPhyloRef.jar)
# - MEMORY: The maximum memory to give Java via `-Xmx` (defaults to 16G)
# Webhook should set environmental variable $JSONLD_FILENAME (JSON-LD input as file)

# Set up proxy
#HTTP_PROXY_HOST= # TODO enter proxy server hostname here
#HTTP_PROXY_PORT= # TODO enter proxy server port here
#HTTPS_PROXY_HOST=$HTTP_PROXY_HOST
#HTTPS_PROXY_PORT=$HTTP_PROXY_PORT

# We first need to uncompress the input file.
mv "$JSONLD_FILENAME" "$JSONLD_FILENAME.gz" 2> /dev/null
gunzip "$JSONLD_FILENAME" 2> /dev/null

# Run it!
java -Xmx"${MEMORY:=16G}" -jar "${JARFILE:=/app/JPhyloRef.jar}" resolve "$JSONLD_FILENAME" -j --errors-as-json 2> /dev/null
