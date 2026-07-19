import { redirect } from 'next/navigation';

/**
 * Legacy standalone order-history route. The canonical location is
 * `/my/orders` (inside the account section with its sidebar). Kept as a
 * permanent redirect so external bookmarks / old links keep working.
 */
export default function LegacyOrdersRedirect() {
  redirect('/my/orders');
}
