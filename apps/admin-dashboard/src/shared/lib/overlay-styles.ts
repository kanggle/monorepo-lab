export const overlayStyle = {
  position: 'fixed',
  inset: 0,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  backgroundColor: 'rgba(0, 0, 0, 0.4)',
  zIndex: 50,
} as const;

export const dialogStyle = {
  backgroundColor: '#fff',
  borderRadius: '16px',
  padding: '28px',
  maxWidth: '420px',
  width: '100%',
  boxShadow: '0 20px 60px rgba(0,0,0,0.15)',
} as const;
