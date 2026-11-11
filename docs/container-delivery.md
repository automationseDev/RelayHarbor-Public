# RelayHarbor — GitHub Container Registry delivery (GHCR)

This is the **image distribution** route for the single-user RelayHarbor Relay. GitHub Actions builds and tests an OCI image; Synology NAS **pulls** the image over HTTPS. GitHub Actions never gets the NAS's SSH keys, Docker socket or admin password.

## Image name and channels

Image: `ghcr.io/automationsedev/relayharbor-public-relay` (lowercase: Docker image names require lowercase).

- `edge`: published automatically from a tested `main` push that touches `relay/**` or the image workflow. For opted-in testing only.
- `sha-XXXXXXXXXXXX`: immutable commit-specific tag (12 lowercase hexadecimal characters) published with every build.
- `vX.Y.Z` and `stable`: published when the image workflow runs on a matching `vX.Y.Z` Git tag, or **Run workflow → channel: release** from `main`. `stable` points to the latest explicitly published release image.
- The NAS deployment script resolves the requested tag to an **image digest** (`@sha256:...`) and saves that digest in `relay/.deploy/active.env`. Running containers do not follow mutable tags.

Publishing uses GitHub Actions' built-in `GITHUB_TOKEN` with `packages: write` permission. GHCR packages are **private when first created by default**. GitHub links the package to the source repository through the OCI source label. Never store a personal GitHub PAT as a repository source file.

CI separately executes Relay Python tests, checks deployment shell syntax, and builds/runs the actual image; this catches omitted Python modules before publishing.

## One-time setup on a Docker-capable host (Synology example)

This example assumes the default Compose project supplied with the repository (adapt the checkout path for your environment):
- Working directory: `/path/to/RelayHarbor/relay`
- Existing container: `relay-relay-1`
- Existing Compose project: `relay`
- Existing named volume: `relay_relay_data`
- Local-only host port: `127.0.0.1:8088`
- Existing Docker network: `relay_default`
- Other existing services and Docker stacks are intentionally **not updated**.

**Do not run `docker compose down -v` or `--remove-orphans`.**

1. On GitHub, open **Actions → Publish Relay Docker Image (GHCR)** and use **Run workflow → main → release** to publish `v0.5.2` plus `stable`. A `main` push also publishes `edge` automatically.
2. If the GHCR image is private, create a **personal access token (classic)** scoped to `read:packages` for the GitHub user with permission to read the package. For an organization/private package, additional authorization may be necessary. Use a dedicated read-only token; do not grant `write:packages` to the NAS.
3. Log into GHCR **once** on Synology, as the same non-root user that will run deployments or scheduled tasks:

   ```bash
   read -rsp 'GHCR read:packages token: ' CR_PAT; echo
   printf '%s' "$CR_PAT" | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
   unset CR_PAT
   ```

   If you later make the package public, ordinary pulls do not require login. Protect `~/.docker/config.json` because Docker may persist credentials there.
4. Update the repository files (keeps `.env` unchanged):

   ```bash
   cd /path/to/RelayHarbor
   git switch main
   git pull --ff-only origin main
   cd relay
   ```

5. Deploy the release image (recommended) or `edge` for initial testing:

   ```bash
   bash scripts/nas-deploy.sh stable
   # or: bash scripts/nas-deploy.sh edge
   curl -fsS http://127.0.0.1:8088/healthz
   ```

The deployment script refuses unexpected Compose projects and database volumes, pulls just `relayharbor-relay`, pins the digest, takes a **SQLite online backup**, recreates only `relay` in project `relay` and checks the health endpoint. It caches/tags the previous application image for rollback. It does **not** update, restart, or remove cloudflared/Dify/web containers, nor overwrite `.env`.

The backup is in `relay/.deploy/backups/` with restricted permissions. Keep external backups too. SQLite backup is consistent via the SQLite backup API, instead of copying a live DB file and WAL independently.

## Roll back

For an application-level rollback to the cached previous version:

```bash
cd /path/to/RelayHarbor/relay
bash scripts/nas-rollback.sh
curl -fsS http://127.0.0.1:8088/healthz
```

When update health checks fail, the deployment script tries the same application rollback automatically. **Database schema/data is not automatically reverted**, to avoid discarding new events. If a future migration is incompatible with the older app, restore the recorded `.sqlite3` backup manually after stopping Relay and investigating the schema. Do not delete `relay_relay_data`. Old local image cache must be retained for rollback, so avoid aggressive `docker image prune -a`.

## Opt-in automatic NAS updates

No inbound port, webhook, SSH credential or self-hosted GitHub Runner is needed.

After the initial successful **manual** deployment of `stable`, Synology **Control Panel → Task Scheduler** can run this command periodically as the same account with Docker and GHCR read access:

```bash
bash /path/to/RelayHarbor/relay/scripts/nas-auto-update.sh stable
```

It polls the GHCR `stable` tag, compares its resolved digest with the recorded digest, and calls the guarded deploy script **only when different**. Use `edge` only for development/testing; `stable` is the recommended production channel. Scheduling the script is opt-in and performed in Synology DSM, not by the GitHub workflow. The NAS needs normal outbound HTTPS access to GHCR.

For new script improvements to be available, update the local Git checkout separately (`git pull --ff-only`), or distribute the updater as its own versioned component later. **This script updates the Relay container image, not the NAS OS, Android APK, other Docker services or the updater itself.**

## Cloudflare Tunnel and existing app

For a default Compose deployment, route the public hostname you configure (for example `https://relay.example.com`) to `http://relay:8000` on the `relay_default` network. Existing Android clients keep their configured tokens, connection credentials and notification database.

If you run a separate reverse proxy or tunnel container, attach it persistently to the Relay Compose network. One-off `docker network connect` changes can be lost when the proxy container is recreated.

## Security and limits

- Tokens stay in NAS `relay/.env` or encrypted Android storage; no secret goes inside the OCI image.
- The GHCR package may be shared with other users, but **the Relay application itself is single-user**, not a public multi-tenant backend. Do not share a single instance/tokens with unrelated users.
- Keep `127.0.0.1:8088` bound to loopback, use Cloudflare Tunnel, and apply WAF/rate limiting to authenticated external APIs.
- Keep GHCR account credentials read-only and outside source control.
- For additional hardening: signed artifact attestations, image vulnerability scanning, registry retention, semantic database migrations and an external encrypted backup are follow-up items.
