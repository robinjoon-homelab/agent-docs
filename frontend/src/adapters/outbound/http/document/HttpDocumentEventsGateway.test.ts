import { describe, expect, it } from 'vitest';
import { HttpDocumentEventsGateway } from './HttpDocumentEventsGateway';
import type { DocumentEventSource } from './HttpDocumentEventsGateway';

class Source implements DocumentEventSource {
  private readonly listeners = new Map<string, () => void>();
  closed = false;
  addEventListener(type: string, listener: () => void): void {
    this.listeners.set(type, listener);
  }
  close(): void {
    this.closed = true;
  }
  dispatch(type: string): void {
    this.listeners.get(type)?.();
  }
}

describe('document event stream', () => {
  it('invalidates and ignores heartbeat and callbacks after disposal', () => {
    const source = new Source();
    const gateway = new HttpDocumentEventsGateway(() => source);
    let view = 'old';
    const stop = gateway.watch('doc-1', () => {
      view = 'fresh';
    });
    source.dispatch('heartbeat');
    expect(view).toBe('old');
    source.dispatch('invalidate');
    expect(view).toBe('fresh');
    stop();
    view = 'unmounted';
    source.dispatch('invalidate');
    expect(view).toBe('unmounted');
    expect(source.closed).toBe(true);
  });
});
