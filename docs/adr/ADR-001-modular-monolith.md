# ADR-001: Modular monolith

Status: Accepted, 2026-10-23

## Context

Corridor starts with one small team and one company per deployment. Separate services would add network calls, deployments and data sync we don't need yet.

## Decision

Ship one Spring Boot application split into Spring Modulith modules (`identity`, `spaces`, `presence` and so on). Modules talk through published APIs and application events. `ModularityTests` fails the build when a module reaches into another module's internals.

## Consequences

One deployable and one database keep local setup and operations simple. A module can move to its own service later, but only with a new ADR. Module boundaries need discipline, and the test enforces it.
