import { SceneCache } from './sceneCache';

it('keeps the most recently used scenes and lets go of the oldest', () => {
  const evicted: string[] = [];
  const cache = new SceneCache<string>(2, (value) => evicted.push(value));

  cache.set('campus', 'campus scene');
  cache.set('hq', 'hq scene');
  cache.get('campus');
  cache.set('l1', 'l1 scene');

  expect(evicted).toEqual(['hq scene']);
  expect(cache.get('hq')).toBeUndefined();
  expect([...cache.values()]).toEqual(['campus scene', 'l1 scene']);
});

it('lets go of every scene when cleared', () => {
  const evicted: string[] = [];
  const cache = new SceneCache<string>(3, (value) => evicted.push(value));
  cache.set('a', 'A');
  cache.set('b', 'B');

  cache.clear();

  expect(evicted).toEqual(['A', 'B']);
  expect([...cache.values()]).toEqual([]);
});
