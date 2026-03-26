#!/usr/bin/env bash
# ============================================================
# deploy.sh - Deploy midPoint configuration to a Docker Compose environment
#
# Usage: ./deploy.sh <environment>
#   e.g.: ./deploy.sh dev
#         ./deploy.sh sit
#         ./deploy.sh uat
#         ./deploy.sh prod
#
# What it does:
#   1. Loads environment-specific .env file
#   2. Detects changed XML files (compared to what's deployed)
#   3. Removes .done files for changed objects (forces re-import)
#   4. Restarts midPoint container
# ============================================================
set -euo pipefail

DEPLOY_ENV="${1:?Usage: deploy.sh <dev|sit|uat|prod>}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

ENV_FILE="${PROJECT_DIR}/midpoint-config/env/${DEPLOY_ENV}.env"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.yml"

if [ ! -f "$ENV_FILE" ]; then
    echo "ERROR: Environment file not found: ${ENV_FILE}"
    exit 1
fi

echo "============================================================"
echo "Deploying midPoint config to: ${DEPLOY_ENV}"
echo "Environment file: ${ENV_FILE}"
echo "============================================================"

# Step 1: Find changed XML files (if git is available)
CHANGED_FILES=""
if command -v git &>/dev/null && git rev-parse --git-dir &>/dev/null; then
    CHANGED_FILES=$(git diff --name-only HEAD~1 -- "${PROJECT_DIR}/midpoint-config/post-initial-objects/" 2>/dev/null || true)
    if [ -n "$CHANGED_FILES" ]; then
        echo ""
        echo "Changed files detected:"
        echo "$CHANGED_FILES" | sed 's/^/  /'
    fi
fi

# Step 2: Remove .done files for changed objects on the target
# This forces midPoint to re-import them on next startup.
# The .done files are inside the midpoint_home Docker volume.
CONTAINER_NAME="midpoint"
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    if [ -n "$CHANGED_FILES" ]; then
        echo ""
        echo "Removing .done markers for changed files..."
        for file in $CHANGED_FILES; do
            basename_xml=$(basename "$file")
            # Find and remove .done file inside the container's MIDPOINT_HOME
            docker exec "$CONTAINER_NAME" bash -c \
                "find /opt/midpoint/var/post-initial-objects -name '${basename_xml}.done' -delete 2>/dev/null" || true
            echo "  Cleared .done for: ${basename_xml}"
        done
    fi
fi

# Step 3: Restart with the correct environment
echo ""
echo "Restarting midPoint with ${DEPLOY_ENV} configuration..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate midpoint

echo ""
echo "Deployment to ${DEPLOY_ENV} initiated."
echo "Monitor logs: docker compose -f ${COMPOSE_FILE} logs -f midpoint"
