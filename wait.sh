#!/usr/bin/env bash
for i in $(seq 1 60); do
  t=$(gh pr view 3613 --repo kanggle/monorepo-lab --json statusCheckRollup --jq '[.statusCheckRollup[]] | length')
  p=$(gh pr view 3613 --repo kanggle/monorepo-lab --json statusCheckRollup --jq '[.statusCheckRollup[] | select((.conclusion // null) == null)] | length')
  f=$(gh pr view 3613 --repo kanggle/monorepo-lab --json statusCheckRollup --jq '[.statusCheckRollup[] | select(.conclusion=="FAILURE" or .conclusion=="CANCELLED" or .conclusion=="TIMED_OUT")] | length')
  echo "$(date -u +%H:%M:%S) total=$t pending=$p failed=$f"
  if [ "$t" -ge 50 ] && [ "$p" = "0" ]; then echo DONE; exit 0; fi
  sleep 45
done
echo TIMEOUT; exit 1
