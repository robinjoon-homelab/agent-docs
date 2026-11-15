import type { DocumentEventsGateway } from '../../../../application/document/ports/DocumentEventsGateway';

export class MemoryDocumentEventsGateway implements DocumentEventsGateway {
  private readonly listeners = new Map<string, Set<() => void>>();

  watch(documentId: string, invalidate: () => void): () => void {
    const listeners = this.listeners.get(documentId) ?? new Set<() => void>();
    this.listeners.set(documentId, listeners);
    listeners.add(invalidate);
    invalidate();
    return () => {
      listeners.delete(invalidate);
      if (listeners.size === 0) this.listeners.delete(documentId);
    };
  }

  invalidate(documentId: string): void {
    for (const listener of this.listeners.get(documentId) ?? []) listener();
  }
}
