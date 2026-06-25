#!/usr/bin/env bash
# Relay Invictus Link bridge on Pi VPN IP -> PC LAN (bypasses broken wg peer routing).
# Run on Pi after copying from PC:
#   scp scripts/invictus-networks/setup-pi-link-proxy.sh user@vpn-hub.local:~/
#   ssh user@vpn-hub.local 'bash ~/setup-pi-link-proxy.sh 192.168.1.50 3003'

set -euo pipefail

PC_LAN_IP="${1:-YOUR_PC_LAN_IP}"
PC_PORT="${2:-3003}"
LISTEN_IP="10.66.66.1"
LISTEN_PORT="$PC_PORT"
SERVICE="invictus-link-proxy"

echo "=== Invictus Link proxy on Pi ==="
echo "  Phone URL: http://${LISTEN_IP}:${LISTEN_PORT}"
echo "  Forwards to: ${PC_LAN_IP}:${PC_PORT}"

sudo apt-get install -y socat

# Quick test that PC bridge is reachable from Pi on LAN
if ! timeout 3 bash -c "echo >/dev/tcp/${PC_LAN_IP}/${PC_PORT}" 2>/dev/null; then
  echo "WARNING: Cannot reach ${PC_LAN_IP}:${PC_PORT} from Pi."
  echo "Ensure bridge is running on PC and PC is on home LAN."
fi

sudo tee "/etc/systemd/system/${SERVICE}.service" >/dev/null <<EOF
[Unit]
Description=Invictus Link bridge proxy to PC
After=network-online.target wg-quick@wg0.service
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/bin/socat TCP-LISTEN:${LISTEN_PORT},fork,bind=${LISTEN_IP},reuseaddr TCP:${PC_LAN_IP}:${PC_PORT}
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable "${SERVICE}"
sudo systemctl restart "${SERVICE}"
sleep 1
sudo systemctl --no-pager status "${SERVICE}" || true

echo ""
echo "Done. In Invictus Link set Bridge URL to:"
echo "  http://${LISTEN_IP}:${LISTEN_PORT}"
echo ""
echo "Test from phone browser (WireGuard ON):"
echo "  http://${LISTEN_IP}:${LISTEN_PORT}/health"
