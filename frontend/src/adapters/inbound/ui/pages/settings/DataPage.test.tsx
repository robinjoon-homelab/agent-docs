// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { PortabilityUseCases } from '../../../../../application/portability/portabilityUseCases';
import { ApiError, ExportIncompleteError } from '../../../../../application/shared/ports/errors';
import { DataPage } from './DataPage';

afterEach(cleanup);

const spaces = [{ slug: 'docs', name: '문서 공간' }];

describe('data export results', () => {
  it.each(['전체 내보내기', '선택한 스페이스 내보내기'])(
    'shows recovery guidance and the first failed original for %s',
    async (button) => {
      const failedAsset = { id: 'missing-asset', sha256: 'a'.repeat(64) };
      const portability: PortabilityUseCases = {
        exportZip: () =>
          Promise.reject(
            new ExportIncompleteError('missing original', 'restore a verified original', failedAsset),
          ),
        importFolder: () => Promise.resolve({ files: [] }),
      };
      render(<DataPage portability={portability} spaces={spaces} owner />);
      fireEvent.click(screen.getByRole('button', { name: button }));

      const alert = await screen.findByRole('alert');
      expect(alert.textContent).toContain('첨부 원본이 없어 내보내기를 완료하지 못했습니다');
      expect(alert.textContent).toContain('검증된 원본이나 보관 사본');
      expect(alert.textContent).toContain('첨부 정보와 문서 이력은 삭제하지 마세요');
      expect(alert.textContent).toContain('추가로 누락된 원본이 있을 수 있습니다');
      expect(screen.getByText(failedAsset.id).tagName).toBe('CODE');
      expect(screen.getByText(failedAsset.sha256).tagName).toBe('CODE');
      expect(screen.queryByRole('status')).toBeNull();
    },
  );

  it('keeps recovery guidance when an incomplete problem has no usable attachment context', async () => {
    const portability: PortabilityUseCases = {
      exportZip: () => Promise.reject(new ApiError('export_incomplete', 500, 'missing original')),
      importFolder: () => Promise.resolve({ files: [] }),
    };
    render(<DataPage portability={portability} spaces={spaces} owner />);
    fireEvent.click(screen.getByRole('button', { name: '전체 내보내기' }));
    expect((await screen.findByRole('alert')).textContent).toContain('첨부 원본을 복구한 뒤 다시 시도하세요');
    expect(screen.queryByText('첨부 ID:')).toBeNull();
  });

  it('keeps ordinary import errors visible and hides export controls from a non-owner', async () => {
    const portability: PortabilityUseCases = {
      exportZip: () => Promise.resolve(new ArrayBuffer(0)),
      importFolder: () => Promise.reject(new Error('가져올 파일을 확인하세요.')),
    };
    const file = new File(['# note'], 'note.md', { type: 'text/markdown' });
    Object.defineProperties(file, {
      webkitRelativePath: { value: 'folder/note.md' },
      arrayBuffer: { value: () => Promise.resolve(new ArrayBuffer(0)) },
    });
    render(<DataPage portability={portability} spaces={spaces} owner={false} />);
    fireEvent.change(screen.getByLabelText('가져올 폴더'), { target: { files: [file] } });
    fireEvent.click(screen.getByRole('button', { name: '가져오기' }));
    await waitFor(() => expect(screen.getByRole('alert').textContent).toBe('가져올 파일을 확인하세요.'));
    expect(screen.queryByRole('button', { name: '전체 내보내기' })).toBeNull();
    expect(screen.queryByText(/첨부 원본을 복구/)).toBeNull();
  });
});
