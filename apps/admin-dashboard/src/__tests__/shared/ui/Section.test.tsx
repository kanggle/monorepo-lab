import { render, screen } from '@testing-library/react';
import { Section } from '@/shared/ui/Section';

describe('Section', () => {
  it('제목을 렌더링한다', () => {
    render(<Section title="기본 정보">내용</Section>);
    expect(screen.getByRole('heading', { name: '기본 정보' })).toBeInTheDocument();
  });

  it('children을 렌더링한다', () => {
    render(<Section title="테스트"><p>자식 컨텐츠</p></Section>);
    expect(screen.getByText('자식 컨텐츠')).toBeInTheDocument();
  });

  it('section 요소로 렌더링된다', () => {
    const { container } = render(<Section title="테스트">내용</Section>);
    expect(container.querySelector('section')).toBeInTheDocument();
  });
});
