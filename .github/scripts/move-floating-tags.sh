#!/usr/bin/env bash
# Point the Docker image's floating tags at the releases they should name, as GitHub
# records them now:
#   - `latest` at the release GitHub marks as Latest;
#   - each `X.Y` at the newest full (non-prerelease, non-draft) release vX.Y.Z in that line.
# A floating tag is only moved to an image that has already been pushed under its full
# version; if that image isn't there yet, the run that pushes it will move the tag.
#
# Run by .github/workflows/docker.yaml after it pushes a release's X.Y.Z image, in a job
# that only one run at a time can be in. Because this works out the right state from
# GitHub's releases rather than from the run that called it, and updates every release
# line rather than only that run's, it gives the same result however runs overlap, finish
# out of order or are cancelled: a hand rebuild of an older release cannot move `latest` or
# `X.Y` back to it, and a run that GitHub cancels while it waits loses nothing, because
# the run that replaced it does the same work.
#
# Usage: move-floating-tags.sh IMAGE
#   IMAGE: the image name without a tag, e.g. ghcr.io/phyloref/jphyloref
# Needs `gh` (with GH_TOKEN and GITHUB_REPOSITORY set) and `docker buildx`, logged in to
# the registry.
set -euo pipefail

IMAGE=${1:?Usage: $0 IMAGE}
FULL_RELEASE='^v?[0-9]+\.[0-9]+\.[0-9]+$'

# point FLOATING RELEASE_TAG: point IMAGE:FLOATING at the image of RELEASE_TAG.
point() {
  local floating=$1 version=${2#v}
  if docker buildx imagetools inspect "$IMAGE:$version" >/dev/null 2>&1; then
    docker buildx imagetools create --tag "$IMAGE:$floating" "$IMAGE:$version"
    echo "$IMAGE:$floating now points at $IMAGE:$version."
  else
    echo "$IMAGE:$version has not been pushed, so $floating was left alone. If a run is pushing it, that run will move $floating."
  fi
}

latest=$(gh release view --repo "$GITHUB_REPOSITORY" --json tagName --jq .tagName)
if [[ "$latest" =~ $FULL_RELEASE ]]; then
  point latest "$latest"
else
  echo "::warning::The latest release, '$latest', is not tagged vX.Y.Z, so latest was left alone."
fi

# Full releases, without the "v", newest last within each line.
releases=$(
  gh release list --repo "$GITHUB_REPOSITORY" --exclude-drafts --exclude-pre-releases \
    --limit 1000 --json tagName --jq '.[].tagName' |
    grep -E "$FULL_RELEASE" |
    sed 's/^v//' |
    sort -V || true
)
for minor in $(echo "$releases" | cut -d. -f1,2 | sort -uV); do
  newest_in_minor=$(echo "$releases" | grep -E "^${minor//./\\.}\.[0-9]+$" | tail -n 1)
  point "$minor" "$newest_in_minor"
done
