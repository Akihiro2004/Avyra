#!/usr/bin/env bash
# One-shot installer for the Avyra Listen Together server on an Ubuntu or
# Debian VPS. Safe to re-run: it rebuilds and restarts, so it is also how a new
# build is deployed.
#
#   sudo DOMAIN=jam.example.com bash deploy/setup.sh
#
# Optional, for invite links that open Avyra without asking which app to use
# (the SHA-256 fingerprint of the key the release APK is signed with):
#
#   sudo DOMAIN=jam.example.com ANDROID_CERT_SHA256=AB:CD:... bash deploy/setup.sh
#
# Run it from the backend/ directory of a copy on the server. The DNS A record
# for $DOMAIN must already point at this server, or Caddy cannot get a
# certificate.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

DOMAIN="${DOMAIN:?set DOMAIN, e.g. DOMAIN=jam.example.com}"
GO_VERSION="${GO_VERSION:-1.26.0}"
BACKEND_DIR="$(cd "$(dirname "$0")/.." && pwd)"

case "$(uname -m)" in
  aarch64) GOARCH=arm64 ;;
  x86_64)  GOARCH=amd64 ;;
  *) echo "unsupported arch $(uname -m)"; exit 1 ;;
esac

echo "== packages"
if ! dpkg -s curl git gnupg debian-keyring debian-archive-keyring apt-transport-https >/dev/null 2>&1; then
  apt-get update -y
  apt-get install -y curl git debian-keyring debian-archive-keyring apt-transport-https gnupg
fi

echo "== Go $GO_VERSION"
if ! /usr/local/go/bin/go version 2>/dev/null | grep -q "go$GO_VERSION"; then
  curl -fsSL "https://go.dev/dl/go$GO_VERSION.linux-$GOARCH.tar.gz" -o /tmp/go.tgz
  rm -rf /usr/local/go && tar -C /usr/local -xzf /tmp/go.tgz && rm /tmp/go.tgz
fi

echo "== build"
id avyra >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin avyra
install -d -o avyra -g avyra /opt/avyra-jam
(cd "$BACKEND_DIR" && /usr/local/go/bin/go build -o /opt/avyra-jam/server.new .)
mv /opt/avyra-jam/server.new /opt/avyra-jam/server
chown avyra:avyra /opt/avyra-jam/server

echo "== settings"
{
  echo "# Written by deploy/setup.sh; re-run it to change these."
  if [ -n "${ANDROID_CERT_SHA256:-}" ]; then
    echo "JAM_ANDROID_CERT_SHA256=$ANDROID_CERT_SHA256"
  fi
} > /etc/avyra-jam.env
chmod 644 /etc/avyra-jam.env

echo "== service"
install -m 644 "$BACKEND_DIR/deploy/avyra-jam.service" /etc/systemd/system/avyra-jam.service
systemctl daemon-reload
systemctl enable avyra-jam
systemctl restart avyra-jam

echo "== caddy (HTTPS + WebSocket proxy)"
if ! command -v caddy >/dev/null; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y && apt-get install -y caddy
fi
sed "s/{\$DOMAIN}/$DOMAIN/" "$BACKEND_DIR/deploy/Caddyfile" > /tmp/Caddyfile
if ! cmp -s /tmp/Caddyfile /etc/caddy/Caddyfile; then
  mv /tmp/Caddyfile /etc/caddy/Caddyfile
  systemctl reload caddy || systemctl restart caddy
fi

echo "== firewall"
# Only 80 and 443 need to be reachable; the server itself listens on 8000
# behind Caddy. Whichever firewall is actually in charge gets the rule.
if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  ufw allow 80/tcp
  ufw allow 443/tcp
elif iptables -L INPUT -n 2>/dev/null | grep -q REJECT; then
  # Some images (Oracle Cloud's Ubuntu among them) ship a REJECT rule that
  # blocks everything but SSH. Open 80/443 above it and keep that over reboots.
  apt-get install -y iptables-persistent
  for port in 80 443; do
    while iptables -D INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null; do :; done
    reject="$(iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" { print $1; exit }')"
    iptables -I INPUT "${reject:-1}" -p tcp --dport "$port" -m state --state NEW -j ACCEPT
  done
  netfilter-persistent save
else
  echo "no active host firewall; make sure your provider's firewall allows TCP 80 and 443"
fi

echo "== check"
sleep 2
curl -fsS http://127.0.0.1:8000/healthz && echo
echo "Done. Once DNS resolves, https://$DOMAIN/healthz should answer."
