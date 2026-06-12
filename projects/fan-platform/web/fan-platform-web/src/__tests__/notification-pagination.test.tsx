import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { NotificationPagination } from '@/features/notification/ui/NotificationPagination';
import {
  computeTotalPages,
  buildNotificationsHref,
} from '@/features/notification/ui/paging';

describe('computeTotalPages', () => {
  it('treats an empty inbox as one page', () => {
    expect(computeTotalPages(0, 20)).toBe(1);
  });

  it('does not add a page on an exact multiple', () => {
    expect(computeTotalPages(20, 20)).toBe(1);
    expect(computeTotalPages(40, 20)).toBe(2);
  });

  it('rounds a partial last page up', () => {
    expect(computeTotalPages(21, 20)).toBe(2);
    expect(computeTotalPages(45, 20)).toBe(3);
  });

  it('guards a non-positive size (no divide-by-zero)', () => {
    expect(computeTotalPages(100, 0)).toBe(1);
  });
});

describe('buildNotificationsHref', () => {
  it('keeps the base path for all-status page 0', () => {
    expect(buildNotificationsHref(undefined, 0)).toBe('/notifications');
  });

  it('adds page but omits page=0', () => {
    expect(buildNotificationsHref(undefined, 2)).toBe('/notifications?page=2');
  });

  it('preserves the status filter', () => {
    expect(buildNotificationsHref('UNREAD', 0)).toBe('/notifications?status=UNREAD');
    expect(buildNotificationsHref('READ', 3)).toBe('/notifications?status=READ&page=3');
  });
});

describe('NotificationPagination', () => {
  it('renders nothing for a single page', () => {
    const { container } = render(<NotificationPagination page={0} totalPages={1} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('disables 이전 on the first page and links 다음', () => {
    render(<NotificationPagination page={0} totalPages={3} />);
    expect(screen.getByTestId('page-indicator')).toHaveTextContent('1 / 3');
    expect(screen.getByText('이전').closest('a')).toBeNull();
    expect(screen.getByText('이전')).toHaveAttribute('aria-disabled', 'true');
    expect(screen.getByText('다음').closest('a')).toHaveAttribute(
      'href',
      '/notifications?page=1',
    );
  });

  it('disables 다음 on the last page and links 이전', () => {
    render(<NotificationPagination page={2} totalPages={3} />);
    expect(screen.getByTestId('page-indicator')).toHaveTextContent('3 / 3');
    expect(screen.getByText('다음').closest('a')).toBeNull();
    expect(screen.getByText('이전').closest('a')).toHaveAttribute(
      'href',
      '/notifications?page=1',
    );
  });

  it('preserves the status filter in page links', () => {
    render(<NotificationPagination status="UNREAD" page={0} totalPages={2} />);
    expect(screen.getByText('다음').closest('a')).toHaveAttribute(
      'href',
      '/notifications?status=UNREAD&page=1',
    );
  });
});
