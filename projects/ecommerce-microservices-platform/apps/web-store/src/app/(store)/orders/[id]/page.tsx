import { redirect } from 'next/navigation';

/**
 * Legacy standalone order-detail route. Canonical location is
 * `/my/orders/[id]`. Kept as a redirect so external bookmarks / old links
 * keep working; forwards the `[id]` param to the canonical path.
 */
export default async function LegacyOrderDetailRedirect({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  redirect(`/my/orders/${id}`);
}
