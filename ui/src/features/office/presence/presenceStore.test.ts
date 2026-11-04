import { positionAt, usePresenceStore } from './presenceStore';

const nour = 'u-nour';

beforeEach(() => usePresenceStore.getState().reset());

it('places people from the occupants list', () => {
  usePresenceStore
    .getState()
    .arrive([{ userId: nour, displayName: 'Nour Hassan', x: 3, y: 4, facing: 0, moving: false }]);

  const person = usePresenceStore.getState().people[nour];
  expect(person?.name).toBe('Nour Hassan');
  expect(positionAt(person!, 0)).toEqual({ x: 3, y: 4 });
});

it('glides between tiles over one step instead of jumping', () => {
  usePresenceStore
    .getState()
    .arrive([{ userId: nour, displayName: 'Nour Hassan', x: 3, y: 4, facing: 0, moving: false }]);

  usePresenceStore
    .getState()
    .applyFrame(
      { moved: [{ userId: nour, x: 4, y: 4, facing: Math.PI / 2, moving: true }], left: [] },
      1000,
    );
  const person = usePresenceStore.getState().people[nour]!;

  expect(positionAt(person, 1000)).toEqual({ x: 3, y: 4 });
  expect(positionAt(person, 1055)).toEqual({ x: 3.5, y: 4 });
  expect(positionAt(person, 1300)).toEqual({ x: 4, y: 4 });
  expect(person.facing).toBe(Math.PI / 2);
});

it('forgets people who left and lists people it has no name for yet', () => {
  usePresenceStore
    .getState()
    .applyFrame({ moved: [{ userId: nour, x: 1, y: 1, facing: 0, moving: false }], left: [] }, 0);
  expect(usePresenceStore.getState().people[nour]?.name).toBe('');
  expect(usePresenceStore.getState().unnamed()).toEqual([nour]);

  usePresenceStore.getState().applyFrame({ moved: [], left: [nour] }, 100);

  expect(usePresenceStore.getState().people[nour]).toBeUndefined();
});
