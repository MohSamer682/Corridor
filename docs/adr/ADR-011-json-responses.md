# ADR-011: JSON response bodies

Status: Accepted, 2026-10-23

## Context

Clients handle responses more simply when every call returns JSON, including errors.

## Decision

Every endpoint returns a JSON body. Deletes and actions return 200 with `ApiResult {id, status}` instead of 204. Errors use RFC 9457 Problem Details.

## Consequences

The UI parses every response the same way. Responses are slightly larger than empty ones.
