import { Icon } from './Icon';
import { useToast } from './Toast';

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export function CopyButton({
  text,
  label = '복사',
  done = '복사했습니다',
  className = 'btn btn-small',
}: {
  text: string;
  label?: string;
  done?: string;
  className?: string;
}) {
  const toast = useToast();
  return (
    <button
      type="button"
      className={className}
      onClick={() => {
        void copyText(text).then((copied) =>
          toast(copied ? done : '복사하지 못했습니다. 직접 선택해 복사하세요.'),
        );
      }}
    >
      <Icon name="link" size={14} />
      {label}
    </button>
  );
}
