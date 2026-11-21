import type { FlowAction } from '../../../../../domain/document/block';
export const ACTION_MODES: Record<FlowAction['mode'], string> = {
  same: '같은 화면',
  page: '페이지 이동',
  sheet: '시트',
  sheetClose: '시트 닫힘',
  popup: '팝업',
  newWindow: '새 창',
  error: '오류 표시',
};
