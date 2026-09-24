#!/usr/bin/env bash
set -Eeuo pipefail

# Safe production deployment for the stateless Next.js owner/public booking console.
# Usage:
#   scripts/deploy-console-release.sh <release-tag>
#
# Invariants:
# - console image is versioned with the same release tag as the backend;
# - backend must already be healthy;
# - an existing Compose project name is reused to avoid container-name conflicts;
# - the new console must answer locally before the deploy is considered successful.

RELEASE_TAG="${1:?release tag required}"
RELEASE_DIR="/opt/troquim/releases/${RELEASE_TAG}"
COMPOSE_FILE="${RELEASE_DIR}/docker-compose.console.yml"
CONSOLE_CONTAINER="troquim-console"
NEW_IMAGE="troquim-console:${RELEASE_TAG}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[[ -f "${COMPOSE_FILE}" ]] || fail "console compose missing: ${COMPOSE_FILE}"
[[ -f "${RELEASE_DIR}/console/Dockerfile" ]] || fail "console Dockerfile missing"
docker inspect troquim-bot >/dev/null || fail "troquim-bot not found"

BACKEND_HEALTH="$(docker inspect troquim-bot --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')"
[[ "${BACKEND_HEALTH}" == "healthy" ]] || fail "backend is not healthy"

PROJECT="troquim-console"
CURRENT_IMAGE=""
if docker inspect "${CONSOLE_CONTAINER}" >/dev/null 2>&1; then
  CURRENT_IMAGE="$(docker inspect "${CONSOLE_CONTAINER}" --format '{{.Config.Image}}')"
  EXISTING_PROJECT="$(docker inspect "${CONSOLE_CONTAINER}" --format '{{ index .Config.Labels "com.docker.compose.project" }}' 2>/dev/null || true)"
  if [[ -n "${EXISTING_PROJECT}" && "${EXISTING_PROJECT}" != "<no value>" ]]; then
    PROJECT="${EXISTING_PROJECT}"
  fi
fi

echo "=== CONSOLE BUILD ==="
echo "Compose project: ${PROJECT}"
echo "Previous image: ${CURRENT_IMAGE:-none}"
echo "Target image: ${NEW_IMAGE}"

cd "${RELEASE_DIR}"
export TROQUIM_CONSOLE_IMAGE="${NEW_IMAGE}"

docker compose -p "${PROJECT}" -f "${COMPOSE_FILE}" build troquim-console
docker image inspect "${NEW_IMAGE}" >/dev/null || fail "console image build failed"

echo "=== CONSOLE DEPLOY ==="
docker compose -p "${PROJECT}" -f "${COMPOSE_FILE}" up -d --no-deps troquim-console

OK=0
for i in $(seq 1 40); do
  STATUS="$(docker inspect "${CONSOLE_CONTAINER}" --format '{{.State.Status}}' 2>/dev/null || true)"
  if [[ "${STATUS}" == "running" ]] && curl -fsS http://127.0.0.1:3001/login >/dev/null 2>&1; then
    OK=1
    break
  fi
  if [[ "${STATUS}" == "exited" || "${STATUS}" == "dead" ]]; then
    break
  fi
  sleep 3
done

if [[ "${OK}" != "1" ]]; then
  docker logs --tail 200 "${CONSOLE_CONTAINER}" 2>&1 || true
  fail "console did not become healthy"
fi

DEPLOYED_IMAGE="$(docker inspect "${CONSOLE_CONTAINER}" --format '{{.Config.Image}}')"
[[ "${DEPLOYED_IMAGE}" == "${NEW_IMAGE}" ]] || fail "unexpected console image: ${DEPLOYED_IMAGE}"

curl -fsS http://127.0.0.1:3001/login >/dev/null

echo "========================================="
echo "CONSOLE DEPLOY COMPLETED"
echo "Image: ${DEPLOYED_IMAGE}"
echo "Previous image: ${CURRENT_IMAGE:-none}"
echo "========================================="
