#!/usr/bin/env bash
# Smoke-test a JPhyloRef Docker image the way the Klados backend calls it: start the
# image, POST a gzipped, base64-encoded JSON-LD file to webhook's /reason endpoint
# (signed as hooks.json expects), and check the JSON that comes back.
#
# Usage: webhook/smoke-test.sh <image> [port]
#   Run from the repository root. Needs docker, curl, gzip, base64, openssl and jq.
#
# Environmental variables:
# - SECRET: the HMAC-SHA1 secret in hooks.json (defaults to "undefined", as in that file).
#
# The fixture's JSON-LD context is fetched from www.phyloref.org at parse time (see
# AGENTS.md), so this needs network access from inside the container.

set -euo pipefail

IMAGE="${1:?Usage: $0 <image> [port]}"
PORT="${2:-18080}"
SECRET="${SECRET:-undefined}"
FIXTURE=src/test/resources/phylorefs/dummy1.jsonld
EXPECTED='{"phylorefs":{"#phyloref0":["#phylogeny0_node2"]}}'

# Mount /data as a tmpfs owned by the image's user, as the Quadlet in
# klados-backend.container does with its U option; exec_jphyloref.sh writes there.
CONTAINER_UID=$(docker run --rm --entrypoint id "$IMAGE" -u)
CID=$(docker run -d -p "$PORT:8080" --tmpfs "/data:rw,uid=$CONTAINER_UID" "$IMAGE")
trap 'docker rm -f "$CID" > /dev/null' EXIT

# Wait for webhook to start listening.
for _ in $(seq 1 60); do
  curl -s -o /dev/null "http://localhost:$PORT/" && break
  sleep 1
done

PAYLOAD=$(printf '{"jsonldGzipped":"%s"}' "$(gzip -c "$FIXTURE" | base64 | tr -d '\n')")
SIGNATURE=$(printf '%s' "$PAYLOAD" | openssl dgst -sha1 -hmac "$SECRET" | awk '{print $NF}')

# start.sh sets an empty URL prefix, so the hook is at /reason rather than /hooks/reason.
RESPONSE=$(curl -sS -X POST "http://localhost:$PORT/reason" \
  -H 'Content-Type: application/json' \
  -H "X-Hub-Signature: sha1=$SIGNATURE" \
  --data-binary "$PAYLOAD")

if [ "$(jq -cS . <<< "$RESPONSE" 2> /dev/null)" == "$(jq -cS . <<< "$EXPECTED")" ]; then
  echo "OK: /reason returned $RESPONSE"
else
  echo "FAILED: /reason returned:"
  echo "$RESPONSE"
  echo "Expected: $EXPECTED"
  echo "Container logs:"
  docker logs "$CID" 2>&1 | tail -20
  exit 1
fi
