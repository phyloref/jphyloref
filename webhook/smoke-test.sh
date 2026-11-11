#!/usr/bin/env bash
# Smoke-test a built Docker image the way the deployment calls it: start the image, POST
# a gzipped, Base64-encoded JSON-LD file to webhook's `reason` hook, signed with the
# X-Hub-Signature that hooks.json checks, and compare the response with what that file
# resolves to. This exercises start.sh, hooks.json, exec_jphyloref.sh, the webhook binary,
# the JRE and the shaded JAR together.
#
# Usage, from the root of the repository:
#   docker build -t jphyloref:test .
#   webhook/smoke-test.sh jphyloref:test
#
# Needs docker, curl, gzip, base64 and openssl. SMOKE_TEST_PORT picks the local port
# (default 18080). The test file has an inline @context, so it needs no network access.
set -euo pipefail

IMAGE=${1:?Usage: $0 IMAGE}
PORT=${SMOKE_TEST_PORT:-18080}
INPUT=src/test/resources/phylorefs/prereasoned.jsonld
# ELK infers that #node1, which is asserted to be in #phyloref0, is its only member.
EXPECTED='{"phylorefs":{"#phyloref0":["#node1"]}}'
# The secret in hooks.json.
SECRET=undefined

# The image defaults to MEMORY=16G (the deployment's -Xmx); the test file needs far less.
container=$(docker run -d --rm -e MEMORY=1G -p "127.0.0.1:$PORT:8080" "$IMAGE")
cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    echo "--- Container logs:"
    docker logs "$container" || true
  fi
  docker stop "$container" >/dev/null || true
  exit "$status"
}
trap cleanup EXIT

# Wait for webhook to answer HTTP requests at all.
for _ in $(seq 60); do
  if curl -s -o /dev/null "http://127.0.0.1:$PORT/"; then
    break
  fi
  sleep 1
done

body=$(printf '{"jsonldGzipped": "%s"}' "$(gzip -c "$INPUT" | base64 | tr -d '\n')")
signature=$(printf '%s' "$body" | openssl dgst -sha1 -hmac "$SECRET" | sed 's/^.* //')
response=$(curl -sS --fail-with-body -X POST \
  -H "Content-Type: application/json" \
  -H "X-Hub-Signature: sha1=$signature" \
  --data-binary "$body" \
  "http://127.0.0.1:$PORT/reason")

if [ "$(printf '%s' "$response" | tr -d '[:space:]')" != "$EXPECTED" ]; then
  echo "Expected: $EXPECTED"
  echo "Got:      $response"
  exit 1
fi
echo "OK: $response"
