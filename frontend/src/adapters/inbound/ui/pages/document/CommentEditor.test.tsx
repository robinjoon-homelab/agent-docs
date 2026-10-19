// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, describe, expect, it } from 'vitest';
import { CommentEditor } from './CommentEditor';

afterEach(cleanup);

function Composer() {
  const [saved, setSaved] = useState('');
  const [cancelled, setCancelled] = useState(false);
  return (
    <>
      {cancelled ? (
        <p>취소됨</p>
      ) : (
        <CommentEditor
          initial=""
          placeholder="댓글"
          submitLabel="보내기"
          mentionLogins={['robin', 'rose', 'other']}
          onSubmit={(body) => {
            setSaved(body);
            return Promise.resolve(true);
          }}
          onCancel={() => setCancelled(true)}
        />
      )}
      <output aria-label="저장된 댓글">{saved}</output>
    </>
  );
}

function type(body: string): HTMLTextAreaElement {
  const input = screen.getByRole<HTMLTextAreaElement>('textbox');
  fireEvent.change(input, { target: { value: body } });
  return input;
}

describe('comment mention menu', () => {
  it.each([
    { key: 'Enter' },
    { key: 'Tab' },
    { key: 'Enter', metaKey: true },
    { key: 'Enter', ctrlKey: true },
  ])('inserts the selected login without sending the comment: %j', (key) => {
    render(<Composer />);
    const input = type('Hello @ro');
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: '@rose' }).getAttribute('aria-selected')).toBe('true');
    fireEvent.keyDown(input, key);
    expect(input.value).toBe('Hello @rose ');
    expect(input.selectionStart).toBe('Hello @rose '.length);
    expect(screen.queryByRole('listbox')).toBeNull();
    expect(screen.getByLabelText('저장된 댓글').textContent).toBe('');
  });

  it('wraps keyboard selection and supports picking with the mouse', () => {
    render(<Composer />);
    const input = type('@ro');
    fireEvent.keyDown(input, { key: 'ArrowUp' });
    expect(screen.getByRole('option', { name: '@rose' }).getAttribute('aria-selected')).toBe('true');
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: '@robin' }).getAttribute('aria-selected')).toBe('true');
    fireEvent.mouseDown(screen.getByRole('option', { name: '@rose' }));
    fireEvent.mouseUp(screen.getByRole('option', { name: '@rose' }));
    fireEvent.click(screen.getByRole('option', { name: '@rose' }));
    expect(input.value).toBe('@rose ');
  });

  it('Escape closes the menu before it can cancel the comment', () => {
    render(<Composer />);
    const input = type('@ro');
    fireEvent.keyDown(input, { key: 'Escape' });
    expect(screen.queryByRole('listbox')).toBeNull();
    expect(input.value).toBe('@ro');
    expect(screen.queryByText('취소됨')).toBeNull();
    fireEvent.keyDown(input, { key: 'Escape' });
    expect(screen.getByText('취소됨')).toBeTruthy();
  });

  it('does not send when no candidates match or while composing text', () => {
    render(<Composer />);
    const input = type('@missing');
    expect(screen.getByText('결과 없음')).toBeTruthy();
    fireEvent.keyDown(input, { key: 'Enter', metaKey: true });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(input.value).toBe('@missing');
    expect(screen.getByLabelText('저장된 댓글').textContent).toBe('');
    type('@ro');
    fireEvent.keyDown(input, { key: 'Enter', metaKey: true, isComposing: true });
    expect(input.value).toBe('@ro');
    expect(screen.getByLabelText('저장된 댓글').textContent).toBe('');
  });

  it('replaces a login at the cursor while preserving surrounding text, then allows submission', async () => {
    render(<Composer />);
    const input = type('Hello @robin, thanks');
    act(() => input.focus());
    fireEvent.select(input, { target: { selectionStart: 9, selectionEnd: 9 } });
    fireEvent.click(screen.getByRole('option', { name: '@rose' }));
    expect(input.value).toBe('Hello @rose , thanks');
    expect(input.selectionStart).toBe(12);
    fireEvent.keyDown(input, { key: 'Enter', metaKey: true });
    await waitFor(() => expect(input.value).toBe(''));
    expect(screen.getByLabelText('저장된 댓글').textContent).toBe('Hello @rose , thanks');
  });
});
