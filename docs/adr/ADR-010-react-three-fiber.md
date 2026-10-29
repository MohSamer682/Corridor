# ADR-010: 3D rendering

Status: Accepted, 2026-10-23. Amended 2026-10-29.

## Context

The office is a 3D campus that must stay smooth on ordinary laptops. The approved prototype already renders it with about 450 lines of tuned three.js: geometry merged per material, instanced walls, procedural textures and ambient occlusion.

## Decision

Render with plain three.js, ported from the prototype into `features/office/scene`. One React component owns the canvas and passes the current view to an `OfficeEngine`. The HUD around the canvas is ordinary React. The 3D code loads as its own chunk on `/office` only.

The first version of this ADR chose React Three Fiber and drei. That would mean rewriting and re-tuning every prototype builder as components, plus two more dependencies, for the same picture.

## Consequences

- The scene matches the prototype and the first page load stays free of three.js.
- Scene code is imperative, so it is tested through its pure parts (camera fit, floor model) and end to end.
- The engine draws only while something moves, and drops shadows and post-processing on software WebGL.
- React Three Fiber stays an option for later declarative scenes.
