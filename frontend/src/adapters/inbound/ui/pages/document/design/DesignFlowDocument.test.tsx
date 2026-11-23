// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { ServicesProvider, type Services } from '../../../../../../config/ServicesContext';
import { DocumentEditorSession } from '../../../../../../application/document/editorSession';
import type {
  DocumentGateway,
  SaveContentResult,
} from '../../../../../../application/document/ports/DocumentGateway';
import type { Block } from '../../../../../../domain/document/block';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { document as fixture, stored } from '../../../../../../test-support/fixtures';
import { DesignFlowDocument } from './DesignFlowDocument';
afterEach(cleanup);
const models: FlowDesignBlock[] = [
  {
    id: 'set0000001',
    type: 'flowDesign',
    design: { id: 'cfg', kind: 'settings', modelVersion: 4, idPrefix: 'A', nextScreen: 2, frame: 'desktop' },
  },
  {
    id: 'scr0000001',
    type: 'flowDesign',
    design: {
      id: 'form',
      kind: 'screen',
      name: '입력 폼',
      code: 'A-01',
      root: {
        id: 'root',
        kind: 'box',
        layout: { mode: 'column', gap: 8 },
        children: [{ id: 'label', kind: 'text', alias: '라벨', text: '원래 문구' }],
      },
    },
  },
  {
    id: 'stt0000001',
    type: 'flowDesign',
    design: { id: 'working', kind: 'state', name: '저장 중', screen: 'form', changes: [] },
  },
  {
    id: 'ann0000001',
    type: 'flowDesign',
    design: {
      id: 'annot',
      kind: 'annotation',
      name: '라벨 설명',
      screen: 'form',
      target: { owner: 'form', node: 'label' },
      description: '표시할 이름',
    },
  },
];
class Server {
  doc = fixture({ type: 'flow', blocks: models.map((b) => stored(b)) });
  saveContent(_id: string, _base: number, blocks: readonly Block[]): Promise<SaveContentResult> {
    this.doc = { ...this.doc, version: this.doc.version + 1, blocks: blocks.map((b) => stored(b)) };
    return Promise.resolve({
      kind: 'saved',
      version: this.doc.version,
      revision: this.doc.revision,
      blocks: this.doc.blocks,
    });
  }
}
const services = { assets: { contentUrl: (id: string) => '/assets/' + id } } as unknown as Services;
function open(path: string, editable: boolean, server = new Server(), request?: (id: string) => void) {
  const session = new DocumentEditorSession(
    server as unknown as DocumentGateway,
    { now: () => new Date() },
    server.doc,
  );
  const result = render(
    <ServicesProvider services={services}>
      <MemoryRouter initialEntries={[path]}>
        <DesignFlowDocument
          session={session}
          editable={editable}
          onInput={() => undefined}
          onRequestComment={request ? (a) => request(a.kind === 'block' ? a.blockId : '') : undefined}
        />
      </MemoryRouter>
    </ServicesProvider>,
  );
  return { session, server, ...result };
}
describe('v4 디자인 문서 보기·편집', () => {
  it('다른 세션에 복사한 화면·상태·요소 링크를 재현하고 읽기 권한에 맞게 편집을 숨긴다', async () => {
    const clipboard = {
      content: '',
      writeText(v: string) {
        this.content = v;
        return Promise.resolve();
      },
    };
    const existing = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    Object.defineProperty(navigator, 'clipboard', { value: clipboard, configurable: true });
    try {
      const first = open('/s/test/d/doc', false);
      fireEvent.change(screen.getByLabelText('상태'), { target: { value: 'stt0000001' } });
      const tree = screen.getByRole('heading', { name: '레이어' }).closest('aside');
      if (!tree) throw Error('레이어 목록이 없습니다.');
      fireEvent.click(within(tree).getByRole('button', { name: '라벨' }));
      fireEvent.click(screen.getByRole('button', { name: '요소 링크 복사' }));
      await waitFor(() => expect(clipboard.content).toContain('flowElement=label'));
      const url = new URL(clipboard.content);
      expect(url.searchParams.get('flowScreen')).toBe('scr0000001');
      expect(url.searchParams.get('flowState')).toBe('stt0000001');
      first.unmount();
      open(url.pathname + url.search, false);
      expect(screen.getByRole('button', { name: '라벨', pressed: true })).toBeDefined();
      expect(screen.getByLabelText<HTMLSelectElement>('상태').value).toBe('stt0000001');
      expect(screen.queryByRole('button', { name: '편집' })).toBeNull();
      expect(screen.queryByRole('button', { name: '마크업 가져오기' })).toBeNull();
    } finally {
      if (existing) Object.defineProperty(navigator, 'clipboard', existing);
      else Reflect.deleteProperty(navigator, 'clipboard');
    }
  });
  it('상태의 텍스트를 편집한 뒤 새 세션에서 기본값과 상태값을 각각 읽는다', async () => {
    const path = '/s/test/d/doc?flowScreen=scr0000001&flowState=stt0000001&flowElement=label';
    const first = open(path, true);
    fireEvent.click(screen.getByRole('button', { name: '편집' }));
    const field = screen.getByLabelText('텍스트');
    fireEvent.change(field, { target: { value: '상태의 문구' } });
    const form = field.closest('form');
    if (!form) throw Error('텍스트 입력 폼이 없습니다.');
    fireEvent.click(within(form).getByRole('button', { name: '적용' }));
    await act(async () => {
      await first.session.idle();
    });
    const saved = first.server.doc.blocks.filter((b) => b.type === 'flowDesign');
    expect(saved[1]?.design).toEqual(models[1]?.design);
    expect(saved[2]?.design.changes).toEqual([
      { op: 'leaf', path: 'text', value: '상태의 문구', target: { owner: 'form', node: 'label' } },
    ]);
    first.unmount();
    open(path, false, first.server);
    expect(screen.getByText('상태의 문구')).toBeDefined();
    fireEvent.change(screen.getByLabelText('상태'), { target: { value: '' } });
    expect(screen.getByText('원래 문구')).toBeDefined();
  });
  it('저장 블록 ID로 댓글을 열고 잘못된 상태 링크는 다른 상태를 대신 보여주지 않는다', () => {
    let anchor = '';
    const view = open('/doc?flowScreen=scr0000001&flowElement=label', false, new Server(), (id) => {
      anchor = id;
    });
    fireEvent.click(screen.getByRole('button', { name: '요소에 댓글 달기' }));
    expect(anchor).toBe('ann0000001');
    view.unmount();
    open('/doc?flowScreen=scr0000001&flowState=wrong', false);
    expect(screen.getByRole('alert').textContent).toContain('찾을 수 없습니다');
    expect(screen.queryByText('원래 문구')).toBeNull();
  });
});
