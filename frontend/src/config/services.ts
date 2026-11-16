import type { MembershipGateway } from '../application/membership/ports/MembershipGateway';
import {
  createMembershipUseCases,
  type MembershipUseCases,
} from '../application/membership/membershipUseCases';
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
import type { DocumentEventsUseCases } from '../application/document/documentEventsUseCases';
import { createDocumentEventsUseCases } from '../application/document/documentEventsUseCases';
import type { DocumentUseCases } from '../application/document/documentUseCases';
import { createDocumentUseCases } from '../application/document/documentUseCases';
import type { DocumentEditorSession } from '../application/document/editorSession';
import { createEditorSession } from '../application/document/editorSession';
import type { DocumentEventsGateway } from '../application/document/ports/DocumentEventsGateway';
import type { DocumentGateway } from '../application/document/ports/DocumentGateway';
import type { InboxUseCases } from '../application/inbox/inboxUseCases';
import { createInboxUseCases } from '../application/inbox/inboxUseCases';
import type { InboxGateway } from '../application/inbox/ports/InboxGateway';
import type { NavigationUseCases } from '../application/navigation/navigationUseCases';
import { createNavigationUseCases } from '../application/navigation/navigationUseCases';
import type { NavigationGateway } from '../application/navigation/ports/NavigationGateway';
import type { PortabilityUseCases } from '../application/portability/portabilityUseCases';
import { createPortabilityUseCases } from '../application/portability/portabilityUseCases';
import type { PortabilityGateway } from '../application/portability/ports/PortabilityGateway';
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
import type { StatisticsGateway } from '../application/statistics/ports/StatisticsGateway';
import type { StatisticsUseCases } from '../application/statistics/statisticsUseCases';
import { createStatisticsUseCases } from '../application/statistics/statisticsUseCases';
import type { ThreadGateway } from '../application/thread/ports/ThreadGateway';
import type { ThreadUseCases } from '../application/thread/threadUseCases';
import { createThreadUseCases } from '../application/thread/threadUseCases';
import type { WebhookGateway } from '../application/webhook/ports/WebhookGateway';
import type { WebhookUseCases } from '../application/webhook/webhookUseCases';
import { createWebhookUseCases } from '../application/webhook/webhookUseCases';
import type { AgentDocument } from '../domain/document/document';

/** Everything the UI may call: input ports (use cases) only, never gateways. */
export type Services = {
  readonly memberships: MembershipUseCases;
  readonly session: SessionUseCases;
  readonly spaces: SpaceUseCases;
  readonly documents: DocumentUseCases;
  /** Signals that an open document changed elsewhere; the page reloads through its own use cases. */
  readonly documentEvents: DocumentEventsUseCases;
  readonly threads: ThreadUseCases;
  readonly assets: AssetUseCases;
  readonly search: SearchUseCases;
  readonly inbox: InboxUseCases;
  readonly activity: ActivityUseCases;
  readonly connections: ConnectionUseCases;
  /** Recently viewed and favorite documents, backlinks and the mobile quick memo. */
  readonly navigation: NavigationUseCases;
  /** Edit time per document; failures here never change an edit's outcome. */
  readonly statistics: StatisticsUseCases;
  /** Folder import and ZIP export. */
  readonly portability: PortabilityUseCases;
  /** The owner's webhook endpoints and their delivery records. */
  readonly webhooks: WebhookUseCases;
  readonly clock: Clock;
  readonly openEditor: (document: AgentDocument) => DocumentEditorSession;
  /** True when running against the in-memory fake backend (VITE_FAKE_API=true). */
  readonly fake: boolean;
};

export type Gateways = {
  readonly memberships: MembershipGateway;
  readonly session: SessionGateway;
  readonly spaces: SpaceGateway;
  readonly documents: DocumentGateway;
  readonly documentEvents: DocumentEventsGateway;
  readonly threads: ThreadGateway;
  readonly assets: AssetGateway;
  readonly search: SearchGateway;
  readonly inbox: InboxGateway;
  readonly activity: ActivityGateway;
  readonly connections: ConnectionGateway;
  readonly oauthRequests: OAuthRequestGateway;
  readonly navigation: NavigationGateway;
  readonly statistics: StatisticsGateway;
  readonly portability: PortabilityGateway;
  readonly webhooks: WebhookGateway;
};

export const systemClock: Clock = { now: () => new Date() };

export function assembleServices(gateways: Gateways, clock: Clock, fake: boolean): Services {
  return {
    memberships: createMembershipUseCases(gateways.memberships),
    session: createSessionUseCases(gateways.session),
    spaces: createSpaceUseCases(gateways.spaces),
    documents: createDocumentUseCases(gateways.documents),
    documentEvents: createDocumentEventsUseCases(gateways.documentEvents),
    threads: createThreadUseCases(gateways.threads),
    assets: createAssetUseCases(gateways.assets),
    search: createSearchUseCases(gateways.search),
    inbox: createInboxUseCases(gateways.inbox),
    activity: createActivityUseCases(gateways.activity),
    connections: createConnectionUseCases(gateways.connections, gateways.oauthRequests, clock),
    navigation: createNavigationUseCases(gateways.navigation),
    statistics: createStatisticsUseCases(gateways.statistics),
    portability: createPortabilityUseCases(gateways.portability),
    webhooks: createWebhookUseCases(gateways.webhooks),
    clock,
    openEditor: (document) => createEditorSession(gateways.documents, clock, document),
    fake,
  };
}
