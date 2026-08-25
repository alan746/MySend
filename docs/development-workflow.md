# Development workflow

This document describes how a MySend change moves from an idea to a tested,
deployable release. It is the working agreement for product, web, API, and
infrastructure changes in this repository.

## Repository map

| Path | Responsibility |
| --- | --- |
| `app/` | React routes, UI components, API client, and global styles |
| `backend/` | Spring Boot API, database access, validation, security, and tests |
| `backend/src/main/resources/db/migration/` | Ordered Flyway database migrations |
| `public/` | Static web assets and social preview image |
| `tests/` | Rendered web output checks |
| `Dockerfile` | Standalone Node.js production image for the web service |
| `railway.toml` | Web health check and restart policy |
| `.github/workflows/` | Pull-request and branch checks |
| `docs/design/` | Ordered product and system design baseline |
| `docs/` | Product delivery notes and maintained project documentation |

## Change lifecycle

### 1. Define the issue

Every change starts with one focused GitHub issue. The title describes the
outcome, and the body uses these sections:

```text
## Goal
## Files to edit
## Expected behaviour
## Testing
```

Split unrelated outcomes into separate issues. A feature that touches both the
web and API may remain one issue when both changes are required for a single
user-visible result.

### 2. Create the branch

Branches follow the repository convention:

```text
issue-<number>-<name>-<layer>-<module>
```

For example:

```text
issue-25-alan746-Frameworks-ProjectDocumentation
```

Start from an up-to-date `main` branch and keep one issue as the branch's main
purpose.

### 3. Design the change

Use the [design process](design/README.md) before implementation. Start at the
earliest stage affected by the change: principle, requirement, use case,
domain, architecture, interaction, interface, or operations. A later technical
choice cannot silently override an earlier product decision.

For a new feature, interaction wireflows and failure states are approved before
controllers, database tables, or framework types are designed. Design documents
state the intended system; implementation alignment is tracked separately in
the issue and pull request.

Trace the proposed change through:

- the principle and user story it serves;
- the normal use-case flow and every alternate path;
- domain invariants and Guest, Free, or Premium differences;
- input/output boundaries and adapter responsibilities;
- screen states, API request/response, and stable failure codes;
- persistence, expiry, authentication, authorization, and abuse effects;
- deployment, monitoring, retention, rollback, and automated tests.

Update the affected design document in the same pull request. A design stage is
complete when the next stage can proceed without inventing an unstated product
rule.

For UI work, verify both a wide desktop layout and a narrow mobile layout.
For API work, preserve the inward dependency rule: controllers translate,
use cases own orchestration, domain objects own invariants, and outer adapters
implement persistence or provider ports. Validate all client-controlled input
at its boundary.

### 4. Implement in reviewable commits

Commit related files together and use Conventional Commit messages:

```text
<type>(<scope>): <description>
```

Common examples are:

```text
feat(room): add password-protected entry
fix(files): reject uploads above the plan limit
test(account): cover expired verification codes
docs(readme): present product and interface
```

Use a short imperative description. Keep generated build output, local
secrets, uploaded test files, IDE settings, and tool-specific metadata out of
the repository.

### 5. Verify locally

Run the web checks from the repository root:

```bash
npm ci
npm run check
```

Run the API checks from `backend/`:

```bash
mvn --batch-mode --no-transfer-progress verify
```

For integration changes, build and start the same services used for delivery:

```bash
docker compose up --build --wait
```

Then verify:

- `http://localhost:3000` loads and can create a guest room;
- an uppercase or lowercase access code reaches the same room;
- clipboard changes and file operations survive an API request cycle;
- private rooms reject an incorrect password;
- countdown, entry count, and storage use match the API response;
- `http://localhost:8080/actuator/health` reports healthy.

Stop local containers without deleting the database volume:

```bash
docker compose down
```

### 6. Open the pull request

Push the issue branch and open a focused pull request. Its body follows this
structure:

```text
Fixes #<issue>

## Behaviours Completed
## Files Changed
## Brief Explanation
## Testing
## Unsure About
```

Describe observable behaviour, list the important files, and include the exact
checks that passed. Add screenshots when the interface changes. Use
`## Unsure About` for real review questions; write `None` when there are none.

### 7. Review, merge, and release

GitHub Actions must pass before merge. Review the complete diff for secrets,
temporary files, accidental binaries, unrelated formatting, and user-facing
copy. Resolve review comments on the same branch, then squash or merge using
the repository's current policy.

After merge:

1. deploy the web and API from `main`;
2. apply Flyway migrations as the API starts;
3. verify the API health endpoint;
4. smoke-test room creation, joining, clipboard, and file transfer;
5. watch application logs and Stripe webhook delivery for regressions.

## Production configuration

The Railway web service uses repository root `/` and builds the root
`Dockerfile`. Its public build values are:

```text
NEXT_PUBLIC_API_BASE_URL=https://api.mysend.app
NEXT_PUBLIC_SITE_URL=https://mysend.app
NEXT_PUBLIC_BILLING_ENABLED=false
```

Attach `mysend.app` to the web service under Public Networking. Copy the DNS
records generated by Railway into the domain provider exactly as shown, then
wait for both domain verification and the managed TLS certificate.

The API production profile uses PostgreSQL, secure cookies, persistent file
storage, and SMTP delivery. Premium billing is staged behind
`BILLING_ENABLED=false`; Stripe values become mandatory only when that flag is
enabled. Start from `backend/.env.example` and store real values in the
deployment platform's secret manager, never in Git.

Build the API with `backend/Dockerfile`, mount durable storage at the same path
as `UPLOAD_DIRECTORY`, and send Stripe subscription events to:

For the API service, set the repository root directory to `/backend` and attach
`api.mysend.app` under Public Networking. Add a Railway Volume to that service
with `/app/uploads` as its mount path, then set
`UPLOAD_DIRECTORY=/app/uploads` and `STORAGE_PERSISTENT=true`. The Dockerfile
does not declare a Docker-managed volume because Railway owns the persistent
mount lifecycle.

```text
https://api.mysend.app/api/billing/webhook
```

The API must also use `WEB_ORIGINS=https://mysend.app` and
`APP_BASE_URL=https://mysend.app`. Keep `BILLING_ENABLED=false` until the
Premium checkout and webhook are ready for release.

The production validator refuses to start when required database, origin,
cookie, storage, or email settings are missing. It also requires all Stripe
settings whenever `BILLING_ENABLED=true`.
