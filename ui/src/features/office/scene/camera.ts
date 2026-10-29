import type { Fit } from './scenes';

/** Camera tilt below the horizon, as in the prototype's isometric look. */
export const PITCH = 0.62;

/** Pixels per world unit that fit the whole `fit` box in the viewport at this rotation. */
export function fitScale(fit: Fit, yaw: number, width: number, height: number) {
  const sy = Math.abs(Math.sin(yaw));
  const cy = Math.abs(Math.cos(yaw));
  const across = fit.w * cy + fit.d * sy;
  const down = (fit.w * sy + fit.d * cy) * Math.sin(PITCH) + fit.h * Math.cos(PITCH);
  return Math.min(width / across, height / down) * 0.94;
}

/** Close-up scale on a floor: roomy on phones, capped so furniture never fills the screen. */
export function floorScale(width: number, zoom: number) {
  const base = width < 700 ? 30 : Math.max(34, width / 26);
  return Math.max(12, Math.min(95, base * zoom));
}

export type CameraMode = 'campus' | 'building' | 'floor';

export function targetScale(
  mode: CameraMode,
  overview: boolean,
  fit: Fit,
  yaw: number,
  width: number,
  height: number,
  zoom: number,
) {
  if (mode === 'floor')
    return overview ? fitScale(fit, yaw, width, height) : floorScale(width, zoom);
  return fitScale(fit, yaw, width, height) * (mode === 'building' ? 0.64 : 1) * zoom;
}

export const clampZoom = (zoom: number) => Math.max(0.45, Math.min(2.6, zoom));
