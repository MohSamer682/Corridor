# ADR-003: Access and refresh tokens

Status: Accepted, 2026-10-23

## Context

The browser needs a session that survives reloads without keeping a long-lived secret where scripts can read it.

## Decision

The app signs its own RS256 JWTs. The access token lasts 10 minutes and travels in the JSON body; the UI keeps it in memory. The refresh token lasts 7 days, lives in an `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/v1/auth`, is rotated on every use and is stored hashed. Reusing a rotated token revokes the whole family. Entra ID sign-in (F-14) ends in the same tokens.

## Consequences

A stolen access token expires quickly, and the refresh token can't be read by scripts. Rotation needs a row lock so two tabs refreshing together don't log the user out.
