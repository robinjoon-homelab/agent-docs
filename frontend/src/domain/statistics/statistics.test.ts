import { expect, it } from 'vitest';
import { mergeInputIntervals } from './statistics';

const at = (seconds: number): string => new Date(Date.UTC(2026, 0, 1, 0, 0, seconds)).toISOString();
const point = (seconds: number) => ({ firstInputAt: at(seconds), lastInputAt: at(seconds) });

it('merges actual input across fields without losing a long idle gap', () => {
  expect(mergeInputIntervals([point(300), point(0), point(60)])).toEqual([
    { firstInputAt: at(0), lastInputAt: at(60) },
    point(300),
  ]);
});

it('an input from another field may bridge a formerly idle gap', () => {
  expect(mergeInputIntervals([point(0), point(200), point(100)])).toEqual([
    { firstInputAt: at(0), lastInputAt: at(200) },
  ]);
  expect(mergeInputIntervals([point(0), point(120)])).toEqual([point(0), point(120)]);
});
