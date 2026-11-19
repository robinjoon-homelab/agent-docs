// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import type {
  DocumentGateway,
  SaveContentResult,
} from '../../../../application/document/ports/DocumentGateway';
import { DocumentEditorSession } from '../../../../application/document/editorSession';
import type { Asset } from '../../../../domain/asset/asset';
import type { Block, StoredBlock } from '../../../../domain/document/block';
import { fakeServicesOf } from '../../../../config/fakeServices';
import { ServicesProvider } from '../../../../config/ServicesContext';
import { document as docFixture, paragraph, stored } from '../../../../test-support/fixtures';
import { MemoryDocumentGateway } from '../../../outbound/memory/document/MemoryDocumentGateway';
import { MemoryStore } from '../../../outbound/memory/shared/store';
import type { DocumentEditorHandle } from './DocumentEditor';
import { DocumentEditor } from './DocumentEditor';

afterEach(cleanup);

function setupEditor(initialBlocks: readonly StoredBlock[] = [stored(paragraph('para000001', ''))]) {
  const store = new MemoryStore({ userId: 'u1', login: 'owner', name: 'Owner', avatarUrl: '', owner: true });
  store.spaces = [
    {
      id: 'space-1',
      slug: 'agent-docs',
      name: 'Agent Docs',
      description: '',
      links: [],
      createdAt: '2026-01-01T00:00:00Z',
      archivedAt: null,
      lastActivityAt: null,
      documentCount: 1,
      permission: 'owner',
    },
  ];
  const baseServices = fakeServicesOf(store, 'UTC');
  const importedUrls: string[] = [];
  const uploadedFiles: { readonly name: string; readonly type: string }[] = [];
  const messages: string[] = [];
  let savedBlocks: readonly Block[] = [];
  const editorRef: { current: DocumentEditorHandle | null } = { current: null };

  const services = {
    ...baseServices,
    assets: {
      ...baseServices.assets,
      importUrl: (_spaceSlug: string, url: string): Promise<Asset> => {
        importedUrls.push(url);
        return Promise.resolve({
          id: 'asset-url-1',
          mediaType: 'image/png',
          size: 120,
          fileName: 'imported.png',
          contentUrl: '/api/assets/asset-url-1/content',
        });
      },
      upload: (
        _spaceSlug: string,
        file: { readonly name: string; readonly type: string; readonly size: number; readonly data: Blob },
      ) => {
        uploadedFiles.push({ name: file.name, type: file.type });
        return Promise.resolve({
          ok: true as const,
          value: {
            id: 'asset-html-1',
            mediaType: file.type,
            size: file.size,
            fileName: file.name,
            contentUrl: '/api/assets/asset-html-1/content',
          },
        });
      },
    },
  };

  const doc = docFixture({
    permission: 'edit',
    blocks: [...initialBlocks],
  });
  const memoryDocs = new MemoryDocumentGateway(store);
  const gateway: DocumentGateway = {
    listInSpace: (spaceSlug, filter) => memoryDocs.listInSpace(spaceSlug, filter),
    listTrash: (spaceSlug) => memoryDocs.listTrash(spaceSlug),
    create: (spaceSlug, newDoc) => memoryDocs.create(spaceSlug, newDoc),
    get: () => Promise.resolve(doc),
    saveContent: (_id: string, _base: number, blocks: readonly Block[]): Promise<SaveContentResult> => {
      savedBlocks = blocks;
      return Promise.resolve({
        kind: 'saved',
        version: 2,
        revision: 1,
        blocks: blocks.map((block) => stored(block)),
      });
    },
    patch: (id, base, patch) => memoryDocs.patch(id, base, patch),
    transfer: (id, base, space, path) => memoryDocs.transfer(id, base, space, path),
    finalize: (id, base) => memoryDocs.finalize(id, base),
    trash: (id) => memoryDocs.trash(id),
    untrash: (id) => memoryDocs.untrash(id),
    destroy: (id) => memoryDocs.destroy(id),
    revisions: (id, filter) => memoryDocs.revisions(id, filter),
    revision: (id, rev) => memoryDocs.revision(id, rev),
    diff: (id, from, to) => memoryDocs.diff(id, from, to),
    restore: (id, rev, base) => memoryDocs.restore(id, rev, base),
    nameRevision: (id, rev, name) => memoryDocs.nameRevision(id, rev, name),
    restoreBlock: (id, rev, blockId, base) => memoryDocs.restoreBlock(id, rev, blockId, base),
  };
  const session = new DocumentEditorSession(gateway, { now: () => new Date() }, doc);
  session.setWritable(true);

  render(
    <MemoryRouter>
      <ServicesProvider services={services}>
        <DocumentEditor
          ref={editorRef}
          document={doc}
          session={session}
          editable
          threads={[]}
          activeThreadId={null}
          reanchoring={false}
          mentionLogins={[]}
          onActivateThread={() => undefined}
          onRequestComment={() => undefined}
          onReanchor={() => undefined}
          onContentChange={() => undefined}
          onMessage={(message) => {
            messages.push(message);
          }}
        />
      </ServicesProvider>
    </MemoryRouter>,
  );

  return { importedUrls, uploadedFiles, messages, session, editorRef, getSavedBlocks: () => savedBlocks };
}

function dispatchPaste(target: Element, options: { text?: string; html?: string; files?: File[] }) {
  const event = new Event('paste', { bubbles: true, cancelable: true });
  const data = {
    files: options.files ?? [],
    types: [
      ...(options.files && options.files.length > 0 ? ['Files'] : []),
      ...(options.html !== undefined ? ['text/html'] : []),
      ...(options.text !== undefined ? ['text/plain'] : []),
    ],
    getData: (type: string) => {
      if (type === 'text/plain') return options.text ?? '';
      if (type === 'text/html') return options.html ?? '';
      return '';
    },
  };
  Object.defineProperty(event, 'clipboardData', { value: data });
  act(() => {
    target.dispatchEvent(event);
  });
}

describe('DocumentEditor paste workflow', () => {
  it('offers explicit import for extensionless public URLs without auto-fetching on paste', async () => {
    const { importedUrls, editorRef, getSavedBlocks } = setupEditor();
    const textbox = await screen.findByRole('textbox', { name: '문서 본문' });
    const url = 'https://images.example.com/photo/123?auto=format';

    dispatchPaste(textbox, { text: url });
    expect(importedUrls).toEqual([]);

    const importButton = await screen.findByRole('button', { name: '이미지로 가져오기' });
    await act(async () => {
      fireEvent.click(importButton);
      await Promise.resolve();
    });

    await waitFor(() => {
      expect(importedUrls).toEqual([url]);
    });
    await act(async () => {
      await editorRef.current?.flushSave();
    });
    expect(getSavedBlocks()).toEqual([expect.objectContaining({ type: 'image', assetId: 'asset-url-1' })]);
  });

  it('does not replace an unrelated earlier paragraph holding the same URL and ignores late resolve after losing access', async () => {
    const url = 'https://images.example.com/photo/123?auto=format';
    const { session, editorRef, getSavedBlocks } = setupEditor([
      stored(paragraph('para000001', '')),
      stored(paragraph('para000000', url)),
    ]);
    const textbox = await screen.findByRole('textbox', { name: '문서 본문' });

    dispatchPaste(textbox, { text: url });
    const importButton = await screen.findByRole('button', { name: '이미지로 가져오기' });
    await act(async () => {
      fireEvent.click(importButton);
      await Promise.resolve();
    });

    await waitFor(async () => {
      await act(async () => {
        await editorRef.current?.flushSave();
      });
      expect(getSavedBlocks().some((block) => block.type === 'image')).toBe(true);
    });
    expect(getSavedBlocks().find((block) => block.id === 'para000000')).toMatchObject({
      id: 'para000000',
      type: 'paragraph',
      text: url,
    });

    session.setWritable(false);
    dispatchPaste(textbox, { text: 'https://images.example.com/photo/456' });
    const secondButton = await screen.findByRole('button', { name: '이미지로 가져오기' });
    const beforeCount = getSavedBlocks().length;
    await act(async () => {
      fireEvent.click(secondButton);
      await Promise.resolve();
    });
    await act(async () => {
      await editorRef.current?.flushSave();
    });
    expect(getSavedBlocks().length).toBe(beforeCount);
  });

  it('uploads pasted HTML files through asset upload while ignoring arbitrary clipboard HTML', async () => {
    const { uploadedFiles, editorRef, getSavedBlocks } = setupEditor();
    const textbox = await screen.findByRole('textbox', { name: '문서 본문' });

    dispatchPaste(textbox, { html: '<div onclick="alert(1)">hello</div>', text: 'hello' });
    expect(uploadedFiles).toEqual([]);

    const htmlFile = new File(['<h1>Mockup</h1>'], 'login.html', { type: 'text/html' });
    dispatchPaste(textbox, { files: [htmlFile] });

    await waitFor(() => {
      expect(uploadedFiles).toEqual([{ name: 'login.html', type: 'text/html' }]);
    });
    await waitFor(async () => {
      await act(async () => {
        await editorRef.current?.flushSave();
      });
      expect(getSavedBlocks()).toEqual([
        expect.objectContaining({
          id: 'para000001',
          type: 'paragraph',
          text: 'hello',
        }),
        expect.objectContaining({
          type: 'image',
          assetId: 'asset-html-1',
          alt: 'login.html',
          caption: 'mockup',
        }),
      ]);
    });
  });
});
