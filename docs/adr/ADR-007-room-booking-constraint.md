# ADR-007: Room booking

Status: Accepted, 2026-10-23

## Context

Two people can book the same room at the same time. Checking overlaps in Java has a race between the check and the insert.

## Decision

A PostgreSQL exclusion constraint on room and time range (`btree_gist`) rejects overlapping meetings. The API maps the violation to 409.

## Consequences

The database guarantees no double bookings, whatever the concurrency. The constraint needs the `btree_gist` extension.
