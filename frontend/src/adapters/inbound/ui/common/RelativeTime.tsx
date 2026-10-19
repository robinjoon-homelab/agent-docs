import { formatDateTime, relativeTime } from '../../../../domain/shared/time';
import { useNow } from '../hooks/useNow';

export function RelativeTime({ iso }: { iso: string }) {
  const now = useNow();
  return (
    <time dateTime={iso} title={formatDateTime(iso)}>
      {relativeTime(iso, now)}
    </time>
  );
}
