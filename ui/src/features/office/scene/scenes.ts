import * as THREE from 'three';
import type { FloorModel, WallSpec } from '../floorModel';
import { paintPlan } from '../floorModel';
import type { Building } from '../types';
import {
  C,
  CURT_H,
  M,
  RB,
  WALL_H,
  beginGroup,
  box,
  canvas,
  canvasTex,
  cyl,
  mergeInto,
  rng,
} from './core';
import { addEnvironment, bench, car, facadeTex, paving, tree } from './environment';
import { furnish, type SignSpec } from './furniture';

export type Fit = { cx: number; cy: number; cz: number; w: number; d: number; h: number };

/** A world-space point the HTML labels follow. */
export type Anchor = { id: string; at: () => THREE.Vector3; side: 'above' | 'right' };

export type WallMeshes = {
  walls: THREE.InstancedMesh;
  caps: THREE.InstancedMesh;
  glass: THREE.InstancedMesh;
  rails: THREE.InstancedMesh;
  curtain: THREE.InstancedMesh;
  mullions: THREE.InstancedMesh;
  bands: THREE.InstancedMesh;
  doorHeads: THREE.InstancedMesh;
};

export type BuiltScene = {
  scene: THREE.Scene;
  fit: Fit;
  /** Meshes that open something when clicked; `userData.target` holds the building or floor id. */
  pick: THREE.Object3D[];
  anchors: Anchor[];
  /** Per-frame hover effects; returns true when something that casts a shadow moved. */
  hover?: (id: string | null, dt: number) => boolean | void;
  setCutaway?: (on: boolean) => void;
  floorView: boolean;
};

const FS = 64;
const MAT2D: Record<string, string> = {
  concrete: '#D0CFCB',
  carpet: '#BDBBB5',
  oak: '#CDB38D',
  herringbone: '#B99671',
  walnut: '#6E4F38',
  navy: '#3E4B66',
  charcoal: '#4C5059',
  sage: '#A3B4A6',
  terrazzo: '#E6E3DC',
  terrazzo2: '#E9E5DD',
  deck: '#B58F67',
  epoxy: '#D3D7DA',
};
const ROUGH: Record<string, number> = {
  concrete: 0.5,
  carpet: 0.97,
  oak: 0.48,
  herringbone: 0.42,
  walnut: 0.36,
  navy: 0.97,
  charcoal: 0.97,
  sage: 0.95,
  terrazzo: 0.24,
  terrazzo2: 0.3,
  deck: 0.78,
  epoxy: 0.22,
};

function paintMaterial(
  g: CanvasRenderingContext2D,
  mat: string,
  x0: number,
  y0: number,
  w: number,
  h: number,
  seed: number,
  rough: CanvasRenderingContext2D,
) {
  const r = rng(seed);
  const px = x0 * FS;
  const py = y0 * FS;
  const pw = w * FS;
  const ph = h * FS;
  const v = Math.round((ROUGH[mat] ?? 0.7) * 255);
  rough.fillStyle = `rgb(${v},${v},${v})`;
  rough.fillRect(px, py, pw, ph);
  g.save();
  g.beginPath();
  g.rect(px, py, pw, ph);
  g.clip();
  g.fillStyle = MAT2D[mat] ?? '#D0CFCB';
  g.fillRect(px, py, pw, ph);
  if (mat === 'concrete') {
    for (let i = 0; i < (pw * ph) / 9000; i++) {
      const cx = px + r() * pw;
      const cy = py + r() * ph;
      const rad = 20 + r() * 80;
      const gr = g.createRadialGradient(cx, cy, 0, cx, cy, rad);
      gr.addColorStop(0, r() < 0.5 ? 'rgba(255,255,255,.07)' : 'rgba(70,60,50,.05)');
      gr.addColorStop(1, 'rgba(0,0,0,0)');
      g.fillStyle = gr;
      g.fillRect(cx - rad, cy - rad, rad * 2, rad * 2);
    }
    for (let i = 0; i < (pw * ph) / 260; i++) {
      g.fillStyle = r() < 0.5 ? 'rgba(255,255,255,.07)' : 'rgba(0,0,0,.035)';
      g.fillRect(px + r() * pw, py + r() * ph, 2 + r() * 6, 2 + r() * 6);
    }
    g.strokeStyle = 'rgba(0,0,0,.06)';
    g.lineWidth = 1;
    for (let x = 0; x <= w; x += 4) {
      g.beginPath();
      g.moveTo(px + x * FS, py);
      g.lineTo(px + x * FS, py + ph);
      g.stroke();
    }
    for (let y = 0; y <= h; y += 4) {
      g.beginPath();
      g.moveTo(px, py + y * FS);
      g.lineTo(px + pw, py + y * FS);
      g.stroke();
    }
  } else if (['carpet', 'navy', 'charcoal', 'sage'].includes(mat)) {
    for (let i = 0; i < (pw * ph) / 30; i++) {
      g.fillStyle = r() < 0.5 ? 'rgba(255,255,255,.05)' : 'rgba(0,0,0,.06)';
      g.fillRect(px + r() * pw, py + r() * ph, 1.5, 1.5);
    }
    for (let y = 0; y < h; y++)
      for (let x = 0; x < w; x++) {
        g.fillStyle = (x + y) % 2 ? 'rgba(255,255,255,.035)' : 'rgba(0,0,0,.035)';
        g.fillRect(px + x * FS, py + y * FS, FS, FS);
        g.strokeStyle = 'rgba(0,0,0,.04)';
        for (let k = 0; k < 4; k++) {
          g.beginPath();
          const o = (k * FS) / 4 + FS / 8;
          if ((x + y) % 2) {
            g.moveTo(px + x * FS + o, py + y * FS);
            g.lineTo(px + x * FS + o, py + y * FS + FS);
          } else {
            g.moveTo(px + x * FS, py + y * FS + o);
            g.lineTo(px + x * FS + FS, py + y * FS + o);
          }
          g.stroke();
        }
      }
  } else if (mat === 'oak' || mat === 'walnut' || mat === 'deck') {
    const pl = FS / 3;
    for (let row = 0; row * pl < ph; row++) {
      let x = -r() * FS * 2;
      while (x < pw) {
        const len = FS * (1.5 + r() * 2.5);
        g.fillStyle = `rgba(${mat === 'walnut' ? '20,10,0' : '90,60,20'},${0.02 + r() * 0.08})`;
        g.fillRect(px + x, py + row * pl, len - 1, pl - 1);
        g.strokeStyle = `rgba(${mat === 'walnut' ? '25,12,4' : '110,75,35'},.12)`;
        g.lineWidth = 0.8;
        for (let k = 0; k < 4; k++) {
          const yy = py + row * pl + 2 + r() * (pl - 4);
          g.beginPath();
          g.moveTo(px + x, yy);
          g.bezierCurveTo(
            px + x + len * 0.3,
            yy + (r() - 0.5) * 3,
            px + x + len * 0.6,
            yy + (r() - 0.5) * 3,
            px + x + len - 2,
            yy,
          );
          g.stroke();
        }
        g.fillStyle = 'rgba(50,30,10,.25)';
        g.fillRect(px + x + len - 1, py + row * pl, 1, pl);
        x += len;
      }
      g.fillStyle = mat === 'deck' ? 'rgba(40,25,10,.35)' : 'rgba(60,40,20,.14)';
      g.fillRect(px, py + row * pl + pl - 1, pw, mat === 'deck' ? 2 : 1);
    }
  } else if (mat === 'herringbone') {
    const s = FS / 2.4;
    for (let y = -2; y * s < ph + s; y++)
      for (let x = -2; x * s * 2 < pw + s; x++) {
        const cx = px + x * s * 2 + (y % 2) * s;
        const cy = py + y * s;
        g.fillStyle = `rgba(80,50,20,${0.05 + ((((x * 7 + y * 3) % 5) + 5) % 5) * 0.03})`;
        g.save();
        g.translate(cx, cy);
        g.rotate((x + y) % 2 ? Math.PI / 4 : -Math.PI / 4);
        g.fillRect(-s, -s / 4, s * 2, s / 2);
        g.strokeStyle = 'rgba(60,35,15,.25)';
        g.strokeRect(-s, -s / 4, s * 2, s / 2);
        g.restore();
      }
  } else if (mat === 'terrazzo' || mat === 'terrazzo2') {
    const t = mat === 'terrazzo' ? 2 : 1;
    g.strokeStyle = 'rgba(120,110,95,.18)';
    for (let x = 0; x <= w; x += t) {
      g.beginPath();
      g.moveTo(px + x * FS, py);
      g.lineTo(px + x * FS, py + ph);
      g.stroke();
    }
    for (let y = 0; y <= h; y += t) {
      g.beginPath();
      g.moveTo(px, py + y * FS);
      g.lineTo(px + pw, py + y * FS);
      g.stroke();
    }
    const chips = [
      'rgba(80,80,90,.22)',
      'rgba(170,120,80,.2)',
      'rgba(255,255,255,.4)',
      'rgba(60,70,90,.18)',
    ];
    for (let i = 0; i < (pw * ph) / 90; i++) {
      g.fillStyle = chips[Math.floor(r() * 4)] ?? 'rgba(0,0,0,.1)';
      g.beginPath();
      g.arc(px + r() * pw, py + r() * ph, 0.6 + r() * 1.8, 0, 7);
      g.fill();
    }
  } else if (mat === 'epoxy') {
    const gr = g.createLinearGradient(px, py, px + pw, py + ph);
    gr.addColorStop(0, 'rgba(255,255,255,.18)');
    gr.addColorStop(1, 'rgba(0,0,0,.05)');
    g.fillStyle = gr;
    g.fillRect(px, py, pw, ph);
  }
  g.restore();
}

/** The floor surface: zone and room finishes, rugs, painted labels and a name plaque at each room door. */
function paintFloor(f: FloorModel) {
  const { c, g } = canvas(f.W * FS, f.H * FS);
  const { c: rc, g: rg } = canvas(c.width, c.height);
  rg.fillStyle = 'rgb(200,200,200)';
  rg.fillRect(0, 0, rc.width, rc.height);
  g.fillStyle = '#3B404A';
  g.fillRect(0, 0, c.width, c.height);
  f.zones.forEach((z, i) =>
    paintMaterial(g, z.mat, z.x0, z.y0, z.x1 - z.x0 + 1, z.y1 - z.y0 + 1, i * 31 + f.W, rg),
  );
  f.rooms.forEach((r, i) =>
    paintMaterial(
      g,
      r.finish,
      r.x0 + 1,
      r.y0 + 1,
      r.x1 - r.x0 - 1,
      r.y1 - r.y0 - 1,
      i * 17 + 7,
      rg,
    ),
  );
  for (const u of f.rugs) {
    const x = u.x0 * FS + 6;
    const y = u.y0 * FS + 6;
    const w = (u.x1 - u.x0 + 1) * FS - 12;
    const h = (u.y1 - u.y0 + 1) * FS - 12;
    rg.fillStyle = 'rgb(250,250,250)';
    rg.fillRect(x, y, w, h);
    const round = (
      rx: number,
      ry: number,
      rw: number,
      rh: number,
      radius: number,
      color: string,
    ) => {
      g.fillStyle = color;
      g.beginPath();
      g.roundRect(rx, ry, rw, rh, radius);
      g.fill();
    };
    round(x + 2, y + 3, w, h, 6, 'rgba(0,0,0,.12)');
    round(x, y, w, h, 6, u.c2);
    round(x + 8, y + 8, w - 16, h - 16, 4, u.c1);
    g.save();
    g.beginPath();
    g.rect(x + 8, y + 8, w - 16, h - 16);
    g.clip();
    g.strokeStyle = u.c2;
    g.globalAlpha = 0.55;
    g.lineWidth = 2;
    if (u.pat === 'stripe')
      for (let k = x; k < x + w; k += 14) {
        g.beginPath();
        g.moveTo(k, y);
        g.lineTo(k, y + h);
        g.stroke();
      }
    if (u.pat === 'diamond')
      for (let k = -h; k < w; k += 22) {
        g.beginPath();
        g.moveTo(x + k, y);
        g.lineTo(x + k + h, y + h);
        g.moveTo(x + k + h, y);
        g.lineTo(x + k, y + h);
        g.stroke();
      }
    if (u.pat === 'border') {
      g.strokeStyle = '#B8935A';
      g.globalAlpha = 0.8;
      g.strokeRect(x + 16, y + 16, w - 32, h - 32);
    }
    g.restore();
  }
  if (f.stage)
    paintMaterial(
      g,
      'walnut',
      f.stage.x0,
      f.stage.y0,
      f.stage.x1 - f.stage.x0 + 1,
      f.stage.y1 - f.stage.y0 + 1,
      99,
      rg,
    );
  g.font = `600 ${(FS * 0.35) | 0}px "IBM Plex Sans",sans-serif`;
  g.fillStyle = 'rgba(30,38,58,.42)';
  for (const l of f.labels) g.fillText(l.t, l.x * FS, l.y * FS);
  for (const r of f.rooms) {
    const door = r.doors.find((d) => d[1] === r.y1 || d[1] === r.y0) ?? r.doors[0];
    if (!door) continue;
    const n = r.doors.filter((q) => q[1] === door[1]).length || 1;
    const horizontal = door[1] === r.y1 || door[1] === r.y0;
    const q = FS / 40;
    g.font = `600 ${(12 * q) | 0}px "IBM Plex Sans",sans-serif`;
    const w = g.measureText(r.name).width + 18 * q;
    const cx = horizontal ? (door[0] + Math.min(n, 4) / 2) * FS : (door[0] - 1.8) * FS;
    const sy = horizontal
      ? door[1] === r.y1
        ? (door[1] + 1) * FS + 6 * q
        : door[1] * FS - 26 * q
      : (door[1] + 1) * FS;
    g.fillStyle = '#1B2A45';
    g.beginPath();
    g.roundRect(cx - w / 2, sy, w, 20 * q, 5 * q);
    g.fill();
    g.fillStyle = '#B8935A';
    g.fillRect(cx - w / 2 + 6 * q, sy + 16 * q, w - 12 * q, 1.5 * q);
    g.fillStyle = '#F3F5F9';
    g.fillText(r.name, cx - w / 2 + 9 * q, sy + 14 * q);
  }
  return { color: c, rough: rc };
}

/* ---------- signs and wall screens (static until meetings and the board go live) ---------- */

type Painter = (g: CanvasRenderingContext2D, c: HTMLCanvasElement) => void;

const paintLogo: Painter = (g, c) => {
  g.clearRect(0, 0, c.width, c.height);
  g.font = '700 70px "Familjen Grotesk",Arial';
  g.fillStyle = '#D2AE6E';
  g.textAlign = 'center';
  g.fillText('CORRIDOR', c.width / 2, 72);
};
const paintLiftSign: Painter = (g, c) => {
  g.fillStyle = '#2E3440';
  g.fillRect(0, 0, c.width, c.height);
  g.fillStyle = '#E6EBF4';
  g.font = '600 22px "IBM Plex Sans",sans-serif';
  g.textAlign = 'center';
  g.fillText('LIFTS  ·  RESTROOMS', c.width / 2, 28);
};
const paintBoard: Painter = (g, c) => {
  g.fillStyle = '#FAF9F6';
  g.fillRect(0, 0, c.width, c.height);
  g.fillStyle = '#23395D';
  g.font = '600 18px "IBM Plex Sans",sans-serif';
  g.fillText('Sprint board', 18, 29);
  const cw = (c.width - 28) / 4;
  ['To do', 'In progress', 'Review', 'Done'].forEach((name, i) => {
    const x = 14 + i * cw;
    g.fillStyle = '#EEEFF1';
    g.fillRect(x + 3, 44, cw - 6, c.height - 50);
    g.fillStyle = '#5B6478';
    g.font = '700 12px "IBM Plex Sans",sans-serif';
    g.fillText(name.toUpperCase(), x + 10, 60);
  });
};
const paintRoomScreen =
  (name: string): Painter =>
  (g, c) => {
    const gr = g.createLinearGradient(0, 0, c.width, 0);
    gr.addColorStop(0, '#1A1F27');
    gr.addColorStop(1, '#232933');
    g.fillStyle = gr;
    g.fillRect(0, 0, c.width, c.height);
    g.fillStyle = '#B8935A';
    g.font = '600 17px "IBM Plex Sans",sans-serif';
    g.fillText(name.toUpperCase(), 18, 30);
    g.fillStyle = '#7D8896';
    g.font = '500 24px "IBM Plex Sans",sans-serif';
    g.fillText('Available', 18, 72);
  };
const paintWide =
  (text: string, background: string, color: string): Painter =>
  (g, c) => {
    g.fillStyle = background;
    g.fillRect(0, 0, c.width, c.height);
    g.fillStyle = color;
    g.font = '600 40px "IBM Plex Sans",sans-serif';
    g.fillText(text, 30, Math.min(74, c.height * 0.62));
  };

function addScreen(
  parent: THREE.Object3D,
  spec: {
    w: number;
    h: number;
    x: number;
    y: number;
    z: number;
    ry: number;
    cw: number;
    ch: number;
    frame: boolean;
    transparent?: boolean;
  },
  paint: Painter,
) {
  const { c, g } = canvas(spec.cw, spec.ch);
  paint(g, c);
  const mesh = new THREE.Mesh(
    new THREE.PlaneGeometry(spec.w, spec.h),
    new THREE.MeshBasicMaterial({
      map: canvasTex(c),
      toneMapped: false,
      transparent: !!spec.transparent,
    }),
  );
  mesh.position.set(spec.x, spec.y, spec.z);
  mesh.rotation.y = spec.ry;
  parent.add(mesh);
  if (spec.frame) {
    const frame = new THREE.Mesh(
      new THREE.BoxGeometry(spec.w + 0.08, spec.h + 0.08, 0.04),
      M('#15191F'),
    );
    frame.position.copy(mesh.position);
    frame.rotation.y = spec.ry;
    frame.position.add(
      new THREE.Vector3(0, 0, -0.025).applyAxisAngle(new THREE.Vector3(0, 1, 0), spec.ry),
    );
    parent.add(frame);
  }
}

function wallSpec(s: WallSpec, depth = 0.03) {
  if (!s.vert) {
    const w = s.x1 - s.x0 + 1;
    return {
      w,
      x: s.x0 + w / 2,
      z: s.facing === 'down' ? s.wy + 1 + depth : s.wy - depth,
      ry: s.facing === 'down' ? 0 : Math.PI,
    };
  }
  const h = s.y1 - s.y0 + 1;
  return {
    w: h,
    x: s.facing === 'right' ? s.wx + 1 + depth : s.wx - depth,
    z: s.y0 + h / 2,
    ry: s.facing === 'right' ? Math.PI / 2 : -Math.PI / 2,
  };
}

function artCanvas(v: number) {
  const { c, g } = canvas(360, 180);
  const palettes = [
    ['#F2EEE6', '#23395D', '#B8935A', '#8EA391'],
    ['#EDE7DC', '#A26B4F', '#2F3D5A', '#D9C7A5'],
    ['#F4F1EA', '#5D63A8', '#B8935A', '#E1D6C2'],
    ['#EFE9DE', '#2F3D5A', '#8E4A42', '#B8935A'],
    ['#F2EEE6', '#3E5F8F', '#D7B47A', '#6E8A6E'],
  ];
  const pal = palettes[v % 5] ?? palettes[0] ?? [];
  g.fillStyle = pal[0] ?? '#F2EEE6';
  g.fillRect(0, 0, 360, 180);
  const r = rng(v * 11 + 3);
  for (let i = 0; i < 5; i++) {
    g.fillStyle = pal[1 + (i % 3)] ?? '#23395D';
    g.globalAlpha = 0.85;
    if (i % 2) {
      g.beginPath();
      g.arc(60 + r() * 240, 40 + r() * 100, 20 + r() * 55, 0, Math.PI * (1 + r()));
      g.fill();
    } else g.fillRect(20 + r() * 260, 20 + r() * 120, 40 + r() * 90, 10 + r() * 40);
  }
  g.globalAlpha = 1;
  g.strokeStyle = pal[1] ?? '#23395D';
  g.lineWidth = 3;
  g.beginPath();
  g.moveTo(20, 150);
  g.bezierCurveTo(120, 60, 220, 170, 340, 70);
  g.stroke();
  return c;
}

/* ---------- floor scene ---------- */

const isWall = (ch: string) => '#GC'.includes(ch);

function instanced(geometry: THREE.BufferGeometry, material: THREE.Material, count: number) {
  return new THREE.InstancedMesh(geometry, material, Math.max(1, count));
}

export function buildFloorScene(
  f: FloorModel,
  environment: THREE.Texture | null,
  tower: boolean,
): BuiltScene {
  const scene = new THREE.Scene();
  const gy = f.level ? -(f.level * 4.4) - 0.55 : -0.02;
  addEnvironment(
    scene,
    {
      cx: f.W / 2,
      cz: f.H / 2,
      w: f.W,
      d: f.H,
      groundY: gy,
      seed: tower ? 11 : 23,
      shadowExt: Math.max(f.W, f.H) / 2 + 6,
      terrace: 4,
    },
    environment,
  );
  const painted = paintFloor(f);
  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(f.W, f.H),
    new THREE.MeshStandardMaterial({
      map: canvasTex(painted.color),
      roughnessMap: canvasTex(painted.rough, undefined, true),
      roughness: 1,
      metalness: 0,
    }),
  );
  floor.rotation.x = -Math.PI / 2;
  floor.position.set(f.W / 2, 0, f.H / 2);
  floor.receiveShadow = true;
  scene.add(floor);
  const slab = new THREE.Mesh(new THREE.BoxGeometry(f.W + 0.4, 0.5, f.H + 0.4), M('#D9D5CD'));
  slab.position.set(f.W / 2, -0.26, f.H / 2);
  slab.receiveShadow = true;
  scene.add(slab);
  if (f.level) {
    // The storeys below, as a glass facade down to the ground.
    const h = -0.5 - gy;
    const levels = Math.max(1, Math.round(h / 4.4));
    const facade = facadeTex(levels, { seed: f.level * 5 });
    const side = (repeat: number) => {
      const t = canvasTex(facade);
      t.wrapS = t.wrapT = THREE.RepeatWrapping;
      t.repeat.set(repeat, 1);
      return new THREE.MeshStandardMaterial({ map: t, roughness: 0.35, metalness: 0.15 });
    };
    const body = new THREE.Mesh(new THREE.BoxGeometry(f.W, h, f.H), [
      side(f.H / 8),
      side(f.H / 8),
      M('#C9C5BD'),
      M('#C9C5BD'),
      side(f.W / 8),
      side(f.W / 8),
    ]);
    body.position.set(f.W / 2, gy + h / 2 - 0.5, f.H / 2);
    scene.add(body);
  }

  const walls = buildWalls(f);
  Object.values(walls).forEach((mesh) => scene.add(mesh));

  const G = beginGroup();
  const signs = furnish(f);
  mergeInto(scene, G);

  const decor = new THREE.Group();
  const painters: Record<SignSpec['key'], Painter> = {
    logo: paintLogo,
    wc: paintLiftSign,
    jira: paintBoard,
  };
  for (const s of signs) addScreen(decor, { ...s, transparent: s.key === 'logo' }, painters[s.key]);
  const roomNames = new Map([...f.rooms, ...f.areas].map((r) => [r.key, r.name]));
  for (const s of f.screens) {
    const w = wallSpec(s);
    const big = s.key === 'town';
    const painter =
      s.key === 'town'
        ? paintWide('Town Hall', '#0E1422', '#8E9AB2')
        : s.key === 'pulse'
          ? paintWide('COMPANY PULSE', '#0B1120', '#D9B26A')
          : paintRoomScreen(roomNames.get(s.room ?? '') ?? '');
    addScreen(
      decor,
      {
        w: w.w - (big ? 0.4 : 0.3),
        h: big ? 1.0 : 0.62,
        x: w.x,
        y: 0.82,
        z: w.z,
        ry: w.ry,
        cw: big ? 1600 : 620,
        ch: big ? 120 : 110,
        frame: true,
      },
      painter,
    );
  }
  for (const a of f.arts) {
    const w = wallSpec(a, 0.02);
    const picture = new THREE.Mesh(
      new THREE.PlaneGeometry(w.w - 0.6, 0.7),
      a.wb
        ? new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.4 })
        : new THREE.MeshStandardMaterial({ map: canvasTex(artCanvas(a.v ?? 0)), roughness: 0.8 }),
    );
    picture.position.set(w.x, 0.85, w.z);
    picture.rotation.y = w.ry;
    decor.add(picture);
    const frame = new THREE.Mesh(
      new THREE.BoxGeometry(w.w - 0.54, 0.76, 0.03),
      M(a.wb ? '#B8BEC6' : C.walnut),
    );
    frame.position.copy(picture.position);
    frame.rotation.y = w.ry;
    frame.position.add(
      new THREE.Vector3(0, 0, -0.02).applyAxisAngle(new THREE.Vector3(0, 1, 0), w.ry),
    );
    decor.add(frame);
  }
  scene.add(decor);

  return {
    scene,
    fit: { cx: f.W / 2, cy: 0, cz: f.H / 2, w: f.W, d: f.H, h: 2 },
    pick: [],
    anchors: [],
    floorView: true,
    setCutaway: (on) => {
      const s = on ? 0.22 : 1;
      walls.walls.scale.y = s;
      walls.glass.scale.y = s;
      walls.curtain.scale.y = on ? 0.3 : 1;
      walls.mullions.scale.y = on ? 0.3 : 1;
      walls.caps.position.y = (s - 1) * WALL_H;
      walls.rails.visible = !on;
      walls.bands.visible = !on;
      walls.doorHeads.visible = !on;
      decor.visible = !on;
    },
  };
}

function buildWalls(f: FloorModel): WallMeshes {
  const dummy = new THREE.Object3D();
  const solid: [number, number][] = [];
  const glass: [number, number][] = [];
  const curtain: [number, number][] = [];
  for (let y = 0; y < f.H; y++)
    for (let x = 0; x < f.W; x++) {
      const ch = f.at(x, y);
      if (ch === '#') solid.push([x, y]);
      else if (ch === 'G') glass.push([x, y]);
      else if (ch === 'C') curtain.push([x, y]);
    }
  const set = (mesh: THREE.InstancedMesh, i: number, x: number, y: number, z: number, ry = 0) => {
    dummy.rotation.set(0, ry, 0);
    dummy.position.set(x, y, z);
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
  };
  const walls = instanced(
    new THREE.BoxGeometry(1, WALL_H, 1),
    new THREE.MeshStandardMaterial({ color: new THREE.Color(C.wall), roughness: 0.88 }),
    solid.length,
  );
  const caps = instanced(new THREE.BoxGeometry(1.002, 0.06, 1.002), M('#2E3440'), solid.length);
  const base = instanced(new THREE.BoxGeometry(1.01, 0.1, 1.01), M('#8C6A50'), solid.length);
  solid.forEach(([x, y], i) => {
    set(walls, i, x + 0.5, WALL_H / 2, y + 0.5);
    set(caps, i, x + 0.5, WALL_H + 0.03, y + 0.5);
    set(base, i, x + 0.5, 0.05, y + 0.5);
  });
  walls.castShadow = walls.receiveShadow = true;
  caps.castShadow = true;
  const horizontal = (x: number, y: number) => {
    const l = f.at(x - 1, y);
    const r = f.at(x + 1, y);
    if (!isWall(l) && !isWall(r)) return false;
    return !(isWall(f.at(x, y - 1)) && isWall(f.at(x, y + 1)) && !isWall(l));
  };
  const glassMat = new THREE.MeshPhysicalMaterial({
    color: new THREE.Color('#D6E8F2'),
    transparent: true,
    opacity: 0.22,
    roughness: 0.04,
    metalness: 0.1,
    clearcoat: 1,
    clearcoatRoughness: 0.02,
    depthWrite: false,
  });
  const panes = instanced(new THREE.BoxGeometry(1, WALL_H * 0.96, 0.05), glassMat, glass.length);
  const rails = instanced(new THREE.BoxGeometry(1, 0.05, 0.1), M('#2E3440'), glass.length * 2);
  glass.forEach(([x, y], i) => {
    const ry = horizontal(x, y) ? 0 : Math.PI / 2;
    set(panes, i, x + 0.5, WALL_H * 0.48, y + 0.5, ry);
    set(rails, i * 2, x + 0.5, WALL_H * 0.96, y + 0.5, ry);
    set(rails, i * 2 + 1, x + 0.5, 0.025, y + 0.5, ry);
  });
  panes.renderOrder = 2;
  const curtMat = new THREE.MeshPhysicalMaterial({
    color: new THREE.Color('#BFD8E8'),
    transparent: true,
    opacity: 0.2,
    roughness: 0.03,
    metalness: 0.2,
    clearcoat: 1,
    clearcoatRoughness: 0.02,
    depthWrite: false,
  });
  const curtainMesh = instanced(new THREE.BoxGeometry(1, CURT_H, 0.06), curtMat, curtain.length);
  const mullions = instanced(
    new THREE.BoxGeometry(0.06, CURT_H + 0.02, 0.12),
    M('#8E959E', { metalness: 0.5, roughness: 0.35 }),
    curtain.length,
  );
  const bands = instanced(
    new THREE.BoxGeometry(1, 0.08, 0.18),
    M('#7D848D', { metalness: 0.5, roughness: 0.35 }),
    curtain.length * 2,
  );
  curtain.forEach(([x, y], i) => {
    const across = y === 0 || y === f.H - 1;
    const ry = across ? 0 : Math.PI / 2;
    set(curtainMesh, i, x + 0.5, CURT_H / 2, y + 0.5, ry);
    set(mullions, i, across ? x : x + 0.5, CURT_H / 2, across ? y + 0.5 : y, ry);
    set(bands, i * 2, x + 0.5, CURT_H, y + 0.5, ry);
    set(bands, i * 2 + 1, x + 0.5, 0.04, y + 0.5, ry);
  });
  curtainMesh.renderOrder = 2;
  mullions.castShadow = true;
  const doors = f.rooms.flatMap((r) =>
    r.doors.map(([x, y]) => [x, y, y === r.y0 || y === r.y1] as const),
  );
  const doorHeads = instanced(
    new THREE.BoxGeometry(1, 0.2, 0.22),
    M('#2E3440', { metalness: 0.4, roughness: 0.4 }),
    doors.length,
  );
  const sills = instanced(new THREE.BoxGeometry(0.96, 0.012, 0.16), M(C.brass), doors.length);
  doors.forEach(([x, y, across], i) => {
    const ry = across ? 0 : Math.PI / 2;
    set(doorHeads, i, x + 0.5, WALL_H - 0.1, y + 0.5, ry);
    set(sills, i, x + 0.5, 0.006, y + 0.5, ry);
  });
  doorHeads.castShadow = true;
  // Sills stay visible in cutaway, so they ride along with the base rather than the switchable set.
  base.add(sills);
  return { walls, caps, glass: panes, rails, curtain: curtainMesh, mullions, bands, doorHeads };
}

/* ---------- building view: the floors as an exploded stack ---------- */

const STACK_GAP = 10;

export function buildStack(floors: FloorModel[], environment: THREE.Texture | null): BuiltScene {
  const W = Math.max(...floors.map((f) => f.W));
  const H = Math.max(...floors.map((f) => f.H));
  const scene = new THREE.Scene();
  addEnvironment(
    scene,
    {
      cx: W / 2,
      cz: H / 2,
      w: W,
      d: H,
      groundY: -0.05,
      seed: floors.length > 1 ? 11 : 23,
      shadowExt: Math.max(W, H) / 2 + 10,
      terrace: 5,
    },
    environment,
  );
  const plinth = new THREE.Mesh(new THREE.BoxGeometry(W + 1, 0.6, H + 1), M('#CFCAC1'));
  plinth.position.set(W / 2, 0.25, H / 2);
  plinth.receiveShadow = plinth.castShadow = true;
  scene.add(plinth);
  const pick: THREE.Object3D[] = [];
  const slabs: { id: string; group: THREE.Group; baseY: number; f: FloorModel }[] = [];
  floors.forEach((f, i) => {
    const y = 1.2 + i * STACK_GAP;
    const group = new THREE.Group();
    group.position.y = y;
    const slab = new THREE.Mesh(new THREE.BoxGeometry(f.W, 0.45, f.H), M('#E4E0D8'));
    slab.position.set(f.W / 2, -0.23, f.H / 2);
    slab.castShadow = slab.receiveShadow = true;
    slab.userData.target = f.id;
    group.add(slab);
    pick.push(slab);
    const plan = paintPlan(f, 28);
    const top = new THREE.Mesh(
      new THREE.PlaneGeometry(f.W, f.H),
      new THREE.MeshStandardMaterial(
        plan ? { map: canvasTex(plan), roughness: 0.85 } : { color: 0xf6f5f1 },
      ),
    );
    top.rotation.x = -Math.PI / 2;
    top.position.set(f.W / 2, 0.005, f.H / 2);
    top.receiveShadow = true;
    top.userData.target = f.id;
    group.add(top);
    pick.push(top);
    const dummy = new THREE.Object3D();
    const solid: [number, number, string][] = [];
    const glass: [number, number][] = [];
    for (let ty = 0; ty < f.H; ty++)
      for (let x = 0; x < f.W; x++) {
        const ch = f.at(x, ty);
        if (ch === '#' || ch === 'F') solid.push([x, ty, ch]);
        else if (ch === 'G' || ch === 'C') glass.push([x, ty]);
      }
    const blocks = instanced(
      new THREE.BoxGeometry(1, 1, 1),
      new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.9 }),
      solid.length,
    );
    const wallColor = new THREE.Color('#F4F2ED');
    const furnitureColor = new THREE.Color('#C9C3B8');
    solid.forEach(([x, ty, ch], k) => {
      const h = ch === '#' ? 0.9 : 0.35;
      dummy.position.set(x + 0.5, h / 2, ty + 0.5);
      dummy.scale.set(1, h, 1);
      dummy.updateMatrix();
      blocks.setMatrixAt(k, dummy.matrix);
      blocks.setColorAt(k, ch === '#' ? wallColor : furnitureColor);
    });
    blocks.castShadow = blocks.receiveShadow = true;
    group.add(blocks);
    const panes = instanced(
      new THREE.BoxGeometry(1, 0.9, 0.08),
      new THREE.MeshStandardMaterial({
        color: new THREE.Color('#9FC2DA'),
        transparent: true,
        opacity: 0.45,
        depthWrite: false,
      }),
      glass.length,
    );
    glass.forEach(([x, ty], k) => {
      const across =
        ty === 0 || ty === f.H - 1 || f.at(x - 1, ty) === 'G' || f.at(x + 1, ty) === 'G';
      dummy.scale.set(1, 1, 1);
      dummy.rotation.set(0, across ? 0 : Math.PI / 2, 0);
      dummy.position.set(x + 0.5, 0.45, ty + 0.5);
      dummy.updateMatrix();
      panes.setMatrixAt(k, dummy.matrix);
    });
    group.add(panes);
    scene.add(group);
    slabs.push({ id: f.id, group, baseY: y, f });
  });
  const topY = 1.2 + (floors.length - 1) * STACK_GAP + 1.4;
  const lift = floors[0]?.items.find((it) => it.t === 'lift');
  if (lift) {
    const core = new THREE.Mesh(
      new THREE.BoxGeometry(8, topY, 5),
      new THREE.MeshStandardMaterial({
        color: new THREE.Color('#B8B2A8'),
        transparent: true,
        opacity: 0.5,
      }),
    );
    core.position.set(lift.x0 + 4, topY / 2, lift.y0 + 2.5);
    scene.add(core);
  }
  const outline = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(W + 0.2, topY, H + 0.2)),
    new THREE.LineBasicMaterial({
      color: new THREE.Color('#23395D'),
      transparent: true,
      opacity: 0.35,
    }),
  );
  outline.position.set(W / 2, topY / 2, H / 2);
  scene.add(outline);
  for (const [x, z] of [
    [0, 0],
    [W, 0],
    [0, H],
    [W, H],
  ] as const) {
    const column = new THREE.Mesh(new THREE.BoxGeometry(0.4, topY, 0.4), M('#A9A398'));
    column.position.set(x, topY / 2, z);
    column.castShadow = true;
    scene.add(column);
  }
  return {
    scene,
    fit: { cx: W / 2, cy: topY / 2, cz: H / 2, w: W, d: H, h: topY + 2 },
    pick,
    floorView: false,
    anchors: slabs.map((s) => ({
      id: s.id,
      side: 'right',
      at: () => new THREE.Vector3(s.f.W + 1, s.group.position.y + 0.3, s.f.H / 2),
    })),
    hover: (id, dt) => {
      let moved = false;
      for (const s of slabs) {
        const target = s.baseY + (s.id === id ? 0.9 : 0);
        const step = (target - s.group.position.y) * Math.min(1, dt * 8);
        if (Math.abs(step) > 1e-4) moved = true;
        s.group.position.y += step;
      }
      return moved;
    },
  };
}

/* ---------- campus view ---------- */

const SCALE = 0.62;

function buildingModel(
  scene: THREE.Scene,
  building: Building,
  ground: FloorModel | undefined,
  pick: THREE.Object3D[],
) {
  const tower = building.floors.length > 1;
  const levels = building.floors.length;
  const w = (ground?.W ?? 48) * SCALE;
  const d = (ground?.H ?? 32) * SCALE;
  const lh = tower ? 4.2 : 5.2;
  const h = levels * lh;
  const g = new THREE.Group();
  g.position.set(building.campusX, 0, building.campusZ);
  const facade = facadeTex(levels, {
    seed: building.name.length * 3,
    top: tower ? '#7F9BB6' : '#9DB4C2',
    bot: tower ? '#B8CCDD' : '#CBD9E1',
  });
  const side = (repeat: number) => {
    const t = canvasTex(facade);
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(repeat, 1);
    return new THREE.MeshStandardMaterial({
      map: t,
      roughness: 0.3,
      metalness: 0.2,
      emissive: new THREE.Color('#1B2A45'),
      emissiveIntensity: 0,
    });
  };
  const sides = [side(d / 5), side(d / 5), M('#D9D5CD'), M('#D9D5CD'), side(w / 5), side(w / 5)];
  const body = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), sides);
  body.position.set(0, h / 2, 0);
  body.castShadow = body.receiveShadow = true;
  body.userData.target = building.id;
  g.add(body);
  pick.push(body);
  for (let l = 0; l <= levels; l++) {
    const band = new THREE.Mesh(new THREE.BoxGeometry(w + 0.5, 0.22, d + 0.5), M('#EEEBE4'));
    band.position.set(0, l * lh, 0);
    band.castShadow = true;
    g.add(band);
  }
  const parapet = new THREE.Mesh(new THREE.BoxGeometry(w + 0.5, 0.7, d + 0.5), M('#E4E0D8'));
  parapet.position.set(0, h + 0.35, 0);
  g.add(parapet);
  const roof = new THREE.Mesh(new THREE.BoxGeometry(w - 0.4, 0.1, d - 0.4), M('#9EA3A8'));
  roof.position.set(0, h + 0.7, 0);
  g.add(roof);
  const r = rng(building.name.length);
  for (let i = 0; i < 5; i++) {
    const unit = new THREE.Mesh(RB(1.6 + r() * 2, 0.9, 1.4 + r() * 1.5, 0.1), M('#B9BEC4'));
    unit.position.set((r() - 0.5) * (w - 5), h + 1.15, (r() - 0.5) * (d - 5));
    unit.castShadow = true;
    g.add(unit);
  }
  if (!tower) {
    // Timber fins and a solar roof for the low pavilion.
    for (let x = -w / 2 + 0.4; x < w / 2; x += 0.7) {
      const fin = new THREE.Mesh(new THREE.BoxGeometry(0.12, h, 0.4), M(C.oak));
      fin.position.set(x, h / 2, d / 2 + 0.2);
      fin.castShadow = true;
      g.add(fin);
    }
    const solar = new THREE.Mesh(
      new THREE.BoxGeometry(w * 0.7, 0.08, d * 0.6),
      M('#2B3A52', { metalness: 0.5, roughness: 0.3 }),
    );
    solar.position.set(0, h + 0.78, 0);
    g.add(solar);
  }
  const canopy = new THREE.Mesh(new THREE.BoxGeometry(6, 0.25, 3), M('#2E3440'));
  canopy.position.set(0, 3.2, d / 2 + 1.4);
  canopy.castShadow = true;
  g.add(canopy);
  for (const x of [-2.8, 2.8]) {
    const post = new THREE.Mesh(new THREE.BoxGeometry(0.18, 3.2, 0.18), M('#2E3440'));
    post.position.set(x, 1.6, d / 2 + 2.7);
    g.add(post);
  }
  const { c, g: sg } = canvas(900, 120);
  sg.font = '700 92px "Familjen Grotesk",Arial';
  sg.fillStyle = '#B8935A';
  sg.textAlign = 'center';
  sg.fillText(building.name.toUpperCase(), 450, 98, 880);
  const sign = new THREE.Mesh(
    new THREE.PlaneGeometry(tower ? 12 : 10, 1.6),
    new THREE.MeshBasicMaterial({ map: canvasTex(c), transparent: true }),
  );
  sign.position.set(0, h + 1.55, d / 2 + 0.27);
  g.add(sign);
  const edge = new THREE.LineSegments(
    new THREE.EdgesGeometry(new THREE.BoxGeometry(w + 0.7, h + 0.9, d + 0.7)),
    new THREE.LineBasicMaterial({ color: new THREE.Color('#B8935A') }),
  );
  edge.position.y = (h + 0.9) / 2;
  edge.visible = false;
  g.add(edge);
  scene.add(g);
  const glow = sides.filter(
    (m): m is THREE.MeshStandardMaterial => m instanceof THREE.MeshStandardMaterial && !!m.map,
  );
  return { id: building.id, group: g, height: h, edge, glow };
}

// shortcut: the plaza, fountain and car park are laid out for the sample campus; derive them from building positions when real campuses differ.
export function buildCampus(
  buildings: Building[],
  grounds: Map<string, FloorModel>,
  environment: THREE.Texture | null,
): BuiltScene {
  const scene = new THREE.Scene();
  addEnvironment(
    scene,
    { cx: 0, cz: 0, w: 100, d: 52, groundY: 0, seed: 3, shadowExt: 70, trees: 70, terrace: 2 },
    environment,
  );
  const G = beginGroup();
  const plaza = new THREE.Mesh(
    new THREE.PlaneGeometry(26, 24),
    new THREE.MeshStandardMaterial({ map: paving(26, 24), roughness: 0.9 }),
  );
  plaza.rotation.x = -Math.PI / 2;
  plaza.position.set(-4, 0.01, 12);
  scene.add(plaza);
  cyl(3.4, 3.6, 0.5, '#D9D4CB', -4, 0.25, 13, 40);
  const water = new THREE.Mesh(
    new THREE.CylinderGeometry(3.05, 3.05, 0.06, 40),
    M('#7FA9C2', { roughness: 0.1, metalness: 0.3 }),
  );
  water.position.set(-4, 0.48, 13);
  scene.add(water);
  cyl(0.4, 0.6, 1.4, '#D9D4CB', -4, 0.9, 13, 16);
  ['#23395D', '#B8935A', '#F2F1ED'].forEach((color, i) => {
    const x = -8 + i * 1.8;
    cyl(0.05, 0.05, 7, '#C9CDD3', x + 9, 3.5, 4.5, 8);
    const flag = new THREE.Mesh(
      new THREE.PlaneGeometry(1.6, 1),
      new THREE.MeshStandardMaterial({ color: new THREE.Color(color), side: THREE.DoubleSide }),
    );
    flag.position.set(x + 9.8, 6.4, 4.5);
    scene.add(flag);
  });
  for (const [x, z, ry] of [
    [-10, 9, 0],
    [-10, 17, 0],
    [2, 9, Math.PI],
    [2, 17, Math.PI],
  ] as const)
    bench(x, 0, z, ry);
  const lot = new THREE.Mesh(
    new THREE.PlaneGeometry(22, 14),
    new THREE.MeshStandardMaterial({ color: new THREE.Color('#646971'), roughness: 0.95 }),
  );
  lot.rotation.x = -Math.PI / 2;
  lot.position.set(26, 0.01, 22);
  scene.add(lot);
  const front = ['#C9CDD3', '#2F3D5A', '#F2F1ED', '#3A3E46', '#8E9AAE'];
  const back = ['#3A3E46', '#C9CDD3', '#6B4A33'];
  for (let i = 0; i < 8; i++) {
    box(0.08, 0.01, 4, '#ECE8DC', 16 + i * 2.6, 0.02, 19);
    box(0.08, 0.01, 4, '#ECE8DC', 16 + i * 2.6, 0.02, 25);
    if (i < 7 && i % 3 !== 1) car(17.3 + i * 2.6, 0, 19, 0, front[i % 5] ?? '#C9CDD3');
    if (i < 7 && i % 2 === 0) car(17.3 + i * 2.6, 0, 25, Math.PI, back[i % 3] ?? '#3A3E46');
  }
  for (let x = -56; x <= 50; x += 8) tree(x, 0, -30, 1.2, 'cypress', rng(x + 100));
  const r = rng(77);
  for (let i = 0; i < 14; i++) tree(-16 + r() * 28, 0, 4 + r() * 2.5, 0.8, 'round', r);
  mergeInto(scene, G);
  const pick: THREE.Object3D[] = [];
  const models = buildings.map((b) => buildingModel(scene, b, grounds.get(b.id), pick));
  return {
    scene,
    fit: { cx: 0, cy: 4, cz: 4, w: 96, d: 64, h: 12 },
    pick,
    floorView: false,
    anchors: models.map((m) => ({
      id: m.id,
      side: 'above',
      at: () => new THREE.Vector3(m.group.position.x, m.height + 3.2, m.group.position.z),
    })),
    hover: (id) => {
      for (const m of models) {
        const hot = m.id === id;
        m.edge.visible = hot;
        for (const material of m.glow) material.emissiveIntensity = hot ? 0.18 : 0;
      }
    },
  };
}
