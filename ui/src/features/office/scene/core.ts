import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';

/* Shared building blocks for every scene, ported from the Corridor Office prototype. */

export const WALL_H = 1.4;
export const CURT_H = 1.6;

/** Seeded random numbers, so trees, books and cars land in the same place on every load. */
export function rng(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
export type Random = ReturnType<typeof rng>;
export const pickFrom = <T>(items: readonly T[], r: Random): T =>
  items[Math.floor(r() * items.length)] as T;

export const C = {
  oak: '#CDB28B',
  oakD: '#B5966C',
  walnut: '#6B4A33',
  walnutD: '#553A28',
  white: '#F2F1ED',
  quartz: '#EAE8E3',
  metal: '#2B2F37',
  steel: '#A7ADB5',
  brass: '#B8935A',
  navy: '#2F3D5A',
  navyL: '#43557A',
  sage: '#8EA391',
  camel: '#A67C52',
  charcoal: '#3A3E46',
  pot: '#E4E0D8',
  potD: '#55595F',
  screen: '#1A1F27',
  wall: '#F2F0EB',
  leaf1: '#4F7446',
  leaf2: '#638A55',
  leaf3: '#7DA36A',
  fabricG: '#5B6170',
} as const;
export const FABRIC: Record<string, [string, string]> = {
  navy: ['#2F3D5A', '#3B4C6E'],
  sage: ['#7F9583', '#94A898'],
  camel: ['#9C7048', '#B08359'],
  charcoal: ['#3A3E46', '#4A4F59'],
};
export const TEAM_FABRIC: Record<string, string> = {
  Backend: '#2F3D5A',
  Frontend: '#7F9583',
  DevOps: '#5B6B80',
  Support: '#9C7048',
  Product: '#5D63A8',
  QA: '#4E7A7E',
  Design: '#A26B4F',
};
/** Rotation that turns a model's local +z toward the way a seat faces. */
export const FACE: Record<string, number> = {
  down: 0,
  up: Math.PI,
  right: Math.PI / 2,
  left: -Math.PI / 2,
};

/** Cached materials and textures that every scene reuses; dropping one scene must leave them alone. */
const shared = new WeakSet<object>();
export const isShared = (resource: object) => shared.has(resource);
export const markShared = <T extends object>(resource: T) => {
  shared.add(resource);
  return resource;
};

let maxAnisotropy = 4;
export const setMaxAnisotropy = (value: number) => {
  maxAnisotropy = value;
};

export function canvas(width: number, height: number) {
  const c = document.createElement('canvas');
  c.width = width;
  c.height = height;
  const g = c.getContext('2d');
  if (!g) throw new Error('2D canvas is not available');
  return { c, g };
}

export function canvasTex(c: HTMLCanvasElement, repeat?: [number, number], raw = false) {
  const t = new THREE.CanvasTexture(c);
  if (!raw) t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = maxAnisotropy;
  if (repeat) {
    t.wrapS = t.wrapT = THREE.RepeatWrapping;
    t.repeat.set(repeat[0], repeat[1]);
  }
  return t;
}

/* ---------- procedural textures: wood grain, marble, woven fabric, brushed metal, leaves ---------- */

const textures = new Map<string, THREE.Texture>();
const once = (key: string, make: () => THREE.Texture) => {
  let t = textures.get(key);
  if (!t) {
    t = make();
    textures.set(key, t);
    markShared(t);
  }
  return t;
};

function texWood(
  key: string,
  base: string,
  dark: string,
  light: string,
  seed: number,
  boards: number,
) {
  return once(key, () => {
    const { c, g } = canvas(512, 512);
    const r = rng(seed);
    g.fillStyle = base;
    g.fillRect(0, 0, 512, 512);
    const bh = 512 / boards;
    for (let b = 0; b < boards; b++) {
      const y0 = b * bh;
      const tone = r();
      g.fillStyle =
        tone < 0.33 ? 'rgba(0,0,0,.06)' : tone > 0.66 ? 'rgba(255,255,255,.06)' : 'rgba(0,0,0,0)';
      g.fillRect(0, y0, 512, bh);
      for (let i = 0; i < 46; i++) {
        const yy = y0 + r() * bh;
        const amp = 1 + r() * 4;
        const fr = 0.004 + r() * 0.01;
        const ph = r() * 9;
        g.strokeStyle = r() < 0.55 ? dark : light;
        g.globalAlpha = 0.08 + r() * 0.22;
        g.lineWidth = 0.6 + r() * 1.8;
        g.beginPath();
        for (let x = 0; x <= 512; x += 8) {
          const yv = yy + Math.sin(x * fr + ph) * amp + Math.sin(x * fr * 3.1 + ph) * amp * 0.3;
          if (x) g.lineTo(x, yv);
          else g.moveTo(x, yv);
        }
        g.stroke();
      }
      g.globalAlpha = 1;
      g.fillStyle = 'rgba(0,0,0,.18)';
      g.fillRect(0, y0, 512, 1);
    }
    for (let k = 0; k < 3; k++) {
      const kx = r() * 512;
      const ky = r() * 512;
      const gr = g.createRadialGradient(kx, ky, 0, kx, ky, 10 + r() * 8);
      gr.addColorStop(0, 'rgba(40,20,5,.35)');
      gr.addColorStop(1, 'rgba(40,20,5,0)');
      g.fillStyle = gr;
      g.beginPath();
      g.ellipse(kx, ky, 22, 8, 0, 0, 7);
      g.fill();
    }
    return canvasTex(c, [1, 1]);
  });
}

function texMarble() {
  return once('marble', () => {
    const { c, g } = canvas(512, 512);
    const r = rng(44);
    g.fillStyle = '#F1EFEA';
    g.fillRect(0, 0, 512, 512);
    for (let i = 0; i < 900; i++) {
      g.fillStyle = `rgba(${(150 + r() * 60) | 0},${(150 + r() * 55) | 0},${(150 + r() * 50) | 0},${r() * 0.08})`;
      g.beginPath();
      g.arc(r() * 512, r() * 512, 4 + r() * 30, 0, 7);
      g.fill();
    }
    g.lineCap = 'round';
    for (let v = 0; v < 9; v++) {
      let x = r() * 512;
      let y = r() * 512;
      g.strokeStyle = v % 3 ? 'rgba(120,118,112,.35)' : 'rgba(176,140,90,.35)';
      g.lineWidth = 0.6 + r() * 2.2;
      g.beginPath();
      g.moveTo(x, y);
      for (let k = 0; k < 14; k++) {
        x += (r() - 0.3) * 70;
        y += (r() - 0.5) * 60;
        g.lineTo(x, y);
      }
      g.stroke();
    }
    return canvasTex(c, [1, 1]);
  });
}

function texWeave() {
  return once('weave', () => {
    const { c, g } = canvas(128, 128);
    const r = rng(12);
    g.fillStyle = '#F3F3F3';
    g.fillRect(0, 0, 128, 128);
    for (let y = 0; y < 128; y += 2)
      for (let x = 0; x < 128; x += 2) {
        const v = ((x >> 1) + (y >> 1)) % 2;
        g.fillStyle = v
          ? `rgba(0,0,0,${0.05 + r() * 0.06})`
          : `rgba(255,255,255,${0.1 + r() * 0.1})`;
        g.fillRect(x, y, 2, 2);
      }
    for (let i = 0; i < 220; i++) {
      g.fillStyle = `rgba(0,0,0,${r() * 0.05})`;
      g.fillRect(r() * 128, r() * 128, 1 + r() * 6, 1);
    }
    return canvasTex(c, [5, 5]);
  });
}

function texBrushed() {
  return once('brushed', () => {
    const { c, g } = canvas(256, 256);
    const r = rng(8);
    g.fillStyle = '#8a8a8a';
    g.fillRect(0, 0, 256, 256);
    for (let i = 0; i < 1400; i++) {
      const v = (110 + r() * 110) | 0;
      g.fillStyle = `rgba(${v},${v},${v},.35)`;
      g.fillRect(r() * 256, r() * 256, 20 + r() * 90, 1);
    }
    return canvasTex(c, [1, 1], true);
  });
}

function texLeaf() {
  return once('leaf', () => {
    const { c, g } = canvas(128, 128);
    const r = rng(3);
    g.fillStyle = '#ffffff';
    g.fillRect(0, 0, 128, 128);
    for (let i = 0; i < 300; i++) {
      g.fillStyle = `rgba(0,40,0,${r() * 0.15})`;
      g.beginPath();
      g.arc(r() * 128, r() * 128, 1 + r() * 4, 0, 7);
      g.fill();
    }
    return canvasTex(c, [2, 2]);
  });
}

/* ---------- materials ---------- */

type Special = {
  physical?: boolean;
  map?: () => THREE.Texture;
  roughnessMap?: () => THREE.Texture;
  color?: string;
  sheenColor?: string;
  roughness?: number;
  metalness?: number;
  clearcoat?: number;
  clearcoatRoughness?: number;
  sheen?: number;
  sheenRoughness?: number;
};

let specialCache: Map<string, Special> | null = null;
function specials() {
  if (specialCache) return specialCache;
  const lighten = (hex: string, amount: number) =>
    '#' + new THREE.Color(hex).lerp(new THREE.Color('#ffffff'), amount).getHexString();
  const wood = (
    map: () => THREE.Texture,
    roughness: number,
    clearcoat: number,
    coatRoughness: number,
  ): Special => ({
    physical: true,
    map,
    color: '#ffffff',
    roughness,
    clearcoat,
    clearcoatRoughness: coatRoughness,
  });
  const s = new Map<string, Special>([
    [C.oak, wood(() => texWood('oak', '#CBAE85', '#7A5A36', '#E8D3B0', 3, 5), 0.5, 0.35, 0.35)],
    [C.oakD, wood(() => texWood('oakD', '#B39066', '#5E4024', '#D7BC92', 5, 5), 0.55, 0.2, 0.4)],
    [C.walnut, wood(() => texWood('wal', '#6A4630', '#2E1B10', '#8E6446', 7, 4), 0.42, 0.7, 0.18)],
    [C.walnutD, wood(() => texWood('walD', '#4F3322', '#24150C', '#6E4A33', 9, 4), 0.5, 0.4, 0.3)],
    [C.white, { physical: true, roughness: 0.32, clearcoat: 0.6, clearcoatRoughness: 0.15 }],
    [C.quartz, wood(texMarble, 0.18, 0.8, 0.08)],
    [C.metal, { metalness: 0.75, roughness: 0.38, roughnessMap: texBrushed }],
    [C.steel, { metalness: 1, roughness: 0.24, roughnessMap: texBrushed, color: '#C4C9CF' }],
    [C.brass, { metalness: 1, roughness: 0.3, color: '#C9A266', roughnessMap: texBrushed }],
    [C.screen, { physical: true, roughness: 0.12, clearcoat: 1, clearcoatRoughness: 0.05 }],
    [C.pot, { physical: true, roughness: 0.45, clearcoat: 0.4, clearcoatRoughness: 0.2 }],
    [C.potD, { physical: true, roughness: 0.5, clearcoat: 0.3 }],
    [
      '#2A2523',
      {
        physical: true,
        roughness: 0.48,
        clearcoat: 0.35,
        clearcoatRoughness: 0.35,
        sheen: 0.3,
        sheenColor: '#6b5a52',
      },
    ],
    ['#3A322E', { physical: true, roughness: 0.48, clearcoat: 0.35, clearcoatRoughness: 0.35 }],
    [C.leaf1, { map: texLeaf, roughness: 0.7 }],
    [C.leaf2, { map: texLeaf, roughness: 0.7 }],
    [C.leaf3, { map: texLeaf, roughness: 0.7 }],
  ]);
  const fabrics = new Set<string>([
    ...Object.values(FABRIC).flat(),
    ...Object.values(TEAM_FABRIC),
    C.charcoal,
    C.camel,
    C.sage,
    C.navy,
    C.navyL,
    C.fabricG,
    '#3B4C6E',
  ]);
  for (const hex of fabrics) {
    if (!s.has(hex))
      s.set(hex, {
        physical: true,
        map: texWeave,
        roughness: 0.92,
        sheen: 1,
        sheenRoughness: 0.55,
        sheenColor: lighten(hex, 0.45),
      });
  }
  specialCache = s;
  return s;
}

export type MaterialOptions = THREE.MeshStandardMaterialParameters;
const materials = new Map<string, THREE.Material>();

/** A cached material for a colour; known colours get wood, fabric, metal or stone finishes. */
export function M(color: string, options: MaterialOptions = {}) {
  const key = color + '|' + JSON.stringify(options);
  const cached = materials.get(key);
  if (cached) return cached;
  const special = specials().get(color);
  const params: THREE.MeshPhysicalMaterialParameters = { roughness: 0.75, metalness: 0 };
  if (special) {
    const { physical, map, roughnessMap, color: tint, sheenColor, ...finish } = special;
    Object.assign(params, finish);
    if (map) params.map = map();
    if (roughnessMap) params.roughnessMap = roughnessMap();
    if (physical && sheenColor) params.sheenColor = new THREE.Color(sheenColor);
    params.color = new THREE.Color(tint && !options.color ? tint : color);
  } else {
    params.color = new THREE.Color(color);
  }
  Object.assign(params, options);
  const material = special?.physical
    ? new THREE.MeshPhysicalMaterial(params)
    : new THREE.MeshStandardMaterial(params);
  materials.set(key, material);
  markShared(material);
  return material;
}

/** A cached physical material with exact parameters (car paint, glass, tyres). */
export function PM(key: string, params: THREE.MeshPhysicalMaterialParameters) {
  const cached = materials.get('P|' + key);
  if (cached) return cached;
  const material = new THREE.MeshPhysicalMaterial(params);
  materials.set('P|' + key, material);
  markShared(material);
  return material;
}

/* ---------- geometry helpers; they add to the current builder group ---------- */

let G = new THREE.Group();
/** Starts a group that the builders fill until `mergeInto` bakes it into a scene. */
export function beginGroup() {
  G = new THREE.Group();
  return G;
}
export const current = () => G;

export function RB(w: number, h: number, d: number, r: number) {
  const radius = Math.max(0.001, Math.min(r, Math.min(w, h, d) / 2 - 0.002));
  return new RoundedBoxGeometry(w, h, d, 2, radius);
}

type Opts = MaterialOptions | null | undefined;

const place = (
  mesh: THREE.Mesh,
  x: number,
  y: number,
  z: number,
  ry: number | undefined,
  parent: THREE.Object3D,
) => {
  mesh.position.set(x, y, z);
  if (ry) mesh.rotation.y = ry;
  parent.add(mesh);
  return mesh;
};

export function box(
  w: number,
  h: number,
  d: number,
  c: string,
  x: number,
  y: number,
  z: number,
  o?: Opts,
  ry?: number,
  parent: THREE.Object3D = G,
) {
  return place(new THREE.Mesh(new THREE.BoxGeometry(w, h, d), M(c, o ?? {})), x, y, z, ry, parent);
}
export function rbox(
  w: number,
  h: number,
  d: number,
  r: number,
  c: string,
  x: number,
  y: number,
  z: number,
  o?: Opts,
  ry?: number,
  parent: THREE.Object3D = G,
) {
  return place(new THREE.Mesh(RB(w, h, d, r), M(c, o ?? {})), x, y, z, ry, parent);
}
export function cyl(
  rt: number,
  rb: number,
  h: number,
  c: string,
  x: number,
  y: number,
  z: number,
  seg = 20,
  o?: Opts,
  parent: THREE.Object3D = G,
) {
  return place(
    new THREE.Mesh(new THREE.CylinderGeometry(rt, rb, h, seg), M(c, o ?? {})),
    x,
    y,
    z,
    0,
    parent,
  );
}
export function sph(
  r: number,
  c: string,
  x: number,
  y: number,
  z: number,
  sx = 1,
  sy = 1,
  sz = 1,
  o?: Opts,
  parent: THREE.Object3D = G,
) {
  const m = place(
    new THREE.Mesh(new THREE.SphereGeometry(r, 16, 12), M(c, o ?? {})),
    x,
    y,
    z,
    0,
    parent,
  );
  m.scale.set(sx, sy, sz);
  return m;
}
export function blob(
  r: number,
  c: string,
  x: number,
  y: number,
  z: number,
  sx = 1,
  sy = 1,
  sz = 1,
  parent: THREE.Object3D = G,
) {
  const material = M(c, { flatShading: true, roughness: 0.9 });
  const m = place(
    new THREE.Mesh(new THREE.IcosahedronGeometry(r, 1), material),
    x,
    y,
    z,
    0,
    parent,
  );
  m.scale.set(sx, sy, sz);
  return m;
}
export function lathe(
  points: [number, number][],
  c: string,
  x: number,
  y: number,
  z: number,
  seg = 22,
  parent: THREE.Object3D = G,
) {
  const geometry = new THREE.LatheGeometry(
    points.map(([px, py]) => new THREE.Vector2(px, py)),
    seg,
  );
  return place(new THREE.Mesh(geometry, M(c, { roughness: 0.6 })), x, y, z, 0, parent);
}
export function grp(x: number, z: number, ry = 0, y = 0) {
  const g = new THREE.Group();
  g.position.set(x, y, z);
  g.rotation.y = ry;
  G.add(g);
  return g;
}

/**
 * Bakes a group into one mesh per material, which keeps draw calls low on furnished floors.
 * Indexed and unindexed shapes are baked apart: unindexing a sphere or cylinder copies each
 * shared vertex up to six times, and every copy is shaded again in the shadow and AO passes.
 */
export function mergeInto(scene: THREE.Object3D, group: THREE.Group, shadows = true) {
  group.updateMatrixWorld(true);
  const buckets = new Map<
    string,
    { material: THREE.Material; geometries: THREE.BufferGeometry[] }
  >();
  const sources = new Set<THREE.BufferGeometry>();
  group.traverse((object) => {
    if (!(object instanceof THREE.Mesh)) return;
    const source = object.geometry as THREE.BufferGeometry;
    sources.add(source);
    // copy() rather than clone(): clone() first rebuilds the shape from its constructor defaults.
    const geometry = new THREE.BufferGeometry().copy(source);
    geometry.applyMatrix4(object.matrixWorld);
    for (const name of Object.keys(geometry.attributes)) {
      if (!['position', 'normal', 'uv'].includes(name)) geometry.deleteAttribute(name);
    }
    if (!geometry.attributes.uv) {
      const count = geometry.attributes.position?.count ?? 0;
      geometry.setAttribute('uv', new THREE.BufferAttribute(new Float32Array(count * 2), 2));
    }
    const material = object.material as THREE.Material;
    const key = `${material.uuid}|${geometry.index ? 'i' : 'n'}`;
    const bucket = buckets.get(key) ?? { material, geometries: [] };
    bucket.geometries.push(geometry);
    buckets.set(key, bucket);
  });
  buckets.forEach(({ material, geometries }) => {
    const merged = mergeGeometries(geometries);
    for (const geometry of geometries) geometry.dispose();
    if (!merged) return;
    const mesh = new THREE.Mesh(merged, material);
    mesh.castShadow = shadows && !material.transparent;
    mesh.receiveShadow = true;
    scene.add(mesh);
  });
  for (const source of sources) source.dispose();
}
