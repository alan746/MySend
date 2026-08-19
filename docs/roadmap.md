# MySend roadmap

The current build proves the main promise: open a temporary room, share text
and files, and leave without creating an account. The next work is ordered
around making that flow dependable in public before adding more surface area.

## Current baseline

Available today:

- guest, Free, and Premium room limits;
- public and password-protected rooms;
- clipboard and common-file sharing;
- owner controls, entry limits, and automatic expiry;
- email registration and account sessions;
- My ShareRooms for registered users;
- Stripe subscription checkout and billing portal;
- PostgreSQL migrations, container builds, cleanup jobs, and CI checks.

## Release 1 — public launch

| Priority | Update | Done when |
| --- | --- | --- |
| P0 | Production web and API hosting | HTTPS domains serve the web and API with reproducible deployments from `main`. |
| P0 | Object storage | Uploads use durable S3-compatible storage and expire with their ShareRoom. |
| P0 | Transactional email | Verification mail is delivered from a verified domain with bounce and failure visibility. |
| P0 | Stripe live mode | Checkout, renewal, cancellation, portal access, and webhook retries are verified end to end. |
| P0 | Monitoring and backups | Health, error rate, storage use, cleanup failures, database backups, and restore steps are observable. |
| P0 | Browser journey tests | Automated tests cover guest creation, private entry, clipboard updates, files, registration, and expiry. |
| P0 | Abuse controls | IP-aware throttling, upload scanning, blocked extensions, and operational limits protect public capacity. |

## Release 1.1 — faster handoffs

| Priority | Update | User result |
| --- | --- | --- |
| P1 | Live room updates | Clipboard, file list, entry count, and room status update without a manual refresh. |
| P1 | Upload progress | Large uploads show progress, clear failures, and a safe retry action. |
| P1 | Better sharing actions | Copy a room link, show a QR code, and share directly on supported mobile devices. |
| P1 | File previews | Preview images, PDFs, Markdown, and plain-text code before downloading. |
| P1 | Account recovery | Reset a forgotten password through a short-lived email link. |
| P1 | Room activity | Owners can see recent joins and file changes without exposing visitor identity. |

## Release 1.2 — dependable larger transfers

| Priority | Update | User result |
| --- | --- | --- |
| P2 | Resumable uploads | Interrupted larger uploads continue instead of restarting from zero. |
| P2 | Background file processing | Virus scanning and preview generation do not block room requests. |
| P2 | Storage-region choice | Paid rooms can keep temporary data closer to the people using it. |
| P2 | Installable web app | MySend can be installed as a lightweight PWA with mobile share-target support. |
| P2 | Accessibility audit | Keyboard, screen-reader, focus, contrast, and reduced-motion behaviour meet WCAG 2.2 AA. |

## Research track

These ideas need security and cost validation before they become committed
release work:

- end-to-end encrypted rooms with a recovery model users can understand;
- room-to-room transfer history for registered users without retaining shared
  content;
- team billing and shared administration;
- optional custom room aliases without weakening access-code availability.

## Product boundaries

MySend is intentionally not a permanent cloud drive, document editor, or
social feed. New work should preserve three qualities: no-login sharing stays
first-class, the room always explains its limits, and expired content is
actually removed.
