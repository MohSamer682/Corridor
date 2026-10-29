import { floorModel } from './floorModel';
import type { FloorLayout } from './types';

// 6 x 5: a walled meeting room at (0..3, 0..3) with a door at (3,2), and an open lounge beside it.
const tiles = ['####..', '#..#..', '#.....', '####..', '......'].join('');
const layout: FloorLayout = {
  floorId: 'f1',
  version: 1,
  width: 6,
  height: 5,
  arrival: { x: 5, y: 4 },
  tiles,
  furniture: { roomFinishes: { nile: 'navy' }, items: [{ t: 'plant', x: 4, y: 0 }] },
  rooms: [
    {
      id: 'r1',
      key: 'nile',
      name: 'Nile Room',
      kind: 'MEETING',
      minX: 0,
      minY: 0,
      maxX: 3,
      maxY: 3,
      isPrivate: true,
      capacity: 2,
    },
    {
      id: 'r2',
      key: 'lounge',
      name: 'Lounge',
      kind: 'SOCIAL',
      minX: 4,
      minY: 0,
      maxX: 5,
      maxY: 4,
      isPrivate: false,
      capacity: 0,
    },
  ],
};

it('separates walled rooms from open areas and finds their doors', () => {
  const model = floorModel(layout, 1);

  expect(model.rooms).toEqual([
    expect.objectContaining({ key: 'nile', kind: 'meeting', finish: 'navy', doors: [[3, 2]] }),
  ]);
  expect(model.areas.map((area) => area.key)).toEqual(['lounge']);
  expect(model.kindOf('lounge')).toBe('social');
  expect(model.items).toHaveLength(1);
});

it('treats everything outside the grid as outer wall', () => {
  const model = floorModel(layout, 0);

  expect(model.at(1, 1)).toBe('.');
  expect(model.at(0, 0)).toBe('#');
  expect(model.at(-1, 2)).toBe('C');
  expect(model.at(6, 0)).toBe('C');
});
