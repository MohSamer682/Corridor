import * as THREE from 'three';
import {
  C,
  M,
  PM,
  beginGroup,
  box,
  canvas,
  canvasTex,
  current,
  cyl,
  grp,
  mergeInto,
  pickFrom,
  rbox,
  rng,
  sph,
  blob,
  type Random,
} from './core';

/* Sky, grass, terrace, road with parked cars, trees and lamps around every scene. */

export const R_CAM = 170;

type EnvTextures = {
  sky: THREE.Texture;
  grass: THREE.Texture;
  pave: THREE.Texture;
  road: THREE.Texture;
};
let env: EnvTextures | null = null;

function envTextures(): EnvTextures {
  if (env) return env;
  let { c, g } = canvas(4, 256);
  const sky = g.createLinearGradient(0, 0, 0, 256);
  sky.addColorStop(0, '#AFC6DD');
  sky.addColorStop(0.55, '#DCE5EE');
  sky.addColorStop(1, '#EEF1F3');
  g.fillStyle = sky;
  g.fillRect(0, 0, 4, 256);
  const skyTex = canvasTex(c);

  ({ c, g } = canvas(256, 256));
  g.fillStyle = '#86A56C';
  g.fillRect(0, 0, 256, 256);
  let r = rng(5);
  for (let i = 0; i < 2600; i++) {
    g.fillStyle = pickFrom(['#7C9B62', '#93B177', '#8AAA70', '#78965E'], r);
    g.fillRect(r() * 256, r() * 256, 2 + r() * 3, 2 + r() * 3);
  }
  const grass = canvasTex(c, [90, 90]);

  ({ c, g } = canvas(128, 128));
  g.fillStyle = '#D8D3CA';
  g.fillRect(0, 0, 128, 128);
  r = rng(9);
  for (let i = 0; i < 300; i++) {
    g.fillStyle = r() < 0.5 ? 'rgba(255,255,255,.12)' : 'rgba(0,0,0,.04)';
    g.fillRect(r() * 128, r() * 128, 2, 2);
  }
  g.strokeStyle = 'rgba(110,100,85,.28)';
  g.lineWidth = 2;
  for (let k = 0; k <= 128; k += 32) {
    g.beginPath();
    g.moveTo(k, 0);
    g.lineTo(k, 128);
    g.moveTo(0, k);
    g.lineTo(128, k);
    g.stroke();
  }
  const pave = canvasTex(c, [1, 1]);

  ({ c, g } = canvas(256, 64));
  g.fillStyle = '#575C64';
  g.fillRect(0, 0, 256, 64);
  r = rng(3);
  for (let i = 0; i < 500; i++) {
    g.fillStyle = r() < 0.5 ? 'rgba(255,255,255,.05)' : 'rgba(0,0,0,.06)';
    g.fillRect(r() * 256, r() * 64, 2, 2);
  }
  g.fillStyle = '#ECE8DC';
  for (let x = 0; x < 256; x += 64) g.fillRect(x + 8, 30, 36, 4);
  g.fillStyle = '#D9D4C7';
  g.fillRect(0, 2, 256, 2);
  g.fillRect(0, 60, 256, 2);
  const road = canvasTex(c, [1, 1]);

  env = { sky: skyTex, grass, pave, road };
  return env;
}

export function paving(width: number, depth: number) {
  const t = envTextures().pave.clone();
  t.needsUpdate = true;
  t.repeat.set(width / 2, depth / 2);
  return t;
}

/** Glass curtain-wall facade, one band per storey. */
export function facadeTex(
  levels: number,
  opts: { seed?: number; top?: string; bot?: string; frame?: string; slab?: string } = {},
) {
  const { c, g } = canvas(512, Math.max(1, levels) * 96);
  const r = rng(opts.seed ?? 1);
  for (let l = 0; l < levels; l++) {
    const y = l * 96;
    const gr = g.createLinearGradient(0, y, 0, y + 96);
    gr.addColorStop(0, opts.top ?? '#7F9BB6');
    gr.addColorStop(1, opts.bot ?? '#B8CCDD');
    g.fillStyle = gr;
    g.fillRect(0, y, 512, 96);
    for (let i = 0; i < 16; i++) {
      if (r() < 0.18) {
        g.fillStyle = 'rgba(255,226,170,.55)';
        g.fillRect(i * 32 + 3, y + 16, 26, 64);
      }
      if (r() < 0.25) {
        g.fillStyle = 'rgba(255,255,255,.18)';
        g.fillRect(i * 32 + 3, y + 16, 26, 64);
      }
    }
    g.fillStyle = opts.frame ?? '#2E3440';
    for (let i = 0; i <= 16; i++) g.fillRect(i * 32 - 1.5, y, 3, 96);
    g.fillStyle = opts.slab ?? '#E9E6DF';
    g.fillRect(0, y, 512, 12);
    g.fillRect(0, y + 88, 512, 8);
  }
  return c;
}

export function tree(x: number, y: number, z: number, s: number, kind: string, r: Random) {
  const G = current();
  if (kind === 'palm') {
    let px = x;
    let py = y;
    const lean = (r() - 0.5) * 0.5;
    for (let i = 0; i < 6; i++) {
      cyl(0.12 * s, 0.15 * s, 0.75 * s, '#7A6248', px, py + 0.37 * s, z, 8);
      py += 0.72 * s;
      px += lean * 0.12 * s;
    }
    for (let i = 0; i < 9; i++) {
      const a = (i / 9) * Math.PI * 2;
      const frond = new THREE.Mesh(
        new THREE.BoxGeometry(0.18 * s, 0.03 * s, 1.7 * s),
        M(i % 2 ? C.leaf2 : C.leaf1, { roughness: 0.9 }),
      );
      frond.position.set(px + Math.sin(a) * 0.75 * s, py - 0.15 * s, z + Math.cos(a) * 0.75 * s);
      frond.rotation.set(-0.55, a, 0, 'YXZ');
      G.add(frond);
    }
    sph(0.18 * s, '#5B4632', px, py - 0.05 * s, z);
    return;
  }
  cyl(0.11 * s, 0.16 * s, 1.4 * s, '#6B5238', x, y + 0.7 * s, z, 8);
  if (kind === 'cypress') {
    const cone = new THREE.Mesh(
      new THREE.ConeGeometry(0.65 * s, 2.8 * s, 9),
      M(C.leaf1, { flatShading: true, roughness: 0.9 }),
    );
    cone.position.set(x, y + 2.4 * s, z);
    G.add(cone);
    return;
  }
  const leaves = [C.leaf1, C.leaf2, C.leaf3];
  blob(1.0 * s, pickFrom(leaves, r), x, y + 2.0 * s, z, 1, 0.9, 1);
  blob(0.75 * s, pickFrom(leaves, r), x + 0.5 * s, y + 2.5 * s, z - 0.2 * s);
  blob(0.7 * s, pickFrom(leaves, r), x - 0.45 * s, y + 2.4 * s, z + 0.3 * s);
}

type CarShape = {
  suv: boolean;
  W: number;
  roof: number;
  belt: number;
  gc: number;
  wr: number;
  wx: number;
  L: number;
  body: THREE.BufferGeometry;
  cab: THREE.BufferGeometry;
  tire: THREE.BufferGeometry;
  rim: THREE.BufferGeometry;
  hub: THREE.BufferGeometry;
  spoke: THREE.BufferGeometry;
};
const carShapes = new Map<string, CarShape>();

function carShape(kind: string): CarShape {
  const cached = carShapes.get(kind);
  if (cached) return cached;
  const suv = kind === 'suv';
  const L = suv ? 2.3 : 2.28;
  const gc = suv ? 0.42 : 0.3;
  const belt = suv ? 1.1 : 0.9;
  const roof = suv ? 1.74 : 1.38;
  const W = suv ? 1.92 : 1.84;
  const wr = suv ? 0.42 : 0.37;
  const wx = suv ? 1.45 : 1.42;
  const s = new THREE.Shape();
  s.moveTo(-L, gc + 0.06);
  s.lineTo(-wx - wr - 0.06, gc);
  s.absarc(-wx, gc, wr + 0.06, Math.PI, 0, true);
  s.lineTo(wx - wr - 0.06, gc);
  s.absarc(wx, gc, wr + 0.06, Math.PI, 0, true);
  s.lineTo(L, gc + 0.06);
  if (suv) {
    s.lineTo(L + 0.02, belt - 0.05);
    s.quadraticCurveTo(L, belt + 0.04, L - 0.15, belt + 0.04);
  } else {
    s.lineTo(L + 0.04, gc + 0.32);
    s.quadraticCurveTo(L + 0.02, belt - 0.02, L - 0.3, belt + 0.03);
  }
  s.lineTo(-0.95, belt);
  s.lineTo(-L + 0.42, belt - (suv ? 0.08 : 0.15));
  s.quadraticCurveTo(-L - 0.03, belt - (suv ? 0.1 : 0.2), -L - 0.02, gc + 0.36);
  s.lineTo(-L, gc + 0.06);
  const bw = W - 0.18;
  const body = new THREE.ExtrudeGeometry(s, {
    depth: bw,
    bevelEnabled: true,
    bevelThickness: 0.09,
    bevelSize: 0.08,
    bevelSegments: 4,
    curveSegments: 14,
  });
  body.translate(0, 0, -bw / 2);
  body.rotateY(Math.PI / 2);
  const c = new THREE.Shape();
  if (suv) {
    c.moveTo(-1.05, belt);
    c.lineTo(L - 0.18, belt);
    c.lineTo(L - 0.3, roof - 0.05);
    c.lineTo(-0.3, roof);
    c.quadraticCurveTo(-0.8, roof - 0.04, -1.05, belt);
  } else {
    c.moveTo(-1.0, belt - 0.01);
    c.lineTo(1.55, belt + 0.02);
    c.quadraticCurveTo(1.05, roof + 0.02, 0.55, roof);
    c.lineTo(-0.12, roof);
    c.quadraticCurveTo(-0.6, roof - 0.02, -1.0, belt - 0.01);
  }
  const cw = W - 0.5;
  const cab = new THREE.ExtrudeGeometry(c, {
    depth: cw,
    bevelEnabled: true,
    bevelThickness: 0.07,
    bevelSize: 0.06,
    bevelSegments: 4,
    curveSegments: 14,
  });
  cab.translate(0, 0, -cw / 2);
  cab.rotateY(Math.PI / 2);
  const tire = new THREE.CylinderGeometry(wr, wr, 0.27, 28).rotateZ(Math.PI / 2);
  const rim = new THREE.CylinderGeometry(wr * 0.66, wr * 0.66, 0.285, 24).rotateZ(Math.PI / 2);
  const hub = new THREE.CylinderGeometry(wr * 0.16, wr * 0.16, 0.3, 12).rotateZ(Math.PI / 2);
  const spoke = new THREE.BoxGeometry(0.29, wr * 1.18, 0.07);
  const shape = { suv, W, roof, belt, gc, wr, wx, L, body, cab, tire, rim, hub, spoke };
  carShapes.set(kind, shape);
  return shape;
}

export function car(x: number, y: number, z: number, ry: number, color: string, kind?: string) {
  const k = carShape(kind ?? (((x * 7 + z * 3) | 0) % 3 === 0 ? 'suv' : 'sedan'));
  const g = grp(x, z, ry, y);
  const mk = (
    geo: THREE.BufferGeometry,
    mat: THREE.Material,
    px: number,
    py: number,
    pz: number,
  ) => {
    const m = new THREE.Mesh(geo, mat);
    m.position.set(px, py, pz);
    g.add(m);
    return m;
  };
  const paint = PM('paint' + color, {
    color: new THREE.Color(color),
    metalness: 0.55,
    roughness: 0.32,
    clearcoat: 1,
    clearcoatRoughness: 0.05,
  });
  const glass = PM('carGlass', {
    color: new THREE.Color('#121822'),
    metalness: 0.4,
    roughness: 0.06,
    clearcoat: 1,
    clearcoatRoughness: 0.03,
  });
  mk(k.body, paint, 0, 0, 0);
  mk(k.cab, glass, 0, 0, 0);
  rbox(
    k.W - 0.62,
    0.05,
    k.suv ? 2.2 : 0.95,
    0.02,
    '#0E1218',
    0,
    k.roof + 0.04,
    k.suv ? -0.35 : -0.2,
    { roughness: 0.2, metalness: 0.5 },
    0,
    g,
  );
  const tireM = PM('tire', { color: new THREE.Color('#17191C'), roughness: 0.85 });
  const rimM = PM('rim', { color: new THREE.Color('#C9CED4'), metalness: 1, roughness: 0.22 });
  const hubM = PM('hub', { color: new THREE.Color('#2B2F35'), metalness: 0.8, roughness: 0.3 });
  for (const [sx, sz] of [
    [-1, 1],
    [1, 1],
    [-1, -1],
    [1, -1],
  ] as const) {
    const px = sx * (k.W / 2 - 0.12);
    const pz = sz * k.wx;
    mk(k.tire, tireM, px, k.wr, pz);
    mk(k.rim, rimM, px + sx * 0.005, k.wr, pz);
    mk(k.hub, hubM, px + sx * 0.01, k.wr, pz);
    for (let i = 0; i < 5; i++)
      mk(k.spoke, rimM, px + sx * 0.006, k.wr, pz).rotation.x = (i / 5) * Math.PI;
  }
  const fz = k.L + 0.1;
  const rz = -(k.L + 0.1);
  const lamp = { emissive: '#E8F1FF', emissiveIntensity: 1.4 };
  box(0.5, 0.07, 0.05, '#F4F7FB', -(k.W / 2 - 0.34), k.belt - 0.22, fz, lamp, 0, g);
  box(0.5, 0.07, 0.05, '#F4F7FB', k.W / 2 - 0.34, k.belt - 0.22, fz, lamp, 0, g);
  box(
    k.W - 0.82,
    0.17,
    0.04,
    '#14171C',
    0,
    k.gc + 0.36,
    fz - 0.01,
    { roughness: 0.3, metalness: 0.6 },
    0,
    g,
  );
  box(
    k.W - 0.3,
    0.05,
    0.05,
    '#B3231E',
    0,
    k.belt - 0.12,
    rz,
    { emissive: '#FF2A1F', emissiveIntensity: 1.1 },
    0,
    g,
  );
  box(k.W - 0.1, 0.06, 0.05, '#2A2E35', 0, k.gc + 0.12, fz - 0.03, null, 0, g);
  box(k.W - 0.1, 0.06, 0.05, '#2A2E35', 0, k.gc + 0.12, rz + 0.03, null, 0, g);
  for (const sx of [-1, 1]) {
    rbox(0.2, 0.11, 0.1, 0.03, color, sx * (k.W / 2 + 0.06), k.belt + 0.1, 0.85, null, 0, g);
    box(
      0.03,
      0.03,
      0.5,
      '#C9CED4',
      sx * (k.W / 2 + 0.005),
      k.belt - 0.02,
      0.2,
      { metalness: 1, roughness: 0.25 },
      0,
      g,
    );
  }
}

function lampPost(x: number, y: number, z: number) {
  cyl(0.06, 0.08, 3.6, '#3A3F48', x, y + 1.8, z, 8);
  box(0.7, 0.08, 0.22, '#3A3F48', x + 0.3, y + 3.6, z);
  box(0.5, 0.04, 0.16, '#FFF4D6', x + 0.4, y + 3.55, z, {
    emissive: '#FFE9B8',
    emissiveIntensity: 0.6,
  });
}

export function bench(x: number, y: number, z: number, ry: number) {
  const g = grp(x, z, ry, y);
  box(1.8, 0.06, 0.5, C.oak, 0, 0.45, 0, null, 0, g);
  box(1.8, 0.4, 0.06, C.oak, 0, 0.7, -0.24, null, 0, g);
  box(0.08, 0.45, 0.5, C.metal, -0.8, 0.22, 0, null, 0, g);
  box(0.08, 0.45, 0.5, C.metal, 0.8, 0.22, 0, null, 0, g);
}

function hedge(x0: number, z0: number, x1: number, z1: number, y: number) {
  const w = Math.abs(x1 - x0) || 0.8;
  const d = Math.abs(z1 - z0) || 0.8;
  rbox(w, 0.6, d, 0.12, '#B9B3A8', (x0 + x1) / 2, y + 0.3, (z0 + z1) / 2);
  rbox(w - 0.1, 0.45, d - 0.1, 0.2, C.leaf2, (x0 + x1) / 2, y + 0.75, (z0 + z1) / 2);
}

export type EnvOptions = {
  cx: number;
  cz: number;
  w: number;
  d: number;
  groundY: number;
  seed: number;
  shadowExt: number;
  terrace: number;
  trees?: number;
};

/** Sky, sun with shadows, grass, a paved terrace and the street with trees, lamps and parked cars. */
export function addEnvironment(
  scene: THREE.Scene,
  o: EnvOptions,
  environment: THREE.Texture | null,
) {
  const t = envTextures();
  scene.background = t.sky;
  scene.fog = new THREE.Fog(new THREE.Color('#E6ECF1'), R_CAM + 40, R_CAM + 230);
  if (environment) {
    scene.environment = environment;
    scene.environmentIntensity = 0.28;
  }
  scene.add(
    new THREE.HemisphereLight(new THREE.Color('#EEF3FA'), new THREE.Color('#7E8672'), 0.62),
  );
  const sun = new THREE.DirectionalLight(new THREE.Color('#FFF0DA'), 2.3);
  sun.position.set(o.cx - 34, 74, o.cz - 40);
  sun.target.position.set(o.cx, 0, o.cz);
  scene.add(sun, sun.target);
  sun.castShadow = true;
  sun.shadow.mapSize.set(4096, 4096);
  Object.assign(sun.shadow.camera, {
    left: -o.shadowExt,
    right: o.shadowExt,
    top: o.shadowExt,
    bottom: -o.shadowExt,
    near: 1,
    far: 260,
  });
  sun.shadow.bias = -0.0003;
  sun.shadow.normalBias = 0.025;
  sun.shadow.radius = 3;
  const gy = o.groundY;
  const ground = (
    map: THREE.Texture | null,
    w: number,
    d: number,
    x: number,
    y: number,
    z: number,
    color?: string,
  ) => {
    const mesh = new THREE.Mesh(
      new THREE.PlaneGeometry(w, d),
      new THREE.MeshStandardMaterial(
        map ? { map, roughness: 0.95 } : { color: new THREE.Color(color), roughness: 0.95 },
      ),
    );
    mesh.rotation.x = -Math.PI / 2;
    mesh.position.set(x, y, z);
    mesh.receiveShadow = true;
    scene.add(mesh);
  };
  ground(t.grass, 900, 900, o.cx, gy - 0.03, o.cz);
  const pw = o.w + o.terrace * 2;
  const pd = o.d + o.terrace * 2;
  ground(paving(pw, pd), pw, pd, o.cx, gy - 0.01, o.cz);
  const roadZ = o.cz + pd / 2 + 6;
  const road = t.road.clone();
  road.needsUpdate = true;
  road.repeat.set(900 / 16, 1);
  ground(road, 900, 8, o.cx, gy - 0.005, roadZ);
  ground(null, 900, 2.4, o.cx, gy, roadZ - 5.2, '#CFCAC0');

  const r = rng(o.seed);
  const G = beginGroup();
  for (let i = 0; i < (o.trees ?? 46); i++) {
    for (let k = 0; k < 30; k++) {
      const x = o.cx + (r() - 0.5) * (pw + 70);
      const z = o.cz + (r() - 0.5) * (pd + 60);
      const inside = Math.abs(x - o.cx) < pw / 2 + 2 && Math.abs(z - o.cz) < pd / 2 + 2;
      if (!inside && Math.abs(z - roadZ) >= 7.5) {
        tree(x, gy, z, 0.9 + r() * 0.7, r() < 0.22 ? 'cypress' : r() < 0.3 ? 'palm' : 'round', r);
        break;
      }
    }
  }
  for (let x = o.cx - pw / 2 - 30; x < o.cx + pw / 2 + 30; x += 9)
    tree(x, gy, roadZ - 7.4, 1.1, 'palm', r);
  for (let x = o.cx - pw / 2 - 20; x < o.cx + pw / 2 + 20; x += 14) lampPost(x, gy, roadZ - 4.6);
  const carColors = [
    '#C9CDD3',
    '#1E2B45',
    '#EDEDEA',
    '#2A2D33',
    '#7E8B9E',
    '#5A3E2C',
    '#8C1C22',
    '#B9BDC2',
  ];
  for (let x = o.cx - pw / 2; x < o.cx + pw / 2; x += 6.5) {
    if (r() < 0.7)
      car(
        x,
        gy,
        roadZ + (r() < 0.5 ? -2 : 2),
        (Math.PI / 2) * (r() < 0.5 ? 1 : -1),
        pickFrom(carColors, r),
      );
  }
  hedge(o.cx - pw / 2 + 1, o.cz + pd / 2 - 0.6, o.cx - o.w / 2 - 1, o.cz + pd / 2 - 0.6, gy);
  hedge(o.cx + o.w / 2 + 1, o.cz + pd / 2 - 0.6, o.cx + pw / 2 - 1, o.cz + pd / 2 - 0.6, gy);
  bench(o.cx - o.w / 2 - 2, gy, o.cz + o.d / 2 + 2, 0);
  bench(o.cx + o.w / 2 + 2, gy, o.cz + o.d / 2 + 2, 0);
  mergeInto(scene, G);
}
