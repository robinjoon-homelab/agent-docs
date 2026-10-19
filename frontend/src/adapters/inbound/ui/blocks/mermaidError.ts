export type MermaidFailure = {
  readonly line: number | null;
  readonly message: string;
  readonly source: string | null;
};

/** Mermaid reports parse errors as text like "Parse error on line 3: ..."; pull out the line for the user. */
export function describeMermaidError(code: string, error: unknown): MermaidFailure {
  const message = error instanceof Error ? error.message : String(error);
  const match = /line (\d+)/i.exec(message);
  const line = match === null ? null : Number(match[1]);
  const source = line === null ? null : (code.split('\n')[line - 1] ?? null);
  return { line, message: message.split('\n')[0] ?? message, source };
}
