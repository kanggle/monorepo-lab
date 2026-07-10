'use client';

import { useState } from 'react';
import { cn } from '@/shared/lib/cn';
import type { OrgNode } from '../api/types';
import { buildTree, type OrgNodeTreeItem } from '../lib/tree';

/**
 * Accessible org-node tree (TASK-PC-FE-237). A nested `<ul>` with
 * `role="tree"` / `role="treeitem"` (children wrapped in `role="group"`),
 * `aria-expanded` on expandable items, and `aria-selected` on the selected
 * one. The expand/collapse control is a real `<button>` (keyboard-operable).
 * Indentation is depth-driven (`paddingLeft`) so it survives the max depth of
 * 5 without collapsing. A GROUP is not selectable in the token sense — only a
 * leaf tenant is (that lives in the switcher); here every node is selectable
 * for MANAGEMENT (its detail panel), which is a distinct concern.
 */
export interface OrgNodeTreeProps {
  nodes: OrgNode[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function OrgNodeTree({ nodes, selectedId, onSelect }: OrgNodeTreeProps) {
  const tree = buildTree(nodes);
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());

  function toggle(id: string) {
    setCollapsed((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function renderItem(item: OrgNodeTreeItem, level: number) {
    const hasChildren = item.children.length > 0;
    const isOpen = hasChildren && !collapsed.has(item.orgNodeId);
    const selected = item.orgNodeId === selectedId;
    return (
      <li
        key={item.orgNodeId}
        role="treeitem"
        aria-selected={selected}
        aria-expanded={hasChildren ? isOpen : undefined}
        data-testid={`org-node-item-${item.orgNodeId}`}
      >
        <div
          className="flex items-center gap-1 py-0.5"
          style={{ paddingLeft: level * 16 }}
        >
          {hasChildren ? (
            <button
              type="button"
              onClick={() => toggle(item.orgNodeId)}
              aria-label={isOpen ? '접기' : '펼치기'}
              data-testid={`org-node-toggle-${item.orgNodeId}`}
              className="flex h-5 w-5 shrink-0 items-center justify-center rounded text-muted-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {isOpen ? '▾' : '▸'}
            </button>
          ) : (
            <span className="inline-block h-5 w-5 shrink-0" aria-hidden="true" />
          )}
          <button
            type="button"
            onClick={() => onSelect(item.orgNodeId)}
            data-testid={`org-node-select-${item.orgNodeId}`}
            className={cn(
              'min-w-0 flex-1 truncate rounded px-2 py-1 text-left text-sm transition-colors',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              selected
                ? 'bg-accent font-medium text-foreground'
                : 'text-muted-foreground hover:bg-accent hover:text-foreground',
            )}
          >
            {item.name}
          </button>
        </div>
        {hasChildren && isOpen && (
          <ul role="group">
            {item.children.map((child) => renderItem(child, level + 1))}
          </ul>
        )}
      </li>
    );
  }

  if (tree.length === 0) {
    return (
      <p
        data-testid="org-tree-empty"
        className="rounded-md border border-border bg-muted px-3 py-4 text-sm text-muted-foreground"
      >
        조직 노드가 없습니다. 최상위(루트) 노드를 먼저 생성하세요.
      </p>
    );
  }

  return (
    <ul role="tree" aria-label="조직 계층 트리" data-testid="org-tree">
      {tree.map((root) => renderItem(root, 0))}
    </ul>
  );
}
