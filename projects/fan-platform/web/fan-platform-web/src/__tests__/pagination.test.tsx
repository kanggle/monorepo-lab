import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Pagination } from '@/shared/ui/Pagination';

describe('Pagination', () => {
  it('renders nothing for a single page', () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} hrefFor={(p) => `?page=${p}`} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders prev / next links and current marker', () => {
    render(<Pagination page={1} totalPages={3} hrefFor={(p) => `?page=${p}`} />);
    expect(screen.getByText('이전').getAttribute('href')).toBe('?page=0');
    expect(screen.getByText('다음').getAttribute('href')).toBe('?page=2');
    expect(screen.getByText('2 / 3')).toBeInTheDocument();
  });

  it('disables prev on the first page', () => {
    render(<Pagination page={0} totalPages={3} hrefFor={(p) => `?page=${p}`} />);
    expect(screen.getByText('이전')).toHaveAttribute('aria-disabled', 'true');
  });

  it('disables next on the last page', () => {
    render(<Pagination page={2} totalPages={3} hrefFor={(p) => `?page=${p}`} />);
    expect(screen.getByText('다음')).toHaveAttribute('aria-disabled', 'true');
  });
});
