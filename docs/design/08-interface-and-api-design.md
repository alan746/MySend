# 8. Interface and API design

**Status: Baseline — interaction and adapter contract approved before coding**

## Information architecture

| Route | Primary job | Authentication |
| --- | --- | --- |
| `/` | Explain the product, create a room, or start room entry | None |
| `/room/{code}` | Clipboard, file board, room status, and owner controls | Owner identity or room access token |
| `/settings` | Register, sign in/out, compare plans, billing, and My ShareRooms | Page is public; account actions require a session |

The home page must keep Create and Join above supporting explanation. Settings
must not imply that registration is required for the core sharing path.

## Visual principles

- Use editorial hierarchy, grid, whitespace, and a small number of purposeful
  accents rather than interchangeable dashboard cards.
- Let type scale and position create hierarchy; do not make every element the
  same font, weight, radius, or alignment.
- Reserve the bright lime accent for active choice, status, or action. The
  purple Settings accent distinguishes account context without recolouring the
  full product.
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

Unauthenticated state exposes Sign in and Create account tabs, the Premium
comparison, and a clear route back to guest use. Authenticated state adds
account identity, sign out, My ShareRooms, and either Upgrade or Manage billing.

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

| Method and path | Purpose | Authorization | Success |
| --- | --- | --- | --- |
| `POST /api/rooms` | Create room | Device or account owner identity | `201 RoomView` |
| `POST /api/rooms/enter` | Validate entry and issue room cookie | Code and optional password | `200 RoomView` + cookie |
| `GET /api/rooms` | List owned active rooms | Account session | `200 RoomView[]` |
| `GET /api/rooms/{code}` | Load room state | Owner or room token | `200 RoomView` |
| `PATCH /api/rooms/{code}/clipboard` | Save clipboard at expected version | Owner or room token | `200 RoomView` |
| `PATCH /api/rooms/{code}/settings` | Change room policy | Owner only | `200 RoomView` |
| `DELETE /api/rooms/{code}` | Close immediately | Owner only | `204` |

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

### File endpoints

| Method and path | Purpose | Authorization | Success |
| --- | --- | --- | --- |
| `GET /api/rooms/{code}/files` | List file metadata | Owner or room token | `200 FileView[]` |
| `POST /api/rooms/{code}/files` | Upload multipart field `file` | Owner or room token | `201 FileView` |
| `GET /api/rooms/{code}/files/{fileId}` | Download as attachment | Owner or room token | Streamed bytes |
| `DELETE /api/rooms/{code}/files/{fileId}` | Delete and release quota | Owner only | `204` |

Allowed extensions are `pdf`, `txt`, `md`, `java`, `py`, `c`, `h`, `cpp`,
`hpp`, `doc`, `docx`, `jpg`, `jpeg`, `png`, `gif`, `webp`, `zip`, and `json`.
Extension allowlisting is a first barrier, not a substitute for malware
scanning in public production.

### Account endpoints

| Method and path | Purpose | Success |
| --- | --- | --- |
| `POST /api/auth/register/code` | Store pending credentials and send code | Verification expiry/delivery status |
| `POST /api/auth/register/verify` | Consume six-digit code and create Free account | `200 AccountView` + session cookie |
| `POST /api/auth/login` | Authenticate email/password | `200 AccountView` + session cookie |
| `GET /api/auth/me` | Resolve current account | `200 AccountView` or `401` |
| `POST /api/auth/logout` | Revoke and clear session | `204` |

`AccountView` contains identity, plan and relevant limits, plus whether a
Stripe billing profile is available. It never includes password or session
material.

### Billing endpoints

| Method and path | Purpose | Authorization |
| --- | --- | --- |
| `POST /api/billing/checkout` | Create Stripe-hosted subscription checkout | Account session |
| `POST /api/billing/portal` | Create Stripe-hosted billing portal | Account session and Stripe customer |
| `POST /api/billing/webhook` | Synchronize signed subscription events | Stripe signature, no browser marker |

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
