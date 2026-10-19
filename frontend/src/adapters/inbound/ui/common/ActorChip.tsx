import type { Actor, AgentClient } from '../../../../domain/shared/actor';
import { actorDisplay } from '../../../../domain/shared/actor';

const CLIENT_GLYPH: Record<AgentClient, string> = { claude: 'C', codex: 'X', other: 'A' };
const CLIENT_LABEL: Record<AgentClient, string> = { claude: 'Claude', codex: 'Codex', other: '에이전트' };

export function Avatar({ actor, size = 20 }: { actor: Actor; size?: number }) {
  const display = actorDisplay(actor);
  if (display.avatar.shape === 'round') {
    return (
      <img
        className="avatar avatar-round"
        src={display.avatar.url}
        alt=""
        width={size}
        height={size}
        style={{ width: size, height: size }}
        loading="lazy"
      />
    );
  }
  const client = display.avatar.client;
  return (
    <span
      className={`avatar avatar-square avatar-${client}`}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.55) }}
      role="img"
      aria-label={CLIENT_LABEL[client]}
    >
      {CLIENT_GLYPH[client]}
    </span>
  );
}

/** The same actor rendering everywhere (history, comments, inbox) so human and agent traces look alike. */
export function ActorChip({ actor, size = 20 }: { actor: Actor; size?: number }) {
  const display = actorDisplay(actor);
  return (
    <span className="actor">
      <Avatar actor={actor} size={size} />
      <span className="actor-name">{display.name}</span>
      {display.delegation === null ? null : <span className="actor-delegation">{display.delegation}</span>}
    </span>
  );
}
