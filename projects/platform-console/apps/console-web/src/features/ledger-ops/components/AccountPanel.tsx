import { messageForCode } from '@/shared/api/errors';
import { AccountLookup } from './AccountLookup';
import { AccountDetail } from './AccountDetail';
import type { useLedgerOpsState } from './use-ledger-ops-state';

type S = ReturnType<typeof useLedgerOpsState>;

/**
 * Account tab panel content (TASK-PC-FE-134 code split, TASK-PC-FE-074
 * surface). Extracted verbatim from `LedgerOpsScreen`; account-code-driven
 * lookup + balance/entries detail load only when the tab is first activated
 * (or seeded via `?accountCode=`). Pure view — state owned by the parent hook.
 */
export function AccountPanel({
  selectedAccountCode,
  setSelectedAccountCode,
  accountNotFound,
  accountBalance,
  accountEntries,
  handleSelectEntry,
}: Pick<
  S,
  | 'selectedAccountCode'
  | 'setSelectedAccountCode'
  | 'accountNotFound'
  | 'accountBalance'
  | 'accountEntries'
  | 'handleSelectEntry'
>) {
  return (
    <>
      <AccountLookup
        initialCode={selectedAccountCode ?? undefined}
        onSubmit={setSelectedAccountCode}
      />
      {!selectedAccountCode ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-account-none"
        >
          조회할 계정 코드를 입력하거나 시산표의 계정 코드를 클릭하세요.
        </p>
      ) : accountNotFound ? (
        <div
          role="status"
          data-testid="ledger-account-not-found"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('LEDGER_ACCOUNT_NOT_FOUND')}
        </div>
      ) : (
        <AccountDetail
          balance={accountBalance}
          entries={accountEntries}
          onSelectEntry={handleSelectEntry}
        />
      )}
    </>
  );
}
