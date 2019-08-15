#!/bin/bash

# Webhooks should set: $JSONLD_FILENAME (JSON-LD input as file)

# Settings
MEMORY=16G

# Configuration
JARFILE=jphyloref-0.2-resolve.jar
# How many jobs to spawn. This should always be 1!
NTASKS=1
CPUS_PER_TASK=4
# Timeout is in minutes.
TIMEOUT=2
# If SHARE is set to '-s' or '--share', we'll share a compute node with
# another job.
SHARE=-s

# Set up proxy
HTTP_PROXY_HOST= # TODO enter proxy server hostname here
HTTP_PROXY_PORT= # TODO enter proxy server port here
HTTPS_PROXY_HOST=$HTTP_PROXY_HOST
HTTPS_PROXY_PORT=$HTTP_PROXY_PORT

# Do it!
echo "{\"phylorefs\":"
srun $SHARE --ntasks=$NTASKS --cpus-per-task=$CPUS_PER_TASK --mem=$MEMORY -t $TIMEOUT java -Dhttp.proxyHost=$HTTP_PROXY_HOST -Dhttp.proxyPort=$HTTP_PROXY_PORT -Dhttps.proxyHost=$HTTPS_PROXY_HOST -Dhttps.proxyPort=$HTTP_PROXY_PORT -Xmx$MEMORY -jar $JARFILE resolve $JSONLD_FILENAME -j 2> /dev/null
echo "}"
