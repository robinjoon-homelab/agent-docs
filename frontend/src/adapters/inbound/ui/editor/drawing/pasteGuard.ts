/**
 * Whether pasted HTML holds an image Excalidraw would fetch before any paste hook runs. It reads the HTML the way
 * Excalidraw does, with DOMParser and each image's src, so a character reference such as h&#116;tps cannot hide an
 * address from a pattern over the raw text. Only inline data: images pass; the page CSP stays the last barrier.
 */
export function hasRemoteImage(html: string): boolean {
  if (html === '') return false;
  const parsed = new DOMParser().parseFromString(html, 'text/html');
  return Array.from(parsed.querySelectorAll('img')).some((image) => {
    const source = image.getAttribute('src')?.trim() ?? '';
    return source !== '' && !source.toLowerCase().startsWith('data:');
  });
}
