const storage = new Map<string, string>()

const memoryStorage: Storage = {
  get length() {
    return storage.size
  },
  clear: () => storage.clear(),
  getItem: (key) => storage.get(key) ?? null,
  key: (index) => Array.from(storage.keys())[index] ?? null,
  removeItem: (key) => storage.delete(key),
  setItem: (key, value) => storage.set(key, String(value)),
}

Object.defineProperty(globalThis, 'localStorage', {
  value: memoryStorage,
  configurable: true,
})
