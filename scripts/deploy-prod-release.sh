#!/usr/bin/env bash
set -Eeuo pipefail

# Safe, versioned production deploy for the Troquim backend.
# Usage:
#   ./scripts/deploy-prod-release.sh <release-tag> <expected-flyway-before> <expected-flyway-after>
# Example:
#   ./scripts/deploy-prod-release.sh a154e10 10 14
#
# Assumptions:
# - release checkout already exists at /opt/troquim/releases/<release-tag>
# - image troquim-bot:<release-tag> is already built locally
# - the running troquim-bot container belongs to the canonical Docker Compose project
# - /opt/troquim/.env (or equivalent Compose environment) still contains production secrets
#
# This script never prints secret values and never restarts postgres/redis.

RELEASE_TAG="${1:?release tag required, e.g. a154e10}"
EXPECTED_BEFORE="${2:?expected current Flyway version required, e.g. 10}"
EXPECTED_AFTER="${3:?expected target Flyway version required, e.g. 14}"

BOT_CONTAINER="troquim-bot"
POSTGRES_CONTAINER="troquim-postgres"
NEW_IMAGE="troquim-bot:${RELEASE_TAG}"
RELEASE_DIR="/opt/troquim/releases/${RELEASE_TAG}"
RELEASE_COMPOSE="${RELEASE_DIR}/docker-compose.release.yml"

TS="$(date +%Y%m%d-%H%M%S)"
BACKUP="/opt/troquim/backups/predeploy-${RELEASE_TAG}-${TS}.dump"
TMP_ARCHIVE="/tmp/predeploy-${RELEASE_TAG}-${TS}.dump"

cleanup() {
  docker exec "${POSTGRES_CONTAINER}" rm -f "${TMP_ARCHIVE}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

echo "=== 1/8 PRE-CHECK ==="

CURRENT_IMAGE="$(docker inspect "${BOT_CONTAINER}" --format '{{.Config.Image}}')"
CURRENT_HEALTH="$(docker inspect "${BOT_CONTAINER}" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')"
PROJECT="$(docker inspect "${BOT_CONTAINER}" --format '{{ index .Config.Labels "com.docker.compose.project" }}')"
WORKDIR="$(docker inspect "${BOT_CONTAINER}" --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}')"
CONFIG_CSV="$(docker inspect "${BOT_CONTAINER}" --format '{{ index .Config.Labels "com.docker.compose.project.config_files" }}')"

echo "Current image: ${CURRENT_IMAGE}"
echo "Current health: ${CURRENT_HEALTH}"
echo "Compose project: ${PROJECT}"

[[ "${CURRENT_HEALTH}" == "healthy" ]] || fail "current backend is not healthy"
[[ -n "${PROJECT}" ]] || fail "missing Docker Compose project label"
[[ -n "${WORKDIR}" ]] || fail "missing Docker Compose working_dir label"
[[ -n "${CONFIG_CSV}" ]] || fail "missing Docker Compose config_files label"

docker image inspect "${NEW_IMAGE}" >/dev/null || fail "image ${NEW_IMAGE} not found"
[[ -d "${RELEASE_DIR}" ]] || fail "release directory missing: ${RELEASE_DIR}"
[[ -f "${RELEASE_COMPOSE}" ]] || fail "release compose missing: ${RELEASE_COMPOSE}"

CURRENT_DROPLET="/opt/troquim/src/troquim-bot/docker-compose.droplet.yml"
RELEASE_DROPLET="${RELEASE_DIR}/docker-compose.droplet.yml"
[[ -f "${CURRENT_DROPLET}" ]] || fail "current droplet compose missing"
[[ -f "${RELEASE_DROPLET}" ]] || fail "release droplet compose missing"
cmp -s "${CURRENT_DROPLET}" "${RELEASE_DROPLET}" || fail "droplet compose differs from current production; review required"

IFS=',' read -r -a CURRENT_CONFIGS <<< "${CONFIG_CSV}"
COMPOSE_ARGS=()
RELEASE_REPLACED=0

for f in "${CURRENT_CONFIGS[@]}"; do
  if [[ "${f}" != /* ]]; then
    f="${WORKDIR}/${f}"
  fi

  if [[ "$(basename "${f}")" == "docker-compose.release.yml" ]]; then
    f="${RELEASE_COMPOSE}"
    RELEASE_REPLACED=$((RELEASE_REPLACED + 1))
  fi

  [[ -f "${f}" ]] || fail "compose file missing: ${f}"
  COMPOSE_ARGS+=( -f "${f}" )
done

[[ "${RELEASE_REPLACED}" -eq 1 ]] || fail "expected exactly one release compose in current project"

PGADMIN="$(docker exec "${POSTGRES_CONTAINER}" printenv POSTGRES_USER)"
PGDB="$(docker exec "${POSTGRES_CONTAINER}" sh -lc 'printf "%s" "${POSTGRES_DB:-$POSTGRES_USER}"')"

[[ -n "${PGADMIN}" ]] || fail "POSTGRES_USER is empty"
[[ -n "${PGDB}" ]] || fail "Postgres database name is empty"

CURRENT_FLYWAY="$(docker exec "${POSTGRES_CONTAINER}" psql -U "${PGADMIN}" -d "${PGDB}" -Atc   "select version from flyway_schema_history where success = true order by installed_rank desc limit 1;")"

echo "Flyway before: ${CURRENT_FLYWAY}"
[[ "${CURRENT_FLYWAY}" == "${EXPECTED_BEFORE}" ]] || fail "expected Flyway ${EXPECTED_BEFORE}, found ${CURRENT_FLYWAY}"

echo
echo "=== 2/8 FRESH DATABASE BACKUP ==="

sudo mkdir -p /opt/troquim/backups

docker exec "${POSTGRES_CONTAINER}" sh -lc   'pg_dump -U "$POSTGRES_USER" -d "${POSTGRES_DB:-$POSTGRES_USER}" -Fc'   | sudo tee "${BACKUP}" >/dev/null

sudo test -s "${BACKUP}" || fail "backup file is empty"

sudo docker cp "${BACKUP}" "${POSTGRES_CONTAINER}:${TMP_ARCHIVE}" >/dev/null
docker exec "${POSTGRES_CONTAINER}" pg_restore -l "${TMP_ARCHIVE}" >/dev/null

echo "Backup validated:"
sudo ls -lh "${BACKUP}"
sudo sha256sum "${BACKUP}"

echo
echo "=== 3/8 VALIDATE COMPOSE ==="

cd "${WORKDIR}"
export TROQUIM_RELEASE_IMAGE="${NEW_IMAGE}"
export TROQUIM_BOT_SRC="${RELEASE_DIR}"
export TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=false

docker compose -p "${PROJECT}" "${COMPOSE_ARGS[@]}" config --quiet
echo "Compose config OK."

echo
echo "=== 4/8 DEPLOY BACKEND ONLY ==="

docker compose -p "${PROJECT}" "${COMPOSE_ARGS[@]}"   up -d --no-deps --force-recreate --no-build "${BOT_CONTAINER}"

echo
echo "=== 5/8 WAIT FOR HEALTH ==="

OK=0
for i in $(seq 1 48); do
  STATUS="$(docker inspect "${BOT_CONTAINER}" --format '{{.State.Status}}' 2>/dev/null || true)"
  HEALTH="$(docker inspect "${BOT_CONTAINER}" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' 2>/dev/null || true)"

  echo "Attempt ${i}: status=${STATUS} health=${HEALTH}"

  if [[ "${HEALTH}" == "healthy" ]]; then
    OK=1
    break
  fi

  if [[ "${STATUS}" == "exited" || "${STATUS}" == "dead" ]]; then
    break
  fi

  sleep 5
done

if [[ "${OK}" -ne 1 ]]; then
  echo
  echo "DEPLOY DID NOT BECOME HEALTHY. Database is not restored automatically."
  echo "Relevant backend logs:"
  docker logs --tail 250 "${BOT_CONTAINER}" 2>&1     | grep -Ei 'error|exception|flyway|migration|failed|caused by|validate'     | tail -150 || true
  exit 1
fi

echo
echo "=== 6/8 VERIFY LOCAL APP + FLYWAY ==="

DEPLOYED_IMAGE="$(docker inspect "${BOT_CONTAINER}" --format '{{.Config.Image}}')"
echo "Deployed image: ${DEPLOYED_IMAGE}"
[[ "${DEPLOYED_IMAGE}" == "${NEW_IMAGE}" ]] || fail "unexpected deployed image: ${DEPLOYED_IMAGE}"

curl -fsS http://127.0.0.1:8080/actuator/health
echo

docker exec "${POSTGRES_CONTAINER}" psql -U "${PGADMIN}" -d "${PGDB}"   -c "select installed_rank, version, description, success from flyway_schema_history order by installed_rank;"

LAST_VERSION="$(docker exec "${POSTGRES_CONTAINER}" psql -U "${PGADMIN}" -d "${PGDB}" -Atc   "select version from flyway_schema_history where success = true order by installed_rank desc limit 1;")"

FAILED="$(docker exec "${POSTGRES_CONTAINER}" psql -U "${PGADMIN}" -d "${PGDB}" -Atc   "select count(*) from flyway_schema_history where success = false;")"

[[ "${LAST_VERSION}" == "${EXPECTED_AFTER}" ]] || fail "expected Flyway ${EXPECTED_AFTER}, found ${LAST_VERSION}"
[[ "${FAILED}" == "0" ]] || fail "Flyway has ${FAILED} failed migration(s)"

echo
echo "=== 7/8 VERIFY PUBLIC API ==="

curl -fsS https://api.troquim.app/actuator/health
echo

echo
echo "=== 8/8 FINAL STATE ==="

docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}' | grep -E 'NAMES|troquim'
echo
echo "========================================="
echo "DEPLOY COMPLETED"
echo "Image: ${NEW_IMAGE}"
echo "Flyway: ${LAST_VERSION}"
echo "Backup: ${BACKUP}"
echo "Previous image remains available locally: ${CURRENT_IMAGE}"
echo "========================================="
