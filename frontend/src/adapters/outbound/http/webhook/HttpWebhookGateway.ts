import type { WebhookGateway } from '../../../../application/webhook/ports/WebhookGateway';
import type {
  IssuedWebhook,
  WebhookDelivery,
  WebhookDraft,
  WebhookEndpoint,
} from '../../../../domain/webhook/webhook';
import { WEBHOOK_EVENTS } from '../../../../domain/webhook/webhook';
import type { HttpClient } from '../shared/httpClient';
import { query, segment } from '../shared/httpClient';
import { bool, list, num, obj, oneOf, optNum, optOneOf, optStr, str } from '../shared/json';

export function toWebhookEndpoint(value: unknown): WebhookEndpoint {
  const source = obj(value, 'webhook endpoint');
  return {
    id: str(source, 'id'),
    url: str(source, 'url'),
    events: list(source.events, (event) => oneOf({ event }, 'event', WEBHOOK_EVENTS), 'events'),
    active: bool(source, 'active'),
    createdAt: str(source, 'createdAt'),
    secretRotatedAt: optStr(source, 'secretRotatedAt'),
    stoppedAt: optStr(source, 'stoppedAt'),
    stopReason: optOneOf(source, 'stopReason', ['owner', 'gone'] as const),
  };
}

export function toWebhookDelivery(value: unknown): WebhookDelivery {
  const source = obj(value, 'webhook delivery');
  return {
    id: str(source, 'id'),
    webhookId: str(source, 'webhookId'),
    event: oneOf(source, 'event', WEBHOOK_EVENTS),
    activityId: num(source, 'activityId'),
    mentioned: optStr(source, 'mentioned'),
    occurredAt: str(source, 'occurredAt'),
    state: oneOf(source, 'state', ['pending', 'succeeded', 'failed'] as const),
    attempts: num(source, 'attempts'),
    maxAttempts: num(source, 'maxAttempts'),
    nextAttemptAt: optStr(source, 'nextAttemptAt'),
    lastAttemptAt: optStr(source, 'lastAttemptAt'),
    lastStatus: optNum(source, 'lastStatus'),
    lastError: optStr(source, 'lastError'),
  };
}

function toIssued(value: unknown): IssuedWebhook {
  const source = obj(value, 'issued webhook');
  return { endpoint: toWebhookEndpoint(source.endpoint), secret: str(source, 'secret') };
}

export class HttpWebhookGateway implements WebhookGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async list(): Promise<WebhookEndpoint[]> {
    return list(await this.http.get('/api/webhooks'), toWebhookEndpoint);
  }

  async register(draft: WebhookDraft): Promise<IssuedWebhook> {
    return toIssued(await this.http.post('/api/webhooks', { url: draft.url, events: draft.events }));
  }

  async stop(endpointId: string): Promise<WebhookEndpoint> {
    return toWebhookEndpoint(await this.http.post(`/api/webhooks/${segment(endpointId)}/stop`));
  }

  async rotateSecret(endpointId: string): Promise<IssuedWebhook> {
    return toIssued(await this.http.post(`/api/webhooks/${segment(endpointId)}/secret`));
  }

  async deliveries(endpointId: string, limit: number): Promise<WebhookDelivery[]> {
    const path = `/api/webhooks/${segment(endpointId)}/deliveries${query({ limit })}`;
    return list(await this.http.get(path), toWebhookDelivery);
  }
}
