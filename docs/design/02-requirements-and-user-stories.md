# 2. Requirements and user stories

**Status: Baseline — approved before implementation**

## Actors

| Actor | Description | Identity |
| --- | --- | --- |
| Guest owner | Creates a room without registering | Long-lived random device cookie, stored server-side only as a hash-derived owner key |
| Visitor | Enters another person's room | Short-lived room access token issued after successful entry |
| Free member | Verified account with higher limits and My ShareRooms | Account session cookie |
| Premium member | Paying account with Premium limits | Account session plus subscription state synchronized by Stripe |
| Room cleanup worker | Removes expired authorization data and old room content | Scheduled server process |
| Mail provider | Delivers registration verification codes | SMTP boundary |
| Stripe | Hosts checkout and billing management and sends subscription events | Signed webhook boundary |

## User stories

### Sharing

- As a guest, I want to create a temporary room without registering so that I
  can start a handoff immediately.
- As an owner, I want a short, case-insensitive access code so that I can say or
  type it easily.
- As an owner, I want to choose public or private entry so that the room has the
  right amount of friction.
- As an owner, I want to limit time and successful entries so that access ends
  when the handoff is complete.
- As a visitor, I want to enter with the code and optional password so that I
  can reach the shared content without creating an account.

### Workspace

- As a room participant, I want one shared clipboard so that I can move text,
  links, and code snippets.
- As a room participant, I want to upload and download common file types so
  that text and files can be handed off together.
- As an owner, I want to see and change room controls so that I stay in control
  of privacy, remaining entries, and closure.
- As a participant, I want conflicts explained instead of overwritten so that
  another tab's newer work is not lost silently.

### Accounts and payment

- As a repeat user, I want to register with a verified email so that I can see
  my active rooms and receive higher Free limits.
- As a member, I want to sign in and out so that my account is not tied to one
  browser device.
- As a Free member, I want to upgrade through a trusted hosted checkout so that
  MySend never handles my card details.
- As a Premium member, I want to manage or cancel billing through Stripe so
  that subscription changes are reflected in my MySend plan.

### Lifecycle

- As any participant, I want closed rooms to reject access immediately so that
  expiry is real rather than cosmetic.
- As an operator, I want expired tokens, sessions, verification records, room
  metadata, and files removed predictably so that temporary data does not grow
  forever.

## Functional requirements

| ID | Requirement | Acceptance summary |
| --- | --- | --- |
| FR-01 | Create without login | A new browser can create one Guest room with no account step. |
| FR-02 | Plan-aware creation | Active room, lifetime, clipboard, file, and entry limits come from the owner's plan. |
| FR-03 | Memorable access code | Creation returns a unique `0000A` code; lookup normalizes case and whitespace. |
| FR-04 | Public/private mode | Public rooms need the code; private rooms may additionally require a password. |
| FR-05 | Entry accounting | Only a successful entry atomically increments the count and issues access. |
| FR-06 | Entry exhaustion | Entry at the limit fails and the room is treated as closed. |
| FR-07 | Authorized room read | Owners use owner identity; visitors use an unexpired room token. |
| FR-08 | Owner controls | Only the owner changes room settings or closes the room. |
| FR-09 | Shared clipboard | Authorized participants read and update clipboard text within the room plan limit. |
| FR-10 | Conflict protection | Clipboard and settings updates require the expected room version. |
| FR-11 | File board | Authorized participants list, upload, and download allowed files within both quotas. |
| FR-12 | File deletion | Only the room owner deletes a file and releases its accounted bytes. |
| FR-13 | Unique account email | Normalized email can belong to only one account. |
| FR-14 | Verification request | Registration stores a password hash and sends a six-digit, ten-minute code. |
| FR-15 | Verification completion | A valid unused code creates a Free account/session and claims open rooms proven by the same device identity. |
| FR-16 | Login/logout | Valid credentials create a session and claim that device's open Guest rooms; logout revokes the session and clears the cookie. |
| FR-17 | Authentication throttling | Verification sends, wrong codes, and failed logins are rate-limited. |
| FR-18 | My ShareRooms | Only an authenticated member can list logically open rooms owned or claimed by that account. |
| FR-19 | Premium checkout | An authenticated Free member receives a Stripe-hosted subscription checkout URL. |
| FR-20 | Billing portal | A member with a Stripe customer can open Stripe-hosted billing management. |
| FR-21 | Subscription synchronization | Valid, idempotent Stripe events promote or demote the account plan. |
| FR-22 | Logical expiry | Manual close, elapsed expiry, or used entries makes room operations fail immediately. |
| FR-23 | Physical cleanup | Expired transient records are purged; room content and code reservation are removed within 24 hours of logical closure. |

## Quality requirements

| ID | Requirement | Design measure |
| --- | --- | --- |
| NFR-01 | Security | Passwords use adaptive hashes; opaque tokens are stored hashed; mutation origin and marker are checked. |
| NFR-02 | Privacy | No visitor account is required and no visitor identity is exposed to the room owner. |
| NFR-03 | Consistency | Entry consumption and versioned changes use atomic database predicates. |
| NFR-04 | Availability | Health probes cover application and database readiness; external billing failure does not corrupt plan state. |
| NFR-05 | Recoverability | A failed change returns a stable code and message; file cleanup failures retain metadata for retry. |
| NFR-06 | Accessibility | Main actions are keyboard reachable, labeled, responsive, and do not depend on colour alone. |
| NFR-07 | Maintainability | Business rules remain in domain/use-case code and are covered without requiring external services. |
| NFR-08 | Deployability | Web and API build independently; production refuses unsafe placeholder configuration. |
| NFR-09 | Observability | Health, failed mail, billing webhook rejection, cleanup failure, storage use, and rate limits are observable. |
| NFR-10 | Performance | Normal room reads and clipboard changes use bounded database work; file bytes stream instead of loading fully in memory. |

## Requirement-to-use-case map

| Use case | Requirements |
| --- | --- |
| UC-01 Create ShareRoom | FR-01–FR-04 |
| UC-02 Enter ShareRoom | FR-03–FR-06 |
| UC-03 Open authorized room | FR-07 |
| UC-04 Update clipboard | FR-09–FR-10 |
| UC-05 Use file board | FR-11–FR-12 |
| UC-06 Manage or close room | FR-08, FR-10, FR-22 |
| UC-07 Register and authenticate | FR-13–FR-17 |
| UC-08 Load My ShareRooms | FR-18 |
| UC-09 Manage Premium | FR-19–FR-21 |
| UC-10 Expire and purge data | FR-22–FR-23 |
