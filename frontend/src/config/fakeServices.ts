import { MemoryMembershipGateway } from '../adapters/outbound/memory/membership/MemoryMembershipGateway';
import { MemoryActivityGateway } from '../adapters/outbound/memory/activity/MemoryActivityGateway';
import { MemoryAssetGateway } from '../adapters/outbound/memory/asset/MemoryAssetGateway';
import {
  MemoryConnectionGateway,
  MemoryOAuthRequestGateway,
} from '../adapters/outbound/memory/connection/MemoryConnectionGateway';
import { MemoryDocumentEventsGateway } from '../adapters/outbound/memory/document/MemoryDocumentEventsGateway';
import {
  MemoryDocumentGateway,
  simulateAgentEdit,
} from '../adapters/outbound/memory/document/MemoryDocumentGateway';
import { MemoryInboxGateway } from '../adapters/outbound/memory/inbox/MemoryInboxGateway';
import { MemoryNavigationGateway } from '../adapters/outbound/memory/navigation/MemoryNavigationGateway';
import {
  memoryNavigationBackend,
  memoryNavigationState,
} from '../adapters/outbound/memory/navigation/memoryNavigationBackend';
import { MemoryPortabilityGateway } from '../adapters/outbound/memory/portability/MemoryPortabilityGateway';
import { MemorySearchGateway } from '../adapters/outbound/memory/search/MemorySearchGateway';
import { MemorySessionGateway } from '../adapters/outbound/memory/session/MemorySessionGateway';
import { seededStore } from '../adapters/outbound/memory/shared/seed';
import type { MemoryStore } from '../adapters/outbound/memory/shared/store';
import { MemorySpaceGateway } from '../adapters/outbound/memory/space/MemorySpaceGateway';
import { MemoryStatisticsGateway } from '../adapters/outbound/memory/statistics/MemoryStatisticsGateway';
import { MemoryThreadGateway } from '../adapters/outbound/memory/thread/MemoryThreadGateway';
import { MemoryWebhookGateway } from '../adapters/outbound/memory/webhook/MemoryWebhookGateway';
import type { Services } from './services';
import { assembleServices, systemClock } from './services';

/** The fake backend over one store. Exported for tests; the app goes through createFakeServices. */
export function fakeServicesOf(store: MemoryStore, timeZone: string): Services {
  // One document gateway: its pending-confirmation state must be the same for the editor and quick memos.
  const documents = new MemoryDocumentGateway(store);
  const documentEvents = new MemoryDocumentEventsGateway();
  store.onDocumentChange((documentId) => documentEvents.invalidate(documentId));
  // Its own state, sending nothing; the example endpoint shows a retrying and a delivered record.
  const webhooks = new MemoryWebhookGateway();
  webhooks.seedExample();
  return assembleServices(
    {
      memberships: new MemoryMembershipGateway(store),
      session: new MemorySessionGateway(store),
      spaces: new MemorySpaceGateway(store),
      documents,
      documentEvents,
      threads: new MemoryThreadGateway(store),
      assets: new MemoryAssetGateway(store),
      search: new MemorySearchGateway(store),
      inbox: new MemoryInboxGateway(store),
      activity: new MemoryActivityGateway(store),
      connections: new MemoryConnectionGateway(store),
      oauthRequests: new MemoryOAuthRequestGateway(store),
      navigation: new MemoryNavigationGateway(
        memoryNavigationState(),
        memoryNavigationBackend(store, documents, timeZone),
      ),
      // Starts empty: the preview does not invent edit time it never measured.
      statistics: new MemoryStatisticsGateway(),
      // Rejects every call: import and export need the server.
      portability: new MemoryPortabilityGateway(),
      webhooks,
    },
    systemClock,
    true,
  );
}

/** Local preview without the backend. Loaded lazily so production bundles do not carry the fake. */
export function createFakeServices(): Services {
  const store = seededStore();
  // Console hook for local preview: agentDocsFake.agentEdit('doc-arch', 'p1archintr', '새 문장')
  Object.assign(window, {
    agentDocsFake: {
      agentEdit: (documentId: string, blockId: string, text: string) =>
        simulateAgentEdit(store, documentId, blockId, text),
    },
  });
  return fakeServicesOf(store, Intl.DateTimeFormat().resolvedOptions().timeZone);
}
