import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OrgNodeTree } from '@/features/org-hierarchy/components/OrgNodeTree';
import type { OrgNode } from '@/features/org-hierarchy/api/types';
import { runAxe } from '../../../a11y/axe-helper';

/** `OrgNodeTree` — accessible nested tree (TASK-PC-FE-237). */

function chain(depth: number): OrgNode[] {
  const nodes: OrgNode[] = [];
  for (let i = 1; i <= depth; i++) {
    nodes.push({
      orgNodeId: `n${i}`,
      parentId: i === 1 ? null : `n${i - 1}`,
      name: `레벨 ${i}`,
      depth: i,
      ceiling: { mode: 'UNBOUNDED' },
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
  }
  return nodes;
}

describe('OrgNodeTree', () => {
  it('renders a depth-5 nesting with tree / treeitem roles', () => {
    render(
      <OrgNodeTree nodes={chain(5)} selectedId="n3" onSelect={vi.fn()} />,
    );
    expect(screen.getByRole('tree')).toBeInTheDocument();
    expect(screen.getAllByRole('treeitem')).toHaveLength(5);
    // The deepest node is rendered (indentation does not drop it).
    expect(screen.getByTestId('org-node-item-n5')).toBeInTheDocument();
    // The selected node carries aria-selected.
    expect(screen.getByTestId('org-node-item-n3')).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('is axe-clean', async () => {
    const { container } = render(
      <OrgNodeTree nodes={chain(5)} selectedId="n1" onSelect={vi.fn()} />,
    );
    expect(await runAxe(container)).toEqual([]);
  });
});
