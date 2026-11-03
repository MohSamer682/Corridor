import * as THREE from 'three';
import { positionAt, STEP_MS, usePresenceStore, type Person } from '../presence/presenceStore';
import { FACE, M, RB } from './core';

/* People on the floor, built like the prototype's avatars; looks come from a hash of the user id. */

const SHIRTS = [
  '#3E5F8F',
  '#8E4A63',
  '#4E7A63',
  '#B07A3E',
  '#5D5A9E',
  '#3E7F8C',
  '#7A5C42',
  '#9A4E42',
  '#4E6E8E',
  '#6E8A4C',
  '#A85E4E',
  '#4A5698',
];
const SKIN = ['#C8946A', '#E0AE88', '#A8744C', '#D6A07A', '#B98358', '#8E5E3C'];
const HAIR = ['#2A1B12', '#1A1210', '#4A2C1A', '#6B3B22', '#141010', '#3A2A1E'];
const FACING: Record<string, string> = { N: 'up', S: 'down', E: 'right', W: 'left' };

function hash(id: string) {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (Math.imul(h, 31) + id.charCodeAt(i)) | 0;
  return Math.abs(h);
}
const pick = (list: string[], n: number) => list[n % list.length] ?? '#3E5F8F';

type Body = {
  group: THREE.Group;
  legL: THREE.Object3D;
  legR: THREE.Object3D;
  armL: THREE.Object3D;
  armR: THREE.Object3D;
  tag: HTMLElement;
  walk: number;
};

type Shapes = {
  leg: THREE.BufferGeometry;
  arm: THREE.BufferGeometry;
  torso: THREE.BufferGeometry;
  head: THREE.BufferGeometry;
  cap: THREE.BufferGeometry;
};
let shapes: Shapes | null = null;
function geometry(): Shapes {
  shapes ??= {
    leg: RB(0.13, 0.38, 0.15, 0.05).translate(0, -0.19, 0),
    arm: RB(0.1, 0.34, 0.11, 0.045).translate(0, -0.16, 0),
    torso: new THREE.LatheGeometry(
      [
        [0, 0],
        [0.19, 0],
        [0.21, 0.1],
        [0.22, 0.26],
        [0.25, 0.36],
        [0.2, 0.43],
        [0.08, 0.46],
        [0, 0.46],
      ].map(([x, y]) => new THREE.Vector2(x, y)),
      18,
    ),
    head: new THREE.SphereGeometry(0.165, 20, 16),
    cap: new THREE.SphereGeometry(0.175, 18, 10, 0, Math.PI * 2, 0, Math.PI * 0.52),
  };
  return shapes;
}

const TAG =
  'pointer-events-none absolute top-0 left-0 flex items-center gap-1.5 rounded-full py-1 pr-2 pl-1.5 text-[11px] font-semibold whitespace-nowrap';

function build(person: Person, isMe: boolean, labels: HTMLElement): Body {
  const g = geometry();
  const n = hash(person.id);
  const group = new THREE.Group();
  const limb = (geo: THREE.BufferGeometry, color: string, x: number, y: number) => {
    const mesh = new THREE.Mesh(geo, M(color));
    mesh.position.set(x, y, 0);
    mesh.castShadow = true;
    group.add(mesh);
    return mesh;
  };
  const shirt = isMe ? '#3E5F8F' : pick(SHIRTS, n);
  const legL = limb(g.leg, '#262B33', -0.085, 0.4);
  const legR = limb(g.leg, '#262B33', 0.085, 0.4);
  limb(g.torso, shirt, 0, 0.36);
  const armL = limb(g.arm, shirt, -0.26, 0.76);
  const armR = limb(g.arm, shirt, 0.26, 0.76);
  limb(g.head, pick(SKIN, n >> 3), 0, 0.99);
  limb(g.cap, pick(HAIR, n >> 5), 0, 1.005).rotation.x = -0.3;
  const tag = document.createElement('div');
  tag.setAttribute('aria-hidden', 'true');
  tag.className = isMe
    ? `${TAG} bg-white text-[#141B2D] shadow-[0_2px_8px_rgba(20,27,45,.2)]`
    : `${TAG} bg-[rgba(20,27,45,.84)] text-[#F3F5F9]`;
  tag.innerHTML = '<i class="size-[7px] rounded-full bg-[#3FBF8A]"></i><span></span>';
  labels.append(tag);
  return { group, legL, legR, armL, armR, tag, walk: 0 };
}

/** Keeps one avatar per person on the floor in step with the presence store. */
export class AvatarLayer {
  readonly group = new THREE.Group();
  private readonly bodies = new Map<string, Body>();
  private readonly labels: HTMLElement;
  private me: string | null = null;

  constructor(labels: HTMLElement) {
    this.labels = labels;
  }

  setMe(userId: string | null) {
    this.me = userId;
  }

  /** Where the signed-in person is, for the camera to follow. */
  myPosition(now: number) {
    const mine = this.me ? usePresenceStore.getState().people[this.me] : undefined;
    return mine ? positionAt(mine, now) : null;
  }

  /** Moves everyone; true while someone is still gliding, so the view keeps drawing. */
  update(now: number, dt: number, camera: THREE.Camera, width: number, height: number) {
    const people = usePresenceStore.getState().people;
    let moving = false;
    for (const [id, body] of this.bodies) {
      if (!people[id]) {
        this.group.remove(body.group);
        body.tag.remove();
        this.bodies.delete(id);
      }
    }
    const v = new THREE.Vector3();
    for (const person of Object.values(people)) {
      let body = this.bodies.get(person.id);
      if (!body) {
        body = build(person, person.id === this.me, this.labels);
        this.bodies.set(person.id, body);
        this.group.add(body.group);
      }
      const at = positionAt(person, now);
      const gliding = now - person.since < STEP_MS + 50;
      moving ||= gliding;
      body.walk = gliding ? body.walk + dt : 0;
      const swing = gliding ? Math.sin(body.walk * 12) * 0.6 : 0;
      body.legL.rotation.x = swing;
      body.legR.rotation.x = -swing;
      body.armL.rotation.x = -swing * 0.8;
      body.armR.rotation.x = swing * 0.8;
      const bob = gliding ? Math.abs(Math.sin(body.walk * 12)) * 0.035 : 0;
      body.group.position.set(at.x + 0.5, bob, at.y + 0.5);
      body.group.rotation.y = FACE[FACING[person.facing] ?? 'down'] ?? 0;
      v.set(at.x + 0.5, 1.48, at.y + 0.5).project(camera);
      body.tag.style.transform = `translate(${((v.x + 1) / 2) * width}px,${((1 - v.y) / 2) * height}px) translate(-50%,-100%)`;
      const label = body.tag.lastChild as HTMLElement;
      const text = person.id === this.me ? 'You' : (person.name.split(' ')[0] ?? '');
      if (label.textContent !== text) label.textContent = text;
    }
    return moving;
  }

  clear() {
    for (const body of this.bodies.values()) body.tag.remove();
    this.bodies.clear();
    this.group.clear();
  }
}
