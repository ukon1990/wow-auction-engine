import { Injectable, signal } from '@angular/core';

export type ToastTone = 'error' | 'success';

export type ToastMessage = {
  id: number;
  tone: ToastTone;
  message: string;
};

@Injectable({
  providedIn: 'root',
})
export class ToastService {
  private nextId = 1;
  private readonly timeoutMs = 5000;
  readonly messages = signal<readonly ToastMessage[]>([]);

  error(message: string): void {
    this.show('error', message);
  }

  success(message: string): void {
    this.show('success', message);
  }

  dismiss(id: number): void {
    this.messages.update((messages) => messages.filter((message) => message.id !== id));
  }

  private show(tone: ToastTone, message: string): void {
    const toast = { id: this.nextId++, tone, message };
    this.messages.update((messages) => [...messages, toast]);
    setTimeout(() => this.dismiss(toast.id), this.timeoutMs);
  }
}
