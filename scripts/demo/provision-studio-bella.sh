#!/usr/bin/env bash
set -Eeuo pipefail

API="${TROQUIM_DEMO_API:-http://127.0.0.1:8080}"
PAYLOAD="${1:-scripts/demo/studio-bella.json}"

if [[ ! -f "${PAYLOAD}" ]]; then
  echo "Payload demo não encontrado: ${PAYLOAD}" >&2
  exit 1
fi

TOKEN="$(docker exec troquim-bot printenv TROQUIM_ADMIN_API_KEY)"
[[ -n "${TOKEN}" ]] || { echo "TROQUIM_ADMIN_API_KEY ausente" >&2; exit 1; }

curl -fsS   -H "Authorization: Bearer ${TOKEN}"   -H "Content-Type: application/json"   --data-binary "@${PAYLOAD}"   "${API}/api/v1/admin/onboarding/provision"

echo
