# ADR-002: UUIDv7 primary keys

Status: Accepted, 2026-10-23

## Context

IDs appear in URLs and API responses, so sequential numbers would leak counts and invite guessing. Random UUIDv4 keys fragment B-tree indexes on insert.

## Decision

Every table uses `id UUID PRIMARY KEY`. Hibernate creates the value with `@UuidGenerator(style = VERSION_7)`.

## Consequences

Keys are time-ordered, so inserts stay index-friendly, and they are safe to expose. They take 16 bytes instead of 8, which is acceptable at our size.
