import { newBlockId } from '../../../../domain/document/block';

/** 10-char [a-z0-9] id from the browser's CSPRNG. */
export function randomBlockId(): string {
  const bytes = new Uint8Array(32);
  let index = bytes.length;
  return newBlockId(() => {
    if (index >= bytes.length) {
      crypto.getRandomValues(bytes);
      index = 0;
    }
    return bytes[index++] ?? 0;
  });
}
