import { documentHref, historyHref, threadHref } from '../document/document';
import type { Actor } from '../shared/actor';

export type Activity = {
  readonly id: string;
  readonly kind: string;
  readonly actor: Actor;
  readonly space: { readonly slug: string; readonly name: string } | null;
  readonly documentId: string | null;
  readonly documentTitle: string | null;
  readonly threadId: string | null;
  readonly revision: number | null;
  readonly summary: string;
  readonly at: string;
};

/** Most specific place an activity points to: thread, then revision, then document. */
export function activityHref(activity: Activity): string | null {
  if (activity.space === null || activity.documentId === null) return null;
  if (activity.threadId !== null)
    return threadHref(activity.space.slug, activity.documentId, activity.threadId);
  if (activity.revision !== null)
    return historyHref(activity.space.slug, activity.documentId, activity.revision);
  return documentHref(activity.space.slug, activity.documentId);
}
