import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import type { FlowScreen } from '../../../../../domain/flow/flow';
import type { FlowPreview } from './flowPreview';

type Selection = { screenId: string; elements: string[] };

export function flowSelectionSearch(search: string, screenId: string, elementId?: string): string {
  const params = new URLSearchParams(search);
  params.set('flowScreen', screenId);
  params.delete('flowState');
  params.delete('flowOwner');
  params.delete('flowVia');
  if (elementId === undefined) params.delete('flowElement');
  else params.set('flowElement', elementId);
  return '?' + params.toString();
}

export function sharedFlowSelectionSearch(
  search: string,
  fake: boolean,
  screenId: string,
  elementId?: string,
): string {
  const params = new URLSearchParams();
  const fixture = new URLSearchParams(search).get('flowCase');
  if (fake && fixture) params.set('flowCase', fixture);
  return flowSelectionSearch(params.toString(), screenId, elementId);
}

export function resolveFlowLink(search: string, screens: readonly FlowScreen[]) {
  const params = new URLSearchParams(search);
  const screenId = params.get('flowScreen');
  const elementId = params.get('flowElement');
  const stateId = params.get('flowState');
  const qualifiedNode = params.has('flowOwner') || params.has('flowVia');
  if (screenId === null && elementId === null && stateId === null && !qualifiedNode) return null;

  const screen = screens.find((item) => item.screen.id === screenId);
  if (!screen || params.getAll('flowScreen').length !== 1)
    return { error: '링크의 화면을 찾을 수 없습니다.' };
  if (stateId !== null)
    return { error: '이 화면 흐름 문서에서 링크의 상태를 찾을 수 없습니다.', screenId: screen.screen.id };
  if (
    qualifiedNode ||
    (elementId !== null &&
      (params.getAll('flowElement').length !== 1 || !screen.elements.some((e) => e.id === elementId)))
  )
    return { error: '이 화면에서 링크의 요소를 찾을 수 없습니다.', screenId: screen.screen.id };
  return { screenId: screen.screen.id, elementId };
}

/** Local selections include marquees and historical comment jumps. Browser navigation restores the link. */
export function useFlowSelection(screens: readonly FlowScreen[], preview: Partial<FlowPreview>) {
  const location = useLocation();
  const navigate = useNavigate();
  const link = resolveFlowLink(location.search, screens);
  const [local, setLocal] = useState<(Selection & { key: string }) | null>(null);
  const first = screens[0]?.screen.id ?? '';
  const initial = link
    ? { screenId: link.screenId ?? first, elements: link.elementId ? [link.elementId] : [] }
    : { screenId: preview.screen ?? first, elements: preview.selection ?? [] };
  const current = local?.key === location.key ? local : initial;

  function select(screenId: string, elements: string[], record = false) {
    if (record && elements.length <= 1) {
      const search = flowSelectionSearch(location.search, screenId, elements[0]);
      if (location.search !== search || location.hash) {
        setLocal(null);
        void navigate({ pathname: location.pathname, search, hash: '' }, { preventScrollReset: true });
        return;
      }
    }
    setLocal({ key: location.key, screenId, elements });
  }
  return { current, select, link };
}
