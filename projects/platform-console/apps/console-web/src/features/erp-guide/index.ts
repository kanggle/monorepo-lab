/**
 * `features/erp-guide` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). ERP domain static guide, TASK-PC-FE-232. Mirrors
 * `features/finance-guide` / `features/scm-guide` — no data-fetch, no
 * permission gate.
 */
export { ErpGuideScreen } from './components/ErpGuideScreen';
