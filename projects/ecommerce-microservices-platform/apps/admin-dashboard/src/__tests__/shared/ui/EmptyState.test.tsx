import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { EmptyState } from '@repo/ui';

describe('EmptyState', () => {
  it('전달된 메시지를 표시한다', () => {
    render(<EmptyState message="데이터가 없습니다." />);
    expect(screen.getByText('데이터가 없습니다.')).toBeInTheDocument();
  });
});
