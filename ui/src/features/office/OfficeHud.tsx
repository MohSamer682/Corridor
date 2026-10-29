import { useEffect, useRef, type ReactNode } from 'react';
import { Link } from 'react-router';
import { useSessionStore } from '../auth/sessionStore';
import { floorModel, paintPlan } from './floorModel';
import type { Building, Campus, FloorLayout, FloorSummary, OfficeControls, Tools } from './types';

// The world keeps the prototype's daylight look in both themes, so its HUD uses fixed light colours.
export const glass =
  'rounded-xl border border-[rgba(20,27,45,.08)] bg-[rgba(255,255,255,.9)] text-[#141B2D] shadow-[0_6px_20px_rgba(20,27,45,.1)] backdrop-blur-[10px]';
const hudButton =
  'flex min-h-9 cursor-pointer items-center gap-1.5 rounded-lg border-0 bg-transparent px-2.5 text-[13px] font-medium text-[#2C3448] hover:bg-[#EEF1F5] aria-pressed:bg-[#23395D] aria-pressed:text-white focus-visible:outline-2 focus-visible:outline-[#23395D]';

const floorTitle = (floor: FloorSummary) => `${floor.code} · ${floor.name}`;

function Icon({ d, size = 16 }: { d: string; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d={d} />
    </svg>
  );
}

export function Breadcrumb({
  building,
  floor,
  onCampus,
  onBuilding,
}: {
  building?: Building;
  floor?: FloorSummary;
  onCampus: () => void;
  onBuilding: (id: string) => void;
}) {
  const company = useSessionStore((state) => state.user?.organizationName) ?? 'Campus';
  const crumb =
    'cursor-pointer rounded-md border-0 bg-transparent px-1.5 py-1 text-[13px] font-medium text-[#5B6478] hover:bg-[#EEF1F5] hover:text-[#141B2D] aria-[current=page]:font-semibold aria-[current=page]:text-[#141B2D]';
  const up = () => (floor && building ? onBuilding(building.id) : onCampus());
  return (
    <nav
      aria-label="Location"
      className={`${glass} absolute top-3 left-3 flex max-w-[calc(100%-24px)] flex-wrap items-center gap-1 px-2 py-1.5`}
    >
      {building && (
        <button
          type="button"
          aria-label="Back"
          onClick={up}
          className="grid size-7 cursor-pointer place-items-center rounded-[7px] border-0 bg-[#23395D] text-white"
        >
          <Icon d="M15 18l-6-6 6-6" size={14} />
        </button>
      )}
      <button
        type="button"
        className={crumb}
        aria-current={building ? undefined : 'page'}
        onClick={onCampus}
      >
        {company}
      </button>
      {building && (
        <>
          <span className="text-[#A6AEBD]">/</span>
          <button
            type="button"
            className={crumb}
            aria-current={floor ? undefined : 'page'}
            onClick={() => onBuilding(building.id)}
          >
            {building.name}
          </button>
        </>
      )}
      {floor && (
        <>
          <span className="text-[#A6AEBD]">/</span>
          <span className={crumb} aria-current="page">
            {floorTitle(floor)}
          </span>
        </>
      )}
    </nav>
  );
}

export function ViewTools({
  mode,
  tools,
  studioAvailable,
  controls,
  onChange,
}: {
  mode: 'campus' | 'building' | 'floor';
  tools: Tools;
  studioAvailable: boolean;
  controls: OfficeControls | null;
  onChange: (tools: Tools) => void;
}) {
  const toggle = (key: keyof Tools) => onChange({ ...tools, [key]: !tools[key] });
  return (
    <div
      role="toolbar"
      aria-label="View tools"
      className={`${glass} absolute right-3 bottom-3 flex max-w-[calc(100%-24px)] flex-wrap items-center justify-end gap-0.5 p-1 sm:top-3 sm:bottom-auto`}
    >
      {mode === 'floor' && (
        <>
          <button
            type="button"
            className={hudButton}
            aria-pressed={tools.cutaway}
            onClick={() => toggle('cutaway')}
          >
            <Icon d="M3 20h18M5 20V10h14v10M9 10V4h6v6" />
            <span className="hidden sm:inline">Cutaway</span>
            <span className="sr-only sm:hidden">Cutaway</span>
          </button>
          <button
            type="button"
            className={hudButton}
            aria-pressed={tools.overview}
            onClick={() => toggle('overview')}
          >
            <Icon d="M8 3H3v5M16 3h5v5M8 21H3v-5M16 21h5v-5" />
            <span className="hidden sm:inline">Whole floor</span>
            <span className="sr-only sm:hidden">Whole floor</span>
          </button>
        </>
      )}
      {studioAvailable && (
        <button
          type="button"
          className={hudButton}
          aria-pressed={tools.studio}
          onClick={() => toggle('studio')}
        >
          <Icon d="M12 3l1.9 4.8L19 9.7l-4 3.4 1.2 5.2L12 15.6 7.8 18.3 9 13.1 5 9.7l5.1-1.9z" />
          <span className="hidden sm:inline">Studio</span>
          <span className="sr-only sm:hidden">Studio</span>
        </button>
      )}
      <span className="mx-1 h-[18px] w-px bg-[rgba(20,27,45,.12)]" aria-hidden="true" />
      <button
        type="button"
        className={hudButton}
        aria-label="Rotate view left"
        onClick={() => controls?.rotate(-1)}
      >
        <Icon d="M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5" />
      </button>
      <button
        type="button"
        className={hudButton}
        aria-label="Rotate view right"
        onClick={() => controls?.rotate(1)}
      >
        <Icon d="M21 12a9 9 0 1 1-3-6.7L21 8M21 3v5h-5" />
      </button>
      <button
        type="button"
        className={hudButton}
        aria-label="Zoom out"
        onClick={() => controls?.zoomBy(1 / 1.25)}
      >
        <Icon d="M5 12h14" />
      </button>
      <button
        type="button"
        className={hudButton}
        aria-label="Zoom in"
        onClick={() => controls?.zoomBy(1.25)}
      >
        <Icon d="M5 12h14M12 5v14" />
      </button>
    </div>
  );
}

export function CampusCard({ campus, onEnter }: { campus: Campus; onEnter: (id: string) => void }) {
  const company = useSessionStore((state) => state.user?.organizationName);
  const buildings = campus.buildings.length;
  const floors = campus.buildings.reduce((sum, b) => sum + b.floors.length, 0);
  const main = campus.buildings[0];
  return (
    <section
      aria-labelledby="campus-title"
      className={`${glass} absolute bottom-16 left-3 flex max-w-[340px] flex-col items-start gap-2 px-4 py-3.5 sm:bottom-3`}
    >
      <span className="text-[11px] font-semibold tracking-widest text-[#8A6830] uppercase">
        {company}
      </span>
      <h2 id="campus-title" className="m-0 font-display text-xl font-bold tracking-[-0.015em]">
        Your campus
      </h2>
      <p className="m-0 text-[13px] text-[#5B6478]">
        {buildings} building{buildings === 1 ? '' : 's'} · {floors} floor{floors === 1 ? '' : 's'}.
        Click a building to look inside.
      </p>
      {main && (
        <button
          type="button"
          onClick={() => onEnter(main.id)}
          className="h-9 cursor-pointer rounded-[9px] border-0 bg-[#23395D] px-3 text-[13px] font-semibold text-white"
        >
          Enter {main.name}
        </button>
      )}
    </section>
  );
}

export function FloorRail({
  campus,
  building,
  currentFloorId,
}: {
  campus: Campus;
  building: Building;
  currentFloorId?: string;
}) {
  const row =
    'flex items-center gap-2.5 rounded-lg p-1.5 text-left text-[#141B2D] no-underline hover:bg-[#EEF1F5] aria-[current=page]:bg-[#23395D] aria-[current=page]:text-white';
  const level =
    'min-w-7 rounded-[5px] border border-current px-1 py-1 text-center font-mono text-[11px] font-bold';
  const others = campus.buildings.filter((b) => b.id !== building.id);
  return (
    <nav
      aria-label="Floors"
      className={`${glass} absolute top-[62px] right-3 hidden max-h-[calc(100%-230px)] w-[210px] flex-col gap-0.5 overflow-auto p-1.5 sm:flex`}
    >
      <span className="px-1.5 pt-1 pb-0.5 text-[10.5px] font-semibold tracking-wider text-[#5B6478] uppercase">
        {building.name} · floors
      </span>
      {[...building.floors].reverse().map((floor) => (
        <Link
          key={floor.id}
          to={`/office/floors/${floor.id}`}
          className={row}
          aria-current={floor.id === currentFloorId ? 'page' : undefined}
        >
          <span className={level}>{floor.code}</span>
          <b className="text-[12.5px] font-semibold">{floor.name}</b>
        </Link>
      ))}
      {others.length > 0 && (
        <span className="px-1.5 pt-2 pb-0.5 text-[10.5px] font-semibold tracking-wider text-[#5B6478] uppercase">
          Other buildings
        </span>
      )}
      {others.map((other) => (
        <Link key={other.id} to={`/office/buildings/${other.id}`} className={row}>
          <span className={level}>{other.floors.length}F</span>
          <span>
            <b className="block text-[12.5px] font-semibold">{other.name}</b>
            <small className="text-[11px] text-[#5B6478]">{other.subtitle}</small>
          </span>
        </Link>
      ))}
    </nav>
  );
}

export function Minimap({
  layout,
  level,
  controls,
}: {
  layout: FloorLayout;
  level: number;
  controls: OfficeControls | null;
}) {
  const canvas = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const target = canvas.current?.getContext('2d');
    const plan = paintPlan(floorModel(layout, level));
    if (target && plan && canvas.current)
      target.drawImage(plan, 0, 0, canvas.current.width, canvas.current.height);
  }, [layout, level]);
  return (
    <div className={`${glass} absolute right-3 bottom-3 hidden flex-col p-1.5 sm:flex`}>
      <span className="px-0.5 pb-1 text-[10.5px] tracking-wider text-[#5B6478] uppercase">
        Floor plan
      </span>
      <canvas
        ref={canvas}
        width={392}
        height={252}
        aria-label="Floor plan. Click a spot to look there."
        className="block h-[126px] w-[196px] cursor-crosshair rounded-md"
        onPointerDown={(event) => {
          const rect = event.currentTarget.getBoundingClientRect();
          controls?.lookAt(
            ((event.clientX - rect.left) / rect.width) * layout.width,
            ((event.clientY - rect.top) / rect.height) * layout.height,
          );
        }}
      />
    </div>
  );
}

export function Hint({ children }: { children: ReactNode }) {
  return (
    <p className="pointer-events-none absolute top-[58px] left-1/2 m-0 max-w-[calc(100%-32px)] -translate-x-1/2 rounded-xl bg-[#1B2A45] px-4 py-2.5 font-semibold whitespace-nowrap text-white shadow-[0_10px_30px_rgba(20,27,45,.35)] sm:top-auto sm:bottom-[18px]">
      {children}
    </p>
  );
}
