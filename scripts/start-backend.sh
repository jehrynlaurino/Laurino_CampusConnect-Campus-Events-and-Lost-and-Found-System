#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/load-env.sh
cd backend
exec mvn spring-boot:run
