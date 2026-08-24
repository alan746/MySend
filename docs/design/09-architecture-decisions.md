# 9. Architecture decision records

**Status: Baseline - decisions accepted for the first deployable product**

## Purpose

Requirements and domain rules explain what must remain true. An Architecture
Decision Record (ADR) explains a consequential solution choice, the options
considered, and what would cause the choice to be revisited. An ADR does not
turn a technology into a business requirement.

## Decision index

| ADR | Decision | Requirements/drivers | Status |
| --- | --- | --- | --- |
| ADR-001 | Modular monolith with inward dependencies | QR-09, QR-11, QR-13 | Accepted |
| ADR-002 | Java 21, Spring Boot, and Maven for the API | QR-11, QR-13; project constraint | Accepted |
| ADR-003 | React and TypeScript responsive web client | FR-01, FR-05, FR-29; QR-05-QR-06 | Accepted |
| ADR-004 | Opaque server-side browser credentials | FR-09, FR-20-FR-21; QR-07; INV-05 | Accepted |
| ADR-005 | PostgreSQL for transactional metadata | QR-01-QR-02, INV-01, INV-03, INV-09-INV-10 | Accepted |
| ADR-006 | File bytes outside PostgreSQL behind a storage port | FR-15-FR-17; QR-04; INV-11 | Accepted |
| ADR-007 | Stripe-hosted subscription billing | FR-24-FR-26; INV-10 | Accepted |
| ADR-008 | Immutable plan snapshot per room | FR-02, FR-26; INV-06, INV-08 | Accepted |
| ADR-009 | Immediate logical closure plus asynchronous purge | FR-27-FR-28; QR-10; INV-04, INV-11 | Accepted |
| ADR-010 | Five-character code retained until safe purge | FR-03, FR-28; INV-01 | Accepted with capacity review |

## ADR-001 - Modular monolith with inward dependencies

**Status:** Accepted

**Context:** Ten use cases share room/account state and require atomic entry,
version, claim, billing-event, and cleanup decisions. One small team must build
and operate the first release while keeping business policy isolated from
delivery technology.

**Decision:** Deliver one API process organized into room, file, account,
billing, and lifecycle application modules. Source dependencies point from
frameworks/adapters to application boundaries and then domain concepts.

**Alternatives considered:**

- microservices per application area;
- framework-centered controller/service/repository packages;
- a browser-only peer-to-peer product.

**Consequences:** Transactions and deployment remain simple, and use cases can
be tested with fake ports. Module boundaries require review and dependency
checks because process isolation does not enforce them. Independent service
scaling is deferred.

**Revisit when:** An application area has sustained independent scale,
security-isolation, availability, or team-ownership needs that outweigh
distributed transactions, deployment, and tracing cost.

## ADR-002 - Java 21, Spring Boot, and Maven for the API

**Status:** Accepted

**Context:** The project explicitly prefers a Java/Maven backend and needs HTTP,
validation, credential protection, scheduling, health, database migration,
and provider adapters without writing infrastructure from scratch.

**Decision:** Implement the API with Java 21, Spring Boot 3.x, and a Maven
build. Spring remains in composition and adapter/application classes; domain
objects do not depend on Spring annotations.

**Alternatives considered:** Python/FastAPI, Node.js, and Java without an
application framework.

**Consequences:** The stack has mature security and testing support and aligns
with the intended learning/maintenance path. Startup/memory cost is higher
than a small Python or Node prototype, and careless annotation use can erase
the designed boundaries.

**Revisit when:** Measured hosting constraints cannot be met after normal JVM
tuning, or the team formally changes its long-term backend language.

## ADR-003 - React and TypeScript responsive web client

**Status:** Accepted

**Context:** MySend must open on a new device without installation, provide
fast Create/Join interactions, maintain clipboard/file progress, and adapt from
360-pixel mobile layouts to desktop.

**Decision:** Use React with TypeScript for the responsive web interface and a
typed API adapter. Pages own presentation and temporary form state; use-case
policy remains in the API.

**Alternatives considered:** Server-rendered Java templates, native mobile
applications, and an untyped JavaScript single-page client.

**Consequences:** UI iteration and contract typing are strong, and web delivery
keeps access friction low. Web/API builds and deployments must remain
compatible, and duplicate client-side limit hints can never become authority.

**Revisit when:** A measured no-JavaScript/accessibility requirement or native
device capability becomes central enough to justify another adapter.

## ADR-004 - Opaque server-side browser credentials

**Status:** Accepted

**Context:** Guest owners need durable device proof, members need revocable
sessions, and visitors need grants scoped to one short-lived room. Browser
scripts do not need to inspect any credential.

**Decision:** Issue random opaque device, account-session, and room-grant
values through HTTP-only cookies. Retain only protected representations on the
server. Scope room grants by room identity and never beyond room expiry.

**Alternatives considered:** Local-storage bearer tokens, JWTs for every
identity, URL query tokens, and repeated code/password entry for every room
operation.

**Consequences:** Credentials are unavailable to ordinary page scripts and can
be revoked centrally. Server-side records require cleanup and shared storage
when the API scales. Cookie transport requires exact origin controls and CSRF
design.

**Revisit when:** A separately owned API client cannot use protected cookies,
or session storage becomes a measured scaling bottleneck after shared-state
options are evaluated.

## ADR-005 - PostgreSQL for transactional metadata

**Status:** Accepted

**Context:** Retained room codes and normalized account emails must be unique;
the final entry and same-version mutation need atomic decisions; billing events
must be claimed once; state must survive process restarts.

**Decision:** Use PostgreSQL as the production system of record for room,
account, authorization, file metadata/reservations, and billing-event claims.
Apply schema changes through versioned migrations and expose persistence only
through application-owned gateway interfaces.

**Alternatives considered:** In-memory state, a document database, embedded
file database in production, and provider-specific managed records.

**Consequences:** Conditional changes and uniqueness constraints reinforce the
domain invariants and support multiple API instances. Operating cost includes
connections, migrations, backup/restore, and relational mapping. SQL remains
an adapter detail.

**Revisit when:** A measured workload cannot meet requirements with suitable
indexes/partitioning, or a specific bounded context can be extracted without
weakening its consistency rules.

## ADR-006 - File bytes outside PostgreSQL behind a storage port

**Status:** Accepted

**Context:** A Premium room may hold 5 GiB and one file may be 1 GiB. Transfers
must stream, while metadata and byte accounting remain transactional and purge
must delete physical objects.

**Decision:** Store file bytes in durable shared/object storage addressed by
generated object identities. Keep room-scoped metadata, reservations, and byte
accounting in PostgreSQL. The file application uses a storage port and explicit
finalize/compensate states.

**Alternatives considered:** Database binary values, local ephemeral disk,
client filenames as storage paths, and direct browser-to-storage as the only
launch path.

**Consequences:** Large transfer and lifecycle operations are separated from
database backup and can stream efficiently. Cross-resource failure requires
durable reservation, compensation, orphan monitoring, and independent storage
recovery. Local mounted storage is acceptable only for private preview when
declared persistent.

**Revisit when:** Direct signed uploads are needed for measured bandwidth
scaling; the same authorization, quota reservation, scan, finalization, and
purge contracts must remain.

## ADR-007 - Stripe-hosted subscription billing

**Status:** Accepted

**Context:** Premium is a monthly subscription, but MySend should not collect
card data or build payment-method, tax, invoice, and cancellation interfaces.

**Decision:** Use Stripe-hosted Checkout and Billing Portal. Treat an
authenticated, current, idempotently claimed Stripe event as the only source
of MySend plan transitions.

**Alternatives considered:** Custom card forms, another hosted subscription
provider, and manual operator-managed Premium flags.

**Consequences:** Card handling and billing UI remain with the provider, which
reduces compliance scope. The product depends on provider availability,
webhook authentication/retry, event-order handling, and a configured recurring
price. Browser redirects can show pending state only.

**Revisit when:** Regional/payment-method coverage, cost, or provider
availability no longer supports the product; a replacement must preserve the
same hosted-boundary and event-authority rules.

## ADR-008 - Immutable plan snapshot per room

**Status:** Accepted

**Context:** An owner may upgrade or downgrade while rooms remain open for at
most three hours. Applying the account's current plan on every room operation
could unexpectedly shrink or expand an existing room.

**Decision:** Copy the full plan policy into the ShareRoom at creation. Account
plan changes affect later rooms only.

**Alternatives considered:** Resolve current account plan on every operation,
immediately migrate all active rooms, or store only the plan name and read a
mutable global table.

**Consequences:** Open-room behaviour is predictable and historical policy is
testable. Snapshot data is repeated and pricing/quota changes need explicit
version policy for new rooms. A downgraded Premium room can retain larger
limits only until its existing maximum three-hour lifetime ends.

**Revisit when:** Room lifetimes become long enough that grandfathered capacity
creates material cost or abuse; migration then requires a new product
requirement and user-facing transition rule.

## ADR-009 - Immediate logical closure plus asynchronous purge

**Status:** Accepted

**Context:** A scheduler cannot be the mechanism that makes an expired room
private. File-provider failure can also prevent safe physical deletion during
the first cleanup attempt.

**Decision:** Evaluate logical closure during every protected room operation.
Run idempotent cleanup separately, delete file objects before metadata/code
reservation, retain retry state on failure, and alert before the 24-hour purge
deadline.

**Alternatives considered:** Scheduler-only expiry, immediate synchronous purge
inside the user request, and deleting metadata before file objects.

**Consequences:** Access stops on time even when cleanup is delayed, while user
requests avoid long storage deletion work. Closed metadata exists temporarily,
cleanup must be observable/retryable, and code reuse waits for safe purge.

**Revisit when:** Retention requirements change, or cleanup volume requires a
separate claimed work queue/service while keeping the same authorization and
deletion ordering.

## ADR-010 - Five-character code retained until safe purge

**Status:** Accepted with capacity review

**Context:** The spoken locator is intentionally short: 10,000 numeric prefixes
times 24 readable letters equals 240,000 codes. Reusing a code while content or
authorization remains can connect a stale browser to the wrong room.

**Decision:** A canonical code is unique among retained rooms and is released
only after content, room grants, metadata, and file objects are safely purged.
Monitor retained occupancy; review at 25% and stop unreviewed growth at 50%.

**Alternatives considered:** Immediate reuse at logical closure, a longer code
at launch, ambiguous letters, and permanent non-reuse.

**Consequences:** Stale room credentials cannot cross into a newly allocated
room with the same code, and the spoken format stays simple. Retention directly
consumes finite code capacity, so purge failure and traffic growth become
capacity signals.

**Revisit when:** Retained occupancy reaches 60,000, collision retries materially
affect creation, or the 24-hour objective cannot be met. Options are shorter
reservation retention with a safe tombstone, a larger readable alphabet/format,
or a separate generation component with equivalent stale-credential safety.

## ADR change rule

An accepted ADR can be superseded, not silently edited into a different
decision. Its replacement must link the affected FR/QR/INV identifiers, name
data/API migration effects, and update architecture, operations, and validation
evidence before implementation changes merge.
