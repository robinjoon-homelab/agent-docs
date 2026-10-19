export type AgentInfo = {
  readonly connectionId: string;
  readonly clientName: string;
};

/** Who did something. Agents always act on behalf of a human login (delegation). */
export type Actor = {
  readonly kind: 'human' | 'agent';
  readonly login: string;
  readonly agent: AgentInfo | null;
};

export type AgentClient = 'claude' | 'codex' | 'other';

export type ActorAvatar =
  | { readonly shape: 'round'; readonly url: string }
  | { readonly shape: 'square'; readonly client: AgentClient };

export type ActorDisplay = {
  readonly name: string;
  readonly avatar: ActorAvatar;
  /** Grey secondary label for agents, e.g. "@octocat 위임". */
  readonly delegation: string | null;
};

export function githubAvatarUrl(login: string): string {
  return `https://github.com/${encodeURIComponent(login)}.png?size=40`;
}

/**
 * `clientName` is "Name (host)" when the client has a client_id host, e.g. "Claude Code (claude.ai)", otherwise the
 * name the client gave itself. Older records hold the bare host, e.g. "claude.ai".
 */
export function agentClientOf(clientName: string): AgentClient {
  const name = clientName.toLowerCase();
  if (name.includes('claude') || name.includes('anthropic')) return 'claude';
  if (name.includes('codex') || name.includes('chatgpt') || name.includes('openai')) return 'codex';
  return 'other';
}

const CLIENT_LABEL: Record<Exclude<AgentClient, 'other'>, string> = { claude: 'Claude', codex: 'Codex' };

function isHost(clientName: string): boolean {
  return /^[a-z0-9-]+(\.[a-z0-9-]+)+$/i.test(clientName);
}

type AgentName = { readonly label: string; readonly host: string | null };

/** "Claude Code (claude.ai)" → Claude Code at claude.ai; a bare known host gets a friendly label. */
function agentName(clientName: string): AgentName {
  const named = /^(.+) \(([^()]+)\)$/.exec(clientName);
  if (named?.[1] !== undefined && named[2] !== undefined && isHost(named[2]))
    return { label: named[1], host: named[2] };
  const client = agentClientOf(clientName);
  if (isHost(clientName) && client !== 'other') return { label: CLIENT_LABEL[client], host: clientName };
  return { label: clientName, host: null };
}

export function isAgent(actor: Actor): boolean {
  return actor.kind === 'agent';
}

/** One display rule for history, comments and inbox so human and agent traces look the same everywhere. */
export function actorDisplay(actor: Actor): ActorDisplay {
  if (actor.kind === 'agent') {
    const clientName = actor.agent?.clientName ?? '에이전트';
    const { label, host } = agentName(clientName);
    // The host stays visible next to the name, since only the host is verified.
    const via = host === null ? '' : `${host} · `;
    return {
      name: label,
      avatar: { shape: 'square', client: agentClientOf(clientName) },
      delegation: `${via}@${actor.login} 위임`,
    };
  }
  return {
    name: `@${actor.login}`,
    avatar: { shape: 'round', url: githubAvatarUrl(actor.login) },
    delegation: null,
  };
}

/** Short name used inside sentences, e.g. hover labels: "Claude Code" or "@octocat". */
export function actorShortName(actor: Actor): string {
  return actor.kind === 'agent' ? agentName(actor.agent?.clientName ?? '에이전트').label : `@${actor.login}`;
}
