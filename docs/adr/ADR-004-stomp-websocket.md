# ADR-004: Real-time transport

Status: Accepted, 2026-10-23

## Context

Presence, chat and WebRTC signaling need server push with low latency.

## Decision

Use WebSocket with STOMP on Spring MVC, authenticated with the access token on `CONNECT`. Version 1 uses the simple in-memory broker. A RabbitMQ STOMP relay replaces it once there are two instances (F-15).

## Consequences

STOMP gives us topics and per-user queues without a custom protocol. The simple broker works on one instance only, which is fine until F-15.
