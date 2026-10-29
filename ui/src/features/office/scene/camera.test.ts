import { PITCH, clampZoom, fitScale, floorScale, targetScale } from './camera';

const floor = { cx: 28, cy: 0, cz: 18, w: 56, d: 36, h: 2 };

it('fits a whole floor inside the viewport at any rotation', () => {
  for (const yaw of [0, Math.PI / 4, Math.PI / 2, (3 * Math.PI) / 4]) {
    const scale = fitScale(floor, yaw, 1000, 700);
    const across = (floor.w * Math.abs(Math.cos(yaw)) + floor.d * Math.abs(Math.sin(yaw))) * scale;
    const down =
      ((floor.w * Math.abs(Math.sin(yaw)) + floor.d * Math.abs(Math.cos(yaw))) * Math.sin(PITCH) +
        floor.h * Math.cos(PITCH)) *
      scale;
    expect(across).toBeLessThanOrEqual(1000);
    expect(down).toBeLessThanOrEqual(700);
    expect(Math.max(across / 1000, down / 700)).toBeCloseTo(0.94, 5);
  }
});

it('keeps the floor close-up within sensible bounds', () => {
  expect(floorScale(390, 1)).toBe(30);
  expect(floorScale(1300, 1)).toBe(50);
  expect(floorScale(1300, 10)).toBe(95);
  expect(floorScale(390, 0.1)).toBe(12);
});

it('shows the whole floor in overview and leaves room for labels beside the building stack', () => {
  expect(targetScale('floor', true, floor, 0, 1000, 700, 1)).toBe(fitScale(floor, 0, 1000, 700));
  expect(targetScale('building', false, floor, 0, 1000, 700, 1)).toBeCloseTo(
    fitScale(floor, 0, 1000, 700) * 0.64,
  );
});

it('limits how far people can zoom', () => {
  expect(clampZoom(10)).toBe(2.6);
  expect(clampZoom(0.1)).toBe(0.45);
});
