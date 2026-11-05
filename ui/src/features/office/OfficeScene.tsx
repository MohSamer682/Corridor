import { useEffect, useRef } from 'react';
import { OfficeEngine } from './scene/engine';
import type { OfficeSceneProps } from './types';

/** The 3D view; loaded as its own chunk so three.js stays out of the first page load. */
export function OfficeScene({
  view,
  tools,
  onReady,
  onOpenBuilding,
  onOpenFloor,
  onOpenBoard,
  onStudioUnavailable,
  onMove,
  meId,
}: OfficeSceneProps) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const labels = useRef<HTMLDivElement>(null);
  const engine = useRef<OfficeEngine | null>(null);
  const callbacks = useRef({
    onOpenBuilding,
    onOpenFloor,
    onOpenBoard,
    onStudioUnavailable,
    onMove,
  });
  useEffect(() => {
    callbacks.current = { onOpenBuilding, onOpenFloor, onOpenBoard, onStudioUnavailable, onMove };
  });

  useEffect(() => {
    if (!canvas.current || !labels.current) return;
    const created = new OfficeEngine(canvas.current, labels.current, {
      onOpenBuilding: (id) => callbacks.current.onOpenBuilding(id),
      onOpenFloor: (id) => callbacks.current.onOpenFloor(id),
      onOpenBoard: () => callbacks.current.onOpenBoard(),
      onStudioUnavailable: () => callbacks.current.onStudioUnavailable(),
      onMove: (pose) => callbacks.current.onMove(pose),
    });
    engine.current = created;
    onReady({
      rotate: (direction) => created.rotate(direction),
      zoomBy: (factor) => created.zoomBy(factor),
      lookAt: (x, z) => created.lookAt(x, z),
      walkTo: (x, y) => created.walkTo(x, y),
    });
    return () => {
      created.dispose();
      engine.current = null;
    };
  }, [onReady]);

  useEffect(() => {
    engine.current?.show(view);
  }, [view]);

  useEffect(() => {
    engine.current?.setTools(tools);
  }, [tools]);

  useEffect(() => {
    engine.current?.setMe(meId);
  }, [meId]);

  return (
    <>
      <canvas ref={canvas} className="block size-full cursor-grab" aria-label={describe(view)} />
      <div ref={labels} className="pointer-events-none absolute inset-0 overflow-hidden" />
    </>
  );
}

function describe(view: OfficeSceneProps['view']) {
  if (view.mode === 'campus')
    return '3D view of the campus. Use the campus card or a building label to go inside.';
  if (view.mode === 'building')
    return `3D view of ${view.building.name}, its floors stacked. Pick a floor from the list.`;
  return `3D view of ${view.building.name}, ${view.floor.code} · ${view.floor.name}. Click the floor or hold the arrow keys to walk; stop on a chair to sit. The Sprint board button opens the team's board.`;
}
