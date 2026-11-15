import { ApiError } from '../../../../application/shared/ports/errors';
import type { WebhookGateway } from '../../../../application/webhook/ports/WebhookGateway';
import type {
  IssuedWebhook,
  WebhookDelivery,
  WebhookDraft,
  WebhookEndpoint,
} from '../../../../domain/webhook/webhook';
import { respond } from '../shared/store';

/**
 * Webhook settings for the local preview. It keeps its own state, sends nothing anywhere, and shows
 * fake secrets only; delivery records are example data, not a model of the server's retries.
 */
export class MemoryWebhookGateway implements WebhookGateway {
  private endpoints: WebhookEndpoint[] = [];
  private readonly records = new Map<string, WebhookDelivery[]>();
  private counter = 0;
  private readonly now: () => string;

  constructor(now: () => string = () => new Date().toISOString()) {
    this.now = now;
  }

  /** One example endpoint with a retrying and a delivered record, so the preview shows both states. */
  seedExample(): void {
    const endpoint: WebhookEndpoint = {
      id: this.nextId('wh'),
      url: 'http://192.168.0.20:8080/agent-docs',
      events: ['document.changed', 'comment.mentioned'],
      active: true,
      createdAt: '2026-11-15T09:00:00Z',
      secretRotatedAt: null,
      stoppedAt: null,
      stopReason: null,
    };
    this.endpoints.push(endpoint);
    this.records.set(endpoint.id, [
      this.example('comment.mentioned', { state: 'pending', attempts: 2, lastStatus: 503 }),
      this.example('document.changed', { state: 'succeeded', attempts: 1, lastStatus: 204 }),
    ]);
  }

  list(): Promise<WebhookEndpoint[]> {
    return respond(() => this.endpoints);
  }

  register(draft: WebhookDraft): Promise<IssuedWebhook> {
    return respond(() => {
      const endpoint: WebhookEndpoint = {
        id: this.nextId('wh'),
        url: draft.url,
        events: [...draft.events],
        active: true,
        createdAt: this.now(),
        secretRotatedAt: null,
        stoppedAt: null,
        stopReason: null,
      };
      this.endpoints.push(endpoint);
      this.records.set(endpoint.id, []);
      return { endpoint, secret: this.fakeSecret() };
    });
  }

  stop(endpointId: string): Promise<WebhookEndpoint> {
    return respond(() => {
      const current = this.find(endpointId);
      if (!current.active) return current;
      const stopped: WebhookEndpoint = {
        ...current,
        active: false,
        stoppedAt: this.now(),
        stopReason: 'owner',
      };
      this.replace(stopped);
      return stopped;
    });
  }

  rotateSecret(endpointId: string): Promise<IssuedWebhook> {
    return respond(() => {
      const current = this.find(endpointId);
      if (!current.active) throw new ApiError('conflict', 409, '중지한 엔드포인트입니다. 새로 등록하세요.');
      const rotated: WebhookEndpoint = { ...current, secretRotatedAt: this.now() };
      this.replace(rotated);
      return { endpoint: rotated, secret: this.fakeSecret() };
    });
  }

  deliveries(endpointId: string, limit: number): Promise<WebhookDelivery[]> {
    return respond(() => {
      this.find(endpointId);
      return (this.records.get(endpointId) ?? []).slice(0, limit);
    });
  }

  private find(endpointId: string): WebhookEndpoint {
    const endpoint = this.endpoints.find((candidate) => candidate.id === endpointId);
    if (endpoint === undefined) throw new ApiError('not_found', 404, '엔드포인트를 찾을 수 없습니다.');
    return endpoint;
  }

  private replace(endpoint: WebhookEndpoint): void {
    this.endpoints = this.endpoints.map((candidate) => (candidate.id === endpoint.id ? endpoint : candidate));
  }

  private nextId(prefix: string): string {
    this.counter += 1;
    return `${prefix}_${this.counter}`;
  }

  private fakeSecret(): string {
    return `whsec_fake${String(this.counter).padStart(4, '0')}`;
  }

  private example(
    event: WebhookDelivery['event'],
    outcome: Pick<WebhookDelivery, 'state' | 'attempts' | 'lastStatus'>,
  ): WebhookDelivery {
    const pending = outcome.state === 'pending';
    return {
      id: this.nextId('whd'),
      webhookId: `msg_fake${this.counter}`,
      event,
      activityId: this.counter,
      mentioned: event === 'comment.mentioned' ? 'robinjoon' : null,
      occurredAt: '2026-11-15T09:10:00Z',
      ...outcome,
      maxAttempts: 8,
      nextAttemptAt: pending ? '2026-11-15T09:10:35Z' : null,
      lastAttemptAt: '2026-11-15T09:10:05Z',
      lastError: null,
    };
  }
}
