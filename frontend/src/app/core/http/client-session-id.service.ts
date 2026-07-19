import { isPlatformBrowser } from '@angular/common';
import { inject, Injectable, OnDestroy, PLATFORM_ID } from '@angular/core';

import { isValidRequestIdentifier } from '../../../request-identifiers';

export const CLIENT_SESSION_STORAGE_KEY = 'wow-auction-engine.client-session-id';
const channelName = 'wow-auction-engine.client-session-claims';
const duplicateProbeWindowMs = 25;

interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

interface ChannelLike {
  postMessage(message: ClientSessionMessage): void;
  addEventListener(type: 'message', listener: (event: MessageEvent<unknown>) => void): void;
  close(): void;
}

type ChannelFactory = () => ChannelLike;

type ClientSessionMessage =
  | { type: 'probe'; sessionId: string; instanceId: string }
  | { type: 'claimed'; sessionId: string; targetInstanceId: string };

export class ClientSessionCoordinator {
  private readonly instanceId = generateUuid();
  private channel: ChannelLike | null = null;
  private sessionId: string | null = null;
  private pendingDuplicateResolution: ((duplicate: boolean) => void) | null = null;

  constructor(
    private readonly storage: StorageLike | null,
    private readonly createChannel: ChannelFactory | null,
  ) {}

  async claim(): Promise<string> {
    if (this.sessionId) {
      return this.sessionId;
    }

    this.sessionId = this.readStoredId() ?? generateUuid();
    this.writeStoredId(this.sessionId);
    if (!this.createChannel) {
      return this.sessionId;
    }

    try {
      this.channel = this.createChannel();
      this.channel.addEventListener('message', (event) => this.onMessage(event.data));
    } catch {
      this.channel = null;
      return this.sessionId;
    }

    const duplicate = await this.probeForDuplicate();
    if (duplicate) {
      this.sessionId = generateUuid();
      this.writeStoredId(this.sessionId);
    }
    return this.sessionId;
  }

  close(): void {
    this.channel?.close();
    this.channel = null;
  }

  private probeForDuplicate(): Promise<boolean> {
    return new Promise((resolve) => {
      const timeout = globalThis.setTimeout(() => {
        this.pendingDuplicateResolution = null;
        resolve(false);
      }, duplicateProbeWindowMs);
      this.pendingDuplicateResolution = (duplicate) => {
        globalThis.clearTimeout(timeout);
        this.pendingDuplicateResolution = null;
        resolve(duplicate);
      };
      this.channel?.postMessage({
        type: 'probe',
        sessionId: this.sessionId!,
        instanceId: this.instanceId,
      });
    });
  }

  private onMessage(value: unknown): void {
    if (!isClientSessionMessage(value) || !this.sessionId) {
      return;
    }
    if (
      value.type === 'probe' &&
      value.instanceId !== this.instanceId &&
      value.sessionId === this.sessionId
    ) {
      this.channel?.postMessage({
        type: 'claimed',
        sessionId: this.sessionId,
        targetInstanceId: value.instanceId,
      });
      return;
    }
    if (
      value.type === 'claimed' &&
      value.targetInstanceId === this.instanceId &&
      value.sessionId === this.sessionId
    ) {
      this.pendingDuplicateResolution?.(true);
    }
  }

  private readStoredId(): string | null {
    try {
      const storedId = this.storage?.getItem(CLIENT_SESSION_STORAGE_KEY);
      return isValidRequestIdentifier(storedId) ? storedId : null;
    } catch {
      return null;
    }
  }

  private writeStoredId(value: string): void {
    try {
      this.storage?.setItem(CLIENT_SESSION_STORAGE_KEY, value);
    } catch {
      // The in-memory value remains stable when storage is unavailable.
    }
  }
}

@Injectable({ providedIn: 'root' })
export class ClientSessionIdService implements OnDestroy {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly coordinator = this.isBrowser
    ? new ClientSessionCoordinator(readSessionStorage(), createBroadcastChannel)
    : null;
  private readonly pageHideListener = () => this.coordinator?.close();
  private readonly claimedId = this.coordinator?.claim() ?? Promise.resolve(null);

  constructor() {
    if (this.isBrowser) {
      globalThis.addEventListener('pagehide', this.pageHideListener, { once: true });
    }
  }

  get(): Promise<string | null> {
    return this.claimedId;
  }

  initialize(): Promise<void> {
    return this.claimedId.then(() => undefined);
  }

  ngOnDestroy(): void {
    this.coordinator?.close();
    if (this.isBrowser) {
      globalThis.removeEventListener('pagehide', this.pageHideListener);
    }
  }
}

export function generateUuid(): string {
  return globalThis.crypto.randomUUID();
}

function readSessionStorage(): StorageLike | null {
  try {
    return globalThis.sessionStorage;
  } catch {
    return null;
  }
}

function createBroadcastChannel(): ChannelLike {
  return new globalThis.BroadcastChannel(channelName);
}

function isClientSessionMessage(value: unknown): value is ClientSessionMessage {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const message = value as Partial<ClientSessionMessage>;
  if (message.type === 'probe') {
    return (
      isValidRequestIdentifier(message.sessionId) && isValidRequestIdentifier(message.instanceId)
    );
  }
  return (
    message.type === 'claimed' &&
    isValidRequestIdentifier(message.sessionId) &&
    isValidRequestIdentifier(message.targetInstanceId)
  );
}
