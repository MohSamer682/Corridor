import { create } from 'zustand';

/** Frames come ten times a second; other people glide from one to the next over about that long. */
export const FRAME_MS = 110;

export type Occupant = {
  userId: string;
  displayName: string;
  x: number;
  y: number;
  /** Radians, 0 = facing south (down the screen's y). */
  facing: number;
  moving: boolean;
};
export type AvatarState = Omit<Occupant, 'displayName'>;
export type FrameMessage = { moved: AvatarState[]; left: string[] };

export type Person = {
  id: string;
  name: string;
  facing: number;
  moving: boolean;
  from: { x: number; y: number };
  to: { x: number; y: number };
  since: number;
};

type PresenceState = {
  people: Record<string, Person>;
  connected: boolean;
  /** Bumped on every change, so the 3D view knows when to draw again. */
  version: number;
  arrive: (occupants: Occupant[]) => void;
  applyFrame: (frame: FrameMessage, now?: number) => void;
  setConnected: (connected: boolean) => void;
  /** The server refused where your browser walked you; `seq` changes each time. */
  correction: { x: number; y: number; seq: number } | null;
  correct: (x: number, y: number) => void;
  unnamed: () => string[];
  reset: () => void;
};

/** Where someone is drawn at `now`, sliding from the tile they left to the one they are stepping into. */
export function positionAt(person: Person, now: number) {
  const t = Math.min(1, Math.max(0, (now - person.since) / FRAME_MS));
  return {
    x: person.from.x + (person.to.x - person.from.x) * t,
    y: person.from.y + (person.to.y - person.from.y) * t,
  };
}

// Read by the 3D frame loop with getState(), so React doesn't re-render ten times a second.
export const usePresenceStore = create<PresenceState>((set, get) => ({
  people: {},
  connected: false,
  version: 0,
  arrive: (occupants) =>
    set((state) => {
      const people = { ...state.people };
      for (const o of occupants) {
        const at = { x: o.x, y: o.y };
        people[o.userId] = {
          id: o.userId,
          name: o.displayName,
          facing: o.facing,
          moving: o.moving,
          from: at,
          to: at,
          since: 0,
        };
      }
      return { people, version: state.version + 1 };
    }),
  applyFrame: (frame, now = performance.now()) =>
    set((state) => {
      const people = { ...state.people };
      for (const moved of frame.moved) {
        const known = people[moved.userId];
        people[moved.userId] = {
          id: moved.userId,
          name: known?.name ?? '',
          facing: moved.facing,
          moving: moved.moving,
          from: known ? positionAt(known, now) : { x: moved.x, y: moved.y },
          to: { x: moved.x, y: moved.y },
          since: now,
        };
      }
      for (const id of frame.left) delete people[id];
      return { people, version: state.version + 1 };
    }),
  setConnected: (connected) => set({ connected }),
  correction: null,
  correct: (x, y) =>
    set((state) => ({ correction: { x, y, seq: (state.correction?.seq ?? 0) + 1 } })),
  unnamed: () =>
    Object.values(get().people)
      .filter((person) => !person.name)
      .map((person) => person.id),
  reset: () => set({ people: {}, connected: false, version: 0, correction: null }),
}));
