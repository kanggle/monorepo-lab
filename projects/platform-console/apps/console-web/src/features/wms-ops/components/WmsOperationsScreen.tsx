import { formatDateTime } from '@/shared/lib/datetime';
import type { WmsOperationsSectionState } from '../api/operations-state';
import type { CellStatus } from '../api/overview-state';
import type { Setting } from '../api/types';
import { cellPlaceholder } from './wms-overview-cell';

/**
 * wms **운영설정**(operations settings) section — TASK-PC-FE-224, the
 * console's first surface for the read-model's `GET /settings` (§ 5.1) +
 * `GET /operations/projection-status` (§ 6.2, already-exported-but-zero-
 * consumer `getProjectionStatus()`). Server-rendered (no client
 * interactivity — read-only, no filters/pagination/mutation, mirrors
 * `WmsRecentAdjustments`), fed by `getWmsOperationsState()`.
 *
 * TWO INDEPENDENT sections (task § Scope item 4): 운영 설정 (예약 TTL·저재고
 * 기본 임계치, key/value/description) + 프로젝션 상태 (read-model lag /
 * last-processed per topic). Each renders its OWN forbidden/degraded/empty
 * branch off its own `CellStatus` — one section degrading never blanks the
 * other (task Failure Scenarios).
 *
 * READ-ONLY (Out of Scope): settings write (config change) + operator RBAC
 * (users/roles/assignments) — `WMS_ADMIN` scope + console write-admin range,
 * out of this task.
 */
export interface WmsOperationsScreenProps {
  state: WmsOperationsSectionState;
}

/**
 * The operational settings surfaced on this screen (task Goal — the two
 * settings the 개요 저재고/재고 배지 derive from). A fixed, ordered
 * key→label catalog: the console never invents a settings key the producer
 * doesn't document (§ 5.1); an absent key simply produces no row (task Edge
 * Case), never a forced-blank placeholder row.
 */
const KNOWN_SETTINGS: { key: string; label: string }[] = [
  {
    key: 'inventory.reservation.ttl_hours',
    label: '예약 TTL (시간)',
  },
  {
    key: 'inventory.low_stock.default_threshold_qty',
    label: '저재고 기본 임계치 (수량)',
  },
];

export function WmsOperationsScreen({ state }: WmsOperationsScreenProps) {
  return (
    <section aria-labelledby="wms-operations-heading">
      <h1 id="wms-operations-heading" className="mb-2 text-2xl font-semibold">
        WMS 운영설정
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        예약 TTL · 저재고 임계치 등 운영 파라미터와 read-model 프로젝션 처리
        상태를 조회합니다 (읽기 전용).
      </p>

      <SettingsSection
        status={state.settingsStatus}
        settings={state.settings}
      />
      <ProjectionSection
        status={state.projectionStatus}
        projections={state.projection?.projections ?? null}
        worstLagSeconds={state.projection?.worstLagSeconds ?? null}
      />
    </section>
  );
}

function formatSettingValue(value: unknown): string {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function SettingsSection({
  status,
  settings,
}: {
  status: CellStatus;
  settings: Setting[] | null;
}) {
  const rows: { key: string; label: string; setting: Setting }[] =
    status === 'ok' && settings
      ? KNOWN_SETTINGS.flatMap((known) => {
          const setting = settings.find((s) => s.key === known.key);
          return setting ? [{ ...known, setting }] : [];
        })
      : [];

  return (
    <div className="mb-8">
      <h2 className="mb-3 text-lg font-medium text-foreground">운영 설정</h2>

      {status === 'forbidden' ? (
        <div
          role="status"
          data-testid="wms-operations-settings-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {cellPlaceholder('forbidden')}
        </div>
      ) : status === 'degraded' ? (
        <div
          role="status"
          data-testid="wms-operations-settings-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 운영 설정을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="wms-operations-settings-empty"
        >
          표시할 운영 설정이 없습니다.
        </p>
      ) : (
        <table className="data-table" data-testid="wms-operations-settings-table">
          <caption className="sr-only">운영 설정 목록</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                설정
              </th>
              <th scope="col" className="p-2">
                값
              </th>
              <th scope="col" className="p-2">
                설명
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <tr
                key={row.key}
                data-testid={`wms-operations-settings-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{row.label}</td>
                <td
                  className="p-2"
                  data-testid={`wms-operations-settings-value-${i}`}
                >
                  {formatSettingValue(row.setting.valueJson)}
                </td>
                <td className="p-2 text-muted-foreground">
                  {row.setting.description ?? '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function ProjectionSection({
  status,
  projections,
  worstLagSeconds,
}: {
  status: CellStatus;
  projections: { topic: string; lagSeconds?: number; lastProjectedAt?: string | null }[] | null;
  worstLagSeconds: number | null;
}) {
  const rows = status === 'ok' && projections ? projections : [];

  return (
    <div>
      <h2 className="mb-3 text-lg font-medium text-foreground">
        프로젝션 상태
      </h2>

      {status === 'forbidden' ? (
        <div
          role="status"
          data-testid="wms-operations-projection-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {cellPlaceholder('forbidden')}
        </div>
      ) : status === 'degraded' ? (
        <div
          role="status"
          data-testid="wms-operations-projection-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 프로젝션 상태를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="wms-operations-projection-empty"
        >
          표시할 프로젝션 상태가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-2 data-table"
            data-testid="wms-operations-projection-table"
          >
            <caption className="sr-only">프로젝션 상태 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  토픽
                </th>
                <th scope="col" className="p-2">
                  Lag (초)
                </th>
                <th scope="col" className="p-2">
                  최근 처리 시각
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((p, i) => (
                <tr
                  key={p.topic}
                  data-testid={`wms-operations-projection-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{p.topic}</td>
                  <td
                    className="p-2"
                    data-testid={`wms-operations-projection-lag-${i}`}
                  >
                    {typeof p.lagSeconds === 'number'
                      ? p.lagSeconds.toLocaleString()
                      : '—'}
                  </td>
                  <td className="p-2">
                    {p.lastProjectedAt ? formatDateTime(p.lastProjectedAt) : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {typeof worstLagSeconds === 'number' && (
            <p
              className="text-sm text-muted-foreground"
              data-testid="wms-operations-worst-lag"
            >
              최대 지연: {worstLagSeconds.toLocaleString()}초
            </p>
          )}
        </>
      )}
    </div>
  );
}
