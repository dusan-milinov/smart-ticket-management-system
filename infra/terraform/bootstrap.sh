#!/bin/bash
set -euo pipefail
# Infrastructure preparation only: no credentials or application secrets in user data.
for attempt in {1..5}; do
  if dnf install -y docker; then break; fi
  sleep 10
done
systemctl enable --now docker
systemctl enable --now amazon-ssm-agent
mkdir -p /opt/smart-ticket
chmod 700 /opt/smart-ticket
