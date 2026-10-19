import type { ActivityUseCases } from '../application/activity/activityUseCases';
import { createActivityUseCases } from '../application/activity/activityUseCases';
import type { ActivityGateway } from '../application/activity/ports/ActivityGateway';
import type { AssetUseCases } from '../application/asset/assetUseCases';
import { createAssetUseCases } from '../application/asset/assetUseCases';
import type { AssetGateway } from '../application/asset/ports/AssetGateway';
import type { ConnectionUseCases } from '../application/connection/connectionUseCases';
import { createConnectionUseCases } from '../application/connection/connectionUseCases';
import type {
  ConnectionGateway,
  OAuthRequestGateway,
} from '../application/connection/ports/ConnectionGateway';
import type { DocumentUseCases } from '../application/document/documentUseCases';
import { createDocumentUseCases } from '../application/document/documentUseCases';
import type { DocumentEditorSession } from '../application/document/editorSession';
import { createEditorSession } from '../application/document/editorSession';
import type { DocumentGateway } from '../application/document/ports/DocumentGateway';
import type { InboxUseCases } from '../application/inbox/inboxUseCases';
import { createInboxUseCases } from '../application/inbox/inboxUseCases';
import type { InboxGateway } from '../application/inbox/ports/InboxGateway';
import type { MetricsUseCases } from '../application/metrics/metricsUseCases';
import { createMetricsUseCases } from '../application/metrics/metricsUseCases';
import type { MetricsGateway } from '../application/metrics/ports/MetricsGateway';
import type { SearchGateway } from '../application/search/ports/SearchGateway';
import type { SearchUseCases } from '../application/search/searchUseCases';
import { createSearchUseCases } from '../application/search/searchUseCases';
import type { SessionGateway } from '../application/session/ports/SessionGateway';
import type { SessionUseCases } from '../application/session/sessionUseCases';
import { createSessionUseCases } from '../application/session/sessionUseCases';
import type { Clock } from '../application/shared/ports/Clock';
import type { SpaceGateway } from '../application/space/ports/SpaceGateway';
import type { SpaceUseCases } from '../application/space/spaceUseCases';
import { createSpaceUseCases } from '../application/space/spaceUseCases';
import type { ThreadGateway } from '../application/thread/ports/ThreadGateway';
import type { ThreadUseCases } from '../application/thread/threadUseCases';
import { createThreadUseCases } from '../application/thread/threadUseCases';
import type { AgentDocument } from '../domain/document/document';

/** Everything the UI may call: input ports (use cases) only, never gateways. */
export type Services = {
  readonly session: SessionUseCases;
  readonly spaces: SpaceUseCases;
  readonly documents: DocumentUseCases;
  readonly threads: ThreadUseCases;
  readonly assets: AssetUseCases;
  readonly search: SearchUseCases;
  readonly inbox: InboxUseCases;
  readonly activity: ActivityUseCases;
  readonly connections: ConnectionUseCases;
  readonly metrics: MetricsUseCases;
  readonly clock: Clock;
  readonly openEditor: (document: AgentDocument) => DocumentEditorSession;
  /** True when running against the in-memory fake backend (VITE_FAKE_API=true). */
  readonly fake: boolean;
};

export type Gateways = {
  readonly session: SessionGateway;
  readonly spaces: SpaceGateway;
  readonly documents: DocumentGateway;
  readonly threads: ThreadGateway;
  readonly assets: AssetGateway;
  readonly search: SearchGateway;
  readonly inbox: InboxGateway;
  readonly activity: ActivityGateway;
  readonly connections: ConnectionGateway;
  readonly oauthRequests: OAuthRequestGateway;
  readonly metrics: MetricsGateway;
};

export const systemClock: Clock = { now: () => new Date() };

export function assembleServices(gateways: Gateways, clock: Clock, fake: boolean): Services {
  return {
    session: createSessionUseCases(gateways.session),
    spaces: createSpaceUseCases(gateways.spaces),
    documents: createDocumentUseCases(gateways.documents),
    threads: createThreadUseCases(gateways.threads),
    assets: createAssetUseCases(gateways.assets),
    search: createSearchUseCases(gateways.search),
    inbox: createInboxUseCases(gateways.inbox),
    activity: createActivityUseCases(gateways.activity),
    connections: createConnectionUseCases(gateways.connections, gateways.oauthRequests, clock),
    metrics: createMetricsUseCases(gateways.metrics),
    clock,
    openEditor: (document) => createEditorSession(gateways.documents, clock, document),
    fake,
  };
}
