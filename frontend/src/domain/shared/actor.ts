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
 * `clientName` is the OAuth client_id host when the client has one (e.g. "claude.ai", "chatgpt.com"),
 * otherwise the name the client gave itself.
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

/** "Claude" for claude.ai, "Codex" for chatgpt.com/openai.com, otherwise the host or client name as is. */
function agentLabel(clientName: string): string {
  const client = agentClientOf(clientName);
  return isHost(clientName) && client !== 'other' ? CLIENT_LABEL[client] : clientName;
}

export function isAgent(actor: Actor): boolean {
  return actor.kind === 'agent';
}

/** One display rule for history, comments and inbox so human and agent traces look the same everywhere. */
export function actorDisplay(actor: Actor): ActorDisplay {
  if (actor.kind === 'agent') {
    const clientName = actor.agent?.clientName ?? '에이전트';
    const label = agentLabel(clientName);
    // The host stays visible next to a friendly label so the record still says who connected.
    const via = label === clientName ? '' : `${clientName} · `;
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

/** Short name used inside sentences, e.g. hover labels: "Claude" or "@octocat". */
export function actorShortName(actor: Actor): string {
  return actor.kind === 'agent' ? agentLabel(actor.agent?.clientName ?? '에이전트') : `@${actor.login}`;
}
