#!/bin/bash
# One-time bootstrap for nginx + Certbot. Run this once on the actual server, after DNS for
# $DOMAIN points at it, before the nginx 443 server block can start (it needs a real cert file
# to exist on disk first, which is what this script obtains).
set -euo pipefail

if [ -z "${DOMAIN:-}" ]; then
  echo "DOMAIN env var is required (copy .env.prod.example to .env.prod, fill it in, then: set -a; source .env.prod; set +a)"
  exit 1
fi
if [ -z "${CERTBOT_EMAIL:-}" ]; then
  echo "CERTBOT_EMAIL env var is required (see .env.prod.example)"
  exit 1
fi

COMPOSE="docker compose -f docker-compose.nginx.yml"

echo "==> Creating dummy self-signed cert so nginx has something to load on first boot..."
mkdir -p "certbot/conf/live/$DOMAIN"
$COMPOSE run --rm --entrypoint "\
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '/etc/letsencrypt/live/$DOMAIN/privkey.pem' \
    -out '/etc/letsencrypt/live/$DOMAIN/fullchain.pem' \
    -subj '/CN=localhost'" certbot

echo "==> Starting nginx with the dummy cert..."
$COMPOSE up -d nginx

echo "==> Deleting dummy cert..."
$COMPOSE run --rm --entrypoint "\
  rm -rf /etc/letsencrypt/live/$DOMAIN && \
  rm -rf /etc/letsencrypt/archive/$DOMAIN && \
  rm -rf /etc/letsencrypt/renewal/$DOMAIN.conf" certbot

echo "==> Requesting real certificate from Let's Encrypt for $DOMAIN..."
$COMPOSE run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    --email $CERTBOT_EMAIL -d $DOMAIN \
    --rsa-key-size 2048 --agree-tos --no-eff-email --force-renewal" certbot

echo "==> Reloading nginx with the real cert..."
$COMPOSE exec nginx nginx -s reload

echo "==> Starting the certbot renewal loop..."
$COMPOSE up -d certbot

echo "Done. nginx is now serving https://$DOMAIN"
