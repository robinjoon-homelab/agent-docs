/**
 * The images a web page put on the clipboard, as absolute http(s) addresses in page order. Images copied from this
 * editor already carry their asset and come through the paste itself, so they are left out.
 */
export function pastedImageUrls(html: string): string[] {
  if (!html.includes('<img')) return [];
  const page = new DOMParser().parseFromString(html, 'text/html');
  const urls = [...page.querySelectorAll('img[src]')]
    .filter((image) => image.closest('figure[data-asset-id]') === null)
    .map((image) => image.getAttribute('src') ?? '')
    .filter((src) => /^https?:\/\//i.test(src));
  return [...new Set(urls)];
}
