#!/usr/bin/env bash
set -Eeuo pipefail

DEPLOY_USER="${1:-deploy}"
APP_DIR="${PETSPARK_APP_DIR:-/opt/petspark}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root: sudo bash bootstrap-vps.sh [deploy-user]" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed. Install Docker from 1Panel before continuing." >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required." >&2
  exit 1
fi

if ! id "${DEPLOY_USER}" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "${DEPLOY_USER}"
fi
usermod -aG docker "${DEPLOY_USER}"

install -d -m 0750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${APP_DIR}"
install -d -m 0750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "${APP_DIR}/backups"
install -d -m 0700 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" "/home/${DEPLOY_USER}/.ssh"
touch "/home/${DEPLOY_USER}/.ssh/authorized_keys"
chmod 0600 "/home/${DEPLOY_USER}/.ssh/authorized_keys"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "/home/${DEPLOY_USER}/.ssh"

echo "VPS bootstrap complete."
echo "1. Append the PetSpark deployment public key to /home/${DEPLOY_USER}/.ssh/authorized_keys"
echo "2. Create ${APP_DIR}/.env from .env.production.example and chmod 600"
echo "3. Log in to GHCR as ${DEPLOY_USER} with a read:packages token"
