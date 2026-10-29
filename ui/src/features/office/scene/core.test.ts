import * as THREE from 'three';
import { mergeInto } from './core';

const material = new THREE.MeshStandardMaterial({ color: '#123456' });

function meshes(scene: THREE.Object3D) {
  const found: THREE.Mesh[] = [];
  scene.traverse((object) => {
    if (object instanceof THREE.Mesh) found.push(object);
  });
  return found;
}

it('keeps indexed shapes indexed when baking a group, so shared vertices are not copied', () => {
  const group = new THREE.Group();
  const sphere = new THREE.SphereGeometry(1, 16, 12);
  const cylinder = new THREE.CylinderGeometry(1, 1, 2, 20);
  group.add(new THREE.Mesh(sphere, material), new THREE.Mesh(cylinder, material));
  const scene = new THREE.Scene();

  mergeInto(scene, group);

  const [baked] = meshes(scene);
  expect(meshes(scene)).toHaveLength(1);
  expect(baked?.geometry.index).not.toBeNull();
  expect(baked?.geometry.attributes.position?.count).toBe(
    sphere.attributes.position!.count + cylinder.attributes.position!.count,
  );
});

it('bakes indexed and unindexed shapes of one material side by side', () => {
  const group = new THREE.Group();
  group.add(
    new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), material),
    new THREE.Mesh(new THREE.IcosahedronGeometry(1, 1), material),
  );
  const scene = new THREE.Scene();

  mergeInto(scene, group);

  const baked = meshes(scene);
  expect(baked).toHaveLength(2);
  expect(baked.every((mesh) => mesh.material === material)).toBe(true);
  expect(baked.map((mesh) => mesh.geometry.index !== null).sort()).toEqual([false, true]);
});

it('frees the source shapes once they are baked', () => {
  const group = new THREE.Group();
  const geometry = new THREE.BoxGeometry(1, 1, 1);
  const disposed = vi.fn();
  geometry.addEventListener('dispose', disposed);
  group.add(new THREE.Mesh(geometry, material));

  mergeInto(new THREE.Scene(), group);

  expect(disposed).toHaveBeenCalled();
});
