/** Built scenes by key, keeping only the most recently used; each scene holds its own textures and shadow map on the GPU. */
export class SceneCache<T> {
  private readonly entries = new Map<string, T>();
  private readonly limit: number;
  private readonly evict: (value: T) => void;

  constructor(limit: number, evict: (value: T) => void) {
    this.limit = limit;
    this.evict = evict;
  }

  get(key: string) {
    const value = this.entries.get(key);
    if (value !== undefined) {
      this.entries.delete(key);
      this.entries.set(key, value);
    }
    return value;
  }

  set(key: string, value: T) {
    this.entries.delete(key);
    this.entries.set(key, value);
    for (const [oldest, old] of this.entries) {
      if (this.entries.size <= this.limit) break;
      this.entries.delete(oldest);
      this.evict(old);
    }
  }

  values() {
    return this.entries.values();
  }

  clear() {
    for (const value of this.entries.values()) this.evict(value);
    this.entries.clear();
  }
}
