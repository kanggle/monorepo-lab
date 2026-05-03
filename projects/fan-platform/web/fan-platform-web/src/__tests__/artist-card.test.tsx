import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ArtistCard } from '@/features/artist/ui/ArtistCard';
import type { Artist } from '@/entities/artist';

const artist: Artist = {
  id: 'a1',
  tenantId: 'fan-platform',
  artistType: 'SOLO',
  status: 'PUBLISHED',
  stageName: 'STAR-A',
  realName: null,
  debutDate: null,
  agency: 'Galaxy Ent.',
  bio: null,
  profileImageRef: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  publishedAt: '2026-01-01T00:00:00Z',
  archivedAt: null,
};

describe('ArtistCard', () => {
  it('renders the stage name and agency', () => {
    render(<ArtistCard artist={artist} />);
    expect(screen.getByText('STAR-A')).toBeInTheDocument();
    expect(screen.getByText(/Galaxy Ent\./)).toBeInTheDocument();
  });

  it('links to the artist detail page', () => {
    render(<ArtistCard artist={artist} />);
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/artists/a1');
  });

  it('renders SOLO label for solo artists', () => {
    render(<ArtistCard artist={artist} />);
    expect(screen.getByText(/솔로/)).toBeInTheDocument();
  });

  it('renders 그룹 멤버 label for GROUP_MEMBER artists', () => {
    render(<ArtistCard artist={{ ...artist, artistType: 'GROUP_MEMBER' }} />);
    expect(screen.getByText(/그룹 멤버/)).toBeInTheDocument();
  });
});
