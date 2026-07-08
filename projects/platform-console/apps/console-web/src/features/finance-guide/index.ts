/**
 * `features/finance-guide` public API (Layered-by-Feature — app/ imports
 * only this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). Finance domain static guide, TASK-PC-FE-229. Mirrors
 * `features/scm-guide` — no data-fetch, no permission gate.
 */
export { FinanceGuideScreen } from './components/FinanceGuideScreen';
