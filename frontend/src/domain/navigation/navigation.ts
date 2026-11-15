export type NavigationDocument = {
  readonly id: string;
  readonly spaceId: string;
  readonly spaceSlug: string;
  readonly spaceName: string;
  readonly path: string;
  readonly title: string;
};
export type NavigationPage = { readonly documents: readonly NavigationDocument[]; readonly total: number };
export type MobilePreferences = {
  readonly space: string;
  readonly diaryPattern: string;
  readonly timeZone: string;
};
export type MemoCommand = {
  readonly opId: string;
  readonly blockId: string;
  readonly space: string;
  readonly path: string;
  readonly text: string;
};

export function diaryPath(pattern: string, date: string): string {
  if (!pattern.includes('{YYYY-MM-DD}')) throw new Error('일기 경로에 {YYYY-MM-DD}를 넣으세요.');
  return pattern.replaceAll('{YYYY-MM-DD}', date);
}
