# ADR-008: Floor layouts

Status: Accepted, 2026-10-23

## Context

Floor layouts are read on every floor visit and change rarely.

## Decision

Each layout is an immutable, versioned JSONB document holding the tile grid and furniture, served with a strong `ETag`. Editing a layout creates a new version.

## Consequences

Clients cache layouts and revalidate cheaply. Old versions stay for history, at the cost of some storage.
