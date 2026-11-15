import { HttpActivityGateway } from '../adapters/outbound/http/activity/HttpActivityGateway';
import { HttpAssetGateway } from '../adapters/outbound/http/asset/HttpAssetGateway';
import {
  HttpConnectionGateway,
  HttpOAuthRequestGateway,
} from '../adapters/outbound/http/connection/HttpConnectionGateway';
import { HttpDocumentEventsGateway } from '../adapters/outbound/http/document/HttpDocumentEventsGateway';
import { HttpDocumentGateway } from '../adapters/outbound/http/document/HttpDocumentGateway';
import { HttpInboxGateway } from '../adapters/outbound/http/inbox/HttpInboxGateway';
import { HttpNavigationGateway } from '../adapters/outbound/http/navigation/HttpNavigationGateway';
import { HttpPortabilityGateway } from '../adapters/outbound/http/portability/HttpPortabilityGateway';
import { HttpSearchGateway } from '../adapters/outbound/http/search/HttpSearchGateway';
import { HttpSessionGateway } from '../adapters/outbound/http/session/HttpSessionGateway';
import type { FetchLike } from '../adapters/outbound/http/shared/httpClient';
import { HttpClient } from '../adapters/outbound/http/shared/httpClient';
import { HttpSpaceGateway } from '../adapters/outbound/http/space/HttpSpaceGateway';
import { HttpStatisticsGateway } from '../adapters/outbound/http/statistics/HttpStatisticsGateway';
import { HttpThreadGateway } from '../adapters/outbound/http/thread/HttpThreadGateway';
import { HttpWebhookGateway } from '../adapters/outbound/http/webhook/HttpWebhookGateway';
import type { Services } from './services';
import { assembleServices, systemClock } from './services';

/**
 * Browser storage for per-user conveniences such as the quick memo's last choices. Blocked or unavailable
 * storage (private windows, disabled site data) reads as empty and drops writes instead of breaking the page.
 */
const browserStorage = {
  getItem(key: string): string | null {
    try {
      return window.localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  setItem(key: string, value: string): void {
    try {
      window.localStorage.setItem(key, value);
    } catch {
      // The choice is simply not remembered.
    }
  },
};

export function createHttpServices(): Services {
  const fetch: FetchLike = (input, init) => window.fetch(input, init);
  const http = new HttpClient({ fetch, cookies: () => document.cookie });
  return assembleServices(
    {
      session: new HttpSessionGateway(http),
      spaces: new HttpSpaceGateway(http),
      documents: new HttpDocumentGateway(http),
      // The session cookie authenticates the stream; EventSource reconnects on its own.
      documentEvents: new HttpDocumentEventsGateway((url) => new EventSource(url, { withCredentials: true })),
      threads: new HttpThreadGateway(http),
      assets: new HttpAssetGateway(http),
      search: new HttpSearchGateway(http),
      inbox: new HttpInboxGateway(http),
      activity: new HttpActivityGateway(http),
      connections: new HttpConnectionGateway(http),
      oauthRequests: new HttpOAuthRequestGateway(http),
      navigation: new HttpNavigationGateway(
        http,
        browserStorage,
        Intl.DateTimeFormat().resolvedOptions().timeZone,
      ),
      statistics: new HttpStatisticsGateway(http),
      portability: new HttpPortabilityGateway(http, fetch),
      webhooks: new HttpWebhookGateway(http),
    },
    systemClock,
    false,
  );
}
