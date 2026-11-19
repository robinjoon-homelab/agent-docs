import { useEffect, useRef, useState } from 'react';
import { InputTimeTracker } from '../../../../../application/statistics/inputTimeTracker';
import type { InputTrace } from '../../../../../domain/statistics/statistics';
import { isApiError } from '../../../../../application/shared/ports/errors';
import { Link } from 'react-router';
import type { NavigationUseCases } from '../../../../../application/navigation/navigationUseCases';
import type { MemoCommand, MobilePreferences } from '../../../../../domain/navigation/navigation';
import { diaryPath } from '../../../../../domain/navigation/navigation';
import { isValidPath } from '../../../../../domain/document/document';
import { newBlockId } from '../../../../../domain/document/block';
import type { Space } from '../../../../../domain/space/space';
import { ErrorBox } from '../../common/States';
import './mobile.css';

function today(timeZone: string): string {
  const fields = new Intl.DateTimeFormat('en', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const part = (type: string): string => fields.find((field) => field.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}
function destination(preferences: MobilePreferences, mode: string, date: string, memoName: string): string {
  try {
    return mode === 'diary' ? diaryPath(preferences.diaryPattern, date) : `메모/${date}-${memoName}`;
  } catch {
    return '';
  }
}
function randomByte(): number {
  return crypto.getRandomValues(new Uint8Array(1))[0] ?? 0;
}

export function MobileMemoPage({
  navigation,
  userId,
  spaces,
}: {
  navigation: NavigationUseCases;
  userId: string;
  spaces: readonly Space[];
}) {
  const [preferences, setPreferences] = useState(() => navigation.preferences(userId));
  const [mode, setMode] = useState('memo');
  const [text, setText] = useState('');
  const [memoName, setMemoName] = useState(() => crypto.randomUUID().slice(0, 8));
  const [pending, setPending] = useState<MemoCommand | null>(null);
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const foreground = useRef<InputTimeTracker | null>(null);
  const input = useRef<InputTimeTracker[]>([]);
  const frozen = useRef<{ tracker: InputTimeTracker; trace: InputTrace }[]>([]);
  useEffect(() => {
    const hide = () => {
      if (document.visibilityState === 'hidden') foreground.current = null;
    };
    document.addEventListener('visibilitychange', hide);
    return () => document.removeEventListener('visibilitychange', hide);
  }, []);
  const enterText = (value: string) => {
    if (foreground.current === null) {
      foreground.current = new InputTimeTracker(crypto.randomUUID(), () => crypto.randomUUID());
      input.current.push(foreground.current);
    }
    foreground.current.input(new Date());
    setText(value);
  };
  const draft = (): MemoCommand => {
    const opId = crypto.randomUUID();
    frozen.current = input.current.flatMap((tracker) => {
      const trace = tracker.snapshot();
      return trace === null ? [] : [{ tracker, trace }];
    });
    const traces = frozen.current.map(({ trace }) => trace);
    return {
      opId,
      blockId: newBlockId(randomByte),
      space: preferences.space,
      path,
      text,
      ...(traces.length === 0 ? {} : { inputTrace: { batchId: opId, traces } }),
    };
  };
  let date = '';
  try {
    date = today(preferences.timeZone);
  } catch {
    /* Invalid IANA input remains editable. */
  }
  const path = destination(preferences, mode, date, memoName);
  const locked = busy || pending !== null;
  function update(next: MobilePreferences): void {
    setPreferences(next);
    navigation.savePreferences(userId, next);
  }
  async function save(): Promise<void> {
    if (pending === null && destination(preferences, mode, today(preferences.timeZone), memoName) !== path) {
      setError(new Error('날짜가 바뀌었습니다. 표시된 저장 경로를 확인하고 다시 저장하세요.'));
      return;
    }
    if (!isValidPath(path)) {
      setError(new Error('저장 경로를 확인하세요.'));
      return;
    }
    if (!navigator.onLine) {
      setError(new Error('오프라인에서는 저장할 수 없습니다. 입력은 이 화면에 유지됩니다.'));
      return;
    }
    const command = pending ?? draft();
    setPending(command);
    setBusy(true);
    setError(null);
    try {
      const id = await navigation.writeMemo(command);
      for (const { tracker, trace } of frozen.current) tracker.accepted(trace);
      input.current = input.current.filter(
        (tracker) => tracker === foreground.current || tracker.hasPendingInput(),
      );
      frozen.current = [];
      setSaved(id);
      setText('');
      setPending(null);
      setMemoName(crypto.randomUUID().slice(0, 8));
    } catch (failure) {
      setError(failure);
      if (isApiError(failure) && [400, 403, 404, 409, 422].includes(failure.status)) setPending(null);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="mobile-memo page">
      <h1>빠른 메모</h1>
      <p className="muted small">
        온라인에서만 저장합니다. 저장하지 않은 입력은 이 화면을 닫으면 사라집니다.
      </p>
      <label>
        스페이스
        <select
          disabled={locked}
          value={preferences.space}
          onChange={(event) => update({ ...preferences, space: event.target.value })}
        >
          <option value="">스페이스를 선택하세요</option>
          {spaces.map((space) => (
            <option key={space.id} value={space.slug}>
              {space.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        작성 모드
        <select disabled={locked} value={mode} onChange={(event) => setMode(event.target.value)}>
          <option value="memo">새 메모</option>
          <option value="diary">오늘 일기에 추가</option>
        </select>
      </label>
      <label>
        일기 경로 규칙
        <input
          disabled={locked}
          value={preferences.diaryPattern}
          onChange={(event) => update({ ...preferences, diaryPattern: event.target.value })}
        />
      </label>
      <label>
        시간대 (IANA)
        <input
          disabled={locked}
          value={preferences.timeZone}
          onChange={(event) => update({ ...preferences, timeZone: event.target.value })}
        />
      </label>
      <p>
        오늘: {date || '올바른 시간대를 입력하세요'}
        <br />
        저장 경로:{' '}
        <strong>
          {preferences.space}/{path}
        </strong>
      </p>
      <label>
        메모
        <textarea
          rows={8}
          disabled={locked}
          value={text}
          onChange={(event) => enterText(event.target.value)}
        />
      </label>
      {error !== null ? <ErrorBox error={error} /> : null}
      {pending !== null && !busy ? (
        <p role="status">입력을 보존했습니다. 다시 저장하면 같은 작업 ID와 본문으로 재시도합니다.</p>
      ) : null}
      <button
        type="button"
        className="btn btn-primary"
        disabled={busy || !date || !path || !preferences.space || !text.trim()}
        onClick={() => {
          void save();
        }}
      >
        {busy ? '저장 중…' : pending ? '같은 요청 다시 저장' : '저장'}
      </button>
      {saved ? (
        <p role="status">
          <Link to={`/d/${encodeURIComponent(saved)}`}>저장한 문서 열기</Link>
        </p>
      ) : null}
    </main>
  );
}
