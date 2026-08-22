# 4. Domain model

**Status: Current model with explicit target invariants**

## Ubiquitous language

| Term | Meaning |
| --- | --- |
| ShareRoom | Time- and entry-bounded workspace containing one clipboard and one file board |
| Owner | Device or account identity allowed to manage the room |
| Participant | Owner or visitor holding valid room authorization |
| Entry | One successful code/password exchange that issues a room token |
| Plan snapshot | Guest, Free, or Premium limits captured on the room at creation |
| Logical closure | Immediate denial caused by manual close, time expiry, or entry exhaustion |
| Physical purge | Later deletion of files, tokens, and room metadata |

## Conceptual model

```mermaid
classDiagram
    class Account {
        +id
        +email
        +passwordHash
        +plan
        +stripeCustomerId
        +stripeSubscriptionId
    }
    class AppSession {
        +tokenHash
        +expiresAt
    }
    class EmailVerification {
        +email
        +passwordHash
        +codeHash
        +expiresAt
        +consumedAt
    }
    class Room {
        +id
        +accessCode
        +ownerKey
        +plan
        +visibility
        +accessLimit
        +accessCount
        +clipboardText
        +fileBytes
        +expiresAt
        +closedAt
        +version
        +isClosedAt(now)
        +remainingEntries()
    }
    class RoomAccessToken {
        +tokenHash
        +expiresAt
    }
    class RoomFile {
        +storageKey
        +originalName
        +contentType
        +sizeBytes
        +uploadedAt
    }
    class AuthenticationAttempt {
        +email
        +attemptType
        +attemptedAt
    }
    class StripeEvent {
        +eventId
        +eventType
        +eventCreatedAt
        +processedAt
    }
    class Plan {
        +activeRooms
        +maximumLifetimeMinutes
        +clipboardCharacters
        +roomFileBytes
        +singleFileBytes
        +maximumEntries
    }

    Account "1" --> "0..*" AppSession
    Account "0..1" --> "0..*" Room : owns
    Account --> Plan
    Room --> Plan : snapshots
    Room "1" --> "0..*" RoomAccessToken
    Room "1" --> "0..*" RoomFile
```

Email verifications and authentication attempts are keyed by normalized email
before an account necessarily exists. Stripe events form an idempotency ledger
rather than a child collection of an account.

## Aggregate boundaries

### Room aggregate

`Room` is the consistency boundary for access count, plan limits, clipboard,
file-byte accounting, settings, expiry, and version. `RoomFile` content lives in
external storage, but its metadata and byte count must agree with the Room
aggregate.

Key invariants:

- access code is exactly four digits followed by one uppercase letter;
- access code is unique among retained rooms;
- `0 <= accessCount <= accessLimit <= plan.maximumEntries`;
- `expiresAt` is between five minutes and the plan maximum after `createdAt`;
- clipboard length is no greater than the plan snapshot;
- `0 <= fileBytes <= plan.roomFileBytes`;
- every room token expires no later than its room;
- only an owner changes settings, closes the room, or deletes a file;
- a participant may read and update shared content only while the room is open;
- each successful mutation increments `version`.

### Account aggregate

`Account` owns normalized email uniqueness, password hash, plan, and Stripe
references. Sessions are revocable credentials for the account. The account
plan changes only from verified local registration or an accepted Stripe event.

Key invariants:

- normalized email is unique;
- a newly verified account starts on Free;
- plaintext passwords, verification codes, session tokens, and Stripe webhook
  secrets are never stored in domain records;
- an older Stripe event cannot overwrite newer subscription state;
- one Stripe event ID is processed at most once.

## Room state model

```mermaid
stateDiagram-v2
    [*] --> Open: room created
    Open --> Closed: owner closes
    Open --> Expired: expiresAt reached
    Open --> Exhausted: accessCount reaches accessLimit
    Closed --> PurgeEligible: retention cutoff reached
    Expired --> PurgeEligible: retention cutoff reached
    Exhausted --> PurgeEligible: retention policy reached
    PurgeEligible --> Purged: files and metadata deleted
    PurgeEligible --> PurgeEligible: storage deletion failed; retry later
    Purged --> [*]
```

`Closed`, `Expired`, and `Exhausted` are all logically unavailable. They are
shown separately because their cause matters for operations even though the
public response is intentionally the same.

## Access-code capacity

The generator uses 10,000 numeric prefixes and 24 readable letters, for a
theoretical pool of 240,000 codes. A code remains unavailable until its room
record is physically deleted, not merely until the room closes.

With the current seven-day record retention, occupancy is approximately the
number of rooms created during the preceding week. The operating target is to
keep retained occupancy below 50% of the pool and alert well before that point.
At 120,000 retained rooms, that corresponds to an average of roughly 17,000
new rooms per day across seven days.

Before traffic approaches that level, choose one of these target changes:

1. shorten closed-room retention while deleting content immediately;
2. separate operational audit records from the reusable access-code row; or
3. enlarge the code format while preserving spoken readability.

## Persistence ownership

| Data | Authoritative store | Retention rule |
| --- | --- | --- |
| Account and plan | PostgreSQL | Until account deletion is designed and requested |
| Account session | PostgreSQL, hashed token | Until revocation or expiry |
| Verification | PostgreSQL, hashed code | Ten-minute usability; expired rows cleaned later |
| Room metadata and clipboard | PostgreSQL | Logically unavailable at closure; current physical purge after seven days |
| Room token | PostgreSQL, hashed token | No later than room expiry |
| File metadata | PostgreSQL | Purged with its room |
| File bytes | Mounted or object storage | Purged before the room record is removed |
| Authentication attempt | PostgreSQL | One-day cleanup horizon |
| Stripe event claim | PostgreSQL | Retained for webhook idempotency and audit |

## Known domain decisions still open

- How active Guest ownership transfers when that device signs in or registers.
- Whether exhausted rooms should appear in My ShareRooms until physical expiry;
  the intended design says no, while the current list query needs alignment.
- Whether room content should be physically purged immediately while a minimal
  non-content tombstone remains for operations.
- Whether a Premium downgrade should affect already open Premium rooms. The
  current plan snapshot allows those rooms to finish under their original
  limits, bounded by a maximum of three hours.
