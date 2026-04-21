export function LoadingSpinner() {
  return (
    <div
      role="status"
      aria-label="로딩 중"
      style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}
    >
      <span>로딩 중...</span>
    </div>
  );
}
