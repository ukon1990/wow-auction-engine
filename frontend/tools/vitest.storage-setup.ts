ensureWebStorage();

export function ensureWebStorage(): void {
  if (webStorageWorks(globalThis.localStorage) && webStorageWorks(globalThis.sessionStorage)) {
    return;
  }

  Object.defineProperty(globalThis, 'localStorage', {
    value: createStorage(),
    configurable: true,
  });
  Object.defineProperty(globalThis, 'sessionStorage', {
    value: createStorage(),
    configurable: true,
  });
}

function webStorageWorks(storage: Storage | undefined): storage is Storage {
  if (!storage) return false;
  try {
    storage.clear();
    storage.setItem('__storage_probe__', '1');
    storage.removeItem('__storage_probe__');
    return true;
  } catch {
    return false;
  }
}

function createStorage(): Storage {
  const values = new Map<string, string>();
  return {
    get length() {
      return values.size;
    },
    clear() {
      values.clear();
    },
    getItem(key: string) {
      return values.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      values.set(key, String(value));
    },
    removeItem(key: string) {
      values.delete(key);
    },
    key(index: number) {
      return [...values.keys()][index] ?? null;
    },
  };
}
