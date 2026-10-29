import type { FloorLayout, RoomKind } from './types';

/** One decor item from the layout JSON; which fields are set depends on `t`. */
export type Item = {
  t: string;
  x: number;
  y: number;
  x0: number;
  y0: number;
  x1: number;
  y1: number;
  style?: string;
  team?: string;
  color?: string;
  face?: string;
  kind?: string;
  back?: string;
  room?: string;
  monitor?: boolean;
};
export type Area = {
  key: string;
  name: string;
  kind: string;
  x0: number;
  y0: number;
  x1: number;
  y1: number;
};
export type WalledRoom = Area & { finish: string; doors: [number, number][] };
export type Zone = { x0: number; y0: number; x1: number; y1: number; mat: string };
export type Rug = Zone & { c1: string; c2: string; pat: string };
export type Label = { t: string; x: number; y: number };
export type WallSpec = {
  key?: string;
  room?: string;
  vert: boolean;
  facing: string;
  x0: number;
  x1: number;
  wy: number;
  wx: number;
  y0: number;
  y1: number;
  v?: number;
  wb?: boolean;
};
export type Chair = { x: number; y: number; face: string; style: string; room: string };
export type HotSpot = { x0: number; y0: number; x1: number; y1: number };

export type FloorModel = {
  id: string;
  level: number;
  W: number;
  H: number;
  at: (x: number, y: number) => string;
  rooms: WalledRoom[];
  areas: Area[];
  kindOf: (roomKey: string | undefined) => string | undefined;
  items: Item[];
  zones: Zone[];
  rugs: Rug[];
  labels: Label[];
  screens: WallSpec[];
  arts: WallSpec[];
  chairs: Chair[];
  hotSpots: Record<string, HotSpot>;
  stage: Zone | null;
};

const list = <T>(value: unknown): T[] => (Array.isArray(value) ? (value as T[]) : []);

/** Turns an API layout into the shape the scene builders and the floor plan draw from. */
export function floorModel(layout: FloorLayout, level: number): FloorModel {
  const { width: W, height: H, tiles, furniture } = layout;
  const at = (x: number, y: number) =>
    x < 0 || y < 0 || x >= W || y >= H ? 'C' : (tiles[y * W + x] ?? 'C');
  const finishes = (furniture.roomFinishes ?? {}) as Record<string, string>;
  const toArea = (room: FloorLayout['rooms'][number]): Area => ({
    key: room.key,
    name: room.name,
    kind: room.kind.toLowerCase(),
    x0: room.minX,
    y0: room.minY,
    x1: room.maxX,
    y1: room.maxY,
  });
  const rooms = layout.rooms
    .filter((room) => room.key in finishes)
    .map((room) => {
      const area = toArea(room);
      return { ...area, finish: finishes[room.key] ?? 'concrete', doors: doorsOf(area, at) };
    });
  const kinds = new Map<string, RoomKind>(layout.rooms.map((room) => [room.key, room.kind]));
  return {
    id: layout.floorId,
    level,
    W,
    H,
    at,
    rooms,
    areas: layout.rooms.filter((room) => !(room.key in finishes)).map(toArea),
    kindOf: (key) => (key ? kinds.get(key)?.toLowerCase() : undefined),
    items: list<Item>(furniture.items),
    zones: list<Zone>(furniture.zones),
    rugs: list<Rug>(furniture.rugs),
    labels: list<Label>(furniture.labels),
    screens: list<WallSpec>(furniture.screens),
    arts: list<WallSpec>(furniture.art),
    chairs: list<Chair>(furniture.chairs),
    hotSpots: (furniture.hotSpots ?? {}) as Record<string, HotSpot>,
    stage: (furniture.stage ?? null) as Zone | null,
  };
}

// A door is any walkable tile on the outline of a walled room.
export function doorsOf(room: Area, at: (x: number, y: number) => string): [number, number][] {
  const doors: [number, number][] = [];
  for (let x = room.x0; x <= room.x1; x++) {
    for (const y of [room.y0, room.y1]) if (at(x, y) === '.') doors.push([x, y]);
  }
  for (let y = room.y0 + 1; y < room.y1; y++) {
    for (const x of [room.x0, room.x1]) if (at(x, y) === '.') doors.push([x, y]);
  }
  return doors;
}

const PLAN_FILL: Record<string, string> = {
  office: '#EFE6D6',
  meeting: '#DCE5F1',
  conference: '#E2DFF2',
  hall: '#EADFDB',
  booth: '#DFEAE2',
  social: '#E1EDE6',
  lab: '#DEE7EC',
  zone: '#EEEDE8',
};

/** A flat floor plan: room fills, walls, glass and furniture footprints. Null where canvas is missing. */
export function paintPlan(f: FloorModel, PS = 18): HTMLCanvasElement | null {
  const c = document.createElement('canvas');
  c.width = f.W * PS;
  c.height = f.H * PS;
  const g = c.getContext('2d');
  if (!g) return null;
  g.fillStyle = '#F6F5F1';
  g.fillRect(0, 0, c.width, c.height);
  for (const r of [...f.areas, ...f.rooms]) {
    g.fillStyle = PLAN_FILL[r.kind] ?? '#EEEDE8';
    g.fillRect(r.x0 * PS, r.y0 * PS, (r.x1 - r.x0 + 1) * PS, (r.y1 - r.y0 + 1) * PS);
  }
  for (let y = 0; y < f.H; y++) {
    for (let x = 0; x < f.W; x++) {
      const ch = f.at(x, y);
      if (ch === '#') {
        g.fillStyle = '#2B3140';
        g.fillRect(x * PS, y * PS, PS, PS);
      } else if (ch === 'G') {
        g.fillStyle = '#6E93B8';
        const vertical = f.at(x - 1, y) !== '.' && f.at(x + 1, y) !== '.';
        if (!vertical && f.at(x, y - 1) !== '.' && f.at(x, y + 1) !== '.') {
          g.fillRect(x * PS + PS * 0.38, y * PS, PS * 0.24, PS);
        } else {
          g.fillRect(x * PS, y * PS + PS * 0.38, PS, PS * 0.24);
        }
      } else if (ch === 'C') {
        g.fillStyle = '#4F6F92';
        g.fillRect(x * PS, y * PS, PS, PS);
      }
    }
  }
  const rect = (x0: number, y0: number, x1: number, y1: number, color: string) => {
    g.fillStyle = color;
    g.beginPath();
    g.roundRect(x0 * PS + 1.5, y0 * PS + 1.5, (x1 - x0 + 1) * PS - 3, (y1 - y0 + 1) * PS - 3, 3);
    g.fill();
  };
  for (const it of f.items) {
    if (it.t === 'plant') {
      g.fillStyle = '#86A974';
      g.beginPath();
      g.arc((it.x + 0.5) * PS, (it.y + 0.5) * PS, PS * 0.38, 0, 7);
      g.fill();
    } else if (it.t === 'cluster') rect(it.x, it.y, it.x + 3, it.y + 1, '#D6D2C7');
    else if (it.t === 'lift' || it.t === 'wc') rect(it.x0, it.y0, it.x1, it.y1, '#9BA2AE');
    else if (it.t === 'table' || it.t === 'exec')
      rect(it.x0, it.y0, it.x1, it.y1, it.style === 'walnut' ? '#8C6A50' : '#CDB38D');
    else if (it.t === 'round') {
      g.fillStyle = '#D2C7B4';
      g.beginPath();
      g.arc((it.x + 1) * PS, (it.y + 1) * PS, PS * 0.85, 0, 7);
      g.fill();
    } else if (it.t === 'sofa') rect(it.x0, it.y, it.x1, it.y, '#8C98AE');
    else if (it.x0 != null && it.y != null) rect(it.x0, it.y, it.x1, it.y, '#BFC3CB');
    else if (it.x0 != null) rect(it.x0, it.y0, it.x1, it.y1, '#BFC3CB');
  }
  for (const chair of f.chairs) {
    g.fillStyle = '#A5ACB8';
    g.beginPath();
    g.arc((chair.x + 0.5) * PS, (chair.y + 0.5) * PS, PS * 0.22, 0, 7);
    g.fill();
  }
  g.font = `600 ${Math.round(PS * 0.62)}px "IBM Plex Sans",sans-serif`;
  g.fillStyle = '#23395D';
  for (const r of [...f.rooms, ...f.areas]) {
    const w = (r.x1 - r.x0) * PS;
    let t = r.name;
    while (g.measureText(t).width > w - 6 && t.length > 3) t = t.slice(0, -2);
    if (t !== r.name) t += '…';
    g.fillText(t, r.x0 * PS + PS * 1.2, r.y0 * PS + PS * 1.9);
  }
  return c;
}
