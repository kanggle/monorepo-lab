import { redirect } from 'next/navigation';

/**
 * `/fan` — the registry `baseRoute` of the `fan` product (TASK-MONO-751). The section has no
 * overview of its own; the directory starts at the agencies list.
 */
export default function FanIndexPage() {
  redirect('/fan/agencies');
}
