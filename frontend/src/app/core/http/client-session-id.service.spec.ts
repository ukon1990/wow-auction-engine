import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { isValidRequestIdentifier } from '../../../request-identifiers';
import {
  CLIENT_SESSION_STORAGE_KEY,
  ClientSessionCoordinator,
  ClientSessionIdService,
} from './client-session-id.service';

describe(ClientSessionIdService.name, () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('stores one valid identifier for the browser tab', async () => {
    TestBed.configureTestingModule({ providers: [{ provide: PLATFORM_ID, useValue: 'browser' }] });
    const service = TestBed.inject(ClientSessionIdService);

    const first = await service.get();
    const second = await service.get();

    expect(isValidRequestIdentifier(first)).toBe(true);
    expect(second).toBe(first);
    expect(sessionStorage.getItem(CLIENT_SESSION_STORAGE_KEY)).toBe(first);
  });

  it('replaces a tampered stored identifier', async () => {
    sessionStorage.setItem(CLIENT_SESSION_STORAGE_KEY, 'invalid\nlog-entry');
    TestBed.configureTestingModule({ providers: [{ provide: PLATFORM_ID, useValue: 'browser' }] });

    const identifier = await TestBed.inject(ClientSessionIdService).get();

    expect(isValidRequestIdentifier(identifier)).toBe(true);
    expect(identifier).not.toContain('invalid');
  });

  it('does not invent a client-session identifier during SSR', async () => {
    TestBed.configureTestingModule({ providers: [{ provide: PLATFORM_ID, useValue: 'server' }] });

    await expect(TestBed.inject(ClientSessionIdService).get()).resolves.toBeNull();
    expect(sessionStorage.length).toBe(0);
  });
});

describe(ClientSessionCoordinator.name, () => {
  it('regenerates a cloned ID when the original tab still has a live claim', async () => {
    const clonedId = '26999606-3d4c-4ae5-908f-d9de6fcf715f';
    const hub = new FakeBroadcastHub();
    const originalStorage = new MemoryStorage(clonedId);
    const clonedStorage = new MemoryStorage(clonedId);
    const original = new ClientSessionCoordinator(originalStorage, () => hub.open());
    const duplicate = new ClientSessionCoordinator(clonedStorage, () => hub.open());

    const originalId = await original.claim();
    const duplicateId = await duplicate.claim();

    expect(originalId).toBe(clonedId);
    expect(isValidRequestIdentifier(duplicateId)).toBe(true);
    expect(duplicateId).not.toBe(originalId);
    expect(clonedStorage.getItem(CLIENT_SESSION_STORAGE_KEY)).toBe(duplicateId);
    original.close();
    duplicate.close();
  });

  it('preserves the stored ID when the previous page instance is no longer live', async () => {
    const storedId = '26999606-3d4c-4ae5-908f-d9de6fcf715f';
    const storage = new MemoryStorage(storedId);
    const hub = new FakeBroadcastHub();
    const previousPage = new ClientSessionCoordinator(storage, () => hub.open());
    await previousPage.claim();
    previousPage.close();

    const reloadedPage = new ClientSessionCoordinator(storage, () => hub.open());

    await expect(reloadedPage.claim()).resolves.toBe(storedId);
    reloadedPage.close();
  });

  it('keeps a stable in-memory ID when storage and channels are unavailable', async () => {
    const unavailableStorage = {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
    };
    const coordinator = new ClientSessionCoordinator(unavailableStorage, null);

    const first = await coordinator.claim();

    expect(await coordinator.claim()).toBe(first);
    expect(isValidRequestIdentifier(first)).toBe(true);
  });
});

class MemoryStorage {
  private value: string | null;

  constructor(initialValue: string | null) {
    this.value = initialValue;
  }

  getItem(key: string): string | null {
    return key === CLIENT_SESSION_STORAGE_KEY ? this.value : null;
  }

  setItem(key: string, value: string): void {
    if (key === CLIENT_SESSION_STORAGE_KEY) {
      this.value = value;
    }
  }
}

class FakeBroadcastHub {
  private readonly channels = new Set<FakeChannel>();

  open(): FakeChannel {
    const channel = new FakeChannel(this);
    this.channels.add(channel);
    return channel;
  }

  broadcast(source: FakeChannel, message: unknown): void {
    for (const channel of this.channels) {
      if (channel !== source) {
        queueMicrotask(() => channel.receive(message));
      }
    }
  }

  close(channel: FakeChannel): void {
    this.channels.delete(channel);
  }
}

class FakeChannel {
  private listener: ((event: MessageEvent<unknown>) => void) | null = null;

  constructor(private readonly hub: FakeBroadcastHub) {}

  postMessage(message: unknown): void {
    this.hub.broadcast(this, message);
  }

  addEventListener(_type: 'message', listener: (event: MessageEvent<unknown>) => void): void {
    this.listener = listener;
  }

  receive(data: unknown): void {
    this.listener?.({ data } as MessageEvent<unknown>);
  }

  close(): void {
    this.hub.close(this);
  }
}
