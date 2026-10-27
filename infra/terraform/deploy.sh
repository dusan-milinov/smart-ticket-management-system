#!/bin/bash
set -euo pipefail
cd /opt/smart-ticket
case "${1:-}" in
  db)
    test -f db.env
    docker pull mysql:9.7.2
    docker volume create ticket_mysql_data
    docker run -d --name db --restart unless-stopped \
      --env-file db.env -p 3306:3306 \
      -v ticket_mysql_data:/var/lib/mysql mysql:9.7.2
    ;;
  app)
    : "${BACKEND_IMAGE:?Set BACKEND_IMAGE to a tested image tag or digest}"
    : "${FRONTEND_IMAGE:?Set FRONTEND_IMAGE to a tested image tag or digest}"
    test -f app.env
    docker pull "$BACKEND_IMAGE"
    docker pull "$FRONTEND_IMAGE"
    docker network inspect ticket-net >/dev/null 2>&1 || docker network create ticket-net
    docker run -d --name backend --restart unless-stopped \
      --network ticket-net --env-file app.env "$BACKEND_IMAGE"
    docker run -d --name frontend --restart unless-stopped \
      --network ticket-net -p 80:80 "$FRONTEND_IMAGE"
    ;;
  *) echo "Usage: sudo bash deploy.sh db|app" >&2; exit 1 ;;
esac
