import { actorShortName } from '../shared/actor';
import { minutesBetween, relativeTime } from '../shared/time';
import type { BlockChange } from './document';

/** How long an agent change counts as "recent" for the change marker. */
export const RECENT_AGENT_CHANGE_MINUTES = 24 * 60;

/** How long the colored bar stays visible after the document loads. */
export const AGENT_MARKER_VISIBLE_MS = 5_000;

export type AgentChangeMark = { readonly blockId: string; readonly label: string };

/** Blocks whose last change came from an agent recently, with the hover label "Claude · 2분 전 · rev 15". */
export function recentAgentChanges(
  changes: readonly BlockChange[],
  now: Date,
  windowMinutes: number = RECENT_AGENT_CHANGE_MINUTES,
): Map<string, AgentChangeMark> {
  const marks = new Map<string, AgentChangeMark>();
  for (const change of changes) {
    if (change.actor.kind !== 'agent' || minutesBetween(change.at, now) > windowMinutes) continue;
    const label = `${actorShortName(change.actor)} · ${relativeTime(change.at, now)} · rev ${change.revision}`;
    marks.set(change.blockId, { blockId: change.blockId, label });
  }
  return marks;
}
