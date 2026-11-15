import { describe, expect, it } from 'vitest';
import { MemoryDocumentEventsGateway } from './MemoryDocumentEventsGateway';

describe('memory document signals', () => {
  it('initially refreshes and disposes each independent document subscription', () => {
    const events = new MemoryDocumentEventsGateway();
    let first = 'old';
    let second = 'old';
    const stop = events.watch('one', () => {
      first = 'fresh';
    });
    events.watch('two', () => {
      second = 'fresh';
    });
    expect(first).toBe('fresh');
    expect(second).toBe('fresh');
    first = 'editing';
    second = 'editing';
    events.invalidate('one');
    expect(first).toBe('fresh');
    expect(second).toBe('editing');
    stop();
    first = 'closed';
    events.invalidate('one');
    expect(first).toBe('closed');
  });
});
