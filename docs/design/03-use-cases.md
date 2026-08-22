# 3. Use cases

**Status: Current business flows, expressed independently of UI details**

The use cases are the application policy. A page, controller, database, email
provider, or payment provider is an adapter around these flows rather than the
place where the rules originate.

## Use-case template

Each use case defines its actor, preconditions, trigger, normal flow,
alternatives, and success guarantee. Stable error codes belong to the output
contract so that a web or future mobile adapter can present the same failure.

## UC-01 — Create ShareRoom

**Primary actor:** Guest owner, Free member, or Premium member
**Preconditions:** None for a guest; an account session is optional.
**Trigger:** The actor submits visibility, optional password, lifetime, and
entry limit.

Normal flow:

1. Resolve the owner from a valid account session; otherwise resolve or create
   a device identity.
2. Select the plan from that identity.
3. Count the owner's currently active rooms.
4. Validate active-room count, lifetime, and entry limit against the plan.
5. Hash a non-blank password only when the room is private.
6. Generate an available four-digit and one-letter code.
7. Store an empty room with its plan snapshot, expiry, zero entries, zero file
   bytes, and version zero.
8. Return the owner view and navigate directly to the room.

Alternatives:

- Active-room limit reached → `ACTIVE_ROOM_LIMIT`.
- Lifetime outside the plan → `INVALID_LIFETIME`.
- Entry limit outside the plan → `INVALID_ACCESS_LIMIT`.
- Code allocation fails after bounded retries → creation fails without a room.

**Success guarantee:** One room exists, the creator has owner authority, and
the access code is unique among retained room records.

## UC-02 — Enter ShareRoom

**Primary actor:** Visitor
**Preconditions:** The room exists and is logically open.
**Trigger:** The visitor submits an access code and optional password.

Normal flow:

1. Trim and uppercase the code.
2. Reject a value that does not match `0000A` without disclosing another room.
3. Load the room and check manual closure, expiry, and remaining entries.
4. When a password hash exists, compare the submitted password.
5. Atomically increment the entry count only if the room is still open and has
   capacity.
6. Generate an opaque room token, store only its hash, and expire it with the
   room.
7. Return the participant view and set the room-specific HTTP-only cookie.

Alternatives:

- Unknown code → `ROOM_NOT_FOUND`.
- Wrong or missing password → `ROOM_PASSWORD_INCORRECT`; entry count unchanged.
- A concurrent entry consumes the last slot → `ROOM_CLOSED`; no token issued.

**Success guarantee:** Exactly one entry is consumed and the browser holds one
room-scoped credential valid no longer than the room.

## UC-03 — Open an authorized room

**Primary actor:** Owner or entered visitor
**Preconditions:** The room is logically open.
**Trigger:** The room page requests current state.

Normal flow:

1. Normalize and load the room code.
2. Resolve owner identity and the token for that exact room.
3. Allow the request when the owner key matches or the room token hash is valid.
4. Return room state and whether owner controls should be available.

Failure is `ROOM_ACCESS_REQUIRED`, `ROOM_NOT_FOUND`, or `ROOM_CLOSED`. A token
for one room never authorizes another room.

## UC-04 — Update clipboard

**Primary actor:** Authorized owner or visitor
**Preconditions:** UC-03 succeeds.
**Trigger:** The participant saves text with the version last read.

Normal flow:

1. Check the text length against the plan snapshot stored on the room.
2. Update only when the stored version equals the submitted version.
3. Increment the version and return the new room state.

Alternatives:

- Text exceeds the plan → `CLIPBOARD_LIMIT`.
- Another change already incremented the version → `ROOM_CHANGED`; no overwrite.
- The room closed between read and save → room-closed output.

**Success guarantee:** The new text is stored once and later stale writes
cannot silently replace it.

## UC-05 — Use file board

**Primary actor:** Authorized owner or visitor; owner for deletion
**Preconditions:** UC-03 succeeds.

List, upload, and download flow:

1. Authorize against the room.
2. For upload, sanitize the displayed filename and validate non-empty content,
   allowed extension, single-file limit, and remaining room bytes.
3. Atomically reserve file bytes, store the object, and record file metadata.
4. For download, load metadata scoped to the room and stream the stored object
   as an attachment with `nosniff`.

Delete flow:

1. Require room ownership.
2. Delete the stored object.
3. Delete its metadata and release its accounted bytes.

Failures include `EMPTY_FILE`, `FILE_TYPE_NOT_ALLOWED`, `SINGLE_FILE_LIMIT`,
`ROOM_FILE_LIMIT`, `FILE_STORE_FAILED`, `FILE_NOT_FOUND`, and owner-required
errors.

**Target compensation rule:** If object storage succeeds but metadata commit
fails, the object must be deleted or queued for orphan cleanup. This is not yet
fully represented by the local file adapter.

## UC-06 — Manage or close room

**Primary actor:** Room owner
**Preconditions:** Owner identity matches an open room.
**Trigger:** The owner changes privacy, password, expiry, entry limit, or closes
the room.

Normal settings flow:

1. Validate the entry limit is not below entries already consumed and not
   above the room plan.
2. Calculate expiry from original creation time and reject a value beyond the
   plan maximum.
3. Clear the password when switching public; preserve or replace its hash when
   private.
4. Apply settings only at the submitted room version and increment the version.

Close flow sets `closedAt` once. All later access treats the room as gone even
before physical cleanup.

## UC-07 — Register and authenticate

**Primary actor:** New or returning member

Registration request:

1. Normalize email and reject an existing account.
2. Enforce a 60-second resend cooldown and five sends per hour.
3. Hash the submitted password and generate a six-digit code.
4. Store only a salted password hash and a one-way verification-code hash.
5. Expire the verification after ten minutes and deliver it through SMTP.

Verification:

1. Enforce five failed codes per ten-minute window.
2. Find the newest usable verification and compare the derived code hash.
3. Atomically consume it, create a Free account, and issue an account session.

Login enforces five failures per fifteen minutes, compares the password hash,
clears recorded failures on success, and issues a session. Logout revokes the
session and expires its cookie.

## UC-08 — Load My ShareRooms

**Primary actor:** Free or Premium member
**Preconditions:** A valid account session.
**Flow:** Resolve `account:<id>` ownership and return its active rooms newest
first. Guests receive `ACCOUNT_REQUIRED` because a device room list is not an
account feature.

**Open design decision:** A guest room is not currently claimed when its device
registers or signs in. Before public launch, choose either an explicit
“attach this room” flow or a separate scoped owner proof so authentication does
not unexpectedly hide owner controls for an active guest room.

## UC-09 — Manage Premium

**Primary actor:** Authenticated member

Checkout flow:

1. Require an account session.
2. Create a Stripe subscription checkout for the configured Premium price.
3. Reuse the Stripe customer when known; otherwise supply the account email.
4. Redirect the browser to Stripe-hosted checkout.
5. Accept only a correctly signed, recent webhook.
6. Claim the event ID once and synchronize customer, subscription, and plan.

Portal flow creates a Stripe-hosted billing-management URL for an account with
a billing profile. Subscription creation, update, pause, resume, or deletion
changes the account plan according to the received event and timestamp.

Existing rooms retain the plan snapshot captured at creation; upgrading or
downgrading changes limits for newly created rooms.

## UC-10 — Expire and purge data

**Primary actor:** Clock and cleanup worker

Logical closure is immediate when any condition becomes true:

- `closedAt` is set;
- `expiresAt` is not after the current time;
- `accessCount` reaches `accessLimit`.

The scheduled cleanup then:

1. deletes expired room tokens, account sessions, and verification records;
2. deletes authentication-attempt records older than one day;
3. finds room records whose manual close or expiry is older than the retention
   cutoff;
4. deletes every stored file for each room;
5. deletes the room record, allowing its access code to be used again;
6. leaves the record intact and logs a warning when file deletion fails so a
   later run can retry.

The current cleanup interval is fifteen minutes and the current room retention
cutoff is seven days. The capacity effect of that retention is reviewed in the
operations design.
