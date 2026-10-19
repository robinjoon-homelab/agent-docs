import { describe, expect, it } from 'vitest';
import { agent, human } from '../../test-support/fixtures';
import { actorDisplay } from './actor';
import { relativeTime } from './time';

describe('actorDisplay', () => {
  it('shows humans with a round GitHub avatar and @login', () => {
    expect(actorDisplay(human('octocat'))).toEqual({
      name: '@octocat',
      avatar: { shape: 'round', url: 'https://github.com/octocat.png?size=40' },
      delegation: null,
    });
  });

  it('shows agents with a square client icon, client name and delegation', () => {
    expect(actorDisplay(agent('Codex CLI', 'octocat'))).toEqual({
      name: 'Codex CLI',
      avatar: { shape: 'square', client: 'codex' },
      delegation: '@octocat 위임',
    });
    expect(actorDisplay(agent('Cursor')).avatar).toEqual({ shape: 'square', client: 'other' });
  });

  it('splits "Name (host)" into the name and a delegation line that keeps the host', () => {
    expect(actorDisplay(agent('Claude Code (claude.ai)', 'octocat'))).toEqual({
      name: 'Claude Code',
      avatar: { shape: 'square', client: 'claude' },
      delegation: 'claude.ai · @octocat 위임',
    });
    expect(actorDisplay(agent('Codex (chatgpt.com)')).avatar).toEqual({ shape: 'square', client: 'codex' });
    expect(actorDisplay(agent('Build (nightly)')).name).toBe('Build (nightly)');
  });

  it('labels known OAuth client hosts and keeps the host in the delegation line', () => {
    expect(actorDisplay(agent('claude.ai', 'octocat'))).toEqual({
      name: 'Claude',
      avatar: { shape: 'square', client: 'claude' },
      delegation: 'claude.ai · @octocat 위임',
    });
    expect(actorDisplay(agent('chatgpt.com')).avatar).toEqual({ shape: 'square', client: 'codex' });
    expect(actorDisplay(agent('openai.com')).name).toBe('Codex');
    expect(actorDisplay(agent('cursor.com')).name).toBe('cursor.com');
  });
});

describe('relativeTime', () => {
  const now = new Date('2026-10-18T12:00:00Z');

  it('uses minutes, hours and days, then the date', () => {
    expect(relativeTime('2026-10-18T11:59:30Z', now)).toBe('방금 전');
    expect(relativeTime('2026-10-18T11:58:00Z', now)).toBe('2분 전');
    expect(relativeTime('2026-10-18T09:00:00Z', now)).toBe('3시간 전');
    expect(relativeTime('2026-10-16T12:00:00Z', now)).toBe('2일 전');
    expect(relativeTime('2026-09-01T12:00:00Z', now)).toMatch(/^2026-09-0[12]$/);
  });
});
