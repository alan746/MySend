# MySend pre-development design

This directory contains the decisions that must exist before a MySend feature
is implemented. It starts from the product problem and intended interaction,
then derives use cases, domain concepts, system boundaries, interfaces,
security, and operations.

Implementation is evaluated against this baseline. Existing code is not used
as the reason for a product or architecture decision.

```mermaid
flowchart LR
    A["0. Design brief"] --> B["1. Principles"]
    B --> C["2. Requirements"]
    C --> D["3. User interaction"]
    D --> E["4. Use cases"]
    E --> F["5. Domain model"]
    F --> G["6. System architecture"]
    G --> H["7. System sequences"]
    H --> I["8. Interface contracts"]
    I --> J["9. Security and operations"]
    J --> K["Issues and implementation"]
    K --> L["Validation and release"]
```

## Required reading order

| Stage | Decision made before coding | Exit gate |
| --- | --- | --- |
| [0. Design brief](00-design-brief.md) | Product scope, chosen experience, system shape, major trade-offs | Stakeholders agree on what is being built and why. |
| [1. Design principles](01-design-principles.md) | Rules that later design must preserve | A proposed feature can be accepted or rejected consistently. |
| [2. Requirements and user stories](02-requirements-and-user-stories.md) | Actors, outcomes, functional and quality requirements | Every requirement is testable and traceable. |
| [3. User interaction design](03-user-interaction-design.md) | Navigation, wireflows, screen composition, states, and feedback | A user can complete every primary journey on paper. |
| [4. Use cases](04-use-cases.md) | Framework-independent normal and alternate application flows | Business behaviour is unambiguous without choosing controllers or tables. |
| [5. Domain model](05-domain-model.md) | Entities, values, relationships, lifecycle, and invariants | Each business rule has one authoritative representation. |
| [6. System architecture](06-system-architecture.md) | Containers, components, CA boundaries, ports, adapters, and technology choices | Dependency direction and component ownership are agreed. |
| [7. System sequences](07-system-sequences.md) | Runtime collaboration and failure ordering | Important journeys cross each boundary in a defined order. |
| [8. Interface and API design](08-interface-and-api-design.md) | UI contract, HTTP resources, cookies, errors, and file policy | Independent adapters can implement against one contract. |
| [9. Security, operations, and validation](09-security-operations-and-validation.md) | Trust boundaries, retention, deployment, monitoring, recovery, and tests | The design is safe and practical to operate. |

## Design language

- **Baseline** — an approved decision that implementation must follow.
- **Open question** — a decision that blocks the affected implementation and
  must receive an owner and deadline.
- **Superseded** — a previous decision retained for history with a link to its
  replacement.

Design documents do not use “current implementation” as a status. Code
alignment belongs in issues, pull requests, and test results.

## Traceability

| Product area | Requirements | Interaction | Use cases | Architecture owner |
| --- | --- | --- | --- | --- |
| Create and enter rooms | FR-01–FR-08 | Home create/join flows | UC-01–UC-03, UC-06 | Room application component |
| Clipboard and files | FR-09–FR-12 | ShareRoom workspace | UC-04–UC-05 | Room and file components |
| Accounts and room continuity | FR-13–FR-18 | Settings and My ShareRooms | UC-07–UC-08 | Account component |
| Premium | FR-19–FR-21 | Upgrade/manage billing | UC-09 | Billing component |
| Expiry and cleanup | FR-22–FR-23 | Closed-room state | UC-10 | Lifecycle component |

## Change rule

A later decision cannot silently override an earlier one. For example, a
permanent file-history API conflicts with the temporary-by-default principle;
the principle and requirements must be deliberately revised before API design
or implementation begins.

Every implementation issue must link:

1. the principle and requirement it serves;
2. the interaction and use-case step it changes;
3. the domain invariant and architecture boundary it uses;
4. the interface, security, retention, and test obligations it introduces.
