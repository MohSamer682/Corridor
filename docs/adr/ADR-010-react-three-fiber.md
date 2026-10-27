# ADR-010: 3D rendering

Status: Accepted, 2026-10-23

## Context

The office is a 3D campus that must stay smooth on ordinary laptops.

## Decision

Render with three.js through React Three Fiber 8 and drei 9 on React 18. Geometry is merged per material and repeated furniture is instanced, as in the prototype.

## Consequences

The scene fits the React component model and stays fast. React 19 waits until React Three Fiber 9 is adopted.
