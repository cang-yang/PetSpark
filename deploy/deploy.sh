#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${PETSPARK_APP_DIR:-/opt/petspark}"
COMPOSE_FILE="${APP_DIR}/docker-compose.prod.yml"
ENV_FILE="${APP_DIR}/.env"
SUCCESS_FILE="${APP_DIR}/.last-successful-tag"
BACKUP_DIR="${APP_DIR}/backups"
LOCK_FILE="${APP_DIR}/.deploy.lock"
NEW_TAG="${1:-}"

exec 9>"${LOCK_FILE}"
if ! flock -n 9; then
  echo "Another PetSpark deployment is already running." >&2
  exit 1
fi

if [[ ! "${NEW_TAG}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Usage: deploy.sh <40-character-git-sha>" >&2
  exit 1
fi
if [[ ! -f "${COMPOSE_FILE}" || ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${COMPOSE_FILE} or ${ENV_FILE}." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

required=(MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD JWT_SECRET AI_MESSAGE_SECRET PETSPARK_HEALTH_DETAIL_SECRET PETSPARK_USER_PHONE_SECRET PETSPARK_DEMO_ADMIN_PASSWORD PETSPARK_DEMO_MEMBER_PASSWORD)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" || "${!name}" == CHANGE_ME* ]]; then
    echo "Required production variable ${name} is missing or still uses CHANGE_ME." >&2
    exit 1
  fi
done

compose() {
  IMAGE_TAG="${IMAGE_TAG:-${NEW_TAG}}" docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

previous_tag=""
if [[ -f "${SUCCESS_FILE}" ]]; then
  previous_tag="$(tr -d '[:space:]' < "${SUCCESS_FILE}")"
fi

mkdir -p "${BACKUP_DIR}"
mysql_id="$(compose ps -q mysql 2>/dev/null || true)"
if [[ -n "${mysql_id}" ]] && [[ "$(docker inspect --format '{{.State.Running}}' "${mysql_id}" 2>/dev/null || true)" == "true" ]]; then
  backup_file="${BACKUP_DIR}/petspark-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
  echo "Creating database backup ${backup_file}"
  compose exec -T mysql sh -c 'exec mysqldump --single-transaction --quick -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' | gzip -9 > "${backup_file}"
  find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'petspark-*.sql.gz' -printf '%T@ %p\n' \
    | sort -nr | tail -n +8 | cut -d' ' -f2- | xargs -r rm -f --
fi

wait_for_service() {
  local service="$1"
  local attempts="$2"
  local id status
  for ((i=1; i<=attempts; i++)); do
    id="$(compose ps -q "${service}" 2>/dev/null || true)"
    if [[ -n "${id}" ]]; then
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${id}" 2>/dev/null || true)"
      if [[ "${status}" == "healthy" || "${status}" == "running" ]]; then
        return 0
      fi
      if [[ "${status}" == "unhealthy" || "${status}" == "exited" || "${status}" == "dead" ]]; then
        docker logs --tail 100 "${id}" >&2 || true
        return 1
      fi
    fi
    sleep 5
  done
  echo "Timed out waiting for ${service}." >&2
  return 1
}

rollback() {
  if [[ "${previous_tag}" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Deployment failed; restoring ${previous_tag}." >&2
    IMAGE_TAG="${previous_tag}" compose pull petspark-server petspark-web || true
    IMAGE_TAG="${previous_tag}" compose up -d --remove-orphans || true
  else
    echo "Deployment failed and no previous successful tag is available." >&2
  fi
}
trap rollback ERR

export IMAGE_TAG="${NEW_TAG}"
compose pull mysql petspark-server petspark-web
compose up -d --remove-orphans
wait_for_service mysql 24
wait_for_service petspark-server 24
wait_for_service petspark-web 12
curl --fail --silent --show-error --retry 6 --retry-delay 3 "http://127.0.0.1:${WEB_BIND_PORT:-18081}/healthz" >/dev/null
curl --fail --silent --show-error --retry 6 --retry-delay 3 "http://127.0.0.1:${WEB_BIND_PORT:-18081}/actuator/health" >/dev/null

printf '%s\n' "${NEW_TAG}" > "${SUCCESS_FILE}"
trap - ERR
docker image prune -f >/dev/null
echo "PetSpark deployment ${NEW_TAG} completed successfully."
