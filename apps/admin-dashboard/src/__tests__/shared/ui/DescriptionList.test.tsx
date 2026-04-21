import { render, screen } from '@testing-library/react';
import { DescriptionList } from '@/shared/ui/DescriptionList';

describe('DescriptionList', () => {
  const items = [
    { label: '이름', value: '홍길동' },
    { label: '이메일', value: 'hong@example.com' },
    { label: '상태', value: '활성' },
  ];

  it('모든 label과 value를 렌더링한다', () => {
    render(<DescriptionList items={items} />);

    expect(screen.getByText('이름')).toBeInTheDocument();
    expect(screen.getByText('홍길동')).toBeInTheDocument();
    expect(screen.getByText('이메일')).toBeInTheDocument();
    expect(screen.getByText('hong@example.com')).toBeInTheDocument();
    expect(screen.getByText('상태')).toBeInTheDocument();
    expect(screen.getByText('활성')).toBeInTheDocument();
  });

  it('빈 배열이면 아무것도 렌더링하지 않는다', () => {
    const { container } = render(<DescriptionList items={[]} />);
    const dl = container.querySelector('dl');
    expect(dl).toBeInTheDocument();
    expect(dl?.children).toHaveLength(0);
  });

  it('value에 ReactNode를 렌더링할 수 있다', () => {
    const richItems = [
      { label: '상태', value: <span data-testid="badge">활성</span> },
    ];
    render(<DescriptionList items={richItems} />);
    expect(screen.getByTestId('badge')).toBeInTheDocument();
  });
});
