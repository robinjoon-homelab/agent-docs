export type WebhookEventType = 'document.changed' | 'comment.created' | 'comment.mentioned';
export type WebhookStopReason = 'owner' | 'gone';
export type WebhookDeliveryState = 'pending' | 'succeeded' | 'failed';

/** A receiver the owner registered. The signing secret is never part of it. */
export type WebhookEndpoint = {
  readonly id: string;
  readonly url: string;
  readonly events: readonly WebhookEventType[];
  readonly active: boolean;
  readonly createdAt: string;
  readonly secretRotatedAt: string | null;
  readonly stoppedAt: string | null;
  readonly stopReason: WebhookStopReason | null;
};

/** Returned only when an endpoint is created or its secret replaced: the one time the secret is shown. */
export type IssuedWebhook = { readonly endpoint: WebhookEndpoint; readonly secret: string };

export type WebhookDraft = { readonly url: string; readonly events: readonly WebhookEventType[] };

export type WebhookDelivery = {
  readonly id: string;
  /** The webhook-id header; the same on every retry, so a receiver can drop repeats. */
  readonly webhookId: string;
  readonly event: WebhookEventType;
  readonly activityId: number;
  readonly mentioned: string | null;
  readonly occurredAt: string;
  readonly state: WebhookDeliveryState;
  readonly attempts: number;
  readonly maxAttempts: number;
  readonly nextAttemptAt: string | null;
  readonly lastAttemptAt: string | null;
  readonly lastStatus: number | null;
  readonly lastError: string | null;
};

export const WEBHOOK_EVENTS: readonly WebhookEventType[] = [
  'document.changed',
  'comment.created',
  'comment.mentioned',
];

export const WEBHOOK_EVENT_LABEL: Record<WebhookEventType, string> = {
  'document.changed': '문서 변경(리비전 확정)',
  'comment.created': '댓글 생성',
  'comment.mentioned': '멘션',
};

export type WebhookDraftError =
  'url_required' | 'url_scheme' | 'url_credentials' | 'url_fragment' | 'no_events';

export const WEBHOOK_DRAFT_ERROR_TEXT: Record<WebhookDraftError, string> = {
  url_required: '받을 주소를 입력하세요.',
  url_scheme: '주소는 http:// 또는 https://로 시작해야 합니다.',
  url_credentials: '주소에 사용자 이름이나 비밀번호를 넣을 수 없습니다.',
  url_fragment: '주소에 #으로 시작하는 부분을 넣을 수 없습니다.',
  no_events: '받을 이벤트를 하나 이상 고르세요.',
};

/**
 * Early feedback before the request; the server checks the address again, including link-local and
 * metadata addresses, and stays the authority.
 */
export function validateWebhookDraft(draft: WebhookDraft): WebhookDraftError[] {
  const errors: WebhookDraftError[] = [];
  const url = draft.url.trim();
  if (url === '') errors.push('url_required');
  else if (!/^https?:\/\/[^/?#]+/.test(url)) errors.push('url_scheme');
  else {
    const authority = url.replace(/^https?:\/\//, '').split(/[/?#]/)[0] ?? '';
    if (authority.includes('@')) errors.push('url_credentials');
    if (url.includes('#')) errors.push('url_fragment');
  }
  if (draft.events.length === 0) errors.push('no_events');
  return errors;
}

export function normalizeWebhookDraft(draft: WebhookDraft): WebhookDraft {
  return { url: draft.url.trim(), events: WEBHOOK_EVENTS.filter((event) => draft.events.includes(event)) };
}

export type DeliveryStatus = { readonly tone: 'ok' | 'waiting' | 'failed' | 'idle'; readonly text: string };

const ERROR_TEXT: Record<string, string> = {
  timeout: '15초 안에 응답 없음',
  connection_failed: '연결 실패',
  unknown_host: '주소를 찾을 수 없음',
  refused_address: '허용하지 않는 주소',
  no_answer_recorded: '응답을 기록하지 못함',
};

function lastAnswer(delivery: WebhookDelivery): string {
  if (delivery.lastStatus !== null) return `HTTP ${delivery.lastStatus}`;
  if (delivery.lastError !== null) return ERROR_TEXT[delivery.lastError] ?? delivery.lastError;
  return '응답 없음';
}

/** What the delivery list says about one delivery: its outcome, or why and when it is tried again. */
export function deliveryStatus(delivery: WebhookDelivery, endpoint: WebhookEndpoint): DeliveryStatus {
  const tries = `${delivery.attempts}/${delivery.maxAttempts}회`;
  if (delivery.state === 'succeeded')
    return { tone: 'ok', text: `전달됨 · ${lastAnswer(delivery)} · ${tries}` };
  if (delivery.state === 'failed') {
    const why = delivery.lastStatus === 410 ? '수신처가 410으로 거부' : lastAnswer(delivery);
    return { tone: 'failed', text: `실패 · ${why} · ${tries}` };
  }
  if (!endpoint.active) return { tone: 'idle', text: '엔드포인트가 중지되어 보내지 않음' };
  if (delivery.attempts === 0) return { tone: 'waiting', text: '보낼 차례를 기다리는 중' };
  return { tone: 'waiting', text: `재시도 대기 · 마지막 ${lastAnswer(delivery)} · ${tries}` };
}

export const STOP_REASON_LABEL: Record<WebhookStopReason, string> = {
  owner: '소유자가 중지',
  gone: '수신처가 410 Gone으로 중지',
};
