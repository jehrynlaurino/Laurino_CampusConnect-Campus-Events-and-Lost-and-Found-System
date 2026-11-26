#!/usr/bin/env bash
# Run project-local PostgreSQL and Mailpit in the foreground. Ctrl-C stops both.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/load-env.sh
export PATH="$(brew --prefix postgresql@16)/bin:$PATH"
command -v initdb >/dev/null
command -v mailpit >/dev/null
: "${DB_USERNAME:?Set DB_USERNAME in .env}" "${DB_PASSWORD:?Set DB_PASSWORD in .env}"
if [[ "$DB_URL" != "jdbc:postgresql://localhost:5432/campusconnect" ]]; then
  echo "Native startup expects DB_URL=jdbc:postgresql://localhost:5432/campusconnect" >&2
  exit 1
fi
umask 077
runtime="$PWD/.campusconnect-local"
mkdir -p "$runtime/socket"
if [[ ! -f "$runtime/pgdata/PG_VERSION" ]]; then
  initdb -D "$runtime/pgdata" -U campus_local_admin --auth-local=trust --auth-host=scram-sha-256 \
    --pwfile=<(openssl rand -hex 32) --encoding=UTF8 --no-locale
fi
pg_ctl -D "$runtime/pgdata" -l "$runtime/postgres.log" \
  -o "-h 127.0.0.1 -p 5432 -k '$runtime/socket'" -w start
trap 'pg_ctl -D "$runtime/pgdata" -m fast -w stop' EXIT
# The maintenance role uses a Unix socket inside the private runtime directory.
# The TCP application role owns only its database. Reuse it on later starts.
psql -X -q -h "$runtime/socket" -U campus_local_admin -d postgres -v ON_ERROR_STOP=1 <<'SQL'
\getenv app_user DB_USERNAME
\getenv app_password DB_PASSWORD
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app_user', :'app_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'app_user') \gexec
SELECT format('CREATE DATABASE campusconnect OWNER %I', :'app_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'campusconnect') \gexec
SQL
mailpit --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025 --database "$runtime/mailpit.db"
