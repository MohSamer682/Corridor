# ADR-006: Presence state

Status: Accepted, 2026-10-23

## Context

Positions change many times a second and only matter while someone is online. Writing them to PostgreSQL would be wasted load.

## Decision

Live positions and tile reservations live in memory behind a `PresenceStore` port. F-15 adds a Redis adapter and pins each floor to one instance.

## Consequences

This is a deliberate exception to stateless services. A restart drops presence, and clients reconnect and re-enter their floor.
