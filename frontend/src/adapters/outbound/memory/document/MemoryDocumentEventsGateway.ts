import type { DocumentEventHandlers, RevocationReason } from '../../../../domain/document/documentEvents';
import type { Permission } from '../../../../domain/space/permission';
import type { DocumentEventsGateway } from '../../../../application/document/ports/DocumentEventsGateway';

export class MemoryDocumentEventsGateway implements DocumentEventsGateway {
  private readonly listeners = new Map<string, Set<() => void>>();

  private readonly handlers = new Map<string, Set<DocumentEventHandlers>>();

  watch(documentId: string, invalidate: () => void, handler: DocumentEventHandlers = {}): () => void {
    const handlers = this.handlers.get(documentId) ?? new Set<DocumentEventHandlers>();
    this.handlers.set(documentId, handlers);
    handlers.add(handler);
    const listeners = this.listeners.get(documentId) ?? new Set<() => void>();
    this.listeners.set(documentId, listeners);
    listeners.add(invalidate);
    invalidate();
    return () => {
      listeners.delete(invalidate);
      handlers.delete(handler);
      if (listeners.size === 0) {
        this.listeners.delete(documentId);
        this.handlers.delete(documentId);
      }
    };
  }

  invalidate(documentId: string): void {
    for (const listener of this.listeners.get(documentId) ?? []) listener();
  }
  permission(documentId: string, permission: Permission): void {
    for (const handler of this.handlers.get(documentId) ?? []) handler.permission?.(permission);
  }
  revoke(documentId: string, reason: RevocationReason): void {
    const handlers = [...(this.handlers.get(documentId) ?? [])];
    this.listeners.delete(documentId);
    this.handlers.delete(documentId);
    for (const handler of handlers) handler.revoked?.(reason);
  }
}
