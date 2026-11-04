let chunk: Promise<typeof import('./OfficeScene')> | null = null;

/** Fetches the three.js chunk once; the office page starts it early so it downloads alongside the office data. */
export function loadOfficeScene() {
  chunk ??= import('./OfficeScene');
  return chunk;
}
