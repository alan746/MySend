# 7. Interface and API design

**Status: Baseline — interaction and adapter contract approved before coding**

## Design inputs

This document adapts [2. Use cases](02-use-cases.md) to the browser experience
in [5. User interaction design](05-user-interaction-design.md). It is allowed
to choose routes, HTTP status codes, JSON, multipart, and cookie transport, but
it cannot redefine plan limits, authorization, closure, or failure guarantees.

Contract traceability is maintained as `endpoint -> UC -> FR/QR/INV`. Stable
problem codes come from use-case outcomes; human messages may change without
changing client behaviour.

## Information architecture

| Route | Primary job | Authentication |
| --- | --- | --- |
| `/` | Explain the product, create a room, or start room entry | None |
| `/room/{code}` | Clipboard, file board, room status, and owner controls | Owner identity or room access token |
| `/login` | Authenticate an existing account | None |
| `/signup` | Create and verify a new account | None |
| `/settings` | Show account limits, billing, sign out, and My ShareRooms | Account session; visitors receive a login gate |

The home page must keep Create and Join above supporting explanation. Settings
must not imply that registration is required for the core sharing path.

## Visual principles

- Use editorial hierarchy, grid, whitespace, and a small number of purposeful
  accents rather than interchangeable dashboard cards.
- Let type scale and position create hierarchy; do not make every element the
  same font, weight, radius, or alignment.
- Reserve the bright lime accent for active choice, status, or action. Account
  pages use the same locked palette rather than introducing a second accent.
- Use direct verbs: “Create ShareRoom”, “Join”, “Copy access code”, “Close
  room”. Avoid promotional filler inside task surfaces.
- Room code, countdown, entries, privacy, and storage must remain scannable
  without opening a secondary page.
- Motion may confirm a state change but must respect reduced-motion settings
  and never delay the task.

## Screen behaviour

### Home

Create state contains visibility, optional private password, lifetime, entry
limit, plan hint, and one primary action. Join state accepts a five-character
code first and requests a password only when needed. Values are normalized in
the interface for readability but validated again by the API.

Required states: idle, validating, submitting, password required, service
unavailable, plan-limit failure, and room unavailable.

### ShareRoom

The page has three stable regions:

1. identity/status — access code, copy action, countdown, privacy, entries,
   plan;
2. workspace — shared clipboard and file board;
3. owner controls — visibility, password, lifetime, entry limit, and close.

Visitors do not see owner controls. All participants see quota and save/upload
failures near the affected surface. A version conflict must offer refresh,
never silently replace local or remote text.

### Settings

Login and signup are focused routes rather than tabs inside Settings. A visitor
who opens Settings receives a short login gate with routes to login, signup,
and guest use. Authenticated Settings shows account identity, sign out, My
ShareRooms, and either Upgrade or Manage billing.

Registration is a two-step state machine: credentials → verification code →
authenticated Free account. The interface displays code expiry and resend
cooldown without revealing a production verification code.

## Responsive and accessible behaviour

- At narrow widths, paired panels stack in task order: identity, clipboard,
  files, then controls.
- The room code remains text, not an image, and can be selected or copied.
- Every form field has a persistent label; placeholder text is supplementary.
- Tabs, dialogs, upload actions, and controls are keyboard operable with visible
  focus.
- Status and validation messages use text in addition to colour.
- Countdown announcements must not create a screen-reader update every second;
  announce meaningful thresholds and closure.
- Touch targets remain at least 44 by 44 CSS pixels where practical.

## HTTP API

All API paths are under `/api`. JSON mutations use `Content-Type:
application/json`, browser credentials, and `X-Requested-With: MySendWeb`.
Multipart upload uses the same request marker without forcing a JSON content
type.

### Room endpoints

| Method and path | Use case / requirements | Authorization | Success |
| --- | --- | --- | --- |
| `POST /api/rooms` | UC-01 / FR-01-FR-04 | Device or account owner identity | `201 RoomView` |
| `POST /api/rooms/enter` | UC-02 / FR-05-FR-09 | Code and optional password | `200 RoomView` + room cookie |
| `GET /api/rooms` | UC-08 / FR-23 | Account session | `200 RoomSummary[]` |
| `GET /api/rooms/{code}` | UC-03 / FR-09-FR-10 | Owner or exact-room grant | `200 RoomView` |
| `PATCH /api/rooms/{code}/clipboard` | UC-04 / FR-13-FR-14 | Owner or exact-room grant | `200 RoomView` |
| `PATCH /api/rooms/{code}/settings` | UC-06 / FR-11 | Owner only | `200 RoomView` |
| `DELETE /api/rooms/{code}` | UC-06 / FR-12, FR-27 | Owner only | `204` |

Create input:

```json
{
  "visibility": "PRIVATE",
  "password": "optional-room-password",
  "lifetimeMinutes": 15,
  "accessLimit": 20
}
```

Entry input:

```json
{
  "accessCode": "4821k",
  "password": "optional-room-password"
}
```

Clipboard input carries both content and concurrency state:

```json
{
  "text": "Shared text",
  "version": 3
}
```

`RoomView` exposes code, plan, visibility, password presence, access limit and
count, remaining entries, clipboard, file usage and limit, clipboard limit,
creation and expiry, owner flag, and version. It never exposes owner keys,
password hashes, or access-token hashes.

`RoomSummary` is the account-owned list projection: access code, visibility,
plan, entries, creation/expiry, file usage, and closure-relevant status. It
does not include clipboard text or visitor grant data.

### File endpoints

| Method and path | Use case / requirements | Authorization | Success |
| --- | --- | --- | --- |
| `GET /api/rooms/{code}/files` | UC-05 / FR-15 | Owner or exact-room grant | `200 FileView[]` |
| `POST /api/rooms/{code}/files` | UC-05 / FR-15, INV-11 | Owner or exact-room grant | `201 FileView` |
| `GET /api/rooms/{code}/files/{fileId}` | UC-05 / FR-16 | Owner or exact-room grant | Streamed bytes |
| `DELETE /api/rooms/{code}/files/{fileId}` | UC-05 / FR-17 | Owner only | `204` |

Allowed extensions are `pdf`, `txt`, `md`, `java`, `py`, `c`, `h`, `cpp`,
`hpp`, `doc`, `docx`, `jpg`, `jpeg`, `png`, `gif`, `webp`, `zip`, and `json`.
Extension allowlisting is a first barrier, not a substitute for malware
scanning in public production.

### Account endpoints

| Method and path | Use case / requirements | Success |
| --- | --- | --- |
| `POST /api/auth/register/code` | UC-07 / FR-18, FR-22 | Verification expiry/delivery status |
| `POST /api/auth/register/verify` | UC-07 / FR-19 | `200 AccountView` + session cookie |
| `POST /api/auth/login` | UC-07 / FR-20, FR-22 | `200 AccountView` + session cookie |
| `GET /api/auth/me` | UC-07 / FR-20-FR-21 | `200 AccountView` or `401` |
| `POST /api/auth/logout` | UC-07 / FR-21 | `204` |

`AccountView` contains identity, plan and relevant limits, plus whether a
Stripe billing profile is available. It never includes password or session
material.

### Billing endpoints

| Method and path | Use case / requirements | Authorization |
| --- | --- | --- |
| `POST /api/billing/checkout` | UC-09 / FR-24 | Account session |
| `POST /api/billing/portal` | UC-09 / FR-25 | Account session and billing profile |
| `POST /api/billing/webhook` | UC-09 / FR-26, INV-10 | Authenticated provider event; no browser marker |

Checkout and portal return only a short-lived provider URL. The browser return
URL is a presentation state; the webhook is authoritative for the account plan.

## Cookie contract

| Cookie | Scope and lifetime | Purpose |
| --- | --- | --- |
| `mysend_device` | Path `/`, one year | Opaque Guest owner identity; hash-derived owner key stored with rooms |
| `mysend_session` | Path `/`, thirty days | Revocable account session |
| `mysend_room_access_{code}` | Path `/`, until that room expires | Authorization for one entered room |

All are HTTP-only and `SameSite=Lax`; production requires `Secure`. Plain
session and room tokens are returned only as cookies and stored server-side as
hashes. The device token itself is not stored in the room row.

## Error contract

```json
{
  "code": "ROOM_CHANGED",
  "message": "The room changed in another tab; refresh before saving again",
  "fields": {},
  "timestamp": "2026-08-22T12:00:00Z"
}
```

| Status | Meaning | Interface response |
| --- | --- | --- |
| `400` | Invalid shape or business choice | Keep input and show field/action guidance |
| `401` | Credentials or room entry required | Show sign-in/password/entry state |
| `403` | Authenticated identity lacks owner authority or origin trust | Hide retry that cannot succeed; explain owner requirement |
| `404` | Code/file does not exist | Return to join or update file list |
| `409` | Active-room limit, stale version, or billing profile state | Present specific recovery action |
| `410` | Room closed, expired, or exhausted | Replace workspace with closed state |
| `413` | Clipboard/file/request exceeds capacity | Show current limit and retain other state |
| `415` | File extension not allowed | Show supported formats |
| `429` | Authentication rate limit | Show retry window; do not loop automatically |
| `502/503` | Provider or configuration unavailable | Keep account/room state unchanged and allow a later retry |

Clients branch on stable `code` values when behaviour differs. `message` is
human-readable fallback text and may be refined without changing the contract.

## Use-case outcome mapping

| Use-case outcome | HTTP status | Stable problem code | Required client behaviour |
| --- | ---: | --- | --- |
| Invalid room policy/lifetime/entries | 400 | `INVALID_ROOM_POLICY`, `INVALID_LIFETIME`, `INVALID_ACCESS_LIMIT` | Keep values and focus the affected field. |
| Owner active-room limit | 409 | `ACTIVE_ROOM_LIMIT` | Offer close-room, sign-in, or plan recovery. |
| Private password missing/wrong | 401 | `ROOM_PASSWORD_INCORRECT` | Keep code, clear password, consume no entry. |
| Room unknown to a direct lookup | 404 | `ROOM_NOT_FOUND` | Return to Join without showing content. |
| Room logically closed | 410 | `ROOM_CLOSED` | Replace editable room state with terminal explanation. |
| Participant grant absent/invalid | 401 | `ROOM_ACCESS_REQUIRED` | Return to Join for that code. |
| Owner capability required | 403 | `ROOM_OWNER_REQUIRED` | Remove impossible retry and explain ownership. |
| Version changed | 409 | `ROOM_CHANGED` | Preserve local input and offer Load latest. |
| Clipboard or file capacity | 413 | `CLIPBOARD_LIMIT`, `SINGLE_FILE_LIMIT`, `ROOM_FILE_LIMIT` | Show applicable limit and preserve unaffected state. |
| File extension disallowed | 415 | `FILE_TYPE_NOT_ALLOWED` | Show launch allowlist. |
| Authentication throttled | 429 | `AUTH_RATE_LIMITED` | Display retry time and do not loop automatically. |
| Mail, billing, storage, or code capacity unavailable | 502/503 | Provider/capacity-specific code | Preserve committed state and allow safe retry. |

Malformed and unknown entry guesses may intentionally share a less specific
unavailable response to avoid creating a room-discovery oracle. The adapter
maps detailed internal outcomes to the least revealing public contract that
still permits legitimate recovery.

## Contract approval gate

The interface contract is ready for implementation when:

- every route maps to a use case and requirement identifiers;
- code canonicalization, expected version, room scoping, and capability data
  are represented without exposing protected values;
- successful entry can atomically return a room result and set one room-scoped
  credential;
- file transfer streams and cross-room identifiers reveal no bytes;
- browser payment return is never an entitlement signal;
- each alternate use-case flow has a stable, non-sensitive outcome mapping.
