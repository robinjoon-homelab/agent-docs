import type { InputTrace } from '../../domain/statistics/statistics';
import { mergeInputIntervals } from '../../domain/statistics/statistics';

type InputMark = { readonly sequence: number; readonly at: string };
type Snapshot = { readonly sequence: number; readonly trace: InputTrace };

/** Tracks actual input separately from autosave or network response times. */
export class InputTimeTracker {
  private readonly tabId: string;
  private readonly nextId: () => string;
  private sequence = 0;
  private marks: readonly InputMark[] = [];
  private snapshots = new Map<string, Snapshot>();
  private lastAcceptedAt: string | null = null;

  constructor(tabId: string, nextId: () => string) {
    this.tabId = tabId;
    this.nextId = nextId;
  }

  input(at: Date): void {
    this.sequence += 1;
    this.marks = [...this.marks, { sequence: this.sequence, at: at.toISOString() }];
  }

  /** Retain this snapshot on retries; later keystrokes belong to another request. */
  snapshot(): InputTrace | null {
    const first = this.marks[0];
    const last = this.marks.at(-1);
    if (first === undefined || last === undefined) return null;
    const intervals = mergeInputIntervals(
      this.marks.map((mark) => ({ firstInputAt: mark.at, lastInputAt: mark.at })),
    );
    const trace: InputTrace = {
      tabId: this.tabId,
      batchId: this.nextId(),
      intervals,
      firstInputAt: intervals[0]?.firstInputAt ?? first.at,
      lastInputAt: intervals.at(-1)?.lastInputAt ?? last.at,
    };
    this.snapshots.set(trace.batchId, { sequence: last.sequence, trace });
    return trace;
  }

  /** Call only for a successful, changed write; a failed/no-op response is not meaningful input. */
  accepted(trace: InputTrace): void {
    const snapshot = this.snapshots.get(trace.batchId);
    if (snapshot === undefined) return;
    this.lastAcceptedAt =
      this.lastAcceptedAt === null
        ? snapshot.trace.lastInputAt
        : this.lastAcceptedAt > snapshot.trace.lastInputAt
          ? this.lastAcceptedAt
          : snapshot.trace.lastInputAt;
    this.marks = this.marks.filter((mark) => mark.sequence > snapshot.sequence);
    for (const [id, candidate] of this.snapshots) {
      if (candidate.sequence <= snapshot.sequence) this.snapshots.delete(id);
    }
  }

  /** A no-op clears submitted marks without counting them as saved input. */
  unchanged(trace: InputTrace): void {
    const snapshot = this.snapshots.get(trace.batchId);
    if (snapshot === undefined) return;
    this.marks = this.marks.filter((mark) => mark.sequence > snapshot.sequence);
    for (const [id, candidate] of this.snapshots) {
      if (candidate.sequence <= snapshot.sequence) this.snapshots.delete(id);
    }
  }

  hasPendingInput(): boolean {
    return this.marks.length > 0;
  }

  closeWatermark(): string | null {
    return this.lastAcceptedAt;
  }
}
