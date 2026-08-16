import { Suspense } from 'react';
import { ArtistProfile } from '@/features/artist';
import { LoadingState } from '@/shared/ui/LoadingState';

export default async function ArtistProfilePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <Suspense fallback={<LoadingState label="아티스트 프로필을 불러오는 중..." />}>
      <ArtistProfile id={id} />
    </Suspense>
  );
}
