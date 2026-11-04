import { MIN_GAP, Walker, type Ground } from './walker';

// 7 x 5: a wall across the middle row with one gap at (3,2), and a chair at (5,3) facing up.
const MAP = ['CCCCCCC', 'C.....C', 'C##.##C', 'C.....C', 'CCCCCCC'];
const ground: Ground = {
  width: 7,
  height: 5,
  walkable: (x, y) => MAP[y]?.[x] === '.',
  seatAt: (x, y) => (x === 5 && y === 3 ? 'up' : undefined),
};

const run = (walker: Walker, seconds: number, yaw = 0, others: { x: number; y: number }[] = []) => {
  for (let t = 0; t < seconds; t += 1 / 60) walker.update(1 / 60, yaw, others);
};

it('glides diagonally with two keys held, not tile by tile', () => {
  const walker = new Walker(ground);
  walker.place(1.5, 1.5);
  walker.press('right', true);
  walker.press('down', true);

  run(walker, 0.05);

  expect(walker.x).toBeGreaterThan(1.5);
  expect(walker.x - 1.5).toBeCloseTo(walker.y - 1.5, 5);
  expect(walker.x % 1).not.toBe(0.5);
});

it('slides along a wall instead of walking into it', () => {
  const walker = new Walker(ground);
  walker.place(5.5, 1.5);
  walker.press('left', true);
  walker.press('down', true);

  run(walker, 0.25);

  expect(walker.y).toBeCloseTo(2 - 0.28, 1);
  expect(walker.y).toBeLessThanOrEqual(2 - 0.28);
  expect(walker.x).toBeLessThan(5);
});

it('walks a clicked path through the gap at any angle and stops on the point', () => {
  const walker = new Walker(ground);
  walker.place(1.5, 1.5);

  expect(walker.walkTo(1.7, 3.4)).toBe(true);
  run(walker, 3);

  expect(walker.x).toBeCloseTo(1.7, 5);
  expect(walker.y).toBeCloseTo(3.4, 5);
  expect(walker.moving).toBe(false);
});

it('sits on a clicked chair, facing the way it faces', () => {
  const walker = new Walker(ground);
  walker.place(1.5, 3.5);

  walker.walkTo(5.2, 3.9);
  run(walker, 2);

  expect(walker.pose()).toMatchObject({ x: 5.5, y: 3.5, facing: Math.PI, moving: false });
  expect(walker.seat()).toBe('up');
});

it('stops short of someone in the way', () => {
  const walker = new Walker(ground);
  walker.place(1.5, 1.5);
  walker.press('right', true);

  run(walker, 1, 0, [{ x: 3, y: 1.5 }]);

  expect(3 - walker.x).toBeGreaterThanOrEqual(MIN_GAP - 0.1);
});

it('treats up as away from the camera when the view is turned', () => {
  const walker = new Walker(ground);
  walker.place(3.5, 3.5);
  walker.press('up', true);

  run(walker, 0.1, Math.PI / 2);

  expect(walker.x).toBeLessThan(3.5);
  expect(walker.y).toBeCloseTo(3.5, 5);
});

it('refuses a click on a wall', () => {
  const walker = new Walker(ground);
  walker.place(1.5, 1.5);

  expect(walker.walkTo(1.5, 2.5)).toBe(false);
});
