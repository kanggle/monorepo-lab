interface OAuthButtonProps {
  provider: string;
  label: string;
  icon: React.ReactNode;
  style?: React.CSSProperties;
}

export function OAuthButton({ provider, label, icon, style }: OAuthButtonProps) {
  return (
    <button
      type="button"
      onClick={() => {
        const callbackUrl = `${window.location.origin}/oauth/callback`;
        window.location.href = `http://localhost:8080/api/auth/oauth/${provider}?callbackUrl=${encodeURIComponent(callbackUrl)}`;
      }}
      className="btn btn-lg"
      style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2)', ...style }}
    >
      {icon}
      {label}
    </button>
  );
}
