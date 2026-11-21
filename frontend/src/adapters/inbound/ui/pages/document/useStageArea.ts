import { useLayoutEffect, useState, type RefObject } from 'react';

export type StageArea = { width: number; height: number };

/** Measures the room the device frame may use inside the scrolling preview, minus space other parts reserve. */
export function useStageArea(
  ref: RefObject<HTMLElement | null>,
  reservedWidth: number,
  reservedHeight: number,
): StageArea | null {
  const [area, setArea] = useState<StageArea | null>(null);
  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const measure = () => {
      const style = getComputedStyle(element);
      const width =
        element.clientWidth - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight) - reservedWidth;
      const height =
        element.clientHeight -
        parseFloat(style.paddingTop) -
        parseFloat(style.paddingBottom) -
        reservedHeight;
      setArea((previous) =>
        previous?.width === width && previous.height === height ? previous : { width, height },
      );
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref, reservedWidth, reservedHeight]);
  return area;
}
