import { existsSync } from 'node:fs';
import path from 'node:path';

/** Repo root — tests/helpers → console-web → apps → platform-console → projects → root. */
export const REPO_ROOT = path.resolve(__dirname, '..', '..', '..', '..', '..', '..');

/**
 * The file/dir part of a citation like
 * `projects/x/y.ts:12-30 (note)` / `infra/demo/README.md:3` / `src/app/api/groups/**\/route.ts`.
 * Globs and brace sets are cut back to the last concrete directory before them,
 * so the check still proves "this area of the repo exists".
 */
export function citedPath(source: string): string {
  let p = source.split(' ')[0];
  p = p.replace(/:[\d,\-]+$/, '');
  const wild = p.search(/[*{]/);
  if (wild >= 0) p = p.slice(0, p.lastIndexOf('/', wild));
  return p;
}

export function citedPathExists(source: string): boolean {
  return existsSync(path.join(REPO_ROOT, citedPath(source)));
}
