# ADR-009: Jira integration

Status: Accepted, 2026-10-23

## Context

The workboard shows Jira issues inside the office. Calling Jira on every page load would be slow and would hit rate limits.

## Decision

Cache issues in `jira_issues`, refreshed by Jira webhooks and a 5-minute poll. The office never calls Jira while a page loads.

## Consequences

Pages stay fast and keep working when Jira is down. Data can be up to five minutes old when a webhook is missed.
