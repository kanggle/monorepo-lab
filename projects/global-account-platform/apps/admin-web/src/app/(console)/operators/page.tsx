import { OperatorInfo } from '@/features/auth/components/OperatorInfo';
import { OperatorManagement } from '@/features/operators/components/OperatorManagement';
import { requireOperatorSession } from '../session';

export const metadata = { title: '운영자 관리 — Admin Console' };

export default async function OperatorsPage() {
  const session = await requireOperatorSession('/operators');

  return (
    <section className="flex flex-col gap-8">
      <div className="flex flex-col gap-2">
        <h1 className="text-xl font-semibold">운영자 정보</h1>
        <OperatorInfo />
      </div>
      <OperatorManagement roles={session.roles} />
    </section>
  );
}
