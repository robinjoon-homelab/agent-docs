import type { Block, StoredBlock } from '../../../../domain/document/block';
import type { AgentDocument, BlockChange } from '../../../../domain/document/document';
import type { Actor } from '../../../../domain/shared/actor';
import type { Thread } from '../../../../domain/thread/thread';
import { MemoryStore } from './store';

const DAY = 24 * 60 * 60 * 1000;

function ago(ms: number): string {
  return new Date(Date.now() - ms).toISOString();
}

function inDays(days: number): string {
  return new Date(Date.now() + days * DAY).toISOString();
}

function hashed(blocks: Block[]): StoredBlock[] {
  return blocks.map((block, index) => ({ ...block, hash: (0x10000000 + index).toString(16) }));
}

const claude: Actor = {
  kind: 'agent',
  login: 'robinjoon',
  agent: { connectionId: 'conn-claude', clientName: 'Claude Code' },
};
const codex: Actor = {
  kind: 'agent',
  login: 'robinjoon',
  agent: { connectionId: 'conn-codex', clientName: 'Codex' },
};

const architectureBlocks: Block[] = [
  { id: 'h1archintr', type: 'heading', level: 1, text: '아키텍처 개요' },
  {
    id: 'p1archintr',
    type: 'paragraph',
    text: '백엔드는 **헥사고날 아키텍처**를 따른다. 도메인은 순수하고 *어댑터*는 가장자리에 둔다. 자세한 규칙은 [설치 안내](doc:doc-setup)를 본다.',
  },
  {
    id: 'c1archnote',
    type: 'callout',
    kind: 'note',
    text: '에이전트와 사람이 같은 블록 문서를 읽고 쓴다.',
  },
  { id: 'h2archlayr', type: 'heading', level: 2, text: '계층' },
  {
    id: 'l1archdomn',
    type: 'listItem',
    style: 'numbered',
    depth: 0,
    checked: false,
    text: '`domain`: 순수 모델',
  },
  {
    id: 'l2archappl',
    type: 'listItem',
    style: 'numbered',
    depth: 0,
    checked: false,
    text: '`application`: 유스케이스와 포트',
  },
  {
    id: 'l3archport',
    type: 'listItem',
    style: 'bullet',
    depth: 1,
    checked: false,
    text: '출력 포트는 인터페이스다',
  },
  {
    id: 'l4archadpt',
    type: 'listItem',
    style: 'numbered',
    depth: 0,
    checked: false,
    text: '`adapters`: HTTP, UI',
  },
  {
    id: 't1archtabl',
    type: 'table',
    columns: [
      { id: 'colname001', align: 'none' },
      { id: 'colrule002', align: 'none' },
      { id: 'colcnt0003', align: 'right' },
    ],
    rows: [
      { id: 'rowhead001', cells: ['계층', '의존 규칙', '파일 수'] },
      { id: 'rowdomn002', cells: ['domain', '아무것도 import하지 않는다', '24'] },
      { id: 'rowappl003', cells: ['application', 'domain과 자기 포트만', '18'] },
    ],
  },
  { id: 'h3archflow', type: 'heading', level: 2, text: '요청 흐름' },
  {
    id: 'm1archmerm',
    type: 'code',
    language: 'mermaid',
    code: 'graph LR\n  UI[React UI] --> UC[Use case]\n  UC --> Port[Gateway port]\n  Port --> HTTP[HTTP adapter]',
  },
  {
    id: 'k1archcode',
    type: 'code',
    language: 'kotlin',
    code: 'class DocumentService(private val documents: DocumentRepository) {\n    fun get(id: DocumentId) = documents.find(id)\n}',
  },
  { id: 'q1archquot', type: 'quote', text: '문서가 주인공이다.<br>색은 의미가 있을 때만 쓴다.' },
  { id: 'd1archdivd', type: 'divider' },
  { id: 'l5archtask', type: 'listItem', style: 'task', depth: 0, checked: true, text: '포트 정의' },
  {
    id: 'l6archtask',
    type: 'listItem',
    style: 'task',
    depth: 0,
    checked: false,
    text: '어댑터 테스트 작성 @robinjoon',
  },
  { id: 'i1archimag', type: 'image', assetId: 'asset-diagram', alt: '', caption: '계층 다이어그램' },
];

const setupBlocks: Block[] = [
  { id: 'h1setupint', type: 'heading', level: 1, text: '설치 안내' },
  {
    id: 'p1setupjdk',
    type: 'paragraph',
    text: 'JDK 25와 Node 24를 설치한다. 2 * 3 = 6처럼 별표도 그대로 쓴다.',
  },
  { id: 'c1setupcmd', type: 'code', language: 'bash', code: 'cd frontend\npnpm install\npnpm dev' },
  {
    id: 'w1setupwrn',
    type: 'callout',
    kind: 'warning',
    text: '`.env` 파일은 커밋하지 않는다.',
  },
];

function architectureDocument(): AgentDocument {
  const blockChanges: BlockChange[] = [
    { blockId: 'p1archintr', actor: claude, revision: 15, at: ago(2 * 60 * 1000) },
    { blockId: 't1archtabl', actor: codex, revision: 14, at: ago(40 * 60 * 1000) },
  ];
  return {
    id: 'doc-arch',
    space: { id: 'space-agent', slug: 'agent-docs', name: 'Agent Docs' },
    path: 'design/architecture',
    title: '아키텍처 개요',
    status: 'in_review',
    tags: ['설계', '백엔드'],
    assignee: 'robinjoon',
    revision: 15,
    createdAt: ago(10 * DAY),
    updatedAt: ago(2 * 60 * 1000),
    trashedAt: null,
    blocks: hashed(architectureBlocks),
    blockChanges,
  };
}

function setupDocument(): AgentDocument {
  return {
    id: 'doc-setup',
    space: { id: 'space-agent', slug: 'agent-docs', name: 'Agent Docs' },
    path: 'guides/setup',
    title: '설치 안내',
    status: 'confirmed',
    tags: ['가이드'],
    assignee: null,
    revision: 4,
    createdAt: ago(8 * DAY),
    updatedAt: ago(3 * DAY),
    trashedAt: null,
    blocks: hashed(setupBlocks),
    blockChanges: [],
  };
}

function seedThreads(store: MemoryStore): void {
  const human = store.actor;
  const threads: Thread[] = [
    {
      id: 'th-hexagon',
      documentId: 'doc-arch',
      anchor: { kind: 'text', blockId: 'p1archintr', start: 5, end: 14, quote: '헥사고날 아키텍처' },
      resolution: { state: 'attached', start: 5, end: 14 },
      status: 'answered',
      author: human,
      comments: [
        {
          id: 'cm-1',
          author: human,
          body: '@Claude 포트와 어댑터 예시를 한 줄 추가해 줄래?',
          createdAt: ago(30 * 60 * 1000),
          editedAt: null,
          deleted: false,
        },
        {
          id: 'cm-2',
          author: claude,
          body: '예시 링크를 추가했습니다. rev 15를 확인해 주세요.',
          createdAt: ago(2 * 60 * 1000),
          editedAt: null,
          deleted: false,
        },
      ],
      linkedRevisions: [15],
      createdAt: ago(30 * 60 * 1000),
      updatedAt: ago(2 * 60 * 1000),
    },
    {
      id: 'th-cell',
      documentId: 'doc-arch',
      anchor: {
        kind: 'cell',
        blockId: 't1archtabl',
        rowId: 'rowappl003',
        columnId: 'colcnt0003',
        quote: '18',
      },
      resolution: { state: 'attached' },
      status: 'open',
      author: human,
      comments: [
        {
          id: 'cm-3',
          author: human,
          body: '이 숫자가 맞는지 확인 필요',
          createdAt: ago(5 * 60 * 60 * 1000),
          editedAt: null,
          deleted: false,
        },
      ],
      linkedRevisions: [],
      createdAt: ago(5 * 60 * 60 * 1000),
      updatedAt: ago(5 * 60 * 60 * 1000),
    },
    {
      id: 'th-detached',
      documentId: 'doc-arch',
      anchor: { kind: 'text', blockId: 'gone000000', start: 0, end: 7, quote: '삭제된 문단의 문장' },
      resolution: { state: 'detached' },
      status: 'open',
      author: human,
      comments: [
        {
          id: 'cm-4',
          author: human,
          body: '이 문장은 왜 지웠나요?',
          createdAt: ago(2 * DAY),
          editedAt: null,
          deleted: false,
        },
      ],
      linkedRevisions: [],
      createdAt: ago(2 * DAY),
      updatedAt: ago(2 * DAY),
    },
    {
      id: 'th-doc',
      documentId: 'doc-arch',
      anchor: { kind: 'document' },
      resolution: { state: 'attached' },
      status: 'resolved',
      author: human,
      comments: [
        {
          id: 'cm-5',
          author: human,
          body: '전체적으로 좋습니다.',
          createdAt: ago(3 * DAY),
          editedAt: null,
          deleted: false,
        },
      ],
      linkedRevisions: [],
      createdAt: ago(3 * DAY),
      updatedAt: ago(3 * DAY),
    },
  ];
  store.threads.set('doc-arch', threads);
  store.threads.set('doc-setup', []);
}

function seedRevisions(store: MemoryStore, document: AgentDocument): void {
  const first: Block[] = document.blocks
    .filter((block) => block.type !== 'image')
    .map((block) =>
      block.id === 'p1archintr'
        ? { id: block.id, type: 'paragraph', text: '백엔드는 **헥사고날 아키텍처**를 따른다.' }
        : block,
    );
  const revisions = [
    {
      revision: document.revision - 1,
      actor: store.actor,
      blocks: first,
      summary: '초안 작성',
      source: 'web' as const,
    },
    {
      revision: document.revision,
      actor: claude,
      blocks: document.blocks,
      summary: '설명 문단 보강, 이미지 추가',
      source: 'mcp' as const,
    },
  ];
  store.revisions.set(
    document.id,
    revisions.map((entry, index) => ({
      meta: {
        revision: entry.revision,
        actor: entry.actor,
        source: entry.source,
        summary: entry.summary,
        changedBlockIds: index === 0 ? [] : ['p1archintr', 'i1archimag'],
        createdAt: ago((revisions.length - index) * 60 * 60 * 1000),
        threadIds: index === 0 ? [] : ['th-hexagon'],
      },
      snapshot: {
        revision: entry.revision,
        title: document.title,
        path: document.path,
        blocks: hashed(entry.blocks.map((block) => ({ ...block }))),
        actor: entry.actor,
        createdAt: ago((revisions.length - index) * 60 * 60 * 1000),
      },
    })),
  );
}

function diagramSvg(): string {
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="560" height="160" viewBox="0 0 560 160">' +
    '<rect width="560" height="160" fill="#f4f1ea"/>' +
    ['domain', 'application', 'adapters']
      .map(
        (label, index) =>
          `<rect x="${20 + index * 180}" y="50" width="160" height="60" rx="8" fill="#fff" stroke="#8a8578"/>` +
          `<text x="${100 + index * 180}" y="86" font-family="sans-serif" font-size="16" text-anchor="middle">${label}</text>`,
      )
      .join('') +
    '</svg>';
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

/** Sample workspace for local preview: two spaces, documents with every block type, threads, tokens. */
export function seededStore(): MemoryStore {
  const store = new MemoryStore({
    userId: 'user-1',
    login: 'robinjoon',
    name: 'Robin',
    avatarUrl: 'https://github.com/robinjoon.png?size=40',
    owner: true,
  });
  store.spaces = [
    {
      id: 'space-agent',
      slug: 'agent-docs',
      name: 'Agent Docs',
      description: '에이전트와 함께 쓰는 문서 플랫폼 개발',
      links: [
        { kind: 'github_repository', value: 'robinjoon/agent-docs' },
        { kind: 'local_path', value: '/Users/robin/IdeaProjects/agent-docs' },
      ],
      createdAt: ago(12 * DAY),
      lastActivityAt: ago(2 * 60 * 1000),
      documentCount: 2,
    },
    {
      id: 'space-life',
      slug: 'personal',
      name: '개인 메모',
      description: '',
      links: [],
      createdAt: ago(20 * DAY),
      lastActivityAt: ago(6 * DAY),
      documentCount: 0,
    },
  ];
  const architecture = architectureDocument();
  store.documents.set(architecture.id, architecture);
  store.documents.set('doc-setup', setupDocument());
  const diagram = diagramSvg();
  store.assets.set('asset-diagram', {
    asset: {
      id: 'asset-diagram',
      mediaType: 'image/svg+xml',
      size: 900,
      fileName: 'layers.svg',
      contentUrl: diagram,
    },
    url: diagram,
  });
  seedRevisions(store, architecture);
  seedThreads(store);
  store.inbox = [
    {
      id: 'in-1',
      kind: 'reply',
      space: { slug: 'agent-docs', name: 'Agent Docs' },
      documentId: 'doc-arch',
      documentTitle: '아키텍처 개요',
      threadId: 'th-hexagon',
      commentId: 'cm-2',
      actor: claude,
      excerpt: '예시 링크를 추가했습니다. rev 15를 확인해 주세요.',
      createdAt: ago(2 * 60 * 1000),
      readAt: null,
    },
    {
      id: 'in-2',
      kind: 'assigned_comment',
      space: { slug: 'agent-docs', name: 'Agent Docs' },
      documentId: 'doc-arch',
      documentTitle: '아키텍처 개요',
      threadId: 'th-cell',
      commentId: 'cm-3',
      actor: codex,
      excerpt: '표의 파일 수를 갱신했습니다.',
      createdAt: ago(DAY),
      readAt: ago(DAY / 2),
    },
  ];
  store.activity = [
    {
      id: 'act-1',
      kind: 'revision',
      actor: claude,
      space: { slug: 'agent-docs', name: 'Agent Docs' },
      documentId: 'doc-arch',
      documentTitle: '아키텍처 개요',
      threadId: null,
      revision: 15,
      summary: '설명 문단 보강, 이미지 추가',
      at: ago(2 * 60 * 1000),
    },
    {
      id: 'act-2',
      kind: 'comment',
      actor: store.actor,
      space: { slug: 'agent-docs', name: 'Agent Docs' },
      documentId: 'doc-arch',
      documentTitle: '아키텍처 개요',
      threadId: 'th-cell',
      revision: null,
      summary: '댓글: 이 숫자가 맞는지 확인 필요',
      at: ago(5 * 60 * 60 * 1000),
    },
  ];
  store.connections = [
    {
      id: 'conn-claude',
      kind: 'agent',
      name: 'Claude Code',
      clientId: 'https://claude.ai/oauth/claude-code-client-metadata',
      clientHost: 'claude.ai',
      spaceIds: ['space-agent'],
      scope: 'edit',
      createdAt: ago(10 * DAY),
      lastUsedAt: ago(2 * 60 * 1000),
      expiresAt: inDays(80),
      revokedAt: null,
    },
    {
      id: 'conn-ci',
      kind: 'script',
      name: 'CI 문서 동기화',
      clientId: null,
      clientHost: null,
      spaceIds: ['space-agent', 'space-life'],
      scope: 'comment',
      createdAt: ago(87 * DAY),
      lastUsedAt: ago(DAY),
      expiresAt: inDays(3),
      revokedAt: null,
    },
  ];
  store.oauthRequests.set('demo', {
    id: 'demo',
    client: {
      id: 'https://claude.ai/oauth/claude-code-client-metadata',
      name: 'Claude Code',
      host: 'claude.ai',
      kind: 'metadata_document',
    },
    redirectHost: 'localhost',
    requestedScope: 'edit',
    expiresAt: inDays(1 / 144),
  });
  store.usage = {
    weeks: [0, 1, 2, 3].map((week) => ({
      weekStart: new Date(Date.now() - (3 - week) * 7 * DAY).toISOString().slice(0, 10),
      spaces: [
        { slug: 'agent-docs', name: 'Agent Docs', documents: 2 + week, comments: 3 + week * 2 },
        { slug: 'personal', name: '개인 메모', documents: week % 2, comments: 0 },
      ],
    })),
    agentResponseMinutesMedian: 12,
    revisitedDocuments: 5,
  };
  return store;
}
