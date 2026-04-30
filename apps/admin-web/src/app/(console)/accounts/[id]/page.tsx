import { AccountDetail } from '@/features/accounts/components/AccountDetail';
import { requireOperatorSession } from '../../session';

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function AccountDetailPage({ params }: PageProps) {
  const { id } = await params;
  const session = await requireOperatorSession(`/accounts/${id}`);
  return <AccountDetail accountId={id} roles={session.roles} />;
}
