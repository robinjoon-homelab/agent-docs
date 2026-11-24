import { useState } from 'react';
import type { DesignJson, FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import type { DesignAddress } from '../../../../../../domain/flow/designAddress';
import { designAddressKey } from '../../../../../../domain/flow/designAddress';
import { object, records, stringValue } from '../../../../../../domain/flow/editDesign';
import { richPlain } from '../../../../../../domain/flow/richText';
import { FlowRichText } from '../FlowRichText';
import { FlowCommentBadge, FlowIcon } from '../FlowIcon';
import { ACTION_MODES } from '../FlowElementOptions';
import { KINDS } from '../../../../../../domain/flow/flow';
import type { FlowKind } from '../../../../../../domain/document/block';
import type { DesignRow } from './designLocation';

export type DesignHotspot = {
  block: FlowDesignBlock;
  row: DesignRow;
  name: string;
  kind: string;
  target: DesignAddress;
  comments: number;
};

export function designKind(row: DesignRow, source?: string) {
  if (source?.startsWith('wire:') && Object.hasOwn(KINDS, source.slice(5)))
    return KINDS[source.slice(5) as FlowKind];
  if (row.occurrence.mounts.some((m) => designAddressKey(m) === designAddressKey(row.address)))
    return '컴포넌트';
  const labels: Record<string, string> = {
    box: '상자',
    text: '텍스트',
    image: '이미지',
    icon: '아이콘',
    vector: '벡터',
    group: '그룹',
    shape: '도형',
    mask: '마스크',
    boolean: '불리언',
  };
  return labels[stringValue(row.occurrence.node.kind)] ?? '요소';
}
export function describeDestination(blocks: readonly FlowDesignBlock[], to: DesignJson | undefined) {
  const target = object(to);
  const screen = target && blocks.find((b) => b.design.kind === 'screen' && b.design.id === target.screen);
  const state =
    target?.state && screen
      ? blocks.find(
          (b) =>
            b.design.kind === 'state' && b.design.screen === screen.design.id && b.design.id === target.state,
        )
      : undefined;
  const name = screen
    ? [screen.design.code, screen.design.name, state?.design.name]
        .filter((part): part is string => typeof part === 'string' && !!part)
        .join(' ')
    : stringValue(object(target?.screen)?.unresolvedName, stringValue(target?.screen));
  const missing = !!target && (!screen || (!!target.state && !state));
  return {
    label: name ? (missing ? name + ' (삭제된 대상)' : name) : '이동 없음',
    short: screen ? stringValue(screen.design.code) : name,
    valid: !!screen && !missing,
  };
}

interface ViewProps {
  blocks: readonly FlowDesignBlock[];
  screen: FlowDesignBlock;
  state?: FlowDesignBlock;
  items: readonly DesignHotspot[];
  highlighted?: DesignAddress | null;
  onInfo: (a: DesignAddress) => void;
  onHighlight: (a: DesignAddress | null) => void;
  onFollow: (to: DesignJson | undefined) => void;
  assetUrl: (id: string) => string;
}
export function DesignOverview({
  blocks,
  screen,
  state,
  items,
  highlighted,
  onInfo,
  onHighlight,
  onFollow,
  assetUrl,
}: ViewProps) {
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set());
  const [hovered, setHovered] = useState<string | null>(null);
  const next = new Map<string, { to: DesignJson; label: string }>();
  for (const item of items)
    for (const action of records(item.block.design.actions)) {
      if (!action.to) continue;
      const dest = describeDestination(blocks, action.to);
      if (dest.valid && !next.has(dest.label)) next.set(dest.label, { to: action.to, label: dest.label });
    }
  return (
    <div className="flow-overview">
      <div className="flow-section-eyebrow">
        <span className="flow-screen-id">{stringValue(screen.design.code)}</span>
        {state ? '상태 설명' : '화면 설명'}
      </div>
      <h2>{stringValue((state ?? screen).design.name)}</h2>
      {screen.design.frame === 'note' ? (
        <p className="flow-description muted">글 페이지입니다. 본문은 가운데에 있습니다.</p>
      ) : (
        <FlowRichText
          className="flow-description"
          text={stringValue((state ?? screen).design.description)}
          assetUrl={assetUrl}
        />
      )}
      <div className="flow-entry">
        <span>진입</span>
        <div>{stringValue(screen.design.entry, '—')}</div>
        <span>다음 화면</span>
        <div className="flow-nexts">
          {next.size ? (
            [...next.values()].map((n) => (
              <button key={n.label} onClick={() => onFollow(n.to)}>
                {n.label}
              </button>
            ))
          ) : (
            <span>없음 (흐름의 끝)</span>
          )}
        </div>
      </div>
      <div className="flow-section-title">
        <h3>주요 요소 {items.length}</h3>
        <span>요소의 (i) 설명을 줄여 보여 줍니다</span>
      </div>
      {!items.length && <p className="muted">설명이 붙은 요소가 없습니다.</p>}
      <ol className="flow-hot-items">
        {items.map((item, i) => {
          const desc = stringValue(item.block.design.description),
            plain = richPlain(desc),
            long = Array.from(plain).length > 60;
          const open = expanded.has(item.block.id) || (hovered === item.block.id && long);
          const destination = describeDestination(blocks, records(item.block.design.actions)[0]?.to).short;
          const active = highlighted && designAddressKey(highlighted) === designAddressKey(item.target);
          return (
            <li
              key={item.block.id}
              className={active ? 'is-hovered' : undefined}
              onMouseEnter={() => {
                setHovered(item.block.id);
                onHighlight(item.target);
              }}
              onMouseLeave={() => {
                setHovered(null);
                onHighlight(null);
              }}
            >
              <span className="flow-number">{i + 1}</span>
              <div>
                <div className="flow-hot-title">
                  <button onClick={() => onInfo(item.target)}>{item.name}</button>
                  <span className="flow-kind-chip">{item.kind}</span>
                  {destination && <span className="flow-target">→ {destination}</span>}
                  <FlowCommentBadge count={item.comments} />
                </div>
                {open ? (
                  <div>
                    <FlowRichText text={desc} assetUrl={assetUrl} />
                    <button
                      onClick={() => {
                        setHovered(null);
                        setExpanded((old) => {
                          const out = new Set(old);
                          out.delete(item.block.id);
                          return out;
                        });
                      }}
                    >
                      접기
                    </button>
                  </div>
                ) : (
                  <p>
                    {plain ? Array.from(plain).slice(0, 60).join('') + (long ? '…' : '') : '(설명 없음)'}
                    {long && (
                      <button onClick={() => setExpanded((old) => new Set([...old, item.block.id]))}>
                        더보기
                      </button>
                    )}
                  </p>
                )}
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
export function DesignElementDetails({
  item,
  number,
  blocks,
  assetUrl,
  onFollow,
}: {
  item: DesignHotspot;
  number: number;
  blocks: readonly FlowDesignBlock[];
  assetUrl: (id: string) => string;
  onFollow: (to: DesignJson | undefined) => void;
}) {
  const { design } = item.block;
  const actions = records(design.actions);
  const state = blocks.find((b) => b.design.kind === 'state' && b.design.id === design.state);
  const stateNames = [
    ...new Set([
      ...(Array.isArray(design.legacyStateNames)
        ? design.legacyStateNames.filter((s): s is string => typeof s === 'string')
        : []),
      ...(state ? [stringValue(state.design.name)] : []),
    ]),
  ];
  return (
    <section className="flow-detail">
      <div className="flow-detail-title">
        <span className="flow-number">{number}</span>
        <h2>{item.name}</h2>
        <span className="flow-kind-chip">{item.kind}</span>
      </div>
      {design.description ? (
        <FlowRichText
          className="flow-description"
          assetUrl={assetUrl}
          text={stringValue(design.description)}
        />
      ) : (
        <p className="flow-description">(설명 없음)</p>
      )}
      {actions.length > 0 && (
        <>
          <h3>이동</h3>
          <ul>
            {actions.map((a, i) => {
              const d = describeDestination(blocks, a.to);
              return (
                <li key={i}>
                  <span>{stringValue(a.when)}</span>
                  <button disabled={!d.valid} onClick={() => onFollow(a.to)}>
                    {d.label}
                    <span> ({ACTION_MODES[stringValue(a.mode, 'same') as keyof typeof ACTION_MODES]})</span>
                    <FlowIcon name="right" size={12} />
                  </button>
                </li>
              );
            })}
          </ul>
        </>
      )}
      {stateNames.length > 0 && (
        <>
          <h3>상태</h3>
          <div className="flow-detail-states">
            {stateNames.map((s) => (
              <span key={s}>{s}</span>
            ))}
          </div>
        </>
      )}
    </section>
  );
}
