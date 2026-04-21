import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { HeroBanner } from '@/widgets/hero/HeroBanner';

describe('HeroBanner', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('초기에는 첫 번째 슬라이드를 표시한다', () => {
    render(<HeroBanner />);

    expect(
      screen.getByRole('heading', { name: '당신에게 딱 맞는 상품을 찾아보세요' }),
    ).toBeInTheDocument();
  });

  it('다음 슬라이드 버튼 클릭 시 두 번째 슬라이드로 이동한다', () => {
    render(<HeroBanner />);

    fireEvent.click(screen.getByRole('button', { name: '다음 슬라이드' }));

    expect(
      screen.getByRole('heading', { name: '신상품이 도착했습니다' }),
    ).toBeInTheDocument();
  });

  it('이전 슬라이드 버튼 클릭 시 마지막 슬라이드로 순환한다', () => {
    render(<HeroBanner />);

    fireEvent.click(screen.getByRole('button', { name: '이전 슬라이드' }));

    expect(
      screen.getByRole('heading', { name: '특별 할인 진행 중' }),
    ).toBeInTheDocument();
  });

  it('도트 버튼 클릭으로 특정 슬라이드로 이동할 수 있다', () => {
    render(<HeroBanner />);

    fireEvent.click(screen.getByRole('button', { name: '슬라이드 3' }));

    expect(
      screen.getByRole('heading', { name: '특별 할인 진행 중' }),
    ).toBeInTheDocument();
  });

  it('5초가 지나면 자동으로 다음 슬라이드로 넘어간다', () => {
    render(<HeroBanner />);

    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(
      screen.getByRole('heading', { name: '신상품이 도착했습니다' }),
    ).toBeInTheDocument();
  });

  it('자동 재생이 마지막 슬라이드에서 첫 번째로 순환한다', () => {
    render(<HeroBanner />);

    act(() => {
      vi.advanceTimersByTime(5000 * 3);
    });

    expect(
      screen.getByRole('heading', { name: '당신에게 딱 맞는 상품을 찾아보세요' }),
    ).toBeInTheDocument();
  });
});
