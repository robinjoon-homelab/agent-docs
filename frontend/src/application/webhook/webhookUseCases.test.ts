import { describe, expect, it } from 'vitest';
import type {
  IssuedWebhook,
  WebhookDelivery,
  WebhookDraft,
  WebhookEndpoint,
} from '../../domain/webhook/webhook';
import type { WebhookGateway } from './ports/WebhookGateway';
import { createWebhookUseCases } from './webhookUseCases';

function endpoint(id: string, createdAt: string, active: boolean): WebhookEndpoint {
  return {
    id,
    url: 'https://hooks.example.com',
    events: ['comment.created'],
    active,
    createdAt,
    secretRotatedAt: null,
    stoppedAt: active ? null : createdAt,
    stopReason: active ? null : 'owner',
  };
}

class FakeWebhookGateway implements WebhookGateway {
  registered: WebhookDraft[] = [];
  readonly endpoints: WebhookEndpoint[];

  constructor(endpoints: WebhookEndpoint[]) {
    this.endpoints = endpoints;
  }

  list(): Promise<WebhookEndpoint[]> {
    return Promise.resolve(this.endpoints);
  }

  register(draft: WebhookDraft): Promise<IssuedWebhook> {
    this.registered = [...this.registered, draft];
    return Promise.resolve({ endpoint: endpoint('new', '2026-11-16T04:00:00Z', true), secret: 'whsec_x' });
  }

  stop(endpointId: string): Promise<WebhookEndpoint> {
    return Promise.resolve(endpoint(endpointId, '2026-11-16T03:00:00Z', false));
  }

  rotateSecret(endpointId: string): Promise<IssuedWebhook> {
    return Promise.resolve({
      endpoint: endpoint(endpointId, '2026-11-16T03:00:00Z', true),
      secret: 'whsec_y',
    });
  }

  deliveries(_endpointId: string, limit: number): Promise<WebhookDelivery[]> {
    return Promise.resolve(Array.from({ length: Math.min(limit, 60) }, () => ({}) as WebhookDelivery));
  }
}

describe('webhook use cases', () => {
  it('lists active endpoints first, each group newest first', async () => {
    const gateway = new FakeWebhookGateway([
      endpoint('old', '2026-11-01T00:00:00Z', true),
      endpoint('stopped', '2026-11-10T00:00:00Z', false),
      endpoint('new', '2026-11-05T00:00:00Z', true),
    ]);
    const ids = (await createWebhookUseCases(gateway).list()).map((e) => e.id);
    expect(ids).toEqual(['new', 'old', 'stopped']);
  });

  it('sends a normalized draft and refuses an invalid one without calling the server', async () => {
    const gateway = new FakeWebhookGateway([]);
    const useCases = createWebhookUseCases(gateway);
    const refused = await useCases.register({ url: 'https://u:p@h.example', events: [] });
    const issued = await useCases.register({
      url: ' https://h.example ',
      events: ['comment.mentioned', 'comment.created'],
    });
    expect(refused).toEqual({ ok: false, error: ['url_credentials', 'no_events'] });
    expect(issued.ok && issued.value.secret).toBe('whsec_x');
    expect(gateway.registered).toEqual([
      { url: 'https://h.example', events: ['comment.created', 'comment.mentioned'] },
    ]);
  });

  it('reads one page of recent deliveries', async () => {
    const deliveries = await createWebhookUseCases(new FakeWebhookGateway([])).deliveries('wh1');
    expect(deliveries).toHaveLength(50);
  });
});
