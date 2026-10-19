import { MemoryActivityGateway } from '../adapters/outbound/memory/activity/MemoryActivityGateway';
import { MemoryAssetGateway } from '../adapters/outbound/memory/asset/MemoryAssetGateway';
import {
  MemoryConnectionGateway,
  MemoryOAuthRequestGateway,
} from '../adapters/outbound/memory/connection/MemoryConnectionGateway';
import {
  MemoryDocumentGateway,
  simulateAgentEdit,
} from '../adapters/outbound/memory/document/MemoryDocumentGateway';
import { MemoryInboxGateway } from '../adapters/outbound/memory/inbox/MemoryInboxGateway';
import { MemoryMetricsGateway } from '../adapters/outbound/memory/metrics/MemoryMetricsGateway';
import { MemorySearchGateway } from '../adapters/outbound/memory/search/MemorySearchGateway';
import { MemorySessionGateway } from '../adapters/outbound/memory/session/MemorySessionGateway';
import { seededStore } from '../adapters/outbound/memory/shared/seed';
import { MemorySpaceGateway } from '../adapters/outbound/memory/space/MemorySpaceGateway';
import { MemoryThreadGateway } from '../adapters/outbound/memory/thread/MemoryThreadGateway';
import type { Services } from './services';
import { assembleServices, systemClock } from './services';

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
  return assembleServices(
    {
      session: new MemorySessionGateway(store),
      spaces: new MemorySpaceGateway(store),
      documents: new MemoryDocumentGateway(store),
      threads: new MemoryThreadGateway(store),
      assets: new MemoryAssetGateway(store),
      search: new MemorySearchGateway(store),
      inbox: new MemoryInboxGateway(store),
      activity: new MemoryActivityGateway(store),
      connections: new MemoryConnectionGateway(store),
      oauthRequests: new MemoryOAuthRequestGateway(store),
      metrics: new MemoryMetricsGateway(store),
    },
    systemClock,
    true,
  );
}
