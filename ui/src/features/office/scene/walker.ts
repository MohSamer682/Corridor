/**
 * Walks your own avatar in the browser, the way the prototype does: held keys glide you in any direction relative to
 * the camera, a click follows a smooth path that cuts corners where the floor allows, walls stop you by sliding,
 * and you stop short of other people. The server checks every position it is sent (presence FloorSession).
 */

export type Point = { x: number; y: number };
export type Pose = Point & { facing: number; moving: boolean };
export type SeatFace = 'up' | 'down' | 'left' | 'right';

/** Walking with the keys and following a path, in tiles per second (the prototype's 165 and 190 px at 32 px). */
export const KEY_SPEED = 5.2;
export const PATH_SPEED = 5.9;
/** Same as the server: half a person's width, and how close two people may stand. */
export const RADIUS = 0.28;
export const MIN_GAP = 0.65;

export const SEAT_FACING: Record<SeatFace, number> = {
  down: 0,
  up: Math.PI,
  right: Math.PI / 2,
  left: -Math.PI / 2,
};

export type Ground = {
  /** True where a person may stand. */
  walkable: (tx: number, ty: number) => boolean;
  /** Which way a seat on this tile faces, if there is one. */
  seatAt: (tx: number, ty: number) => SeatFace | undefined;
  width: number;
  height: number;
};

export class Walker {
  x = 0;
  y = 0;
  facing = 0;
  moving = false;
  placed = false;
  private path: Point[] = [];
  private blockedFor = 0;
  private readonly keys = new Set<'up' | 'down' | 'left' | 'right'>();
  private readonly ground: Ground;

  constructor(ground: Ground) {
    this.ground = ground;
  }

  place(x: number, y: number) {
    this.x = x;
    this.y = y;
    this.placed = true;
    this.path = [];
  }

  press(key: 'up' | 'down' | 'left' | 'right', down: boolean) {
    if (down) {
      this.keys.add(key);
      this.path = [];
    } else this.keys.delete(key);
  }

  releaseAll() {
    this.keys.clear();
  }

  /** Plans a walk to a point; a click on a seat walks to the seat. False when there is no way there. */
  walkTo(tx: number, ty: number): boolean {
    const seat = this.ground.seatAt(Math.floor(tx), Math.floor(ty));
    const target = seat
      ? { x: Math.floor(tx) + 0.5, y: Math.floor(ty) + 0.5 }
      : this.standable(tx, ty)
        ? { x: tx, y: ty }
        : null;
    const tiles = this.route(Math.floor(tx), Math.floor(ty));
    if (!tiles) return false;
    const points = tiles.map(([px, py]) => ({ x: px + 0.5, y: py + 0.5 }));
    if (target && points.length) points[points.length - 1] = target;
    else if (target) points.push(target);
    this.path = this.smooth({ x: this.x, y: this.y }, points);
    this.blockedFor = 0;
    return this.path.length > 0;
  }

  /** The seat you are on, if you are standing still on one. */
  seat(): SeatFace | undefined {
    return this.moving ? undefined : this.ground.seatAt(Math.floor(this.x), Math.floor(this.y));
  }

  pose(): Pose {
    const seat = this.seat();
    return {
      x: this.x,
      y: this.y,
      facing: seat ? SEAT_FACING[seat] : this.facing,
      moving: this.moving,
    };
  }

  /** Moves for one frame; `yaw` is the camera's, `others` everyone else on the floor. True while active. */
  update(dt: number, yaw: number, others: Point[]): boolean {
    if (!this.placed) return false;
    const vx = (this.keys.has('right') ? 1 : 0) - (this.keys.has('left') ? 1 : 0);
    const vy = (this.keys.has('down') ? 1 : 0) - (this.keys.has('up') ? 1 : 0);
    if (vx || vy) return this.glide(dt, yaw, vx, vy, others);
    return this.follow(dt, others);
  }

  private glide(dt: number, yaw: number, vx: number, vy: number, others: Point[]) {
    // Up on the keyboard means away from the camera, however the view is turned.
    let mx = Math.cos(yaw) * vx + Math.sin(yaw) * vy;
    let my = -Math.sin(yaw) * vx + Math.cos(yaw) * vy;
    const length = Math.hypot(mx, my);
    mx /= length;
    my /= length;
    this.facing = Math.atan2(mx, my);
    const step = KEY_SPEED * dt;
    const before = { x: this.x, y: this.y };
    // One axis at a time, so walls and people make you slide instead of stick.
    this.tryMove(this.x + mx * step, this.y, others);
    this.tryMove(this.x, this.y + my * step, others);
    this.moving = this.x !== before.x || this.y !== before.y;
    return true;
  }

  private follow(dt: number, others: Point[]) {
    const next = this.path[0];
    if (!next) {
      const was = this.moving;
      this.moving = false;
      return was;
    }
    const dx = next.x - this.x;
    const dy = next.y - this.y;
    const distance = Math.hypot(dx, dy);
    const step = PATH_SPEED * dt;
    if (distance > 0.01) this.facing = Math.atan2(dx, dy);
    const arrives = distance <= step;
    const to = arrives
      ? next
      : { x: this.x + (dx / distance) * step, y: this.y + (dy / distance) * step };
    if (this.bumps(to.x, to.y, others)) {
      // Someone is in the way: wait a little, then give up rather than push through them.
      this.blockedFor += dt;
      this.moving = false;
      if (this.blockedFor > 2.5) this.path = [];
      return true;
    }
    this.blockedFor = 0;
    this.x = to.x;
    this.y = to.y;
    if (arrives) this.path.shift();
    this.moving = this.path.length > 0;
    return true;
  }

  private tryMove(x: number, y: number, others: Point[]) {
    if (this.standable(x, y) && !this.bumps(x, y, others)) {
      this.x = x;
      this.y = y;
    }
  }

  /** The person's footprint stands on walkable floor only. */
  standable(x: number, y: number) {
    const w = this.ground.walkable;
    const r = RADIUS;
    return (
      w(Math.floor(x - r), Math.floor(y - r)) &&
      w(Math.floor(x + r), Math.floor(y - r)) &&
      w(Math.floor(x - r), Math.floor(y + r)) &&
      w(Math.floor(x + r), Math.floor(y + r))
    );
  }

  // Too close to someone and getting closer; stepping away from someone close is fine.
  private bumps(x: number, y: number, others: Point[]) {
    return others.some((o) => {
      const after = Math.hypot(o.x - x, o.y - y);
      return after < MIN_GAP && after < Math.hypot(o.x - this.x, o.y - this.y);
    });
  }

  /** Shortest walk over tiles, eight ways, never cutting a wall corner. Null when unreachable. */
  private route(tx: number, ty: number): [number, number][] | null {
    const { width: W, height: H, walkable } = this.ground;
    const sx = Math.floor(this.x);
    const sy = Math.floor(this.y);
    if (!walkable(tx, ty)) return null;
    const key = (x: number, y: number) => y * W + x;
    const prev = new Map<number, number>([[key(sx, sy), -1]]);
    const queue: [number, number][] = [[sx, sy]];
    for (let i = 0; i < queue.length; i++) {
      const [x, y] = queue[i]!;
      if (x === tx && y === ty) break;
      for (const [dx, dy] of STEPS) {
        const nx = x + dx;
        const ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
        if (prev.has(key(nx, ny)) || !walkable(nx, ny)) continue;
        if (dx && dy && (!walkable(x + dx, y) || !walkable(x, y + dy))) continue;
        prev.set(key(nx, ny), key(x, y));
        queue.push([nx, ny]);
      }
    }
    if (!prev.has(key(tx, ty))) return null;
    const tiles: [number, number][] = [];
    for (let k = key(tx, ty); k !== key(sx, sy) && k !== -1; k = prev.get(k)!)
      tiles.unshift([k % W, Math.floor(k / W)]);
    return tiles;
  }

  /** Drops corners the footprint can walk straight past, so a walk takes any angle, not stairs. */
  private smooth(from: Point, points: Point[]) {
    const out: Point[] = [];
    let at = from;
    let i = 0;
    while (i < points.length) {
      let far = i;
      for (let j = points.length - 1; j > i; j--) {
        if (this.clearLine(at, points[j]!)) {
          far = j;
          break;
        }
      }
      out.push(points[far]!);
      at = points[far]!;
      i = far + 1;
    }
    return out;
  }

  private clearLine(a: Point, b: Point) {
    const steps = Math.ceil(Math.hypot(b.x - a.x, b.y - a.y) / 0.2);
    for (let s = 1; s <= steps; s++) {
      const t = s / steps;
      if (!this.standable(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)) return false;
    }
    return true;
  }
}

const STEPS = [
  [1, 0],
  [-1, 0],
  [0, 1],
  [0, -1],
  [1, 1],
  [1, -1],
  [-1, 1],
  [-1, -1],
] as const;
