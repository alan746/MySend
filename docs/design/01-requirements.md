# 1. Requirements baseline

**Status: Baseline - approved before behavioural and technical design**

## Purpose

This document defines what MySend must do before deciding how a page,
controller, database table, or provider adapter will do it. Every requirement
is observable, testable, and assigned a stable identifier. Later design
documents may refine a requirement into flows and boundaries, but they may not
silently change its meaning.

The required design order is:

```text
product problem
  -> observable requirement
  -> use-case behaviour and failure order
  -> domain invariant and owner
  -> application boundary
  -> adapter and implementation choice
```

## Requirement writing standard

Each requirement contains:

1. an actor or a condition;
2. required behaviour;
3. an externally observable result;
4. a boundary or failure condition that prevents an ambiguous happy path.

The identifiers are permanent:

- `FR` - functional behaviour visible to a user or external actor;
- `QR` - measurable quality requirement that applies across use cases;
- `INV` - business invariant that must remain true at every boundary.

When a requirement is retired, its ID is marked superseded rather than reused.
Technology names appear only when the technology is itself part of the product
boundary, such as Stripe-hosted billing. Spring annotations, SQL tables, and
frontend components are never used as reasons for business rules.

## Actors and external conditions

| Actor or condition | Definition |
| --- | --- |
| Guest owner | A person who creates a room without an account and proves ownership through the same browser device. |
| Visitor | A person who submits another room's code and, when configured, its password. |
| Free member | A person with a verified account and the Free plan. |
| Premium member | A verified member whose subscription state grants the Premium plan. |
| Room participant | The room owner or a visitor who has completed a successful entry for that exact room. |
| Operator | The person responsible for deployment, capacity, retention, recovery, and incident response. |
| Clock | The authoritative current time used to decide expiry and retention. |
| Mail service | The external boundary that delivers a registration code. |
| Billing service | The external boundary that hosts payment and reports subscription lifecycle events. |

## Fixed product limits

These limits are product policy, not defaults owned by the user interface.
The room records the creator's plan limits at creation so an open room does not
change shape during a later upgrade or downgrade.

| Limit | Guest | Free | Premium |
| --- | ---: | ---: | ---: |
| Active rooms per owner | 1 | 2 | 5 |
| Maximum room lifetime | 15 minutes | 60 minutes | 180 minutes |
| Clipboard characters per room | 2,000 | 10,000 | 100,000 |
| Total files per room | 256 MiB | 1 GiB | 5 GiB |
| Maximum single file | 50 MiB | 250 MiB | 1 GiB |
| Successful visitor entries | 20 | 100 | 1,000 |

All plans use a five-minute minimum lifetime. Premium is CA$9.99 per month at
launch. A later pricing or quota change requires a new requirement decision
and does not mutate already-open rooms.

## Functional requirements

### Room creation and ownership

| ID | Requirement | Objective acceptance checks |
| --- | --- | --- |
| FR-01 | A person without an account shall be able to create a Guest ShareRoom without being redirected to registration or login. | In a browser with no account session, a valid create request returns one open Guest room; no account record or verification step is required. A second simultaneous Guest room is rejected without creating another room. |
| FR-02 | At creation, the owner shall choose room visibility, optional private-room password, lifetime, and successful-entry limit within the identified plan. | A choice inside every plan boundary creates the room with those values. A value outside any boundary rejects the entire operation and reports the invalid choice. |
| FR-03 | Every created room shall receive one canonical five-character access code consisting of four digits followed by one readable letter, with `I` and `O` excluded. | The returned code matches `0000A`, is unique among retained rooms, and can locate the same room after surrounding whitespace is removed and letter case is changed. Allocation failure creates no room. |
| FR-04 | Successful creation shall grant owner authority to the creating identity and return enough information to open the owner workspace immediately. | The creator opens the new room without entering its code or consuming a visitor entry. A different identity receives no owner-only controls. |

### Room entry and authorization

| ID | Requirement | Objective acceptance checks |
| --- | --- | --- |
| FR-05 | A visitor shall be able to enter an open public room by submitting only its access code. | A valid code for an open public room succeeds without account or password input and grants access to the workspace. |
| FR-06 | A private room shall support an owner-selected password; when a password exists, entry shall require the matching value. | Correct code and password succeeds. Missing or incorrect password fails without granting access or changing the successful-entry count. A private room created without a password remains code-only as explicitly chosen by the owner. |
| FR-07 | Each successful visitor entry shall consume exactly one successful-entry allowance and issue authorization for that room. | After one accepted entry, the count increases by one and the visitor can open the room. Invalid code, wrong password, closed room, and service failure consume zero entries and issue no authorization. |
| FR-08 | Entry shall fail once the successful-entry limit is reached, including when callers compete for the last allowance. | At most one of two concurrent requests for the final allowance succeeds; the count never exceeds the limit. Later attempts receive the same unavailable outcome as other logically closed rooms. |
| FR-09 | Visitor authorization shall apply to one room and shall end no later than that room becomes unavailable. | Authorization issued for room A cannot open room B. Closing or expiring room A makes the prior authorization unusable immediately. |
| FR-10 | An open room shall be readable only by its owner or a visitor who successfully entered that room. | An authorized participant receives room status, clipboard, file metadata, and plan limits. An unentered browser or unrelated account receives no room content. |

### Shared workspace and owner controls

| ID | Requirement | Objective acceptance checks |
| --- | --- | --- |
| FR-11 | Only the owner shall change visibility, password, lifetime, or entry limit. | An owner can save valid settings. A visitor or unrelated account cannot change them. Lifetime remains measured from original creation, and a new entry limit cannot be below entries already consumed or above the room plan. |
| FR-12 | Only the owner shall close a room, and closure shall be immediately terminal for room operations. | One close action makes subsequent entry, reads, clipboard changes, file operations, and settings changes fail. Repeating close does not reopen or duplicate the transition. |
| FR-13 | Every authorized participant shall be able to read the room's single shared clipboard. | Owner and entered visitor observe the same accepted clipboard value; an unauthorized caller observes none of it. |
| FR-14 | Every authorized participant shall be able to replace clipboard text within the room's character limit without silently overwriting a newer change. | A change at the last-read version succeeds and returns a new version. Oversized text is rejected unchanged. A stale version receives a conflict and preserves the newer stored text. |
| FR-15 | Every authorized participant shall be able to list and upload allowed files while both the single-file and total-room quotas remain satisfied. | Non-empty allowed files within both limits appear once in the file board after upload. Disallowed types, oversized files, or uploads beyond remaining room capacity are rejected without consuming quota. |
| FR-16 | Every authorized participant shall be able to download a file belonging to the same open room. | A listed file streams with its safe display name and content type. A file identifier from another room or a closed room returns no bytes. |
| FR-17 | Only the owner shall be able to delete a room file, and successful deletion shall release exactly that file's accounted bytes. | Owner deletion removes the file from later lists/downloads and decreases room usage once. Visitor deletion and repeated deletion do not alter usage. |

Allowed launch extensions are `pdf`, `txt`, `md`, `java`, `py`, `c`, `h`,
`cpp`, `hpp`, `doc`, `docx`, `jpg`, `jpeg`, `png`, `gif`, `webp`, `zip`, and
`json`. This allowlist does not assert that file contents are safe.

### Registration, authentication, and My ShareRooms

| ID | Requirement | Objective acceptance checks |
| --- | --- | --- |
| FR-18 | A new email/password registration shall require a six-digit email code that expires ten minutes after issue, and one normalized email shall register at most once. | A new normalized email receives a code challenge. The same email in different case cannot create another account. An expired, wrong, or already-used code creates no account. |
| FR-19 | Completing the first registration challenge shall create a signed-in Free account and claim still-open Guest rooms proven to belong to the same device. | A valid unused code creates exactly one Free account and session. Eligible open rooms appear under that account; closed, expired, exhausted, or foreign-device rooms do not. Repeating verification creates nothing additional. |
| FR-20 | A returning member shall sign in with normalized email and password without another email verification code. | Correct credentials create one usable account session and idempotently claim eligible rooms from the proven device. Incorrect credentials reveal neither whether the email exists nor its password state. |
| FR-21 | A signed-in member shall be able to sign out and revoke the current session. | After logout, the former session cannot call account-only actions. Guest create and join remain available. |
| FR-22 | Registration-code sends, wrong verification codes, and failed logins shall be rate-limited with a visible retry time. | Requests within policy succeed; excess attempts return a retry interval and do not send mail, create an account, or create a session. A later request after the interval can proceed. |
| FR-23 | My ShareRooms shall be available only to a signed-in member and shall list that account's logically open rooms. | Free and Premium members see owned or claimed open rooms in newest-first order. Guests, unrelated accounts, and closed/expired/exhausted rooms are excluded. |

### Premium billing

| ID | Requirement | Objective acceptance checks |
| --- | --- | --- |
| FR-24 | A signed-in Free member shall be able to start a CA$9.99 monthly Premium subscription through hosted checkout. | The member receives a provider-hosted checkout destination for the configured monthly product. A redirect or browser return alone does not change the MySend plan. |
| FR-25 | A member with an existing billing profile shall be able to open hosted billing management. | The account receives a short-lived provider destination. An account without a billing profile receives a recoverable unavailable result rather than another member's portal. |
| FR-26 | The account plan shall follow authentic subscription lifecycle events exactly once and in event order. | A valid new active-subscription event promotes the account; a valid cancellation or terminal event demotes it. Invalid, duplicate, or older events do not change the plan. Open rooms retain their creation-time plan limits. |

### Lifecycle and user feedback

| ID | Requirement | Objective acceptance checks |
| --- | --- | --- |
| FR-27 | A room shall become logically unavailable when the owner closes it, its expiry time is reached, or its successful-entry limit is exhausted. | At the first true closure condition, every later protected operation fails immediately without waiting for a cleanup process. The public outcome does not disclose whether an unknown room once existed. |
| FR-28 | Clipboard content, filenames, stored files, room authorizations, room metadata, and the reusable code reservation shall be physically removed within 24 hours after logical closure. | A retention check at the 24-hour deadline finds none of the listed content or credentials. A failed storage deletion keeps a retryable record and does not falsely release the code. |
| FR-29 | The room interface shall expose the access code, privacy mode, plan, time remaining, successful entries used/allowed, clipboard capacity, file capacity, and owner-only actions in the context where they apply. | Owner and participant views show the same room limits and status; only the owner sees policy/close/delete controls. On closure, editable controls are replaced by an unavailable explanation. |
| FR-30 | Each rejected action shall return a stable problem identifier and enough non-sensitive guidance for the interface to preserve input or offer the next valid action. | Clients can distinguish validation, wrong password, authorization, conflict, capacity, rate limit, closure, and provider failure without parsing prose. Responses never expose hashes, tokens, secrets, room content, or account existence beyond the actor's authorization. |

## Quality requirements

| ID | Quality requirement | Objective measure |
| --- | --- | --- |
| QR-01 | Entry consistency | Under concurrent entry tests, `0 <= accessCount <= accessLimit`; one accepted entry produces one increment and one room authorization. |
| QR-02 | Mutation consistency | Concurrent clipboard/settings mutations with the same expected version allow at most one success; no accepted value is silently lost. |
| QR-03 | Interactive performance | In the launch reference environment at 50 concurrent non-file users, the 95th percentile for room create, enter, read, and clipboard operations is at most 500 ms, excluding external mail/billing time. |
| QR-04 | File-transfer resource use | During a maximum-size transfer, no application buffer exceeds 16 MiB and no operation materializes the complete payload in process memory. |
| QR-05 | Accessibility | Guest create, public/private join, clipboard, file transfer, owner close, registration, and billing actions meet WCAG 2.2 AA checks and are keyboard operable without colour-only meaning. |
| QR-06 | Responsive presentation | Core journeys work without horizontal page scrolling or hidden primary actions from 360 CSS pixels through desktop layouts. |
| QR-07 | Credential protection | Production traffic is encrypted; account, device, and room credentials are unavailable to page scripts and absent from response bodies, URLs, logs, and stored plaintext. |
| QR-08 | Privacy | MySend creates no visitor profile for room entry and exposes no visitor identity to the owner; operational logs exclude clipboard and file contents. |
| QR-09 | Availability and recovery | Database or provider failure never acknowledges an uncommitted room/account/plan change; retry does not duplicate a successful operation. |
| QR-10 | Retention reliability | Cleanup runs often enough that every logically closed room meets FR-28, and an alert fires before any room reaches the 24-hour purge deadline. |
| QR-11 | Maintainability | All business rules and alternate flows can be tested with controlled time and fake boundaries without a browser, framework container, database, mail service, storage service, or billing service. |
| QR-12 | Observability | Operators can measure API failures by problem code, retained room/code occupancy, stored bytes, cleanup lag/retries, authentication throttles, mail failures, and billing-event rejection/backlog without logging user content or credentials. |
| QR-13 | Deployability | Web and API build independently, expose readiness checks, and production startup refuses missing or unsafe database, origin, cookie, storage, mail, and billing configuration. |

## Domain invariants

These statements are repeated here before the domain model so later design
must assign each one a single authoritative owner.

| ID | Invariant |
| --- | --- |
| INV-01 | The same access-code value is canonicalized and compared case-insensitively at every boundary; two retained rooms never share one canonical code. |
| INV-02 | A failed entry attempt never consumes a successful-entry allowance or issues room authorization. |
| INV-03 | Even under concurrency, a room's successful-entry count never exceeds its configured limit. |
| INV-04 | Once a room is logically closed, no later operation implicitly reopens it. |
| INV-05 | Room authorization is scoped to exactly one room and expires no later than that room. |
| INV-06 | Clipboard characters, individual file bytes, total file bytes, lifetime, active rooms, and successful entries never exceed the room's creation-time plan limits. |
| INV-07 | Only the proven owner changes room policy, closes the room, or deletes its files. |
| INV-08 | A room's plan snapshot is immutable even when its owner's current account plan changes. |
| INV-09 | One normalized email identifies at most one account, and a newly verified account begins on Free. |
| INV-10 | Only authentic, new, causally current billing events change a plan; one event changes it at most once. |
| INV-11 | File metadata, stored objects, and room byte accounting either describe the same accepted upload or remain recoverable for compensation. |
| INV-12 | Guest-room claim requires both a valid account session and proof of the device identity that owns the still-open room. |

## Requirement-to-use-case allocation

| Use case | Requirements allocated before detailed design |
| --- | --- |
| UC-01 Create ShareRoom | FR-01-FR-04, INV-01, INV-06, INV-08 |
| UC-02 Enter ShareRoom | FR-05-FR-09, INV-01-INV-05 |
| UC-03 Open authorized room | FR-09-FR-10, INV-04-INV-05 |
| UC-04 Update clipboard | FR-13-FR-14, QR-02, INV-04-INV-06 |
| UC-05 Use file board | FR-15-FR-17, QR-04, INV-05-INV-07, INV-11 |
| UC-06 Manage or close room | FR-11-FR-12, FR-27, INV-04, INV-06-INV-08 |
| UC-07 Register and authenticate | FR-18-FR-22, INV-09, INV-12 |
| UC-08 Load My ShareRooms | FR-23, INV-04, INV-12 |
| UC-09 Manage Premium | FR-24-FR-26, INV-08, INV-10 |
| UC-10 Expire and purge data | FR-27-FR-28, QR-10, INV-04-INV-05, INV-11 |
| Cross-cutting interface and operations | FR-29-FR-30, QR-03, QR-05-QR-09, QR-11-QR-13 |

## Scope exclusions

The baseline does not require permanent cloud storage, file history,
simultaneous rich-text editing, public room discovery, social identity,
visitor analytics, native applications, custom card collection, or a promise
that an allowed file is free of malware. Adding any of these starts with new
requirements rather than an adapter-only change.

## Requirements approval gate

Requirements are ready for use-case and domain design only when:

- every ID is unique and has an objective acceptance check;
- Guest create, join, workspace, and close form a complete no-login path;
- plan limits and Premium price agree across the product baseline;
- failure and concurrency conditions preserve INV-01 through INV-12;
- every FR is allocated to at least one use case or cross-cutting design;
- no implementation mechanism is presented as the reason a business rule
  exists.
