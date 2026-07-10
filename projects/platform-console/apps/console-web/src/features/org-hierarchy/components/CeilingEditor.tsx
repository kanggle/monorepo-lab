'use client';

import { useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  ORG_DOMAIN_KEYS,
  type Ceiling,
  type OrgNode,
} from '../api/types';
import { isCeilingSubset, permitsNothing } from '../lib/tree';
import { OrgReasonDialog } from './OrgReasonDialog';

/**
 * Entitlement-ceiling editor (TASK-PC-FE-237 / ADR-047 D2 — the correctness
 * core of this task).
 *
 * A ceiling is a CAP, not a grant: it only NARROWS which domains a company's
 * tenants may use. The editor is a RADIO PAIR — `제한 없음(UNBOUNDED)` vs
 * `상한 지정(BOUNDED)` — plus a domain checkbox group enabled only in BOUNDED
 * mode, because `UNBOUNDED` and `BOUNDED([])` are OPPOSITE states that must
 * never collapse into one control:
 *   · `UNBOUNDED` — no cap at all (a domain added tomorrow is still permitted).
 *   · `BOUNDED([])` — permits NOTHING (a prominent lock-out warning fires).
 *
 * Client subset validation (`isCeilingSubset` against the parent's EFFECTIVE
 * ceiling) is UX assistance only — it blocks Save early, but the SERVER is the
 * authority: a 422 `ORG_NODE_CEILING_NOT_SUBSET` / 403
 * `ORG_NODE_SELF_CEILING_DENIED` (`errorMessage`) is ALWAYS surfaced even when
 * client validation passed.
 */
export interface CeilingEditorProps {
  node: OrgNode;
  /** The parent's EFFECTIVE ceiling (root→parent intersection). Root nodes
   *  pass `{ mode: 'UNBOUNDED' }` (no ancestor cap). Read-only context + the
   *  client subset-check target. */
  parentEffective: Ceiling;
  onSubmit: (ceiling: Ceiling, reason: string) => void;
  pending: boolean;
  /** Server error already mapped to Korean copy (via `messageForCode`). */
  errorMessage: string | null;
}

function describeCeiling(c: Ceiling): string {
  if (c.mode === 'UNBOUNDED') return '제한 없음';
  if (c.domains.length === 0) return '허용 도메인 없음';
  return c.domains.join(', ');
}

export function CeilingEditor({
  node,
  parentEffective,
  onSubmit,
  pending,
  errorMessage,
}: CeilingEditorProps) {
  const [mode, setMode] = useState<'UNBOUNDED' | 'BOUNDED'>(node.ceiling.mode);
  const [domains, setDomains] = useState<string[]>(
    node.ceiling.mode === 'BOUNDED' ? node.ceiling.domains : [],
  );
  const [confirming, setConfirming] = useState(false);

  const next: Ceiling = useMemo(
    () =>
      mode === 'UNBOUNDED'
        ? { mode: 'UNBOUNDED' }
        : { mode: 'BOUNDED', domains },
    [mode, domains],
  );

  const subsetOk = isCeilingSubset(next, parentEffective);
  const lockOut = permitsNothing(next);

  function toggleDomain(key: string) {
    setDomains((prev) =>
      prev.includes(key) ? prev.filter((d) => d !== key) : [...prev, key],
    );
  }

  return (
    <section aria-labelledby="ceiling-heading" className="space-y-3">
      <h3 id="ceiling-heading" className="text-base font-semibold text-foreground">
        상한 (entitlement ceiling)
      </h3>
      <p className="text-sm text-muted-foreground" data-testid="ceiling-explainer">
        이 회사에서 사용할 수 있는 도메인의 상한(cap)입니다. 상한은 도메인을
        좁히기만 하며(deny-only), 어떤 권한도 새로 생기지 않습니다. 상한을 넓혀도
        서비스가 자동으로 열리지 않습니다.
      </p>

      <p
        className="text-sm text-muted-foreground"
        data-testid="ceiling-parent-context"
      >
        상위 노드의 유효 상한:{' '}
        <strong className="text-foreground">
          {describeCeiling(parentEffective)}
        </strong>
      </p>

      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-foreground">상한 모드</legend>
        <label className="flex items-center gap-2 text-sm text-foreground">
          <input
            type="radio"
            name="ceiling-mode"
            value="UNBOUNDED"
            checked={mode === 'UNBOUNDED'}
            onChange={() => setMode('UNBOUNDED')}
            data-testid="ceiling-mode-unbounded"
          />
          제한 없음 (UNBOUNDED) — 상한을 두지 않음
        </label>
        <label className="flex items-center gap-2 text-sm text-foreground">
          <input
            type="radio"
            name="ceiling-mode"
            value="BOUNDED"
            checked={mode === 'BOUNDED'}
            onChange={() => setMode('BOUNDED')}
            data-testid="ceiling-mode-bounded"
          />
          상한 지정 (BOUNDED) — 아래에서 고른 도메인으로 제한
        </label>
      </fieldset>

      <fieldset className="space-y-1" disabled={mode !== 'BOUNDED'}>
        <legend className="text-sm font-medium text-foreground">
          허용 도메인 (상한)
        </legend>
        <div className="flex flex-wrap gap-3">
          {ORG_DOMAIN_KEYS.map((key) => (
            <label
              key={key}
              className="flex items-center gap-1.5 text-sm text-foreground"
            >
              <input
                type="checkbox"
                checked={mode === 'BOUNDED' && domains.includes(key)}
                onChange={() => toggleDomain(key)}
                disabled={mode !== 'BOUNDED'}
                data-testid={`ceiling-domain-${key}`}
              />
              {key}
            </label>
          ))}
        </div>
      </fieldset>

      {lockOut && (
        <p
          role="alert"
          data-testid="ceiling-lockout-warning"
          className="rounded-md border border-amber-300/50 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
        >
          이 회사의 모든 서비스가 어떤 도메인도 사용할 수 없게 됩니다.
        </p>
      )}

      {!subsetOk && (
        <p
          role="alert"
          data-testid="ceiling-subset-block"
          className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          상한은 상위 노드의 상한보다 넓을 수 없습니다. 상위 노드의 유효 상한(
          {describeCeiling(parentEffective)}) 이내로만 지정하세요.
        </p>
      )}

      {errorMessage && (
        <p
          role="alert"
          data-testid="ceiling-server-error"
          className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {errorMessage}
        </p>
      )}

      <Button
        onClick={() => setConfirming(true)}
        disabled={!subsetOk || pending}
        data-testid="ceiling-save"
      >
        상한 저장
      </Button>

      {confirming && (
        <OrgReasonDialog
          title="상한 변경"
          description={`이 노드(${node.name})의 상한을 "${describeCeiling(next)}" 로 변경합니다.`}
          confirmLabel="상한 저장"
          pending={pending}
          error={errorMessage}
          onConfirm={(reason) => {
            onSubmit(next, reason);
            setConfirming(false);
          }}
          onCancel={() => setConfirming(false)}
        />
      )}
    </section>
  );
}
