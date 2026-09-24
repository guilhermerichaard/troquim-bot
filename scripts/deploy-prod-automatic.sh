#!/usr/bin/env bash
set -Eeuo pipefail

# Automatic, idempotent production deploy invoked remotely through AWS SSM.
#
# Usage:
#   scripts/deploy-prod-automatic.sh <full-git-sha>
#
# Invariants:
# - exact immutable GitHub SHA is deployed;
# - production must be healthy before changes;
# - a fresh pg_dump is validated;
# - the dump is restored into temporary PostgreSQL first;
# - the new image must boot and migrate the temporary copy successfully;
# - only then does the canonical production deploy script touch production;
# - postgres/redis are never restarted by this script;
# - secrets are inherited from the running production container and never printed.

SHA="${1:?full git SHA required}"
[[ "${SHA}" =~ ^[0-9a-f]{40}$ ]] || { echo "ERROR: invalid git SHA" >&2; exit 2; }

if [[ "${EUID}" -ne 0 ]]; then
  exec sudo -E "$0" "$@"
fi

TAG="${SHA:0:7}"
REPO="https://github.com/guilhermerichaard/troquim-bot"
RELEASE_DIR="/opt/troquim/releases/${TAG}"

BOT="troquim-bot"
PG="troquim-postgres"

TMP_PG="troquim-preflight-pg-${TAG}"
TMP_APP="troquim-preflight-app-${TAG}"
TMP_PORT="18081"
TMP_ENV="/tmp/troquim-preflight-${TAG}.env"
TMP_DUMP="/tmp/troquim-preflight-${TAG}.dump"

cleanup() {
  docker rm -f "${TMP_APP}" >/dev/null 2>&1 || true
  docker rm -f "${TMP_PG}" >/dev/null 2>&1 || true
  rm -f "${TMP_ENV}" "${TMP_DUMP}"
}
trap cleanup EXIT

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

echo "=== AUTOMATIC DEPLOY ${TAG} ==="

docker inspect "${BOT}" >/dev/null || fail "${BOT} not found"
docker inspect "${PG}" >/dev/null || fail "${PG} not found"

CURRENT_HEALTH="$(docker inspect "${BOT}" --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}')"
CURRENT_IMAGE="$(docker inspect "${BOT}" --format '{{.Config.Image}}')"

[[ "${CURRENT_HEALTH}" == "healthy" ]] || fail "current backend is not healthy"

PGUSER="$(docker exec "${PG}" printenv POSTGRES_USER)"
PGDB="$(docker exec "${PG}" sh -lc 'printf "%s" "${POSTGRES_DB:-$POSTGRES_USER}"')"
PGPASS="$(docker exec "${PG}" printenv POSTGRES_PASSWORD)"

[[ -n "${PGUSER}" ]] || fail "POSTGRES_USER is empty"
[[ -n "${PGDB}" ]] || fail "POSTGRES_DB is empty"
[[ -n "${PGPASS}" ]] || fail "POSTGRES_PASSWORD is empty"

BEFORE="$(docker exec "${PG}" psql -U "${PGUSER}" -d "${PGDB}" -Atc   "select version from flyway_schema_history where success = true order by installed_rank desc limit 1;")"
[[ -n "${BEFORE}" ]] || fail "could not determine current Flyway version"

rm -rf "${RELEASE_DIR}"
mkdir -p "${RELEASE_DIR}"

curl -fsSL "${REPO}/archive/${SHA}.tar.gz"   | tar -xz -C "${RELEASE_DIR}" --strip-components=1

test -f "${RELEASE_DIR}/Dockerfile" || fail "release Dockerfile missing"
test -f "${RELEASE_DIR}/scripts/deploy-prod-release.sh" || fail "canonical deploy script missing"

TARGET="$(
  find "${RELEASE_DIR}/src/main/resources/db/migration"     -maxdepth 1 -type f -name 'V[0-9]*__*.sql' -printf '%f\n'   | sed -nE 's/^V([0-9]+)__.*/\1/p'   | sort -n   | tail -1
)"
[[ "${TARGET}" =~ ^[0-9]+$ ]] || fail "could not determine target Flyway version"

echo "Current image: ${CURRENT_IMAGE}"
echo "Flyway: ${BEFORE} -> ${TARGET}"

if [[ "${CURRENT_IMAGE}" == "troquim-bot:${TAG}" && "${BEFORE}" == "${TARGET}" ]]; then
  curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null
  curl -fsS https://api.troquim.app/actuator/health >/dev/null

  CURRENT_CONSOLE_IMAGE="$(docker inspect troquim-console --format '{{.Config.Image}}' 2>/dev/null || true)"
  if [[ "${CURRENT_CONSOLE_IMAGE}" == "troquim-console:${TAG}" ]] &&
     curl -fsS http://127.0.0.1:3001/login >/dev/null 2>&1; then
    echo "Release ${TAG} is already fully deployed and healthy."
    exit 0
  fi

  echo "Backend ${TAG} is already healthy; recovering console only."
  chmod +x "${RELEASE_DIR}/scripts/deploy-console-release.sh"
  "${RELEASE_DIR}/scripts/deploy-console-release.sh" "${TAG}"
  RECOVERED_CONSOLE_IMAGE="$(docker inspect troquim-console --format '{{.Config.Image}}')"
  [[ "${RECOVERED_CONSOLE_IMAGE}" == "troquim-console:${TAG}" ]] ||
    fail "console-only recovery produced unexpected image: ${RECOVERED_CONSOLE_IMAGE}"
  echo "Release ${TAG} console recovery succeeded."
  exit 0
fi

echo "=== BUILD ==="
docker build -t "troquim-bot:${TAG}" "${RELEASE_DIR}"
docker image inspect "troquim-bot:${TAG}" >/dev/null || fail "image build failed"

echo "=== PREFLIGHT BACKUP ==="
docker exec "${PG}" sh -lc   'pg_dump -U "$POSTGRES_USER" -d "${POSTGRES_DB:-$POSTGRES_USER}" -Fc'   > "${TMP_DUMP}"
test -s "${TMP_DUMP}" || fail "preflight dump is empty"

docker cp "${TMP_DUMP}" "${PG}:/tmp/preflight.dump" >/dev/null
docker exec "${PG}" pg_restore -l /tmp/preflight.dump >/dev/null
docker exec "${PG}" rm -f /tmp/preflight.dump

NETWORK="$(
  docker inspect "${BOT}"     --format '{{range $name,$cfg := .NetworkSettings.Networks}}{{println $name}}{{end}}'   | head -n 1
)"
[[ -n "${NETWORK}" ]] || fail "Docker network not found"

echo "=== PREFLIGHT TEMPORARY DATABASE ==="
docker run -d   --name "${TMP_PG}"   --network "${NETWORK}"   -e POSTGRES_USER="${PGUSER}"   -e POSTGRES_PASSWORD="${PGPASS}"   -e POSTGRES_DB="${PGDB}"   postgres:16-alpine >/dev/null

READY=0
for _ in $(seq 1 30); do
  if docker exec "${TMP_PG}" pg_isready -U "${PGUSER}" -d "${PGDB}" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 2
done
[[ "${READY}" == "1" ]] || fail "temporary PostgreSQL did not become ready"

docker cp "${TMP_DUMP}" "${TMP_PG}:/tmp/preflight.dump" >/dev/null
docker exec "${TMP_PG}" pg_restore   -U "${PGUSER}"   -d "${PGDB}"   --clean   --if-exists   /tmp/preflight.dump >/dev/null

echo "=== PREFLIGHT TEMPORARY APP ==="
docker inspect "${BOT}" --format '{{range .Config.Env}}{{println .}}{{end}}' > "${TMP_ENV}"
chmod 600 "${TMP_ENV}"

docker run -d   --name "${TMP_APP}"   --network "${NETWORK}"   --env-file "${TMP_ENV}"   -e SPRING_DATASOURCE_URL="jdbc:postgresql://${TMP_PG}:5432/${PGDB}"   -e SPRING_DATASOURCE_USERNAME="${PGUSER}"   -e SPRING_DATASOURCE_PASSWORD="${PGPASS}"   -e TROQUIM_FLYWAY_BASELINE_ON_MIGRATE=false   -p "127.0.0.1:${TMP_PORT}:8080"   "troquim-bot:${TAG}" >/dev/null

UP=0
for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${TMP_PORT}/actuator/health" 2>/dev/null       | grep -q '"status":"UP"'; then
    UP=1
    break
  fi

  STATUS="$(docker inspect "${TMP_APP}" --format '{{.State.Status}}' 2>/dev/null || true)"
  [[ "${STATUS}" != "exited" && "${STATUS}" != "dead" ]] || break
  sleep 3
done

if [[ "${UP}" != "1" ]]; then
  docker logs --tail 250 "${TMP_APP}" 2>&1 || true
  fail "new image did not become healthy against temporary database"
fi

TEST_VERSION="$(docker exec "${TMP_PG}" psql -U "${PGUSER}" -d "${PGDB}" -Atc   "select version from flyway_schema_history where success=true order by installed_rank desc limit 1;")"
TEST_FAILED="$(docker exec "${TMP_PG}" psql -U "${PGUSER}" -d "${PGDB}" -Atc   "select count(*) from flyway_schema_history where success=false;")"

[[ "${TEST_VERSION}" == "${TARGET}" ]]   || fail "temporary database ended at Flyway ${TEST_VERSION}; expected ${TARGET}"
[[ "${TEST_FAILED}" == "0" ]]   || fail "temporary database contains ${TEST_FAILED} failed migration(s)"

echo "Preflight approved."

docker rm -f "${TMP_APP}" >/dev/null
docker rm -f "${TMP_PG}" >/dev/null
rm -f "${TMP_ENV}" "${TMP_DUMP}"

echo "=== CANONICAL BACKEND PRODUCTION DEPLOY ==="
chmod +x "${RELEASE_DIR}/scripts/deploy-prod-release.sh"
cd "${RELEASE_DIR}"
./scripts/deploy-prod-release.sh "${TAG}" "${BEFORE}" "${TARGET}"

echo "=== CANONICAL CONSOLE PRODUCTION DEPLOY ==="
chmod +x "${RELEASE_DIR}/scripts/deploy-console-release.sh"
./scripts/deploy-console-release.sh "${TAG}"

FINAL_IMAGE="$(docker inspect "${BOT}" --format '{{.Config.Image}}')"
FINAL_FLYWAY="$(docker exec "${PG}" psql -U "${PGUSER}" -d "${PGDB}" -Atc   "select version from flyway_schema_history where success=true order by installed_rank desc limit 1;")"
FAILED="$(docker exec "${PG}" psql -U "${PGUSER}" -d "${PGDB}" -Atc   "select count(*) from flyway_schema_history where success=false;")"

curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null
curl -fsS https://api.troquim.app/actuator/health >/dev/null
curl -fsS http://127.0.0.1:3001/login >/dev/null
CONSOLE_IMAGE="$(docker inspect troquim-console --format '{{.Config.Image}}')"

[[ "${FINAL_IMAGE}" == "troquim-bot:${TAG}" ]] || fail "unexpected final image: ${FINAL_IMAGE}"
[[ "${FINAL_FLYWAY}" == "${TARGET}" ]] || fail "unexpected final Flyway: ${FINAL_FLYWAY}"
[[ "${FAILED}" == "0" ]] || fail "production has ${FAILED} failed migration(s)"
[[ "${CONSOLE_IMAGE}" == "troquim-console:${TAG}" ]] || fail "unexpected final console image: ${CONSOLE_IMAGE}"

echo "========================================="
echo "AUTOMATIC DEPLOY SUCCEEDED"
echo "release=${TAG}"
echo "commit=${SHA}"
echo "flyway=${FINAL_FLYWAY}"
echo "console=${CONSOLE_IMAGE}"
echo "========================================="
