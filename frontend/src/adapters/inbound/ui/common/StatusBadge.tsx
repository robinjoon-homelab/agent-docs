import type { DocumentStatus } from '../../../../domain/document/document';
import { DOCUMENT_STATUSES, STATUS_LABEL } from '../../../../domain/document/document';

export function StatusBadge({ status }: { status: DocumentStatus }) {
  return <span className={`badge badge-${status}`}>{STATUS_LABEL[status]}</span>;
}

export function StatusSelect({
  value,
  onChange,
  disabled = false,
}: {
  value: DocumentStatus;
  onChange: (status: DocumentStatus) => void;
  disabled?: boolean;
}) {
  return (
    <label className={`badge badge-${value} status-select`}>
      <span className="visually-hidden">상태</span>
      <select
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value as DocumentStatus)}
        aria-label="문서 상태"
      >
        {DOCUMENT_STATUSES.map((status) => (
          <option key={status} value={status}>
            {STATUS_LABEL[status]}
          </option>
        ))}
      </select>
    </label>
  );
}
