const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

export function toKstDateKey(iso: string): string {
  const kst = new Date(new Date(iso).getTime() + KST_OFFSET_MS);
  return kst.toISOString().slice(0, 10);
}

export function todayKstKey(now: Date = new Date()): string {
  const kst = new Date(now.getTime() + KST_OFFSET_MS);
  return kst.toISOString().slice(0, 10);
}

export function last7DaysKstKeys(now: Date = new Date()): string[] {
  const todayKst = new Date(now.getTime() + KST_OFFSET_MS);
  const keys: string[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(todayKst);
    d.setUTCDate(d.getUTCDate() - i);
    keys.push(d.toISOString().slice(0, 10));
  }
  return keys;
}
