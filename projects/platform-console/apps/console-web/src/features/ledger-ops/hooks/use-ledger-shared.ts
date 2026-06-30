'use client';

import { clampPageSize } from '@/shared/lib/pagination';
import {
  LEDGER_DEFAULT_PAGE_SIZE,
  LEDGER_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Shared ledger-ops hook infrastructure (TASK-PC-FE-148 split). Holds the
 * common `LEDGER_KEY` queryKey prefix + the page-size clamp used across every
 * ledger read hook. Feature-internal — imported by the sibling hook modules
 * (periods / entries / reconciliation / fx), NOT widened to the public
 * `use-ledger-ops` surface. Pure structural extraction — 0 behavior change
 * (the constant + clamp are verbatim from the pre-split file).
 *
 * The `use-ledger-ops` barrel preserves the stable public import path; the
 * domain hooks now live in cohesive sibling modules.
 */

export const LEDGER_KEY = 'ledger-ops';

// Page size arithmetic — NOT money. F5 invariant is amount-only.
export const clampSize = (size?: number): number =>
  clampPageSize(size, LEDGER_DEFAULT_PAGE_SIZE, LEDGER_MAX_PAGE_SIZE);
