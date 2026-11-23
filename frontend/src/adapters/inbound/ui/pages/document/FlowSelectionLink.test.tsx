// @vitest-environment jsdom
import type { ReactNode } from 'react';
import { act, cleanup, fireEvent, render, renderHook, screen } from '@testing-library/react';
import { MemoryRouter, useLocation, useNavigate } from 'react-router';
import { afterEach, expect, it } from 'vitest';
import { newElement, newFlow, readFlow } from '../../../../../domain/flow/flow';
import { ToastProvider } from '../../common/Toast';
import { FlowSelectionLink } from './FlowSelectionLink';
import {
  flowSelectionSearch,
  resolveFlowLink,
  sharedFlowSelectionSearch,
  useFlowSelection,
} from './useFlowSelection';

afterEach(cleanup);
const original = readFlow(newFlow('settings', 'first')).screens[0];
if (!original) throw new Error('화면이 필요합니다.');
const screens = [
  { screen: original.screen, elements: [newElement('one', 'button', 390, 844)] },
  {
    screen: { ...original.screen, id: 'second', screenId: 'M-02' },
    elements: [newElement('two', 'button', 390, 844)],
  },
];

it('resolves only a stable block ID within the requested screen, even with duplicate names', () => {
  expect(resolveFlowLink('?flowScreen=second&flowElement=two', screens)).toEqual({
    screenId: 'second',
    elementId: 'two',
  });
  for (const search of [
    '?flowScreen=M-02',
    '?flowScreen=first&flowElement=two',
    '?flowElement=two',
    '?flowScreen=first&flowElement=removed',
    '?flowScreen=first&flowState=absent',
    '?flowScreen=first&flowElement=one&flowOwner=other',
    '?flowScreen=first&flowScreen=second',
  ]) {
    expect(resolveFlowLink(search, screens)?.error).toBeTruthy();
  }
});

it('keeps unrelated parameters during navigation and replaces stale selection parameters', () => {
  const search = flowSelectionSearch(
    '?flowCase=17&flowElement=one&flowState=deleted&flowOwner=other&flowVia=stale',
    'second',
  );
  expect(Object.fromEntries(new URLSearchParams(search))).toEqual({ flowCase: '17', flowScreen: 'second' });
});

it('retains only the fixture data selector in a shared fake link', () => {
  const query = '?flowCase=7&flowUi=overrides&unrelated=private';
  expect(sharedFlowSelectionSearch(query, true, 'first', 'one')).toBe(
    '?flowCase=7&flowScreen=first&flowElement=one',
  );
  expect(sharedFlowSelectionSearch(query, false, 'first')).toBe('?flowScreen=first');
});

it('restores each element on browser back/forward and leaves the bare document at its initial screen', async () => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={['/s/test/d/doc?flowCase=17']}>{children}</MemoryRouter>
  );
  const { result } = renderHook(
    () => ({ ...useFlowSelection(screens, {}), navigate: useNavigate(), location: useLocation() }),
    { wrapper },
  );
  act(() => {
    result.current.select('first', ['one'], true);
  });
  act(() => {
    result.current.select('second', ['two'], true);
  });
  expect(result.current.location.search).toContain('flowCase=17');
  await act(() => result.current.navigate(-1));
  expect(result.current.current).toEqual({ screenId: 'first', elements: ['one'] });
  await act(() => result.current.navigate(-1));
  expect(result.current.current).toEqual({ screenId: 'first', elements: [] });
  await act(() => result.current.navigate(1));
  expect(result.current.current).toEqual({ screenId: 'first', elements: ['one'] });
});

it('respects a fake preview on a bare link and gives an explicit shared link precedence', () => {
  const preview = { screen: 'second', selection: ['two'] };
  const renderFixture = (search: string) =>
    renderHook(() => useFlowSelection(screens, preview), {
      wrapper: ({ children }: { children: ReactNode }) => (
        <MemoryRouter initialEntries={['/s/team/d/doc' + search]}>{children}</MemoryRouter>
      ),
    });
  expect(renderFixture('?flowCase=7').result.current.current).toEqual({
    screenId: 'second',
    elements: ['two'],
  });
  expect(renderFixture('?flowCase=7&flowScreen=first').result.current.current).toEqual({
    screenId: 'first',
    elements: [],
  });
});

it('offers a manually copyable canonical element URL when the clipboard is unavailable', async () => {
  render(
    <MemoryRouter initialEntries={['/s/team/d/doc?flowCase=17#thread-abc']}>
      <ToastProvider>
        <FlowSelectionLink screenId="screen" elementId="element" />
      </ToastProvider>
    </MemoryRouter>,
  );
  fireEvent.click(screen.getByRole('button', { name: '요소 링크 복사' }));
  expect((await screen.findByRole<HTMLInputElement>('textbox', { name: '요소 링크 주소' })).value).toBe(
    window.location.origin + '/s/team/d/doc?flowScreen=screen&flowElement=element',
  );
  expect(screen.getByText('복사하지 못했습니다. 표시된 주소를 선택해 복사하세요.')).toBeTruthy();
});
