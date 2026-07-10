import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CeilingEditor } from '@/features/org-hierarchy/components/CeilingEditor';
import { messageForCode } from '@/shared/api/errors';
import type { OrgNode, Ceiling } from '@/features/org-hierarchy/api/types';
import { runAxe } from '../../../a11y/axe-helper';

/**
 * `CeilingEditor` — the deny-only ceiling semantics (TASK-PC-FE-237 / ADR-047).
 * A ceiling is a CAP not a grant; UNBOUNDED and BOUNDED([]) are opposite
 * states; the server is the final authority on the subset rule.
 */

function makeNode(ceiling: Ceiling): OrgNode {
  return {
    orgNodeId: 'n1',
    parentId: 'p1',
    name: '테스트 회사',
    depth: 2,
    ceiling,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

describe('CeilingEditor', () => {
  it('(a) copy says 상한 and never "부여"', () => {
    const { container } = render(
      <CeilingEditor
        node={makeNode({ mode: 'UNBOUNDED' })}
        parentEffective={{ mode: 'UNBOUNDED' }}
        onSubmit={vi.fn()}
        pending={false}
        errorMessage={null}
      />,
    );
    expect(container.textContent).toContain('상한');
    expect(container.textContent).not.toContain('부여');
  });

  it('(b) BOUNDED with zero domains renders the lock-out warning', async () => {
    const user = userEvent.setup();
    render(
      <CeilingEditor
        node={makeNode({ mode: 'UNBOUNDED' })}
        parentEffective={{ mode: 'UNBOUNDED' }}
        onSubmit={vi.fn()}
        pending={false}
        errorMessage={null}
      />,
    );
    expect(screen.queryByTestId('ceiling-lockout-warning')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('ceiling-mode-bounded'));
    expect(screen.getByTestId('ceiling-lockout-warning')).toHaveTextContent(
      '어떤 도메인도 사용할 수 없게 됩니다',
    );
  });

  it('(c) UNBOUNDED and BOUNDED([]) produce different submitted payloads', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();

    // UNBOUNDED submit
    const { unmount } = render(
      <CeilingEditor
        node={makeNode({ mode: 'UNBOUNDED' })}
        parentEffective={{ mode: 'UNBOUNDED' }}
        onSubmit={onSubmit}
        pending={false}
        errorMessage={null}
      />,
    );
    await user.click(screen.getByTestId('ceiling-save'));
    await user.type(screen.getByTestId('org-reason-input'), '상한 없음으로');
    await user.click(screen.getByTestId('org-reason-submit'));
    unmount();

    // BOUNDED([]) submit
    render(
      <CeilingEditor
        node={makeNode({ mode: 'UNBOUNDED' })}
        parentEffective={{ mode: 'UNBOUNDED' }}
        onSubmit={onSubmit}
        pending={false}
        errorMessage={null}
      />,
    );
    await user.click(screen.getByTestId('ceiling-mode-bounded'));
    await user.click(screen.getByTestId('ceiling-save'));
    await user.type(screen.getByTestId('org-reason-input'), '전면 차단');
    await user.click(screen.getByTestId('org-reason-submit'));

    expect(onSubmit).toHaveBeenCalledTimes(2);
    expect(onSubmit.mock.calls[0][0]).toEqual({ mode: 'UNBOUNDED' });
    expect(onSubmit.mock.calls[1][0]).toEqual({ mode: 'BOUNDED', domains: [] });
  });

  it('(d) a ceiling wider than the parent blocks Save', async () => {
    const user = userEvent.setup();
    render(
      <CeilingEditor
        node={makeNode({ mode: 'BOUNDED', domains: ['wms'] })}
        parentEffective={{ mode: 'BOUNDED', domains: ['wms'] }}
        onSubmit={vi.fn()}
        pending={false}
        errorMessage={null}
      />,
    );
    // Widen to UNBOUNDED — wider than the bounded parent.
    await user.click(screen.getByTestId('ceiling-mode-unbounded'));
    expect(screen.getByTestId('ceiling-subset-block')).toBeInTheDocument();
    expect(screen.getByTestId('ceiling-save')).toBeDisabled();
  });

  it('(e) surfaces a server 422 ORG_NODE_CEILING_NOT_SUBSET even when client validation passed', () => {
    render(
      <CeilingEditor
        node={makeNode({ mode: 'BOUNDED', domains: ['wms'] })}
        parentEffective={{ mode: 'UNBOUNDED' }}
        onSubmit={vi.fn()}
        pending={false}
        errorMessage={messageForCode('ORG_NODE_CEILING_NOT_SUBSET')}
      />,
    );
    // Client validation passes (parent UNBOUNDED), but the server's verdict is
    // still shown verbatim.
    expect(screen.queryByTestId('ceiling-subset-block')).not.toBeInTheDocument();
    expect(screen.getByTestId('ceiling-server-error')).toHaveTextContent(
      '상한은 상위 노드의 상한보다 넓을 수 없습니다',
    );
  });

  it('(f) is axe-clean', async () => {
    const { container } = render(
      <CeilingEditor
        node={makeNode({ mode: 'BOUNDED', domains: ['wms'] })}
        parentEffective={{ mode: 'UNBOUNDED' }}
        onSubmit={vi.fn()}
        pending={false}
        errorMessage={null}
      />,
    );
    expect(await runAxe(container)).toEqual([]);
  });
});
