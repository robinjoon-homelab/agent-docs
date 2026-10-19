import { HttpActivityGateway } from '../adapters/outbound/http/activity/HttpActivityGateway';
import { HttpAssetGateway } from '../adapters/outbound/http/asset/HttpAssetGateway';
import {
  HttpConnectionGateway,
  HttpOAuthRequestGateway,
} from '../adapters/outbound/http/connection/HttpConnectionGateway';
import { HttpDocumentGateway } from '../adapters/outbound/http/document/HttpDocumentGateway';
import { HttpInboxGateway } from '../adapters/outbound/http/inbox/HttpInboxGateway';
import { HttpMetricsGateway } from '../adapters/outbound/http/metrics/HttpMetricsGateway';
import { HttpSearchGateway } from '../adapters/outbound/http/search/HttpSearchGateway';
import { HttpSessionGateway } from '../adapters/outbound/http/session/HttpSessionGateway';
import { HttpClient } from '../adapters/outbound/http/shared/httpClient';
import { HttpSpaceGateway } from '../adapters/outbound/http/space/HttpSpaceGateway';
import { HttpThreadGateway } from '../adapters/outbound/http/thread/HttpThreadGateway';
import type { Services } from './services';
import { assembleServices, systemClock } from './services';

export function createHttpServices(): Services {
  const http = new HttpClient({
    fetch: (input, init) => window.fetch(input, init),
    cookies: () => document.cookie,
  });
  return assembleServices(
    {
      session: new HttpSessionGateway(http),
      spaces: new HttpSpaceGateway(http),
      documents: new HttpDocumentGateway(http),
      threads: new HttpThreadGateway(http),
      assets: new HttpAssetGateway(http),
      search: new HttpSearchGateway(http),
      inbox: new HttpInboxGateway(http),
      activity: new HttpActivityGateway(http),
      connections: new HttpConnectionGateway(http),
      oauthRequests: new HttpOAuthRequestGateway(http),
      metrics: new HttpMetricsGateway(http),
    },
    systemClock,
    false,
  );
}
