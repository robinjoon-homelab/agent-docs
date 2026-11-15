import { describe, expect, it } from 'vitest';
import { InputTimeTracker } from './inputTimeTracker';

const at = (seconds: number): Date => new Date(Date.UTC(2026, 0, 1, 0, 0, seconds));
function tracker(): InputTimeTracker {
  let id = 0;
  return new InputTimeTracker('tab', () => `batch-${++id}`);
}

describe('InputTimeTracker', () => {
  it('reading alone creates no input or close watermark', () => {
    const times = tracker();
    expect(times.snapshot()).toBeNull();
    expect(times.hasPendingInput()).toBe(false);
    expect(times.closeWatermark()).toBeNull();
  });

  it('records input time rather than delayed save response time', () => {
    const times = tracker();
    times.input(at(1));
    times.input(at(5));
    const snapshot = times.snapshot();
    expect(snapshot?.firstInputAt).toBe(at(1).toISOString());
    expect(snapshot?.lastInputAt).toBe(at(5).toISOString());
    if (snapshot === null) throw new Error('expected snapshot');
    times.accepted(snapshot);
    expect(times.closeWatermark()).toBe(at(5).toISOString());
    expect(times.snapshot()).toBeNull();
  });

  it('keeps input typed during an in-flight save out of that save', () => {
    const times = tracker();
    times.input(at(1));
    const first = times.snapshot();
    if (first === null) throw new Error('expected snapshot');
    times.input(at(8));
    times.accepted(first);
    expect(times.hasPendingInput()).toBe(true);
    expect(times.closeWatermark()).toBe(at(1).toISOString());
    expect(times.snapshot()?.firstInputAt).toBe(at(8).toISOString());
  });

  it('a failed save is not adopted and its trace can be retried', () => {
    const times = tracker();
    times.input(at(3));
    const failed = times.snapshot();
    expect(times.closeWatermark()).toBeNull();
    expect(times.snapshot()?.firstInputAt).toBe(at(3).toISOString());
    if (failed === null) throw new Error('expected snapshot');
    times.accepted(failed);
    expect(times.closeWatermark()).toBe(at(3).toISOString());
  });

  it('no-op saves clear pending input without counting it', () => {
    const times = tracker();
    times.input(at(2));
    const trace = times.snapshot();
    if (trace === null) throw new Error('expected snapshot');
    times.unchanged(trace);
    expect(times.snapshot()).toBeNull();
    expect(times.closeWatermark()).toBeNull();
  });
  it('preserves two isolated inputs as separate zero-duration intervals in one save', () => {
    const times = tracker();
    times.input(at(0));
    times.input(at(300));
    const snapshot = times.snapshot();
    expect(snapshot?.intervals).toEqual([
      { firstInputAt: at(0).toISOString(), lastInputAt: at(0).toISOString() },
      { firstInputAt: at(300).toISOString(), lastInputAt: at(300).toISOString() },
    ]);
    if (snapshot === null) throw new Error('expected snapshot');
    times.accepted(snapshot);
    expect(times.closeWatermark()).toBe(at(300).toISOString());
  });

  it('splits at exactly120 seconds and retains a failed snapshot until adoption', () => {
    const times = tracker();
    times.input(at(0));
    times.input(at(119));
    times.input(at(239));
    const snapshot = times.snapshot();
    expect(snapshot?.intervals).toEqual([
      { firstInputAt: at(0).toISOString(), lastInputAt: at(119).toISOString() },
      { firstInputAt: at(239).toISOString(), lastInputAt: at(239).toISOString() },
    ]);
    expect(times.snapshot()?.intervals).toEqual(snapshot?.intervals);
    expect(times.closeWatermark()).toBeNull();
  });
});
