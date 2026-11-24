import {
  useCallback,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
  type Ref,
} from 'react';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import { readAddress, sameAddress } from '../../../../../../domain/flow/editDesign';
import { type Rect } from '../../../../../../domain/flow/flow';
import { resizeHandles } from '../../../../../../domain/flow/editCanvasGeometry';
import { FlowToolIcon } from '../FlowIcon';
import { useEditCommands, type DesignCanvasCommands, type EditCommandProps } from './DesignEditCommands';
import { groupBounds, measureLayers, selectedBoxes, transformable } from './designEditLayout';
import { useDesignDrag } from './useDesignDrag';
import './designEditCanvas.css';

export { type DesignCanvasCommands } from './DesignEditCommands';
export { DesignMultiSelection } from './DesignMultiSelection';

export function useDesignCanvasSelection(
  screen: string,
  state: string | undefined,
  address: DesignAddress | undefined,
  focus: (a: DesignAddress | undefined) => void,
) {
  const scope = JSON.stringify([screen, state]);
  const key = address ? designAddressKey(address) : '';
  const [local, setLocal] = useState<{
    scope: string;
    origin: string;
    destination: string;
    list: readonly DesignAddress[];
  }>();
  const applies = local?.scope === scope && (key === local.origin || key === local.destination);
  // A navigation can commit after pointer-up. Until it arrives, the next key or toolbar action belongs to this selection.
  const addresses = useMemo(
    () => (applies ? local.list : address ? [address] : []),
    [applies, local, address],
  );
  if (local && !applies) setLocal(undefined);
  if (applies && key === local.destination && local.origin !== key) {
    setLocal(local.list.length > 1 ? { ...local, origin: key } : undefined);
  }
  const change = (next: readonly DesignAddress[]) => {
    const first = next[0],
      destination = first ? designAddressKey(first) : '';
    setLocal({ scope, origin: key, destination, list: next });
    if (destination !== key || (local && local.destination !== destination)) focus(first);
  };
  return { addresses, change };
}

type Props = EditCommandProps & {
  ref?: Ref<DesignCanvasCommands>;
  children: ReactNode;
  onUndo: () => void;
  onRedo: () => void;
};
type SelectionOutline = { box: Rect; resizable: boolean; grouped: boolean; locked: boolean };
const position = (r: Rect) => ({ left: r.x, top: r.y, width: r.w, height: r.h });
const handles = {
  nw: '왼쪽 위',
  n: '위',
  ne: '오른쪽 위',
  e: '오른쪽',
  se: '오른쪽 아래',
  s: '아래',
  sw: '왼쪽 아래',
  w: '왼쪽',
};
export function DesignEditCanvas({ ref, children, onUndo, onRedo, ...props }: Props) {
  const host = useRef<HTMLDivElement>(null);
  const { blocks, scope, selection, rows, editable } = props;
  const annotated = useMemo(
    () =>
      new Set(
        blocks.flatMap((b) => {
          const a = readAddress(b.design.target);
          return a &&
            b.design.kind === 'annotation' &&
            b.design.screen === scope.screen &&
            (!b.design.state || b.design.state === scope.state) &&
            b.design.enabled !== false
            ? [designAddressKey(a)]
            : [];
        }),
      ),
    [blocks, scope.screen, scope.state],
  );
  const [outline, setOutline] = useState<SelectionOutline>();
  const commands = useEditCommands(host, props);
  useImperativeHandle(ref, () => commands, [commands]);
  const drag = useDesignDrag(host, props, annotated);
  const selectedRows = useMemo(
    () => rows.filter((r) => selection.some((a) => sameAddress(a, r.address))),
    [rows, selection],
  );
  const refresh = useCallback(() => {
    if (!host.current) return;
    const selected = selectedBoxes(measureLayers(host.current, selectedRows), selection);
    const box = groupBounds(selected, 'overlay'),
      first = selected[0];
    const next =
      box && first
        ? {
            box,
            resizable:
              selected.length === 1 && !first.node.locked && !!first.row.parent && transformable(first),
            grouped:
              typeof first.node.group === 'string' &&
              selected.every((b) => b.node.group === first.node.group),
            locked: selected.every((b) => b.node.locked === true),
          }
        : undefined;
    setOutline((old) => (JSON.stringify(old) === JSON.stringify(next) ? old : next));
  }, [selectedRows, selection]);
  useLayoutEffect(() => {
    const el = host.current;
    if (!el) return;
    refresh();
    let frame = 0;
    const schedule = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(refresh);
    };
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(schedule);
    observer?.observe(el);
    if (selectedRows.length) {
      const boxes = measureLayers(el, selectedRows);
      for (const b of boxes.values()) observer?.observe(b.element);
    }
    el.addEventListener('scroll', schedule, true);
    window.addEventListener('resize', schedule);
    const fonts = (document as { fonts?: FontFaceSet }).fonts;
    fonts?.addEventListener('loadingdone', schedule);
    return () => {
      observer?.disconnect();
      el.removeEventListener('scroll', schedule, true);
      window.removeEventListener('resize', schedule);
      fonts?.removeEventListener('loadingdone', schedule);
      cancelAnimationFrame(frame);
    };
  }, [refresh, selectedRows]);
  function keydown(e: KeyboardEvent<HTMLDivElement>) {
    if (
      !editable ||
      !(e.target instanceof Element) ||
      e.target.closest('input,textarea,select,[contenteditable=true],[data-design-edit-ui]')
    )
      return;
    const k = e.key.toLowerCase(),
      cmd = e.metaKey || e.ctrlKey;
    if (cmd) {
      switch (k) {
        case 'z':
          if (e.shiftKey) onRedo();
          else onUndo();
          break;
        case 'y':
          onRedo();
          break;
        case 'a':
          commands.all();
          break;
        case 'c':
          commands.execute('copy');
          break;
        case 'v':
          commands.execute('paste');
          break;
        case 'd':
          commands.execute('duplicate');
          break;
        case 'g':
          commands.execute(e.shiftKey ? 'ungroup' : 'group');
          break;
        default:
          return;
      }
    } else if (k === 'backspace' || k === 'delete') commands.execute('delete');
    else if (k === 'escape') props.onSelect([]);
    else if (['arrowright', 'arrowleft', 'arrowdown', 'arrowup'].includes(k)) {
      const step = e.shiftKey ? 10 : 1;
      commands.nudge(
        k === 'arrowleft' ? -step : k === 'arrowright' ? step : 0,
        k === 'arrowup' ? -step : k === 'arrowdown' ? step : 0,
      );
    } else return;
    e.preventDefault();
    e.stopPropagation();
  }
  const box = drag.preview?.box ?? outline?.box,
    multi = selection.length > 1,
    coordinates = drag.preview?.coordinates;
  return (
    <div
      ref={host}
      className="design-edit-canvas"
      tabIndex={0}
      aria-label="요소 편집 캔버스"
      {...drag.events}
      onKeyDown={keydown}
    >
      {children}
      {editable && box && (
        <div className={'design-edit-outline' + (multi ? ' is-multiple' : '')} style={position(box)}>
          {multi && (
            <span className="design-selection-label">
              요소 {selection.length}개{outline?.grouped ? ' · 묶음' : ''}
            </span>
          )}
          {!multi &&
            !drag.preview?.box &&
            outline?.resizable &&
            resizeHandles.map((h) => (
              <button
                type="button"
                tabIndex={-1}
                aria-label={handles[h] + ' 크기 조절'}
                data-design-edit-ui=""
                data-resize-handle={h}
                key={h}
                className={'flow-resize handle-' + h}
              />
            ))}
          {!multi && !coordinates && (
            <div
              className="flow-floating"
              data-design-edit-ui=""
              role="group"
              aria-label="요소 도구"
              style={{ top: box.y < 38 ? box.h + 6 : -35 }}
            >
              {(
                [
                  ['front', '앞으로 가져오기', 'front'],
                  ['back', '뒤로 보내기', 'back'],
                  ['duplicate', '복제', 'copy'],
                  ['delete', '삭제', 'delete'],
                ] as const
              ).map(([op, label, icon]) => (
                <button type="button" key={op} aria-label={label} onClick={() => commands.execute(op)}>
                  <FlowToolIcon name={icon} />
                </button>
              ))}
            </div>
          )}
          {coordinates && (
            <small className="flow-coordinates">
              {Math.round(coordinates.x)} · {Math.round(coordinates.y)} · {Math.round(coordinates.w)}×
              {Math.round(coordinates.h)}
            </small>
          )}
        </div>
      )}
      {drag.preview?.range && <div className="design-edit-marquee" style={position(drag.preview.range)} />}
      {drag.preview?.guides?.map((g) => (
        <i
          key={g.axis}
          className={'flow-guide ' + g.axis}
          style={g.axis === 'x' ? { left: g.value } : { top: g.value }}
        />
      ))}
    </div>
  );
}
