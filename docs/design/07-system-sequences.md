# 7. System sequences

**Status: Baseline — runtime collaboration designed before adapters**

These diagrams connect the use cases to their adapters. They describe ordering
and trust boundaries; method names may evolve without changing the sequence.

## Create a guest room

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

## Update the clipboard with optimistic concurrency

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

## Register a Free account

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

## Upgrade and synchronize Premium

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
