#!/usr/bin/env bash
# Prints an access token of a demo user. Demo users and their permissions: security/README.md.
#
# Usage:  TOKEN=$(security/token.sh alice)
#         curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/orders/<id>
#
# KEYCLOAK_URL: http://localhost:8180 (compose, default) or http://keycloak.localhost:8080 (k3d).
# The password grant is for scripts and demos only; real clients use the authorization code flow.
set -euo pipefail

user=${1:?usage: token.sh alice|bob}
url=${KEYCLOAK_URL:-http://localhost:8180}

curl -sf "$url/realms/blueprints/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=blueprints-cli \
  -d username="$user" -d password="$user" \
  | jq -r .access_token
