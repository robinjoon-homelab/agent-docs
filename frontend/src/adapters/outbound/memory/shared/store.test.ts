import { expect, it } from 'vitest';
import { seededStore } from './seed';

it('reports each changed document once, after the write that changed it has finished', async () => {
  const store = seededStore();
  const reported: string[] = [];
  const stop = store.onDocumentChange((documentId) => reported.push(documentId));
  const architecture = store.document('doc-arch');

  store.documents.set('doc-arch', { ...architecture, title: '바뀐 제목' });
  store.threads.set('doc-arch', []);
  store.threads.set('doc-setup', []);
  expect(reported).toEqual([]);

  await Promise.resolve();
  expect(reported).toEqual(['doc-arch', 'doc-setup']);

  stop();
  store.documents.delete('doc-setup');
  await Promise.resolve();
  expect(reported).toEqual(['doc-arch', 'doc-setup']);
});

it('does not replay writes made before anyone listened', async () => {
  const store = seededStore();
  store.documents.set('doc-setup', store.document('doc-setup'));
  const reported: string[] = [];
  store.onDocumentChange((documentId) => reported.push(documentId));

  await Promise.resolve();

  expect(reported).toEqual([]);
});
