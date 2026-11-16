import { describe, expect, it } from 'vitest';
import { HttpDocumentEventsGateway } from './HttpDocumentEventsGateway';
import type { DocumentEventSource } from './HttpDocumentEventsGateway';

class Source implements DocumentEventSource {
  private readonly listeners = new Map<string, (event: { readonly data?: string }) => void>();
  closed = false;
  addEventListener(type: string, listener: (event: { readonly data?: string }) => void): void {
    this.listeners.set(type, listener);
  }
  close(): void {
    this.closed = true;
  }
  dispatch(type: string, data?: string): void {
    this.listeners.get(type)?.({ data });
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

it('parses permission and terminal revocation without reconnecting or later invalidation', () => {
  const source = new Source();
  const gateway = new HttpDocumentEventsGateway(() => source);
  let permission = 'edit';
  let reason = '';
  let view = 'draft';
  gateway.watch(
    'doc-1',
    () => {
      view = 'server';
    },
    {
      permission: (value) => {
        permission = value;
      },
      revoked: (value) => {
        reason = value;
      },
    },
  );
  source.dispatch('permission', '{"permission":"comment"}');
  expect(permission).toBe('comment');
  expect(source.closed).toBe(false);
  source.dispatch('revoked', '{"reason":"unauthenticated"}');
  source.dispatch('permission', '{"permission":"owner"}');
  source.dispatch('invalidate', '{}');
  expect([reason, permission, view, source.closed]).toEqual(['unauthenticated', 'comment', 'draft', true]);
});
