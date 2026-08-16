#!/usr/bin/env bash
set -euo pipefail

: "${TESTMAIL_APIKEY:?TESTMAIL_APIKEY is required}"
: "${TESTMAIL_NAMESPACE:?TESTMAIL_NAMESPACE is required}"

TAG="${1:-user001}"
ENDPOINT="https://api.testmail.app/api/json"

response="$(curl -fsS --get "$ENDPOINT" \
  --data-urlencode "apikey=${TESTMAIL_APIKEY}" \
  --data-urlencode "namespace=${TESTMAIL_NAMESPACE}" \
  --data-urlencode "tag=${TAG}" \
  --data-urlencode "limit=10")"

printf '%s' "$response" | python3 -c '
import json, sys

tag = sys.argv[1]
data = json.load(sys.stdin)
result = data.get("result")
count = data.get("count", 0)
message = data.get("message")
print(json.dumps({
    "result": result,
    "tag": tag,
    "count": count,
    "message": message,
    "connected": result == "success",
}, ensure_ascii=False))
if result != "success":
    raise SystemExit(1)
' "$TAG"
