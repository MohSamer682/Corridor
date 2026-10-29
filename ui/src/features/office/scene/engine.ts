import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { GTAOPass } from 'three/addons/postprocessing/GTAOPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { floorModel, type FloorModel } from '../floorModel';
import type { OfficeView, Tools } from '../types';
import { PITCH, clampZoom, targetScale, type CameraMode } from './camera';
import { setMaxAnisotropy } from './core';
import { R_CAM } from './environment';
import { buildCampus, buildFloorScene, buildStack, type BuiltScene } from './scenes';

export type EngineCallbacks = {
  onOpenBuilding: (id: string) => void;
  onOpenFloor: (id: string) => void;
  onStudioUnavailable: () => void;
};

type Label = { id: string; element: HTMLElement };

// Ambient occlusion skips glass so panes don't darken what is behind them.
class GlassAwareAO extends GTAOPass {
  override overrideVisibility() {
    super.overrideVisibility();
    this.scene.traverse((object) => {
      if (object instanceof THREE.Mesh && (object.material as THREE.Material).transparent)
        object.visible = false;
    });
  }
}

const reducedMotion = () =>
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

/** Owns the renderer, camera and scenes; React tells it what to show and the HUD drives it through methods. */
export class OfficeEngine {
  private readonly renderer: THREE.WebGLRenderer;
  private readonly camera = new THREE.OrthographicCamera(-10, 10, 10, -10, 1, 600);
  private readonly environment: THREE.Texture;
  private composer: EffectComposer | null = null;
  private renderPass: RenderPass | null = null;
  private aoPass: GlassAwareAO | null = null;
  private readonly scenes = new Map<string, BuiltScene>();
  private active: BuiltScene | null = null;
  private mode: CameraMode = 'campus';
  private labels: Label[] = [];
  private width = 1;
  private height = 1;
  private yaw = Math.PI / 4;
  private yawTarget = Math.PI / 4;
  private scale = 10;
  private zoom = 1;
  private target = new THREE.Vector3();
  private focus: THREE.Vector3 | null = null;
  private snap = true;
  private tools: Tools = { cutaway: false, overview: false, studio: true };
  private hovered: string | null = null;
  private settleUntil = 0;
  private downAt: [number, number] | null = null;
  private flight: { start: number; then: () => void } | null = null;
  private frames = { count: 0, time: 0, checked: false };
  private last = performance.now();
  private raf = 0;
  private readonly raycaster = new THREE.Raycaster();
  private readonly pointer = new THREE.Vector2();
  private readonly resize: ResizeObserver;

  private readonly canvas: HTMLCanvasElement;
  private readonly labelLayer: HTMLElement;
  private readonly callbacks: EngineCallbacks;

  constructor(canvas: HTMLCanvasElement, labelLayer: HTMLElement, callbacks: EngineCallbacks) {
    this.canvas = canvas;
    this.labelLayer = labelLayer;
    this.callbacks = callbacks;
    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      powerPreference: 'high-performance',
    });
    // Software WebGL (no GPU) can't afford shadows or post-processing, so it gets the plain look.
    const software = isSoftwareRenderer(this.renderer);
    this.renderer.setPixelRatio(software ? 1 : Math.min(2, window.devicePixelRatio || 1));
    this.renderer.shadowMap.enabled = !software;
    this.renderer.shadowMap.type = THREE.PCFShadowMap;
    this.renderer.toneMapping = THREE.NeutralToneMapping;
    this.renderer.toneMappingExposure = 0.92;
    setMaxAnisotropy(this.renderer.capabilities.getMaxAnisotropy());
    const pmrem = new THREE.PMREMGenerator(this.renderer);
    this.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    pmrem.dispose();
    if (software) callbacks.onStudioUnavailable();
    else this.setupComposer();
    this.resize = new ResizeObserver(() => this.fitToContainer());
    this.resize.observe(canvas.parentElement ?? canvas);
    this.fitToContainer();
    canvas.addEventListener('pointermove', this.onPointerMove);
    canvas.addEventListener('pointerleave', this.onPointerLeave);
    canvas.addEventListener('pointerdown', this.onPointerDown);
    canvas.addEventListener('pointerup', this.onPointerUp);
    canvas.addEventListener('wheel', this.onWheel, { passive: false });
    this.raf = requestAnimationFrame(this.frame);
  }

  show(view: OfficeView) {
    const key = sceneKey(view);
    let built = this.scenes.get(key);
    if (!built) {
      built = this.build(view);
      this.scenes.set(key, built);
    }
    if (built !== this.active) {
      this.active = built;
      this.mode = view.mode;
      this.snap = true;
      this.focus = null;
      this.zoom = 1;
      this.hovered = null;
      built.setCutaway?.(this.tools.cutaway);
    }
    this.makeLabels(view, built);
    this.wake();
  }

  setTools(tools: Tools) {
    this.tools = tools;
    for (const scene of this.scenes.values()) scene.setCutaway?.(tools.cutaway);
    this.wake();
  }

  rotate(direction: 1 | -1) {
    this.yawTarget += (direction * Math.PI) / 2;
    this.wake();
  }

  zoomBy(factor: number) {
    this.zoom = clampZoom(this.zoom * factor);
    this.wake();
  }

  lookAt(x: number, z: number) {
    this.focus = new THREE.Vector3(x, 0, z);
    this.wake();
  }

  dispose() {
    cancelAnimationFrame(this.raf);
    this.resize.disconnect();
    this.canvas.removeEventListener('pointermove', this.onPointerMove);
    this.canvas.removeEventListener('pointerleave', this.onPointerLeave);
    this.canvas.removeEventListener('pointerdown', this.onPointerDown);
    this.canvas.removeEventListener('pointerup', this.onPointerUp);
    this.canvas.removeEventListener('wheel', this.onWheel);
    for (const built of this.scenes.values()) disposeScene(built.scene);
    this.labelLayer.replaceChildren();
    this.composer?.dispose();
    this.renderer.dispose();
  }

  private build(view: OfficeView): BuiltScene {
    if (view.mode === 'campus') {
      const grounds = new Map<string, FloorModel>();
      for (const building of view.campus.buildings) {
        const ground = building.floors[0];
        const layout = view.layouts.find((l) => l.floorId === ground?.id);
        if (ground && layout) grounds.set(building.id, floorModel(layout, ground.level));
      }
      return buildCampus(view.campus.buildings, grounds, this.environment);
    }
    if (view.mode === 'building') {
      const floors = view.building.floors.flatMap((floor) => {
        const layout = view.layouts.find((l) => l.floorId === floor.id);
        return layout ? [floorModel(layout, floor.level)] : [];
      });
      return buildStack(floors, this.environment);
    }
    return buildFloorScene(
      floorModel(view.layout, view.floor.level),
      this.environment,
      view.building.floors.length > 1,
    );
  }

  private makeLabels(view: OfficeView, built: BuiltScene) {
    this.labelLayer.replaceChildren();
    const card =
      'pointer-events-none absolute top-0 left-0 whitespace-nowrap will-change-transform';
    this.labels = built.anchors.map((anchor) => {
      const element = document.createElement('div');
      if (view.mode === 'campus') {
        const building = view.campus.buildings.find((b) => b.id === anchor.id);
        const floors = building?.floors.length ?? 0;
        element.className = `${card} rounded-xl border-l-[3px] border-[#B8935A] bg-white px-3 py-2 text-xs text-[#141B2D] shadow-[0_8px_24px_rgba(20,27,45,.18)]`;
        element.innerHTML =
          '<b class="block font-display text-[15px] leading-tight"></b><small class="text-[#5B6478]"></small>';
        (element.firstChild as HTMLElement).textContent = building?.name ?? '';
        (element.lastChild as HTMLElement).textContent =
          `${floors} floor${floors === 1 ? '' : 's'}`;
      } else if (view.mode === 'building') {
        const floor = view.building.floors.find((f) => f.id === anchor.id);
        element.className = `${card} flex items-center gap-2.5 rounded-[10px] bg-[rgba(255,255,255,.94)] py-1.5 pr-2.5 pl-1.5 text-xs text-[#141B2D] shadow-[0_6px_18px_rgba(20,27,45,.14)]`;
        element.innerHTML =
          '<span class="rounded-md bg-[#23395D] px-[7px] py-1.5 font-mono text-xs font-bold text-white"></span><span><b class="block text-[13px] font-semibold"></b><small class="text-[#5B6478]"></small></span>';
        const [code, text] = Array.from(element.children) as HTMLElement[];
        if (code) code.textContent = floor?.code ?? '';
        if (text?.firstChild) (text.firstChild as HTMLElement).textContent = floor?.name ?? '';
        if (text?.lastChild) (text.lastChild as HTMLElement).textContent = floor?.details ?? '';
      }
      element.setAttribute('aria-hidden', 'true');
      this.labelLayer.append(element);
      return { id: anchor.id, element };
    });
  }

  private setupComposer() {
    try {
      const ratio = Math.min(1.5, window.devicePixelRatio || 1);
      const target = new THREE.WebGLRenderTarget(1, 1, { type: THREE.HalfFloatType, samples: 4 });
      this.composer = new EffectComposer(this.renderer, target);
      this.composer.setPixelRatio(ratio);
      this.renderPass = new RenderPass(new THREE.Scene(), this.camera);
      this.aoPass = new GlassAwareAO(new THREE.Scene(), this.camera, 1, 1);
      this.aoPass.updateGtaoMaterial({
        radius: 2.2,
        distanceExponent: 1.8,
        thickness: 4,
        scale: 1.3,
        samples: 16,
      });
      this.aoPass.updatePdMaterial({
        lumaPhi: 10,
        depthPhi: 2,
        normalPhi: 3,
        radius: 6,
        rings: 2,
        samples: 16,
      });
      this.aoPass.blendIntensity = 1;
      this.composer.addPass(this.renderPass);
      this.composer.addPass(this.aoPass);
      this.composer.addPass(new OutputPass());
    } catch {
      this.composer = null;
      this.callbacks.onStudioUnavailable();
    }
  }

  private fitToContainer() {
    const box = (this.canvas.parentElement ?? this.canvas).getBoundingClientRect();
    this.width = Math.max(1, box.width);
    this.height = Math.max(1, box.height);
    this.renderer.setSize(this.width, this.height, false);
    this.composer?.setSize(this.width, this.height);
    this.wake();
  }

  private readonly frame = (now: number) => {
    const dt = Math.min(0.05, (now - this.last) / 1000);
    this.last = now;
    const built = this.active;
    // Draw only while something moves; an idle office costs nothing.
    if (built && (this.updateCamera(dt, built) || now < this.settleUntil)) {
      built.hover?.(this.hovered, dt);
      this.placeLabels(built);
      this.render(built, dt);
    }
    this.raf = requestAnimationFrame(this.frame);
  };

  /** Moves the camera toward its target; true while it is still moving. */
  private updateCamera(dt: number, built: BuiltScene) {
    const flightZoom = this.flight ? this.flightZoom() : 1;
    const target = targetScale(
      this.mode,
      this.tools.overview,
      built.fit,
      this.yaw,
      this.width,
      this.height,
      this.zoom * flightZoom,
    );
    const fit = built.fit;
    let tx = fit.cx;
    const ty = fit.cy;
    let tz = fit.cz;
    if (this.mode === 'floor' && !this.tools.overview && this.focus) {
      tx = this.focus.x;
      tz = this.focus.z;
    }
    if (this.mode === 'building') {
      // Shift the stack left so the floor labels fit on its right.
      const shift = Math.min(160, this.width * 0.14) / this.scale;
      tx += Math.cos(this.yaw) * shift;
      tz -= Math.sin(this.yaw) * shift;
    }
    if (this.snap) {
      this.snap = false;
      this.yaw = this.yawTarget;
      this.scale = reducedMotion() ? target : target * 0.55;
      this.target.set(tx, ty, tz);
    }
    const moving =
      this.flight !== null ||
      Math.abs(this.yawTarget - this.yaw) > 1e-4 ||
      Math.abs(target - this.scale) > target * 1e-3 ||
      Math.hypot(tx - this.target.x, ty - this.target.y, tz - this.target.z) > 1e-3;
    const k = Math.min(1, dt * 5.5);
    this.yaw += (this.yawTarget - this.yaw) * k;
    this.scale += (target - this.scale) * k;
    this.target.x += (tx - this.target.x) * k;
    this.target.y += (ty - this.target.y) * k;
    this.target.z += (tz - this.target.z) * k;
    const c = this.camera;
    c.position.set(
      this.target.x + R_CAM * Math.sin(this.yaw) * Math.cos(PITCH),
      this.target.y + R_CAM * Math.sin(PITCH),
      this.target.z + R_CAM * Math.cos(this.yaw) * Math.cos(PITCH),
    );
    c.lookAt(this.target);
    const hw = this.width / 2 / this.scale;
    const hh = this.height / 2 / this.scale;
    c.left = -hw;
    c.right = hw;
    c.top = hh;
    c.bottom = -hh;
    c.near = 1;
    c.far = R_CAM + 420;
    c.updateProjectionMatrix();
    if (this.flight && performance.now() - this.flight.start > 550) {
      const done = this.flight.then;
      this.flight = null;
      done();
    }
    return moving;
  }

  private wake(ms = 1200) {
    this.settleUntil = Math.max(this.settleUntil, performance.now() + ms);
  }

  private flightZoom() {
    if (!this.flight) return 1;
    const t = Math.min(1, (performance.now() - this.flight.start) / 550);
    return 1 + 1.6 * t * t;
  }

  private placeLabels(built: BuiltScene) {
    const v = new THREE.Vector3();
    for (const label of this.labels) {
      const anchor = built.anchors.find((a) => a.id === label.id);
      if (!anchor) continue;
      v.copy(anchor.at()).project(this.camera);
      const x = ((v.x + 1) / 2) * this.width;
      const y = ((1 - v.y) / 2) * this.height;
      label.element.style.transform =
        anchor.side === 'above'
          ? `translate(${x}px,${y}px) translate(-50%,-100%)`
          : `translate(${x + 10}px,${y}px) translate(0,-50%)`;
      label.element.style.outline = this.hovered === label.id ? '2px solid #B8935A' : '';
    }
  }

  private render(built: BuiltScene, dt: number) {
    if (this.composer && this.renderPass && this.aoPass && this.tools.studio) {
      this.renderPass.scene = built.scene;
      this.aoPass.scene = built.scene;
      this.composer.render(dt);
    } else {
      this.renderer.render(built.scene, this.camera);
    }
    // Switch studio lighting off once if the first frames run under 24 fps.
    const f = this.frames;
    if (!f.checked && this.tools.studio) {
      f.count++;
      f.time += dt;
      if (f.count >= 45) {
        f.checked = true;
        if (f.time / f.count > 1 / 24) this.callbacks.onStudioUnavailable();
      }
    }
  }

  private pick(event: PointerEvent) {
    const box = this.canvas.getBoundingClientRect();
    this.pointer.set(
      ((event.clientX - box.left) / box.width) * 2 - 1,
      -((event.clientY - box.top) / box.height) * 2 + 1,
    );
    this.raycaster.setFromCamera(this.pointer, this.camera);
    const hit = this.raycaster.intersectObjects(this.active?.pick ?? [], false)[0];
    return (hit?.object.userData.target as string | undefined) ?? null;
  }

  private readonly onPointerMove = (event: PointerEvent) => {
    if (!this.active || this.active.floorView) return;
    const hovered = this.pick(event);
    if (hovered !== this.hovered) this.wake();
    this.hovered = hovered;
    this.canvas.style.cursor = this.hovered ? 'pointer' : 'grab';
  };

  private readonly onPointerLeave = () => {
    this.hovered = null;
    this.wake();
  };

  private readonly onPointerDown = (event: PointerEvent) => {
    this.downAt = [event.clientX, event.clientY];
  };

  private readonly onPointerUp = (event: PointerEvent) => {
    const down = this.downAt;
    this.downAt = null;
    if (!down || !this.active || this.active.floorView || this.flight) return;
    if (Math.hypot(event.clientX - down[0], event.clientY - down[1]) > 8) return;
    const id = this.pick(event);
    if (!id) return;
    const open = () =>
      this.mode === 'campus' ? this.callbacks.onOpenBuilding(id) : this.callbacks.onOpenFloor(id);
    if (reducedMotion()) open();
    else this.flight = { start: performance.now(), then: open };
  };

  private readonly onWheel = (event: WheelEvent) => {
    event.preventDefault();
    this.zoom = clampZoom(this.zoom * Math.exp(-event.deltaY * 0.0015));
    this.wake();
  };
}

function isSoftwareRenderer(renderer: THREE.WebGLRenderer) {
  const gl = renderer.getContext();
  const info = gl.getExtension('WEBGL_debug_renderer_info');
  const name = info ? String(gl.getParameter(info.UNMASKED_RENDERER_WEBGL)) : '';
  return /swiftshader|llvmpipe|software/i.test(name);
}

function sceneKey(view: OfficeView) {
  if (view.mode === 'campus')
    return 'campus:' + view.layouts.map((l) => `${l.floorId}:${l.version}`).join(',');
  if (view.mode === 'building')
    return (
      `b:${view.building.id}:` + view.layouts.map((l) => `${l.floorId}:${l.version}`).join(',')
    );
  return `f:${view.layout.floorId}:${view.layout.version}`;
}

function disposeScene(scene: THREE.Scene) {
  scene.traverse((object) => {
    if (object instanceof THREE.Mesh || object instanceof THREE.LineSegments) {
      object.geometry.dispose();
      const materials = Array.isArray(object.material) ? object.material : [object.material];
      for (const material of materials as THREE.Material[]) {
        for (const value of Object.values(material))
          if (value instanceof THREE.Texture) value.dispose();
        material.dispose();
      }
    }
  });
}
