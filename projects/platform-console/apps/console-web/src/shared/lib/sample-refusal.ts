/**
 * Client-side signal: "a sample visitor just tried to write" (ADR-MONO-074 R1ⓐ).
 *
 * `shared/api/client.ts` — the ONLY backend entry point for client components —
 * publishes here whenever a same-origin call is refused with `SAMPLE_READ_ONLY`.
 * The sample banner in the `(console)` layout subscribes and shows the refusal
 * copy.
 *
 * 🔴 Why a shell-level signal and not just the inline error renderers: the
 *    write-error renderers are not uniform (TASK-PC-FE-282 AC-0 counts them) —
 *    most look the code up via `messageForCode`, some print `err.message`, and
 *    some print a fixed string that never reads the error at all. The first two
 *    already show the copy (the mapping has the code; the client rewrites the
 *    message from the same mapping). The third cannot, so the shell says it
 *    once, for every write, from one place.
 *
 * Dependency-free on purpose: client-safe, no React, no network.
 */

type Listener = (code: string) => void;

const listeners = new Set<Listener>();

export function publishSampleRefusal(code: string): void {
  for (const listener of listeners) listener(code);
}

export function subscribeSampleRefusal(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
