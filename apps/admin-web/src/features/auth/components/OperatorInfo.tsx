'use client';

import { useOperatorSession } from '../hooks/useOperatorSession';
import { Badge } from '@/shared/ui/badge';

export function OperatorInfo() {
  const { data, isLoading, isError } = useOperatorSession();

  if (isLoading) return <p>로딩 중...</p>;
  if (isError || !data) return <p role="alert" className="text-destructive">운영자 정보를 불러오지 못했습니다.</p>;

  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-2 gap-2 text-sm max-w-md">
        <dt className="text-muted-foreground">운영자 ID</dt>
        <dd className="font-mono text-xs">{data.operatorId}</dd>
        <dt className="text-muted-foreground">이메일</dt>
        <dd>{data.email}</dd>
        <dt className="text-muted-foreground">역할</dt>
        <dd className="flex flex-wrap gap-1">
          {data.roles.map((role) => (
            <Badge key={role}>{role}</Badge>
          ))}
        </dd>
      </dl>
    </div>
  );
}
