# nginx + Certbot (HTTPS reverse proxy)

Terminates TLS in front of the app so login credentials (and everything else) travel over
HTTPS instead of plaintext HTTP. This is separate from `docker-compose.yml` and only relevant
once you have a real domain pointed at a real server — it does nothing useful on localhost.

## What it proxies to

- `/api/*` → `host.docker.internal:8080` (the Spring Boot backend — run however you normally
  run it on that host, e.g. `java -jar` or a systemd service; not containerized here)
- `/` → `host.docker.internal:5173` (the `frontend` container from the existing
  `docker-compose.yml`, which already publishes that port to the host)

`host.docker.internal` is what lets an nginx container reach ports on its host. On Docker
Desktop (Mac/Windows) this works out of the box; on Linux the `extra_hosts: host-gateway`
entry in `docker-compose.nginx.yml` makes it work there too.

## One-time setup on the server

1. Point your domain's DNS A record at the server's IP.
2. `cp .env.prod.example .env.prod` and fill in `DOMAIN` and `CERTBOT_EMAIL`.
3. Load the env vars and run the bootstrap script once:
   ```
   set -a; source .env.prod; set +a
   ./init-letsencrypt.sh
   ```
   This handles the chicken-and-egg problem: nginx's 443 block needs a cert file to exist
   before it will even start, but Certbot needs nginx answering on port 80 to prove domain
   ownership. The script starts nginx with a throwaway self-signed cert first, requests the
   real one from Let's Encrypt via the HTTP-01 challenge, then reloads nginx with it.
4. From then on, `docker compose -f docker-compose.nginx.yml up -d` starts nginx and the
   Certbot renewal loop (checks twice a day, renews when within 30 days of expiry — no further
   manual steps).

## Notes

- `certbot/` (created locally by the above) holds the actual private key material — it's
  gitignored, never commit it.
- The backend already sets `server.forward-headers-strategy: framework`
  (`backend/src/main/resources/application.yml`) so it correctly reads `X-Forwarded-Proto` from
  nginx — this matters for `app.cookie-secure` and any HTTPS-vs-HTTP checks.
