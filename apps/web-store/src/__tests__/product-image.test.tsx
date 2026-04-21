import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ProductImage } from '@/entities/product/ui/ProductImage';

vi.mock('next/image', () => ({
  default: ({ src, alt, ...props }: { src: string; alt: string; [key: string]: unknown }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={alt} {...props} />
  ),
}));

describe('ProductImage', () => {
  it('이미지를 렌더링한다', () => {
    render(<ProductImage images={['/test.jpg']} alt="테스트 이미지" />);

    const img = screen.getByRole('img');
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute('src', '/test.jpg');
  });

  it('이미지 로드 실패 시 fallback 메시지를 표시한다', () => {
    render(<ProductImage images={['/broken.jpg']} alt="깨진 이미지" />);

    const img = screen.getByRole('img');
    fireEvent.error(img);

    expect(screen.getByText('이미지를 불러올 수 없습니다')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('여러 이미지가 있으면 슬라이드 버튼과 dot이 표시된다', () => {
    render(<ProductImage images={['/a.jpg', '/b.jpg', '/c.jpg']} alt="슬라이드" />);

    expect(screen.getByLabelText('이전 이미지')).toBeInTheDocument();
    expect(screen.getByLabelText('다음 이미지')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /이미지 \d/ })).toHaveLength(3);
  });

  it('다음 버튼 클릭 시 이미지가 전환된다', () => {
    render(<ProductImage images={['/a.jpg', '/b.jpg']} alt="슬라이드" />);

    const img = screen.getByRole('img');
    expect(img).toHaveAttribute('src', '/a.jpg');

    fireEvent.click(screen.getByLabelText('다음 이미지'));

    const nextImg = screen.getByRole('img');
    expect(nextImg).toHaveAttribute('src', '/b.jpg');
  });

  it('이미지가 하나면 슬라이드 버튼이 없다', () => {
    render(<ProductImage images={['/single.jpg']} alt="단일" />);

    expect(screen.queryByLabelText('이전 이미지')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('다음 이미지')).not.toBeInTheDocument();
  });
});
