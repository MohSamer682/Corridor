import * as THREE from 'three';
import type { FloorModel, Item } from '../floorModel';
import {
  C,
  CURT_H,
  FABRIC,
  FACE,
  M,
  TEAM_FABRIC,
  WALL_H,
  blob,
  box,
  current,
  cyl,
  grp,
  lathe,
  pickFrom,
  rbox,
  rng,
  sph,
} from './core';

/* Furniture library from the prototype. Local +z is the side a seated person faces. */

/** A canvas-drawn sign the floor scene hangs on a wall or desk (logo, lift sign, sprint board). */
export type SignSpec = {
  key: 'logo' | 'wc' | 'jira';
  w: number;
  h: number;
  x: number;
  y: number;
  z: number;
  ry: number;
  cw: number;
  ch: number;
  frame: boolean;
};

const SCREEN_COLORS = ['#86B6D8', '#9CC9B5', '#B9B0E4', '#E8C98F'];

function monitor(
  parent: THREE.Object3D,
  x: number,
  y: number,
  z: number,
  ry: number,
  col = '#86B6D8',
) {
  const g = new THREE.Group();
  g.position.set(x, y, z);
  g.rotation.y = ry;
  parent.add(g);
  rbox(0.66, 0.4, 0.035, 0.02, C.screen, 0, 0.26, 0, null, 0, g);
  box(
    0.6,
    0.34,
    0.005,
    col,
    0,
    0.26,
    0.02,
    { emissive: col, emissiveIntensity: 0.35, roughness: 0.3 },
    0,
    g,
  );
  box(0.05, 0.18, 0.05, C.metal, 0, 0.04, -0.04, null, 0, g);
  box(0.22, 0.015, 0.16, C.metal, 0, -0.05, -0.03, null, 0, g);
}

function officeChair(x: number, z: number, ry: number, col: string = C.charcoal, exec = false) {
  const g = grp(x, z, ry);
  const fabric = exec ? '#2A2523' : col;
  rbox(0.52, 0.09, 0.5, 0.04, fabric, 0, 0.47, 0.02, null, 0, g);
  rbox(0.5, exec ? 0.72 : 0.55, 0.08, 0.04, fabric, 0, exec ? 0.88 : 0.8, -0.24, null, 0, g);
  if (exec) rbox(0.46, 0.16, 0.06, 0.03, '#3A322E', 0, 1.28, -0.23, null, 0, g);
  box(0.06, 0.2, 0.3, C.metal, -0.27, 0.58, -0.02, null, 0, g);
  box(0.06, 0.2, 0.3, C.metal, 0.27, 0.58, -0.02, null, 0, g);
  cyl(0.03, 0.03, 0.3, C.metal, 0, 0.3, 0, 8, null, g);
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2;
    box(0.04, 0.03, 0.3, C.metal, Math.sin(a) * 0.15, 0.06, Math.cos(a) * 0.15, null, a, g);
    sph(0.035, '#14161A', Math.sin(a) * 0.29, 0.035, Math.cos(a) * 0.29, 1, 1, 1, null, g);
  }
}

function guestChair(x: number, z: number, ry: number, col: string) {
  const g = grp(x, z, ry);
  rbox(0.5, 0.1, 0.5, 0.05, col, 0, 0.46, 0, null, 0, g);
  rbox(0.48, 0.42, 0.08, 0.04, col, 0, 0.72, -0.22, null, 0, g);
  for (const [a, b] of [
    [-0.2, -0.2],
    [0.2, -0.2],
    [-0.2, 0.2],
    [0.2, 0.2],
  ] as const)
    cyl(0.022, 0.018, 0.42, C.walnut, a, 0.21, b, 6, null, g);
}

function stool(x: number, z: number) {
  cyl(0.2, 0.19, 0.06, C.oak, x, 0.66, z, 18);
  cyl(0.025, 0.025, 0.64, C.metal, x, 0.33, z, 6);
  const ring = new THREE.Mesh(new THREE.TorusGeometry(0.15, 0.012, 6, 16), M(C.metal));
  ring.rotation.x = Math.PI / 2;
  ring.position.set(x, 0.25, z);
  current().add(ring);
  cyl(0.17, 0.19, 0.03, C.metal, x, 0.015, z, 16);
}

function theatreSeat(x: number, z: number, ry: number) {
  const g = grp(x, z, ry);
  rbox(0.7, 0.12, 0.58, 0.05, '#2F3D5A', 0, 0.42, 0, null, 0, g);
  rbox(0.7, 0.62, 0.12, 0.05, '#3B4C6E', 0, 0.7, -0.28, null, 0, g);
  box(0.08, 0.62, 0.62, C.metal, -0.38, 0.31, 0, null, 0, g);
  box(0.08, 0.62, 0.62, C.metal, 0.38, 0.31, 0, null, 0, g);
}

function deskCluster(it: Item) {
  const fab = TEAM_FABRIC[it.team ?? ''] ?? C.navy;
  const g = grp(it.x + 2, it.y + 1, 0);
  for (const dz of [-0.48, 0.48]) {
    rbox(3.86, 0.05, 0.9, 0.02, C.oak, 0, 0.74, dz, null, 0, g);
    rbox(3.86, 0.012, 0.9, 0.006, C.white, 0, 0.768, dz, null, 0, g);
  }
  for (const [a, b] of [
    [-1.86, -0.48],
    [1.86, -0.48],
    [-1.86, 0.48],
    [1.86, 0.48],
  ] as const)
    box(0.05, 0.72, 0.75, C.metal, a, 0.36, b, null, 0, g);
  box(3.6, 0.05, 0.08, C.metal, 0, 0.55, 0, null, 0, g);
  rbox(3.7, 0.36, 0.06, 0.03, fab, 0, 0.95, 0, { roughness: 1 }, 0, g);
  const seats = [
    [-1, -1],
    [1, -1],
    [-1, 1],
    [1, 1],
  ] as const;
  seats.forEach(([ox, side], k) => {
    const sx = ox * 0.5;
    monitor(g, sx, 0.8, side * 0.32, side < 0 ? Math.PI : 0, SCREEN_COLORS[(it.x + k) % 4]);
    box(0.42, 0.015, 0.14, '#E6E8EB', sx, 0.785, side * 0.68, null, 0, g);
    if (k % 2)
      cyl(
        0.04,
        0.035,
        0.09,
        ['#FFFFFF', '#23395D', '#B8935A'][k % 3] ?? '#FFFFFF',
        sx + 0.38,
        0.82,
        side * 0.55,
        10,
        null,
        g,
      );
  });
  cyl(0.07, 0.06, 0.12, C.pot, -1.6, 0.83, -0.2, 12, null, g);
  blob(0.12, C.leaf2, -1.6, 0.98, -0.2, 1, 1, 1, g);
  box(0.5, 0.03, 0.3, '#D5D8DD', 1.45, 0.79, 0.45, null, 0.2, g);
}

function execDesk(it: Item) {
  const w = it.x1 - it.x0 + 1;
  const d = it.y1 - it.y0 + 1;
  const wal = it.style === 'walnut';
  const top = wal ? C.walnut : C.oak;
  const body = wal ? C.walnutD : C.oakD;
  const g = grp(it.x0 + w / 2, it.y0 + d / 2, 0);
  rbox(w - 0.1, 0.07, d - 0.2, 0.03, top, 0, 0.76, 0, null, 0, g);
  box(w - 0.9, 0.012, d - 0.75, wal ? '#3A2E28' : '#E8E3D8', 0, 0.8, 0.05, null, 0, g);
  rbox(0.08, 0.72, d - 0.3, 0.02, body, -(w / 2 - 0.2), 0.37, 0, null, 0, g);
  rbox(0.08, 0.72, d - 0.3, 0.02, body, w / 2 - 0.2, 0.37, 0, null, 0, g);
  rbox(w - 0.6, 0.5, 0.05, 0.02, body, 0, 0.45, 0.35, null, 0, g);
  monitor(g, 0, 0.8, -0.3, Math.PI, '#9CC9B5');
  box(0.48, 0.015, 0.17, '#E6E8EB', 0, 0.81, -0.05, null, 0, g);
  cyl(0.06, 0.09, 0.04, C.brass, -(w / 2 - 0.45), 0.81, -0.25, 12, null, g);
  cyl(0.012, 0.012, 0.42, C.brass, -(w / 2 - 0.45), 1.02, -0.25, 6, null, g);
  lathe(
    [
      [0, 0],
      [0.12, 0],
      [0.08, 0.14],
      [0, 0.14],
    ],
    '#F1E7D0',
    -(w / 2 - 0.45),
    1.18,
    -0.25,
    22,
    g,
  );
  box(0.26, 0.02, 0.34, '#F4F1EA', w / 2 - 0.6, 0.81, 0.1, null, 0.25, g);
  cyl(0.04, 0.035, 0.1, '#FFFFFF', w / 2 - 0.4, 0.84, -0.3, 10, null, g);
}

const tableColor = (style?: string) =>
  ({ walnut: C.walnut, oak: C.oak, white: C.white })[style ?? ''] ?? C.oak;

function meetTable(it: Item) {
  const w = it.x1 - it.x0 + 1;
  const d = it.y1 - it.y0 + 1;
  const g = grp(it.x0 + w / 2, it.y0 + d / 2, 0);
  rbox(
    w - 0.15,
    0.08,
    d - 0.25,
    Math.min(0.45, (d - 0.25) / 2),
    tableColor(it.style),
    0,
    0.76,
    0,
    null,
    0,
    g,
  );
  for (const x of [-w / 3, w / 3])
    rbox(0.5, 0.68, Math.max(0.4, d - 1.2), 0.08, C.metal, x, 0.36, 0, null, 0, g);
  box(0.5, 0.03, 0.18, C.steel, 0, 0.81, 0, { metalness: 0.6, roughness: 0.3 }, 0, g);
  for (let x = 0; x < w; x += 2) {
    const lx = -w / 2 + x + 0.5;
    box(0.32, 0.012, 0.24, '#F4F1EA', lx, 0.81, -d / 2 + 0.42, null, 0.1, g);
    box(0.32, 0.012, 0.24, '#F4F1EA', lx, 0.81, d / 2 - 0.42, null, -0.1, g);
  }
  if (w > 8) {
    for (let x = -w / 2 + 1.5; x < w / 2; x += 3)
      cyl(
        0.06,
        0.05,
        0.24,
        '#DCE9F0',
        x,
        0.92,
        0,
        10,
        { transparent: true, opacity: 0.55, roughness: 0.1 },
        g,
      );
  } else {
    cyl(0.09, 0.07, 0.12, C.pot, 0, 0.86, 0, 12, null, g);
    blob(0.13, C.leaf2, 0, 1.0, 0, 1, 1, 1, g);
  }
}

function roundTable(it: Item, f: FloorModel) {
  const cx = it.x + 1;
  const cz = it.y + 1;
  const kind = f.kindOf(it.room);
  const top = kind === 'office' ? C.walnut : kind === 'social' ? C.white : C.oak;
  cyl(0.86, 0.86, 0.06, top, cx, 0.74, cz, 32);
  cyl(0.06, 0.1, 0.7, C.metal, cx, 0.37, cz, 10);
  cyl(0.38, 0.42, 0.03, C.metal, cx, 0.015, cz, 24);
  cyl(0.06, 0.05, 0.09, '#FFFFFF', cx - 0.3, 0.81, cz - 0.15, 10);
  cyl(0.06, 0.05, 0.09, '#23395D', cx + 0.25, 0.81, cz + 0.2, 10);
  cyl(0.12, 0.1, 0.02, '#E8E2D4', cx + 0.1, 0.785, cz - 0.3, 14);
}

const fabricOf = (color: string | undefined, fallback: [string, string]) =>
  FABRIC[color ?? ''] ?? fallback;
const legs = [
  [-1, -1],
  [1, -1],
  [-1, 1],
  [1, 1],
] as const;

function sofa(it: Item) {
  const n = it.x1 - it.x0 + 1;
  const [c0, c1] = fabricOf(it.color, ['#2F3D5A', '#3B4C6E']);
  const g = grp(it.x0 + n / 2, it.y + 0.5, FACE[it.face ?? 'down'] ?? 0);
  rbox(n - 0.06, 0.3, 0.86, 0.08, c0, 0, 0.22, 0, null, 0, g);
  rbox(n - 0.06, 0.5, 0.22, 0.08, c0, 0, 0.55, -0.34, null, 0, g);
  rbox(0.22, 0.5, 0.86, 0.08, c0, -(n / 2 - 0.12), 0.4, 0, null, 0, g);
  rbox(0.22, 0.5, 0.86, 0.08, c0, n / 2 - 0.12, 0.4, 0, null, 0, g);
  for (let i = 0; i < n; i++) {
    rbox(0.94, 0.14, 0.62, 0.07, c1, -n / 2 + i + 0.5, 0.43, 0.06, null, 0, g);
    rbox(0.86, 0.4, 0.16, 0.07, c1, -n / 2 + i + 0.5, 0.65, -0.2, null, 0, g);
  }
  for (const [a, b] of legs)
    cyl(0.03, 0.025, 0.08, C.walnut, a * (n / 2 - 0.15), 0.04, b * 0.35, 6, null, g);
  if (n >= 3)
    rbox(0.36, 0.3, 0.12, 0.05, C.brass, -(n / 2 - 0.6), 0.75, -0.12, { roughness: 0.9 }, 0, g);
}

function armchair(it: Item) {
  const [c0, c1] = fabricOf(it.color, ['#9C7048', '#B08359']);
  const g = grp(it.x + 0.5, it.y + 0.5, FACE[it.face ?? 'down'] ?? 0);
  rbox(0.82, 0.28, 0.78, 0.08, c0, 0, 0.25, 0, null, 0, g);
  rbox(0.82, 0.5, 0.18, 0.08, c0, 0, 0.58, -0.3, null, 0, g);
  rbox(0.16, 0.42, 0.78, 0.07, c0, -0.33, 0.42, 0, null, 0, g);
  rbox(0.16, 0.42, 0.78, 0.07, c0, 0.33, 0.42, 0, null, 0, g);
  rbox(0.52, 0.12, 0.56, 0.06, c1, 0, 0.44, 0.05, null, 0, g);
  for (const [a, b] of legs) cyl(0.025, 0.02, 0.12, C.walnut, a * 0.32, 0.06, b * 0.3, 6, null, g);
}

function coffeeTable(it: Item) {
  const w = it.x1 - it.x0 + 1;
  const d = it.y1 - it.y0 + 1;
  const cx = it.x0 + w / 2;
  const cz = it.y0 + d / 2;
  rbox(
    w - 0.2,
    0.06,
    d - 0.25,
    0.12,
    { walnut: C.walnut, oak: C.oak, white: C.white }[it.style ?? ''] ?? C.walnut,
    cx,
    0.4,
    cz,
  );
  for (const [a, b] of legs)
    cyl(0.025, 0.025, 0.38, C.metal, cx + a * (w / 2 - 0.3), 0.19, cz + b * (d / 2 - 0.25), 6);
  box(0.36, 0.05, 0.26, '#2F3D5A', cx - 0.2, 0.45, cz, null, 0.2);
  box(0.3, 0.04, 0.22, '#B8935A', cx - 0.18, 0.49, cz + 0.02, null, 0.35);
  cyl(0.08, 0.06, 0.16, '#EEEAE2', cx + 0.3, 0.51, cz, 12);
}

function shelf(it: Item) {
  const n = it.x1 - it.x0 + 1;
  const cx = it.x0 + n / 2;
  const zc = it.y + 0.5 + (it.back === 'up' ? -0.22 : 0.22);
  rbox(n - 0.05, 1.75, 0.42, 0.02, C.walnut, cx, 0.875, zc);
  const r = rng(it.x0 * 7 + it.y);
  for (let s = 0; s < 4; s++) {
    box(n - 0.12, 0.03, 0.36, C.walnutD, cx, 0.08 + s * 0.42, zc);
    let x = it.x0 + 0.12;
    while (x < it.x1 + 0.85) {
      const bw = 0.05 + r() * 0.06;
      const bh = 0.22 + r() * 0.12;
      if (r() < 0.12) {
        sph(
          0.08,
          pickFrom(['#E9E4D8', '#B8935A', '#2F3D5A'], r),
          x + 0.08,
          0.18 + s * 0.42,
          zc,
          1,
          1.4,
          1,
        );
        x += 0.24;
        continue;
      }
      box(
        bw,
        bh,
        0.26,
        pickFrom(['#2F3D5A', '#B8935A', '#8E4A42', '#E6E1D4', '#4E6E5E', '#3A3E46'], r),
        x,
        0.1 + s * 0.42 + bh / 2,
        zc,
      );
      x += bw + 0.01;
    }
  }
}

function plant(it: Item) {
  const x = it.x + 0.5;
  const z = it.y + 0.5;
  const r = rng(it.x * 13 + it.y * 7);
  const G = current();
  if (it.kind === 'snake') {
    lathe(
      [
        [0, 0],
        [0.2, 0],
        [0.22, 0.4],
        [0, 0.4],
      ],
      C.pot,
      x,
      0,
      z,
    );
    for (let i = 0; i < 11; i++) {
      const a = r() * Math.PI * 2;
      const h = 0.5 + r() * 0.5;
      const blade = new THREE.Mesh(
        new THREE.BoxGeometry(0.06, h, 0.015),
        M(i % 3 ? '#3E6A44' : '#6F8F4E', { roughness: 0.8 }),
      );
      blade.position.set(x + Math.cos(a) * 0.08, 0.4 + h / 2, z + Math.sin(a) * 0.08);
      blade.rotation.set((r() - 0.5) * 0.35, a, (r() - 0.5) * 0.35);
      G.add(blade);
    }
    return;
  }
  if (it.kind === 'olive') {
    lathe(
      [
        [0, 0],
        [0.26, 0],
        [0.3, 0.48],
        [0, 0.48],
      ],
      C.potD,
      x,
      0,
      z,
    );
    cyl(0.03, 0.04, 0.6, '#6B5238', x, 0.75, z, 6);
    blob(0.36, '#7E9467', x, 1.15, z, 1, 0.8, 1);
    blob(0.24, '#8FA676', x + 0.18, 1.32, z, 1, 0.8, 1);
    blob(0.22, '#6E8758', x - 0.16, 1.28, z + 0.1, 1, 0.8, 1);
    return;
  }
  lathe(
    [
      [0, 0],
      [0.24, 0],
      [0.28, 0.45],
      [0, 0.45],
    ],
    C.pot,
    x,
    0,
    z,
  );
  cyl(0.025, 0.035, 1.1, '#6B5238', x, 0.98, z, 6);
  for (let i = 0; i < 14; i++) {
    const a = r() * Math.PI * 2;
    const h = 0.7 + r() * 0.85;
    const rad = 0.12 + r() * 0.22;
    const leaf = new THREE.Mesh(
      new THREE.SphereGeometry(0.13, 8, 6),
      M(i % 3 ? C.leaf1 : C.leaf2, { roughness: 0.85 }),
    );
    leaf.scale.set(1, 0.28, 1.4);
    leaf.position.set(x + Math.cos(a) * rad, 0.45 + h, z + Math.sin(a) * rad);
    leaf.rotation.set(0.5, a, 0);
    G.add(leaf);
  }
}

function counter(it: Item) {
  const n = it.x1 - it.x0 + 1;
  const cx = it.x0 + n / 2;
  const cz = it.y + 0.5;
  const back = it.back === 'up' ? -1 : 1;
  rbox(n, 0.95, 0.82, 0.03, '#2F3D5A', cx, 0.475, cz);
  rbox(n + 0.06, 0.06, 0.9, 0.02, C.quartz, cx, 0.98, cz);
  for (let i = 0; i < n; i++)
    box(0.9, 0.6, 0.01, '#3B4C6E', it.x0 + i + 0.5, 0.45, cz - back * 0.415);
  rbox(0.55, 0.6, 0.45, 0.04, C.steel, it.x0 + 0.8, 1.3, cz + back * 0.18, {
    metalness: 0.6,
    roughness: 0.3,
  });
  box(0.4, 0.18, 0.02, '#1E2126', it.x0 + 0.8, 1.38, cz + back * 0.18 - back * 0.23);
  for (let i = 0; i < 4; i++)
    cyl(0.055, 0.045, 0.1, i % 2 ? '#FFFFFF' : '#E8DCC6', it.x0 + 1.7 + i * 0.32, 1.06, cz, 10);
  rbox(1.2, 0.32, 0.5, 0.04, '#DCEAF0', it.x0 + n - 1.2, 1.17, cz, {
    transparent: true,
    opacity: 0.45,
    roughness: 0.1,
  });
  ['#C8955E', '#E3C08A', '#8A5A3A', '#D9A86B'].forEach((col, i) =>
    sph(0.07, col, it.x0 + n - 1.6 + i * 0.25, 1.08, cz, 1, 0.6, 1),
  );
}

function reception(it: Item) {
  const n = it.x1 - it.x0 + 1;
  const cx = it.x0 + n / 2;
  const cz = it.y + 0.5;
  rbox(n, 1.05, 0.9, 0.2, C.white, cx, 0.525, cz);
  for (let x = it.x0 + 0.1; x < it.x1 + 0.95; x += 0.18)
    box(0.09, 0.85, 0.04, C.oak, x, 0.5, cz + 0.47);
  rbox(n + 0.1, 0.06, 1.0, 0.15, C.quartz, cx, 1.08, cz);
  box(n - 0.4, 0.03, 0.4, C.brass, cx, 0.12, cz + 0.47, { metalness: 0.7, roughness: 0.35 });
  monitor(current(), cx - 1, 1.1, cz - 0.2, Math.PI);
  monitor(current(), cx + 1, 1.1, cz - 0.2, Math.PI);
  cyl(0.09, 0.07, 0.14, C.pot, it.x0 + 0.5, 1.18, cz, 12);
  blob(0.16, C.leaf2, it.x0 + 0.5, 1.35, cz);
}

function logoWall(it: Item, signs: SignSpec[]) {
  const n = it.x1 - it.x0 + 1;
  const cx = it.x0 + n / 2;
  const cz = it.y + 0.5;
  rbox(n, 2.4, 0.36, 0.03, C.walnut, cx, 1.2, cz);
  for (let x = it.x0 + 0.1; x < it.x1 + 0.95; x += 0.2)
    box(0.1, 2.36, 0.04, C.walnutD, x, 1.2, cz + 0.19);
  signs.push({
    key: 'logo',
    w: n * 0.7,
    h: 0.7,
    x: cx,
    y: 1.45,
    z: cz + 0.22,
    ry: 0,
    cw: 900,
    ch: 90,
    frame: false,
  });
}

function liftCore(it: Item) {
  const w = it.x1 - it.x0 + 1;
  const d = it.y1 - it.y0 + 1;
  rbox(w, WALL_H + 0.2, d, 0.04, '#C9C5BD', it.x0 + w / 2, (WALL_H + 0.2) / 2, it.y0 + d / 2);
  for (let i = 0; i < 2; i++) {
    const dx = it.x0 + 0.95 + i * 2;
    box(1.3, 1.25, 0.04, C.steel, dx, 0.63, it.y1 + 1.01, { metalness: 0.7, roughness: 0.25 });
    box(0.02, 1.25, 0.05, '#6E747C', dx, 0.63, it.y1 + 1.02);
    box(1.5, 0.08, 0.06, C.brass, dx, 1.32, it.y1 + 1.01, { metalness: 0.6, roughness: 0.3 });
  }
}

function wcCore(it: Item, signs: SignSpec[]) {
  const w = it.x1 - it.x0 + 1;
  const d = it.y1 - it.y0 + 1;
  const cx = it.x0 + w / 2;
  rbox(w, WALL_H + 0.2, d, 0.04, '#BEB9B0', cx, (WALL_H + 0.2) / 2, it.y0 + d / 2);
  box(1.2, 1.1, 0.04, '#8C6A50', it.x0 + 1, 0.56, it.y1 + 1.01);
  box(1.2, 1.1, 0.04, '#8C6A50', it.x0 + 3, 0.56, it.y1 + 1.01);
  signs.push({
    key: 'wc',
    w: 1.9,
    h: 0.26,
    x: cx,
    y: 1.3,
    z: it.y1 + 1.03,
    ry: 0,
    cw: 300,
    ch: 40,
    frame: false,
  });
}

function podium(it: Item) {
  const cx = it.x0 + (it.x1 - it.x0 + 1) / 2;
  const cz = it.y + 0.5;
  rbox(1.2, 1.1, 0.6, 0.06, C.walnut, cx, 0.22 + 0.55, cz);
  box(1.22, 0.05, 0.62, C.brass, cx, 0.22 + 1.0, cz, { metalness: 0.6, roughness: 0.3 });
  cyl(0.012, 0.012, 0.36, '#222222', cx, 0.22 + 1.22, cz - 0.15, 6);
}

function stage(it: Item) {
  const w = it.x1 - it.x0 + 1;
  const d = it.y1 - it.y0 + 1;
  box(w, 0.22, d, C.walnut, it.x0 + w / 2, 0.11, it.y0 + d / 2);
  box(w, 0.04, 0.06, C.brass, it.x0 + w / 2, 0.2, it.y1 + 0.98, {
    metalness: 0.6,
    roughness: 0.35,
  });
}

function workBench(it: Item) {
  const n = it.x1 - it.x0 + 1;
  const cx = it.x0 + n / 2;
  const cz = it.y + 0.5;
  rbox(
    n - 0.06,
    0.06,
    0.86,
    0.03,
    { white: C.white, oak: C.oak }[it.style ?? ''] ?? C.oak,
    cx,
    0.76,
    cz,
  );
  box(0.06, 0.72, 0.7, C.metal, it.x0 + 0.12, 0.37, cz);
  box(0.06, 0.72, 0.7, C.metal, it.x1 + 0.88, 0.37, cz);
  if (it.monitor) {
    monitor(current(), cx, 0.8, cz - 0.2, 0, '#9CC9B5');
    return;
  }
  for (let x = it.x0 + 0.6; x < it.x1 + 0.6; x += 1.6) {
    box(0.42, 0.015, 0.3, '#D5D8DD', x, 0.8, cz, null, 0.15);
    cyl(0.04, 0.035, 0.09, '#FFFFFF', x + 0.4, 0.83, cz - 0.2, 10);
  }
}

function deviceWall(it: Item) {
  const n = it.x1 - it.x0 + 1;
  const cx = it.x0 + n / 2;
  const zc = it.y + 0.3;
  rbox(n, 1.6, 0.3, 0.02, C.white, cx, 0.8, zc);
  for (let s = 0; s < 3; s++) {
    box(n - 0.2, 0.03, 0.28, C.oak, cx, 0.45 + s * 0.42, zc + 0.05);
    for (let x = it.x0 + 0.3; x < it.x1 + 0.8; x += 0.36) {
      const tablet = (x * 7 + s) % 3 < 1;
      box(tablet ? 0.26 : 0.13, tablet ? 0.2 : 0.26, 0.015, '#16191E', x, 0.6 + s * 0.42, zc + 0.1);
      const glow = SCREEN_COLORS[Math.floor(x * 3 + s) % 4] ?? '#86B6D8';
      box(tablet ? 0.22 : 0.1, tablet ? 0.16 : 0.22, 0.005, glow, x, 0.6 + s * 0.42, zc + 0.11, {
        emissive: '#6E8FA8',
        emissiveIntensity: 0.3,
      });
    }
  }
}

function easel(it: Item, signs: SignSpec[]) {
  const n = it.x1 - it.x0 + 1;
  const cx = it.x0 + n / 2;
  const cz = it.y + 0.5;
  box(0.07, 1.9, 0.07, C.metal, it.x0 + 0.2, 0.95, cz);
  box(0.07, 1.9, 0.07, C.metal, it.x1 + 0.8, 0.95, cz);
  box(n, 0.05, 0.5, C.metal, cx, 0.025, cz);
  signs.push({
    key: 'jira',
    w: n - 0.5,
    h: 1.05,
    x: cx,
    y: 1.25,
    z: cz + 0.05,
    ry: 0,
    cw: 900,
    ch: 170,
    frame: true,
  });
}

function moodBoard(it: Item) {
  const n = it.x1 - it.x0 + 1;
  const cz = it.y + 0.5;
  box(n, 1.4, 0.06, '#EFEDE8', it.x0 + n / 2, 0.95, cz);
  box(0.06, 0.25, 0.4, C.metal, it.x0 + 0.3, 0.12, cz);
  box(0.06, 0.25, 0.4, C.metal, it.x1 + 0.7, 0.12, cz);
  const r = rng(it.x0);
  const swatches = ['#2F3D5A', '#B8935A', '#8EA391', '#A26B4F', '#E6E1D4', '#5D63A8'];
  for (let i = 0; i < 10; i++)
    box(
      0.3 + r() * 0.35,
      0.22 + r() * 0.3,
      0.01,
      swatches[i % 6] ?? '#2F3D5A',
      it.x0 + 0.3 + r() * (n - 0.6),
      0.55 + r() * 0.75,
      cz + 0.04,
    );
}

function floorLamp(it: Item) {
  const x = it.x + 0.5;
  const z = it.y + 0.5;
  cyl(0.18, 0.2, 0.03, C.metal, x, 0.015, z, 16);
  cyl(0.015, 0.015, 1.5, C.brass, x, 0.76, z, 6);
  lathe(
    [
      [0, 0],
      [0.24, 0],
      [0.16, 0.26],
      [0, 0.26],
    ],
    '#F2EBDD',
    x,
    1.42,
    z,
  );
}

function entrance(it: Item, f: FloorModel) {
  const n = it.x1 - it.x0 + 1;
  const cx = it.x0 + n / 2;
  const z = f.H - 0.5;
  box(n + 1, 0.08, 2.2, '#E4E0D8', cx, CURT_H + 0.12, z + 0.9);
  box(n + 1.05, 0.1, 0.06, C.brass, cx, CURT_H + 0.12, z + 2.0, { metalness: 0.6, roughness: 0.3 });
  for (let i = 0; i < n; i++) box(0.04, 1.7, 0.06, '#2E3440', it.x0 + i, 0.85, z + 0.2);
  cyl(0.9, 0.9, 1.7, '#DCEAF0', cx, 0.85, z + 0.3, 24, {
    transparent: true,
    opacity: 0.35,
    roughness: 0.1,
  });
}

/** Builds every decor item and seat of a floor into the current group; returns the signs to paint. */
export function furnish(f: FloorModel): SignSpec[] {
  const signs: SignSpec[] = [];
  const builders: Record<string, (it: Item) => void> = {
    cluster: deskCluster,
    exec: execDesk,
    table: meetTable,
    round: (it) => roundTable(it, f),
    sofa,
    arm: armchair,
    ctable: coffeeTable,
    shelf,
    plant,
    counter,
    bench: workBench,
    reception,
    logo: (it) => logoWall(it, signs),
    lift: liftCore,
    wc: (it) => wcCore(it, signs),
    podium,
    stage,
    devwall: deviceWall,
    easel: (it) => easel(it, signs),
    mood: moodBoard,
    bean: (it) => sph(0.45, it.color ?? '#B8936A', it.x + 0.5, 0.3, it.y + 0.5, 1, 0.62, 1),
    lamp: floorLamp,
    entrance: (it) => entrance(it, f),
  };
  for (const it of f.items) builders[it.t]?.(it);
  for (const c of f.chairs) {
    const x = c.x + 0.5;
    const z = c.y + 0.5;
    const ry = FACE[c.face] ?? 0;
    if (c.style === 'theatre') theatreSeat(x, z, ry);
    else if (c.style === 'stool') stool(x, z);
    else if (c.style === 'guest')
      guestChair(x, z, ry, f.kindOf(c.room) === 'office' ? C.camel : C.sage);
    else officeChair(x, z, ry, c.style === 'exec' ? '#2A2523' : C.charcoal, c.style === 'exec');
  }
  return signs;
}
