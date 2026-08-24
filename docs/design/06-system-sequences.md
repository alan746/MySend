# 6. System sequences

**Status: Baseline — runtime collaboration designed before adapters**

These diagrams connect the use cases to their adapters. They describe ordering
and trust boundaries; method names may evolve without changing the sequence.
Each diagram names the requirement/use-case contract it realizes and draws the
failure order that adapters must preserve.

## Create a guest room

**Trace:** UC-01; FR-01-FR-04; INV-01, INV-06, INV-08

```mermaid
sequenceDiagram
    actor Guest
    participant Web as Home page
    participant RC as Room controller
    participant Identity as Device identity
    participant UC as Create-room use case
    participant DB as Room gateway

    Guest->>Web: Choose privacy, lifetime, entries
    Web->>RC: POST /api/rooms
    RC->>Identity: Resolve account or device
    Identity-->>RC: Guest owner and plan
    RC->>UC: Create room input
    UC->>DB: Count active rooms
    DB-->>UC: Active count
    UC->>UC: Validate plan limits
    loop Up to 64 allocation attempts
        UC->>DB: Is code unavailable?
        DB-->>UC: yes or no
    end
    UC->>DB: Save room
    DB-->>UC: Stored
    UC-->>RC: Owner room output
    RC-->>Web: 201 + device cookie if new
    Web-->>Guest: Navigate to /room/{code}
```

No room is written when identity resolution, plan validation, or code
allocation fails.

## Enter a private room

**Trace:** UC-02; FR-05-FR-09; QR-01; INV-01-INV-05

```mermaid
sequenceDiagram
    actor Visitor
    participant Web as Join form
    participant RC as Room controller
    participant UC as Enter-room use case
    participant DB as Room gateway
    participant Hash as Password hash port
    participant Token as Room-token gateway

    Visitor->>Web: Enter code and password
    Web->>RC: POST /api/rooms/enter
    RC->>UC: Normalized entry input
    UC->>DB: Load open room
    DB-->>UC: Room
    UC->>Hash: Compare password
    alt Password incorrect
        Hash-->>UC: false
        UC-->>RC: ROOM_PASSWORD_INCORRECT
        RC-->>Web: 401, count unchanged
    else Password correct
        Hash-->>UC: true
        UC->>DB: Atomically consume one entry
        alt Capacity lost concurrently
            DB-->>UC: not updated
            UC-->>RC: ROOM_CLOSED
            RC-->>Web: 410
        else Entry consumed
            DB-->>UC: updated room
            UC->>Token: Issue token until room expiry
            Token-->>UC: Plain token once
            UC-->>RC: Participant room output
            RC-->>Web: 200 + HTTP-only room cookie
            Web-->>Visitor: Open workspace
        end
    end
```

Password comparison always precedes entry consumption. Token issuance always
follows successful atomic consumption.

## Open an authorized room

**Trace:** UC-03; FR-09-FR-10, FR-27, FR-29-FR-30; INV-04-INV-05

```mermaid
sequenceDiagram
    actor User as Owner or participant
    participant Web as Room page
    participant RC as Room controller
    participant UC as Open-room use case
    participant Rooms as Room gateway
    participant Owner as Owner identity
    participant Grants as Room-grant gateway

    User->>Web: Open /room/{candidateCode}
    Web->>RC: GET room state with credentials
    RC->>UC: Candidate code and credential inputs
    UC->>UC: Canonicalize code
    UC->>Rooms: Find room by canonical code
    Rooms-->>UC: Room or absent
    UC->>UC: Evaluate logical closure at now
    alt Room absent or closed
        UC-->>RC: Unavailable result without content
        RC-->>Web: 404 or 410 problem
    else Room is open
        UC->>Owner: Does owner proof match room?
        Owner-->>UC: yes or no
        opt Owner proof does not match
            UC->>Grants: Validate grant for exact room and now
            Grants-->>UC: valid or invalid
        end
        alt Neither owner nor grant is valid
            UC-->>RC: ROOM_ACCESS_REQUIRED
            RC-->>Web: 401 without room content
        else Authorized
            UC-->>RC: Room result and capability set
            RC-->>Web: 200 room view
            Web-->>User: Render owner or participant workspace
        end
    end
```

Open-state evaluation precedes content projection. A grant is checked against
the loaded room identity rather than treated as general room access.

## Update the clipboard with optimistic concurrency

**Trace:** UC-04; FR-13-FR-14; QR-02; INV-04-INV-06

```mermaid
sequenceDiagram
    actor User as Room participant
    participant Web as Room page
    participant RC as Room controller
    participant Auth as Room authorization
    participant UC as Clipboard use case
    participant DB as Room gateway

    User->>Web: Edit and save text
    Web->>RC: PATCH clipboard {text, version}
    RC->>Auth: Owner identity or room token
    Auth-->>RC: Authorized room
    RC->>UC: Update input
    UC->>UC: Check plan character limit
    UC->>DB: UPDATE ... WHERE version = expected
    alt Version matches
        DB-->>UC: Updated and version incremented
        UC-->>Web: New room state
    else Version changed
        DB-->>UC: No row updated
        UC-->>Web: 409 ROOM_CHANGED
        Web-->>User: Explain refresh before retry
    end
```

## Upload a file

**Trace:** UC-05; FR-15-FR-17; QR-04; INV-04-INV-07, INV-11

```mermaid
sequenceDiagram
    actor User as Room participant
    participant Web as File board
    participant FC as File controller
    participant Auth as Room authorization
    participant UC as Upload use case
    participant RoomDB as Room gateway
    participant Store as Object store
    participant FileDB as File gateway

    User->>Web: Select file
    Web->>FC: multipart upload
    FC->>Auth: Authorize exact room
    Auth-->>FC: Open room and plan
    FC->>UC: Upload stream and metadata
    UC->>UC: Sanitize name and validate type/size
    UC->>RoomDB: Atomically reserve bytes
    alt Room quota exceeded
        RoomDB-->>UC: Rejected
        UC-->>Web: 413 ROOM_FILE_LIMIT
    else Bytes reserved
        UC->>Store: Store object by generated key
        Store-->>UC: Stored
        UC->>FileDB: Insert metadata
        FileDB-->>UC: Committed
        UC-->>Web: 201 file view
    end
```

**Target failure path:** storage or metadata failure releases reserved bytes;
a stored object without committed metadata is deleted immediately or queued for
orphan cleanup.

## Manage settings and close a room

**Trace:** UC-06; FR-11-FR-12, FR-27, FR-29-FR-30; QR-02; INV-04,
INV-06-INV-08

```mermaid
sequenceDiagram
    actor Owner
    participant Web as Owner controls
    participant RC as Room controller
    participant Identity as Owner identity
    participant UC as Manage-room use case
    participant Domain as ShareRoom
    participant Rooms as Room gateway

    Owner->>Web: Save policy with expected version
    Web->>RC: PATCH room settings
    RC->>Identity: Resolve owner proof
    Identity-->>RC: Owner identity
    RC->>UC: Policy command
    UC->>Rooms: Load room
    Rooms-->>UC: Room
    UC->>Domain: Require owner and open state
    UC->>Domain: Validate policy against snapshot and consumption
    UC->>Rooms: Update when version still matches
    alt Version or open state changed
        Rooms-->>UC: No update
        UC-->>Web: ROOM_CHANGED or ROOM_CLOSED
    else Policy accepted
        Rooms-->>UC: Updated room
        UC-->>Web: New room result and version
    end
    Owner->>Web: Confirm close
    Web->>RC: DELETE room
    RC->>UC: Close command with owner proof
    UC->>Rooms: Set closedAt once for owner and open room
    Rooms-->>UC: Closed or already terminal
    UC-->>Web: Completed close or terminal result
    Web-->>Owner: Replace workspace with closed view
```

Ownership and plan validation precede mutation. Close is monotonic: a repeated
or racing request never returns the room to Open.

## Register a Free account

**Trace:** UC-07; FR-18-FR-22; INV-09, INV-12

```mermaid
sequenceDiagram
    actor User
    participant Web as Settings page
    participant AC as Account controller
    participant UC as Registration use cases
    participant DB as Account and verification gateways
    participant Mail as SMTP adapter
    participant Session as Session gateway

    User->>Web: Submit email and password
    Web->>AC: POST /api/auth/register/code
    AC->>UC: Request verification
    UC->>DB: Check email and send rate limits
    UC->>DB: Store password hash and code hash
    UC->>Mail: Deliver six-digit code
    Mail-->>User: Verification email
    AC-->>Web: Expiry and delivery status
    User->>Web: Submit code
    Web->>AC: POST /api/auth/register/verify
    AC->>UC: Verify latest code
    UC->>DB: Atomically consume verification
    UC->>DB: Create unique Free account
    UC->>Session: Issue session
    Session-->>AC: Plain token once
    UC->>DB: Claim open rooms owned by device proof
    AC-->>Web: Account + HTTP-only session cookie
```

If mail delivery is not configured in production, the flow fails rather than
exposing the verification code. A development code may be returned only when
explicitly enabled outside production.

## Sign in a returning member

**Trace:** UC-07; FR-20-FR-22; QR-07-QR-09; INV-12

```mermaid
sequenceDiagram
    actor Member
    participant Web as Settings page
    participant AC as Account controller
    participant UC as Login use case
    participant Attempts as Attempt gateway
    participant Accounts as Account gateway
    participant Sessions as Session gateway
    participant Rooms as Guest-room claim gateway

    Member->>Web: Submit email and password
    Web->>AC: Login request with device proof
    AC->>UC: Normalized login input
    UC->>Attempts: Check failed-login allowance
    alt Rate limited
        Attempts-->>UC: Retry time
        UC-->>Web: AUTH_RATE_LIMITED
    else Attempt allowed
        UC->>Accounts: Find protected credentials by email
        Accounts-->>UC: Credentials or indistinguishable absence
        UC->>UC: Verify password
        alt Invalid credentials
            UC->>Attempts: Record failure
            UC-->>Web: Generic login failure
        else Valid credentials
            UC->>Sessions: Issue revocable account session
            UC->>Rooms: Claim open rooms for matching device owner
            Rooms-->>UC: Idempotent claim result
            UC-->>AC: Account result and session value once
            AC-->>Web: 200 plus protected session cookie
            Web-->>Member: My ShareRooms
        end
    end
```

Ordinary login has no email-code step. Device proof can add eligible rooms but
failure to claim a closed/foreign room cannot transfer ownership.

## Load My ShareRooms

**Trace:** UC-08; FR-23, FR-29-FR-30; INV-04, INV-12

```mermaid
sequenceDiagram
    actor Member
    participant Web as Settings page
    participant AC as Account controller
    participant Session as Account-session gateway
    participant UC as List-owned-rooms use case
    participant Rooms as Room gateway

    Member->>Web: Open My ShareRooms
    Web->>AC: GET owned active rooms
    AC->>Session: Resolve account session
    alt Session missing or revoked
        Session-->>AC: No account
        AC-->>Web: ACCOUNT_REQUIRED
    else Account resolved
        Session-->>AC: Account identity
        AC->>UC: List at current time
        UC->>Rooms: Find account-owned rooms
        Rooms-->>UC: Candidate rooms
        UC->>UC: Exclude closed, expired, and exhausted
        UC-->>Web: Newest-first room summaries
        Web-->>Member: Empty or populated list
    end
```

List membership never replaces room authorization; opening an item still uses
UC-03 and reevaluates logical closure.

## Upgrade and synchronize Premium

**Trace:** UC-09; FR-24-FR-26; INV-08, INV-10

```mermaid
sequenceDiagram
    actor Member
    participant Web as Settings page
    participant BC as Billing controller
    participant Billing as Billing use case
    participant Stripe as Stripe
    participant Events as Event gateway
    participant Accounts as Account gateway

    Member->>Web: Upgrade
    Web->>BC: POST /api/billing/checkout
    BC->>Billing: Create checkout for account
    Billing->>Stripe: Create hosted subscription checkout
    Stripe-->>Billing: Checkout URL
    Billing-->>Web: URL
    Web->>Stripe: Redirect
    Member->>Stripe: Complete payment
    Stripe->>BC: Signed checkout/subscription webhook
    BC->>Billing: Payload and signature
    Billing->>Billing: Verify HMAC and timestamp
    Billing->>Events: Claim event ID
    alt Event already processed
        Events-->>Billing: Existing claim
        Billing-->>Stripe: 200 without duplicate change
    else New event
        Events-->>Billing: Claimed
        Billing->>Accounts: Apply plan if event is not older
        Accounts-->>Billing: Updated
        Billing-->>Stripe: 200
    end
```

The redirect is not proof of payment. Only a verified webhook changes the plan.

## Expire and purge a room

**Trace:** UC-10; FR-27-FR-28; QR-10, QR-12; INV-04-INV-05, INV-11

```mermaid
sequenceDiagram
    participant Clock
    participant API as Room authorization
    participant Job as Cleanup worker
    participant DB as Repositories
    participant Store as File storage

    Clock->>API: now >= expiresAt
    API-->>API: Reject immediately as ROOM_CLOSED
    Clock->>Job: Scheduled cleanup tick
    Job->>DB: Delete expired tokens and transient auth data
    Job->>DB: Find rooms approaching 24-hour purge deadline
    loop Each purge-eligible room
        DB-->>Job: Room and file metadata
        Job->>Store: Delete each stored object
        alt Every object deleted
            Store-->>Job: Success
            Job->>DB: Delete room cascade
        else Storage failure
            Store-->>Job: Error
            Job-->>Job: Log and retain record for retry
        end
    end
```

Logical closure is part of request authorization. The scheduled job is not
allowed to be the mechanism that makes an expired room inaccessible.

## UI state transitions

```mermaid
stateDiagram-v2
    [*] --> HomeCreate
    HomeCreate --> Creating: submit valid choices
    Creating --> OwnerRoom: 201 created
    Creating --> HomeCreate: validation or service error
    [*] --> HomeJoin
    HomeJoin --> Entering: submit code
    Entering --> PasswordPrompt: room requires password
    PasswordPrompt --> Entering: submit password
    Entering --> ParticipantRoom: successful entry
    Entering --> HomeJoin: code or room failure
    OwnerRoom --> ClosedView: owner closes or room expires
    ParticipantRoom --> ClosedView: room expires or entries exhausted
    OwnerRoom --> ConflictView: stale version
    ConflictView --> OwnerRoom: refresh latest state
```
