# 2. Use cases

**Status: Baseline - application behaviour approved before interface adapters**

## Purpose

A use case describes an actor's goal, decision order, and success and failure
guarantees. It does not name a page component, controller method, table, or
framework annotation. Those are later adapters around the behaviour defined
here.

The requirement identifiers come from [1. Requirements
baseline](01-requirements.md). Stable outcome codes are part of the use-case
output boundary so different interfaces can present the same behaviour.

## Use-case template

Every use case records:

- requirements and invariants served;
- primary actor, preconditions, and trigger;
- normal flow in business decision order;
- alternate/failure flows, including concurrency where relevant;
- success and minimum failure guarantees.

## UC-01 - Create ShareRoom

**Requirements:** FR-01-FR-04, FR-29-FR-30, QR-09, INV-01, INV-06, INV-08  
**Primary actor:** Guest owner, Free member, or Premium member  
**Preconditions:** None for a guest; account authentication is optional.  
**Trigger:** The actor submits visibility, optional private password, lifetime,
and successful-entry limit.

Normal flow:

1. Resolve an authenticated account owner when present; otherwise resolve or
   establish the browser's device owner identity.
2. Select the owner's Guest, Free, or Premium plan policy.
3. Determine how many of that owner's rooms are currently open.
4. Validate active-room count, lifetime, successful-entry limit, visibility,
   and password choice against that policy.
5. Generate an available canonical access code.
6. Establish `createdAt`, `expiresAt`, empty clipboard, zero successful
   entries, zero file bytes, and the immutable plan snapshot.
7. Create one ShareRoom and save it as a complete unit.
8. Return the owner-facing room result so the actor can open it immediately.

Alternate flows:

- **A1 - Active-room limit:** The owner already has the plan maximum -> return
  `ACTIVE_ROOM_LIMIT`; create no room.
- **A2 - Invalid lifetime:** The requested lifetime is below five minutes or
  above the plan maximum -> return `INVALID_LIFETIME`; create no room.
- **A3 - Invalid entries:** The requested allowance is below one or above the
  plan maximum -> return `INVALID_ACCESS_LIMIT`; create no room.
- **A4 - Invalid privacy choice:** A public room carries password data or a
  password violates the accepted credential policy -> return
  `INVALID_ROOM_POLICY`; create no room.
- **A5 - Code collision:** A generated code is retained by another room ->
  generate another code within a bounded attempt count.
- **A6 - Code capacity unavailable:** No code is allocated within that bound ->
  return `CODE_CAPACITY_UNAVAILABLE`; create no room.
- **A7 - Save failure:** The room cannot be saved -> report a retryable failure
  and do not present the code as created.

**Success guarantee:** Exactly one open room exists with one owner, one unique
canonical code, and one immutable plan snapshot; owner access consumes no
visitor entry.  
**Minimum failure guarantee:** No partially created room or owner result is
observable.

## UC-02 - Enter ShareRoom

**Requirements:** FR-05-FR-09, FR-27, FR-30, QR-01, INV-01-INV-05  
**Primary actor:** Visitor  
**Preconditions:** The visitor knows a candidate access code.  
**Trigger:** The visitor submits the code and optional room password.

Normal flow:

1. Trim and canonicalize the submitted code.
2. Find the room identified by the canonical code.
3. Reject the room if manual closure, expiry, or entry exhaustion is already
   true.
4. When password protection exists, verify the submitted password.
5. Prepare a new opaque authorization grant that is scoped to this room and
   expires no later than the room.
6. Atomically recheck open state and capacity, consume exactly one successful
   entry, and retain the authorization grant.
7. Return the participant room result and the grant value once.

Alternate flows:

- **A1 - Malformed or unknown code:** No room can be identified -> return an
  unavailable outcome; consume no entry and reveal no room content.
- **A2 - Missing or wrong password:** Verification fails -> return
  `ROOM_PASSWORD_INCORRECT`; consume no entry and issue no grant.
- **A3 - Already unavailable:** The room is closed, expired, or exhausted ->
  return `ROOM_CLOSED`; consume no entry.
- **A4 - Closure race:** The room expires or is closed between lookup and the
  atomic entry decision -> the atomic decision fails; issue no grant.
- **A5 - Final-entry race:** Two callers compete for the last allowance -> at
  most one atomic decision succeeds.
- **A6 - Grant persistence failure:** Entry and grant cannot be committed as
  one consistent result -> neither is acknowledged as successful.

**Success guarantee:** The count increases exactly once and the returned grant
authorizes only that room for no longer than its remaining lifetime.  
**Minimum failure guarantee:** INV-02 holds: zero entries are consumed and no
authorization is issued.

## UC-03 - Open authorized room

**Requirements:** FR-09-FR-10, FR-27, FR-29-FR-30, INV-04-INV-05  
**Primary actor:** Room owner or entered visitor  
**Preconditions:** The actor presents owner proof or a room authorization
grant.  
**Trigger:** The actor requests the current room workspace.

Normal flow:

1. Canonicalize and find the room.
2. Check that it is still logically open at the authoritative current time.
3. Accept owner proof that matches the room, or validate an unexpired grant
   scoped to the exact room.
4. Return room status, plan limits, clipboard, file usage, version, and an
   owner/participant capability description.

Alternate flows:

- **A1 - Unknown code:** Return `ROOM_NOT_FOUND` without room content.
- **A2 - Closed room:** Return `ROOM_CLOSED`, even when an old grant exists.
- **A3 - No matching proof:** Return `ROOM_ACCESS_REQUIRED`.
- **A4 - Cross-room grant:** A grant for another room is treated as no proof.
- **A5 - Revoked/expired grant:** Return `ROOM_ACCESS_REQUIRED`.

**Success guarantee:** The result contains only data and capabilities for one
open room.  
**Minimum failure guarantee:** No clipboard, filename, file usage, or owner
information is disclosed.

## UC-04 - Update clipboard

**Requirements:** FR-13-FR-14, FR-27, FR-30, QR-02, INV-04-INV-06  
**Primary actor:** Authorized room participant  
**Preconditions:** UC-03 authorization can succeed; the actor has the version
last read.  
**Trigger:** The participant submits replacement text and expected version.

Normal flow:

1. Authorize the participant for the exact open room.
2. Count characters and compare the result with the room's plan snapshot.
3. Replace the clipboard only when the room remains open and the stored
   version equals the submitted version.
4. Increment the room version once.
5. Return the accepted clipboard value, count, and new version.

Alternate flows:

- **A1 - Access missing:** Return `ROOM_ACCESS_REQUIRED`; change nothing.
- **A2 - Character limit:** Return `CLIPBOARD_LIMIT`; preserve stored text and
  report the applicable limit.
- **A3 - Stale version:** Return `ROOM_CHANGED`; preserve the newer stored
  value and provide the current version for refresh.
- **A4 - Closure race:** If the room closes before the conditional change,
  return `ROOM_CLOSED`; change nothing.
- **A5 - Save failure:** Do not acknowledge the submitted value; a retry with
  the same expected version remains safe.

**Success guarantee:** One accepted value replaces the previous clipboard and
advances the version once.  
**Minimum failure guarantee:** Existing clipboard content is not overwritten.

## UC-05 - Use file board

**Requirements:** FR-15-FR-17, FR-27, FR-30, QR-04, INV-04-INV-07, INV-11  
**Primary actor:** Authorized participant; room owner for deletion  
**Preconditions:** The room is open and the actor is authorized.  
**Trigger:** The actor lists, uploads, downloads, or deletes a room file.

List flow:

1. Authorize the actor for the exact room.
2. Return safe file metadata scoped to that room in a stable order.

Upload flow:

1. Validate non-empty content, safe display name, allowed extension, and the
   single-file limit.
2. Reserve the declared bytes only if total room usage remains within the plan
   snapshot and the room remains open.
3. Store the bytes under a generated storage identity unrelated to the client
   filename.
4. Record metadata that binds the stored object to this room.
5. Finalize byte accounting and return one file result.

Download flow:

1. Find file metadata only within the authorized room.
2. Stream the corresponding object as a download using the safe display name.

Delete flow:

1. Prove room ownership.
2. Remove the stored object and its room-scoped metadata.
3. Release exactly its accounted bytes once.

Alternate flows:

- **A1 - Empty/disallowed file:** Return `EMPTY_FILE` or
  `FILE_TYPE_NOT_ALLOWED`; reserve no bytes.
- **A2 - Quota failure:** Return `SINGLE_FILE_LIMIT` or `ROOM_FILE_LIMIT`;
  reserve no bytes.
- **A3 - Cross-room identifier:** Return `FILE_NOT_FOUND`; disclose no other
  room metadata or bytes.
- **A4 - Visitor delete:** Return `ROOM_OWNER_REQUIRED`; retain file and usage.
- **A5 - Object write failure:** Release the reservation and return
  `FILE_STORE_FAILED`.
- **A6 - Metadata failure after object write:** Delete or quarantine the orphan
  object and release the reservation; retain a retryable compensation record
  if immediate compensation fails.
- **A7 - Delete failure:** Retain metadata and accounted bytes so cleanup can
  retry; do not claim successful deletion.
- **A8 - Closure race:** Reject finalization when the room closes and execute
  the same compensation guarantees.

**Success guarantee:** File metadata, stored bytes, and room usage describe one
accepted operation.  
**Minimum failure guarantee:** Quota cannot be permanently lost and an orphan
remains discoverable for compensation.

## UC-06 - Manage or close room

**Requirements:** FR-11-FR-12, FR-27, FR-29-FR-30, QR-02, INV-04, INV-06-INV-08  
**Primary actor:** Room owner  
**Preconditions:** The owner proves control of an open room and supplies the
version last read.  
**Trigger:** The owner changes policy or requests immediate closure.

Settings flow:

1. Prove ownership.
2. Validate visibility/password, lifetime, and successful-entry limit against
   the room's immutable plan snapshot and current consumption.
3. Derive expiry from original creation time.
4. Apply the complete policy change only if the expected version still
   matches and the room remains open.
5. Increment version and return the accepted room result.

Close flow:

1. Prove ownership.
2. Record logical closure once at the authoritative current time.
3. Revoke the usefulness of every prior participant grant through the room's
   open-state rule.
4. Return a completed close result.

Alternate flows:

- **A1 - Not owner:** Return `ROOM_OWNER_REQUIRED`; change nothing.
- **A2 - Invalid policy:** Return the specific limit/policy outcome; preserve
  all current settings.
- **A3 - Entries already consumed:** A proposed limit below the current count
  returns `INVALID_ACCESS_LIMIT`.
- **A4 - Stale version:** Return `ROOM_CHANGED`; preserve the newer policy.
- **A5 - Closure race/repeated close:** Return the terminal closed outcome and
  never reopen the room.

**Success guarantee:** Settings change atomically, or the room becomes
terminally unavailable.  
**Minimum failure guarantee:** Prior accepted policy remains intact.

## UC-07 - Register and authenticate

**Requirements:** FR-18-FR-22, FR-30, QR-07-QR-09, INV-09, INV-12  
**Primary actor:** New or returning member  
**Preconditions:** Registration requires access to the supplied email;
ordinary login requires an existing verified account.  
**Trigger:** The actor requests registration, verifies a code, signs in, or
signs out.

Registration-request flow:

1. Normalize the email and validate the password policy.
2. Reject an email that already identifies an account.
3. Enforce the 60-second send cooldown and five-send rolling-hour allowance.
4. Create a six-digit challenge that expires after ten minutes, retaining only
   protected password and challenge representations.
5. Ask the mail boundary to deliver the challenge.
6. Return expiry and resend timing without returning the production code.

Verification flow:

1. Enforce at most five wrong codes for the normalized email in ten minutes.
2. Find the newest usable challenge for the normalized email.
3. Verify and atomically consume the challenge.
4. Create exactly one Free account and one account session.
5. When the same request proves a device owner, claim only that device's
   still-open Guest rooms, idempotently.
6. Return the signed-in account result.

Login flow:

1. Normalize email and enforce at most five failed logins for that email in
   fifteen minutes.
2. Verify email/password without distinguishing which value was wrong.
3. Issue an account session and clear relevant failure history.
4. Apply the same proven-device Guest-room claim.

Logout flow revokes the current session and returns a completed result.

Alternate flows:

- **A1 - Existing email:** Return `EMAIL_ALREADY_REGISTERED`; create no
  challenge or account.
- **A2 - Mail failure:** Return `MAIL_UNAVAILABLE`; create no bypass to a
  production account.
- **A3 - Wrong/expired/consumed code:** Return the appropriate verification
  outcome; create no account/session.
- **A4 - Concurrent verification:** At most one caller consumes the challenge
  and creates the unique account.
- **A5 - Invalid credentials:** Return one generic login failure; issue no
  session.
- **A6 - Rate limit:** Return `AUTH_RATE_LIMITED` with retry timing; perform no
  protected action.
- **A7 - Foreign or unavailable Guest room:** Do not claim it; registration or
  login can still succeed.
- **A8 - Repeated logout:** The session remains revoked; Guest sharing remains
  available.

**Success guarantee:** The actor has one revocable session for the intended
account; newly verified accounts start on Free.  
**Minimum failure guarantee:** No account/session is created from unproven
email or invalid credentials, and no foreign room is claimed.

## UC-08 - Load My ShareRooms

**Requirements:** FR-23, FR-29-FR-30, INV-04, INV-12  
**Primary actor:** Free or Premium member  
**Preconditions:** A valid account session.  
**Trigger:** The member opens or refreshes My ShareRooms.

Normal flow:

1. Resolve the account from its active session.
2. Find rooms owned by that account.
3. Exclude manually closed, expired, and entry-exhausted rooms at the current
   time.
4. Return the remaining rooms newest first with status and plan limits.

Alternate flows:

- **A1 - No session:** Return `ACCOUNT_REQUIRED`.
- **A2 - Guest device only:** Do not provide a device room list; return
  `ACCOUNT_REQUIRED`.
- **A3 - No open rooms:** Return an empty collection, not an error.
- **A4 - Room closes during read:** Exclude it or let the later open operation
  return `ROOM_CLOSED`; never grant access from list membership alone.

**Success guarantee:** The result contains only logically open rooms owned by
the current account.  
**Minimum failure guarantee:** No room from another owner is disclosed.

## UC-09 - Manage Premium

**Requirements:** FR-24-FR-26, FR-30, QR-09, INV-08, INV-10  
**Primary actor:** Authenticated member; billing service for subscription
events  
**Preconditions:** Checkout/portal requires an account session; event handling
requires an authentic provider event.  
**Trigger:** The member starts checkout/portal, or the billing service reports
a lifecycle event.

Checkout flow:

1. Resolve the Free account.
2. Request hosted subscription checkout for the configured CA$9.99 monthly
   Premium product, reusing its billing customer when known.
3. Return the short-lived hosted destination.

Portal flow resolves an account with a billing profile and returns a hosted
billing-management destination.

Subscription-event flow:

1. Authenticate the event and reject an unacceptable timestamp.
2. Claim the event identifier once.
3. Map the supported subscription state to Free or Premium.
4. Apply it only when the event is not older than the account's accepted
   subscription state.
5. Retain the event result and acknowledge processing.

Alternate flows:

- **A1 - No account:** Return `ACCOUNT_REQUIRED`; create no provider session.
- **A2 - No billing profile:** Portal returns `BILLING_PROFILE_REQUIRED`.
- **A3 - Provider unavailable:** Return a retryable outcome; leave plan
  unchanged.
- **A4 - Browser return without event:** Show pending/current plan; do not
  promote from the redirect.
- **A5 - Invalid event:** Reject it; leave plan unchanged.
- **A6 - Duplicate event:** Return the recorded successful handling without a
  second plan transition.
- **A7 - Older event:** Record/acknowledge according to policy but do not
  overwrite newer subscription state.

**Success guarantee:** Hosted billing is available to the intended account and
only an authentic, current, once-claimed event changes plan.  
**Minimum failure guarantee:** Existing account plan and open-room snapshots
remain unchanged.

## UC-10 - Expire and purge data

**Requirements:** FR-27-FR-28, FR-30, QR-10, QR-12, INV-04-INV-05, INV-11  
**Primary actor:** Clock and cleanup operator  
**Preconditions:** Transient records or rooms have reached their cleanup
condition.  
**Trigger:** Any protected room operation evaluates time, or a cleanup cycle
starts.

Logical-closure flow:

1. Evaluate manual closure, expiry time, and successful-entry exhaustion at
   every protected room operation.
2. Deny the operation immediately when any condition is true.

Physical-cleanup flow:

1. Delete expired room grants, account sessions, verification challenges, and
   obsolete authentication-attempt records according to their policies.
2. Find rooms whose logical closure is approaching the 24-hour purge deadline.
3. For each room, enumerate all file metadata and request deletion of every
   stored object.
4. Only after all objects are absent, delete file metadata, room content,
   authorization records, room metadata, and the retained code reservation.
5. Record cleanup evidence and remaining purge lag.

Alternate flows:

- **A1 - Storage deletion failure:** Retain retryable metadata and code
  reservation; retry later and alert before the deadline.
- **A2 - Partial object deletion:** Continue idempotently from retained
  metadata; never restore room access.
- **A3 - Concurrent room request:** Logical closure still denies the request;
  physical cleanup timing does not control authorization.
- **A4 - Cleanup worker overlap:** Claim a room once or make every delete
  idempotent so duplicate cycles cannot corrupt accounting.
- **A5 - Deadline risk:** Emit an operational alert with eligible room and lag
  counts before 24 hours.

**Success guarantee:** Closed content, credentials, metadata, and reusable code
reservation are absent by the requirement deadline.  
**Minimum failure guarantee:** The room remains inaccessible and retained
metadata is sufficient for safe retry.

## Use-case approval gate

The use cases are ready for domain design when:

- every allocated FR and INV appears in at least one use case;
- normal flows are stated without controller, table, or framework ownership;
- all business validation happens before irreversible effects where possible;
- failed entry, final-entry concurrency, stale update, upload compensation,
  billing replay, Guest-room claim, and cleanup retry are explicit;
- every flow states what remains true after success and after failure.
