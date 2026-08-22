# 1. Design principles

**Status: Baseline**

## Product problem

Moving a short piece of text or a few files between people or devices is often
slower than the content deserves. Permanent drives require folders and
accounts; chat tools retain history; email makes the sender choose recipients.
MySend exists for the temporary handoff in between.

## Product promise

> Send what you need through one short-lived room, without requiring an
> account, and make the room's limits and disappearance obvious.

## Governing principles

### P1. No-login sharing is a complete path

A guest must be able to create, enter, use, and close a ShareRoom. Registration
may add continuity and capacity, but it must not unlock the basic act of
sharing.

### P2. Temporary is the default, not a cleanup option

Every room has an expiry at creation. A room is unusable after manual closure,
time expiry, or exhausting its entry count. Associated access tokens and files
must follow the room lifecycle.

### P3. One memorable locator

The primary handoff is one five-character access code: four digits followed by
one readable letter. Input is case-insensitive. Links and QR codes may be added
later, but the code remains sufficient by itself.

The current alphabet omits `I` and `O`, leaving 24 letters and 240,000 possible
codes. Availability is protected through a database uniqueness constraint and
bounded collision retries; capacity must be monitored before adding longer
room retention.

### P4. Limits are visible before they become errors

Lifetime, entry use, clipboard capacity, file capacity, privacy, and plan are
shown in context. The API remains authoritative, but the interface should stop
an invalid choice before submission whenever possible.

### P5. Owners control the room, not its visitors

The owner may change privacy, password, entry limit, expiry, content, and close
the room. Visitors may use the shared clipboard and file board after entry,
but cannot change room policy or delete files. MySend does not build visitor
profiles or expose visitor identity to the owner.

### P6. One room has two surfaces

Clipboard and file board belong to the same room, authorization, countdown,
and quota display. They should feel like two parts of one handoff rather than
separate products.

### P7. Payment buys capacity, not basic correctness

Guest, Free, and Premium use the same security and sharing model. Higher plans
increase active rooms, lifetime, clipboard size, file size, and entry count;
they do not receive a more reliable protocol.

### P8. Friction should match risk

Public rooms require only the access code. Private rooms may add a password.
Account creation requires a short-lived email verification code. Sensitive
browser mutations require an allowed origin and the MySend request marker.

### P9. Concurrent changes fail explicitly

Room content and settings use an observable version. A stale update receives a
conflict instead of silently overwriting a newer edit. The interface should
explain how to refresh and retry.

### P10. Operational simplicity cannot imitate production safety

The first deployment may use a modular monolith, one PostgreSQL database, and
mounted file storage. Production still requires HTTPS cookies, durable
storage, verified email delivery, signed billing webhooks, health checks, and
secret management.

## Fixed product constraints

| Constraint | Guest | Free | Premium |
| --- | ---: | ---: | ---: |
| Active rooms | 1 | 2 | 5 |
| Maximum lifetime | 15 minutes | 60 minutes | 180 minutes |
| Clipboard | 2,000 characters | 10,000 characters | 100,000 characters |
| Total room files | 256 MiB | 1 GiB | 5 GiB |
| Single file | 50 MiB | 250 MiB | 1 GiB |
| Successful entries | 20 | 100 | 1,000 |

All plans have a five-minute minimum room lifetime. Premium is currently
priced at CA$9.99 per month.

## Non-goals

MySend is not designed to become:

- a permanent cloud drive or backup service;
- a collaborative document editor with revision history;
- a social network, contact graph, or visitor analytics product;
- an anonymous public file index;
- a payment or card-data processor.

## Decision test

A design proposal is acceptable only when it can answer yes to all applicable
questions:

- Does guest sharing remain complete?
- Does the user know when and why content disappears?
- Is the access code still sufficient to complete the handoff?
- Are authorization and plan limits enforced by the API?
- Does failure preserve newer data and explain recovery?
- Can expired content be removed from both metadata and file storage?
- Can the core business rule be tested without external services?
