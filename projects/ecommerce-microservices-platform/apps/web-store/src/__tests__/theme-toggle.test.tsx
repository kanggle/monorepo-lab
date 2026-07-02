import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeToggle } from '@/shared/ui/ThemeToggle';
import { THEME_STORAGE_KEY } from '@/shared/lib/theme';

describe('ThemeToggle', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme');
    localStorage.clear();
  });

  it('라이트 상태에서 다크 전환 라벨을 노출한다', () => {
    render(<ThemeToggle />);

    expect(screen.getByTestId('theme-toggle')).toHaveAttribute(
      'aria-label',
      '다크 모드로 전환',
    );
  });

  it('클릭하면 다크 테마를 적용하고 저장한다', async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    await user.click(screen.getByTestId('theme-toggle'));

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
    expect(screen.getByTestId('theme-toggle')).toHaveAttribute(
      'aria-label',
      '라이트 모드로 전환',
    );
  });

  it('다시 클릭하면 라이트 테마로 되돌린다', async () => {
    const user = userEvent.setup();
    render(<ThemeToggle />);

    const button = screen.getByTestId('theme-toggle');
    await user.click(button);
    await user.click(button);

    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('이미 적용된 다크 테마를 마운트 시 반영한다', () => {
    document.documentElement.setAttribute('data-theme', 'dark');

    render(<ThemeToggle />);

    // Mounted effect reads the applied theme → shows the "switch to light" label.
    expect(screen.getByTestId('theme-toggle')).toHaveAttribute(
      'aria-label',
      '라이트 모드로 전환',
    );
  });
});
