import { useState } from 'react';
import { useLocation } from 'react-router';
import { copyText } from '../../common/CopyButton';
import { Icon } from '../../common/Icon';
import { useToast } from '../../common/Toast';
import { sharedFlowSelectionSearch } from './useFlowSelection';

export function FlowSelectionLink({ screenId, elementId }: { screenId: string; elementId?: string }) {
  const { pathname, search } = useLocation();
  const toast = useToast();
  const [failedUrl, setFailedUrl] = useState<string>();
  const target = sharedFlowSelectionSearch(
    search,
    import.meta.env.VITE_FAKE_API === 'true',
    screenId,
    elementId,
  );
  const url = new URL(pathname + target, window.location.origin).href;
  const label = elementId ? '요소 링크' : '화면 링크';
  return (
    <>
      <button
        type="button"
        className="btn btn-small"
        onClick={() => {
          void copyText(url).then((ok) => {
            setFailedUrl(ok ? undefined : url);
            toast(ok ? `${label}를 복사했습니다` : '복사하지 못했습니다. 표시된 주소를 선택해 복사하세요.');
          });
        }}
      >
        <Icon name="link" size={14} />
        {label} 복사
      </button>
      {failedUrl === url && (
        <input
          aria-label={`${label} 주소`}
          readOnly
          value={url}
          onFocus={(event) => event.currentTarget.select()}
        />
      )}
    </>
  );
}
