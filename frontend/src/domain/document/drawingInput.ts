/**
 * Tells real scene edits from everything else the drawing editor reports. The caller passes a fingerprint of the
 * scene content (element versions plus background); view moves, zoom, selection and tool changes leave it alone.
 * The first fingerprint is the scene as opened, after the editor normalized it, so it is never input.
 */
export class DrawingEditState {
  private baseline: string | null = null;
  private current: string | null = null;

  /** True when this fingerprint is a new edit, i.e. one input to record. */
  observe(fingerprint: string): boolean {
    if (this.current === null) {
      this.baseline = fingerprint;
      this.current = fingerprint;
      return false;
    }
    if (fingerprint === this.current) return false;
    this.current = fingerprint;
    return true;
  }

  /** The scene differs from what was opened or last saved. */
  isDirty(): boolean {
    return this.current !== null && this.current !== this.baseline;
  }

  /** A save of this exact content succeeded; later edits make the scene dirty again. */
  saved(fingerprint: string): void {
    this.baseline = fingerprint;
  }

  fingerprint(): string | null {
    return this.current;
  }
}
