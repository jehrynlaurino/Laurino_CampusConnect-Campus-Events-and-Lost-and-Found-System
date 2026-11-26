#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/load-env.sh

port="${SERVER_PORT:-${PORT:-8080}}"
case "${SERVER_ADDRESS:-127.0.0.1}" in
  localhost|127.0.0.1|0.0.0.0)
    health_url="http://127.0.0.1:$port/api/health"
    if health=$(curl --noproxy '*' --fail --silent --max-time 2 "$health_url") &&
       [[ $health =~ \"service\"[[:space:]]*:[[:space:]]*\"CampusConnect\" ]] &&
       [[ $health =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]; then
      echo "CampusConnect is already running at $health_url"
      echo "To apply backend or .env changes, stop the running backend before starting it again."
      exit 0
    fi
    ;;
esac

cd backend
exec mvn spring-boot:run
