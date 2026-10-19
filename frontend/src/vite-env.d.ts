/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "true" wires the in-memory fake backend instead of HTTP (local preview only). */
  readonly VITE_FAKE_API?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
