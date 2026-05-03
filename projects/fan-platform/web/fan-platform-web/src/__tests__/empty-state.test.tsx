import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EmptyState } from '@/shared/ui/EmptyState';
import { ErrorState } from '@/shared/ui/ErrorState';
import { LoadingState } from '@/shared/ui/LoadingState';

describe('shared UI states', () => {
  it('EmptyState renders title and description', () => {
    render(<EmptyState title="비어있어요" description="포스트가 없습니다" />);
    expect(screen.getByText('비어있어요')).toBeInTheDocument();
    expect(screen.getByText('포스트가 없습니다')).toBeInTheDocument();
  });

  it('ErrorState declares role=alert for screen readers', () => {
    render(<ErrorState description="실패" />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('LoadingState declares role=status for screen readers', () => {
    render(<LoadingState />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});
