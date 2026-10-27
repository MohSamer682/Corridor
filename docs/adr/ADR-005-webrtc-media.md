# ADR-005: Voice and video

Status: Accepted, 2026-10-23

## Context

People talk when they walk up to each other, in rooms and in large meetings. Media must not pass through the application server.

## Decision

Use WebRTC. Groups of 2 to 4 connect peer to peer, with signaling over STOMP. Groups of 5 or more, meeting rooms and the town hall use a LiveKit SFU. coturn provides TURN for restrictive networks.

## Consequences

Small conversations cost nothing on the server. LiveKit and coturn are two more services to run, and the switch between peer to peer and SFU needs care in the client.
