import { useState } from 'react';
import type { FlowElementBlock, FlowKind } from '../../../../../domain/document/block';
import { KINDS } from '../../../../../domain/flow/flow';

export type FlowAppearanceValue = Pick<
  FlowElementBlock,
  'kind' | 'label' | 'value' | 'date' | 'items' | 'look'
>;
const LOOKS: Partial<Record<FlowKind, Record<string, string>>> = {
  title: { large: '큰 제목', small: '작은 제목' },
  button: { primary: '진한 버튼', secondary: '테두리 버튼', disabled: '비활성' },
  notice: { info: '안내', error: '오류', ok: '성공' },
  field: { normal: '기본', inactive: '비활성' },
  select: { normal: '기본', inactive: '비활성' },
  area: { normal: '기본', inactive: '비활성' },
  box: {
    white: '흰 판',
    sheet: '아래 시트 판',
    gray: '회색 자리',
    dim: '어두운 덮개',
    outline: '점선 테두리',
    handle: '시트 손잡이',
  },
};

export function FlowAppearanceFields({
  element: e,
  onPatch,
  onChangeKind,
  onUploadImage,
}: {
  element: FlowAppearanceValue;
  onPatch: (patch: Partial<FlowAppearanceValue>) => void;
  onChangeKind?: (kind: FlowKind) => void;
  onUploadImage?: (file: File) => Promise<string>;
}) {
  const [uploadError, setUploadError] = useState('');
  const [uploading, setUploading] = useState(false);
  return (
    <>
      <div className="flow-two-columns">
        <label>
          종류
          <select
            value={e.kind}
            disabled={!onChangeKind}
            onChange={(event) => onChangeKind?.(event.target.value as FlowKind)}
          >
            {Object.entries(KINDS).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          요소 이름
          <input
            value={e.label}
            maxLength={100}
            onChange={(event) => onPatch({ label: event.target.value })}
          />
        </label>
      </div>
      {e.kind === 'list' ? (
        <label>
          목록 항목 (한 줄에 하나)
          <textarea
            rows={4}
            value={e.items?.join('\n') ?? ''}
            onChange={(event) =>
              onPatch({
                items: event.target.value
                  .split('\n')
                  .slice(0, 100)
                  .map((v) => v.slice(0, 200)),
              })
            }
          />
        </label>
      ) : (
        e.kind !== 'box' && (
          <label>
            {e.kind === 'button'
              ? '버튼 글자'
              : e.kind === 'link'
                ? '링크 글자'
                : e.kind === 'title'
                  ? '제목 글자'
                  : e.kind === 'field' || e.kind === 'select'
                    ? '보이는 값'
                    : '보이는 글자'}
            <input
              value={e.value}
              maxLength={500}
              onChange={(event) => onPatch({ value: event.target.value })}
            />
          </label>
        )
      )}
      {e.kind === 'image' && onUploadImage && (
        <div className="flow-image-upload">
          <label className="flow-upload-button">
            {uploading ? '올리는 중…' : '이미지 올리기'}
            <input
              type="file"
              accept="image/*"
              hidden
              disabled={uploading}
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = '';
                if (!file) return;
                setUploading(true);
                setUploadError('');
                onUploadImage(file)
                  .then((value) => onPatch({ value }))
                  .catch((error: unknown) =>
                    setUploadError(error instanceof Error ? error.message : '이미지를 올리지 못했습니다.'),
                  )
                  .finally(() => setUploading(false));
              }}
            />
          </label>
          <small>
            보이는 글자가 asset:ID이면 그 이미지를 요소 크기에 맞춰 보여 줍니다. 요소 이름이 대체
            텍스트입니다.
          </small>
          {uploadError && <p role="alert">{uploadError}</p>}
        </div>
      )}
      {LOOKS[e.kind] && (
        <label>
          모양
          <select value={e.look ?? ''} onChange={(event) => onPatch({ look: event.target.value })}>
            {Object.entries(LOOKS[e.kind] ?? {}).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
      )}
      {e.kind === 'path' && (
        <label>
          날짜 (선택)
          <input
            value={e.date ?? ''}
            maxLength={40}
            onChange={(event) => onPatch({ date: event.target.value || null })}
          />
        </label>
      )}
    </>
  );
}
