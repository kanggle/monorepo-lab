import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RoleGuard } from '@/shared/ui/RoleGuard';

describe('RoleGuard', () => {
  it('renders children when operator has a permitted role', () => {
    render(
      <RoleGuard roles={['SUPPORT_LOCK']} allow={['SUPER_ADMIN', 'SUPPORT_LOCK']}>
        <button>잠금</button>
      </RoleGuard>,
    );
    expect(screen.getByRole('button', { name: '잠금' })).toBeInTheDocument();
  });

  it('hides children for SUPPORT_READONLY when not in allow list', () => {
    render(
      <RoleGuard roles={['SUPPORT_READONLY']} allow={['SUPER_ADMIN', 'SUPPORT_LOCK']}>
        <button>잠금</button>
      </RoleGuard>,
    );
    expect(screen.queryByRole('button', { name: '잠금' })).not.toBeInTheDocument();
  });

  it('renders fallback when no role matches', () => {
    render(
      <RoleGuard roles={['SECURITY_ANALYST']} allow={['SUPER_ADMIN']} fallback={<span>권한 없음</span>}>
        <button>잠금</button>
      </RoleGuard>,
    );
    expect(screen.getByText('권한 없음')).toBeInTheDocument();
  });
});
