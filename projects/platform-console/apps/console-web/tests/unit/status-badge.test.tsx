import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusBadge, statusToneClass } from '@/shared/ui/StatusBadge';

/**
 * TASK-PC-FE-158 — the shared status pill. The single home for the console's
 * status-badge markup + colour palette; every domain maps its own status enum
 * to a semantic tone and renders through here (ecommerce users/sellers, wms
 * outbound, …). This test locks the palette so the domains stay consistent.
 */

describe('StatusBadge (shared status pill)', () => {
  it('renders the raw label text and the tone colour', () => {
    render(<StatusBadge tone="success">ACTIVE</StatusBadge>);
    const el = screen.getByText('ACTIVE');
    expect(el).toHaveClass('bg-green-100');
    // The pill markup is centralised (no per-feature copy).
    expect(el.className).toContain('inline-block rounded');
  });

  it('defaults to the neutral tone — safe for an unknown status', () => {
    render(<StatusBadge>UNKNOWN</StatusBadge>);
    expect(screen.getByText('UNKNOWN')).toHaveClass('bg-muted');
  });

  it('appends an optional extra className after the tone classes', () => {
    render(
      <StatusBadge tone="warning" className="shrink-0">
        PENDING
      </StatusBadge>,
    );
    const el = screen.getByText('PENDING');
    expect(el).toHaveClass('shrink-0');
    expect(el.className).toContain('amber');
  });

  it('maps every semantic tone to a distinct palette class', () => {
    expect(statusToneClass('success')).toContain('green');
    expect(statusToneClass('progress')).toContain('blue');
    expect(statusToneClass('warning')).toContain('amber');
    expect(statusToneClass('danger')).toContain('red');
    expect(statusToneClass('neutral')).toContain('muted');
    // Dark-mode variants are baked in centrally (previously only erp had them).
    expect(statusToneClass('success')).toContain('dark:');
  });
});
