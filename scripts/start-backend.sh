#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -f .env ]; then echo "Copy .env.example to .env and configure first."; exit 1; fi
# Import simple KEY=VALUE lines without executing the .env file as shell commands.
while IFS='=' read -r key value || [ -n "${key:-}" ]; do
  key="${key%$'\r'}"
  value="${value%$'\r'}"
  [[ "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || continue
  export "$key=$value"
done < .env
cd backend
exec mvn spring-boot:run
