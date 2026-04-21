---
name: component-library
description: Reusable component primitives, a11y, design tokens
category: frontend
---

# Skill: Component Library

Patterns for building reusable UI components in Next.js apps.

Prerequisite: read `specs/platform/coding-rules.md` (TypeScript Rules) before using this skill. See `frontend/architecture/feature-sliced-design/SKILL.md` or `frontend/architecture/layered-by-feature/SKILL.md` for placement rules.

---

## Layer Placement

| Component Type | Location | Sharing |
|---|---|---|
| Design system primitives (Button, Input, Modal) | `shared/ui/` | App-wide |
| Composite UI (DataTable, Pagination) | `shared/ui/` | App-wide |
| Feature components (OrderCard, ProductForm) | `features/<name>/components/` | Feature only |
| Page layouts | `app/<route>/layout.tsx` | Route only |

Promote a component from `features/` to `shared/ui/` only after **3 distinct features** use it.

---

## Primitive Pattern (shadcn/ui style)

```tsx
// shared/ui/button.tsx
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/shared/lib/cn';

const buttonVariants = cva(
  'inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:ring-2',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground hover:bg-primary/90',
        outline: 'border border-input bg-background hover:bg-accent',
        ghost: 'hover:bg-accent',
      },
      size: { sm: 'h-8 px-3', md: 'h-10 px-4', lg: 'h-12 px-6' },
    },
    defaultVariants: { variant: 'default', size: 'md' },
  }
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}
```

Rules:
- Forward `className` for override
- Use `cva` for variant typing
- Forward all native HTML props via `...props`
- Use `forwardRef` only if the consumer needs ref access

---

## Accessibility (a11y)

| Element | Requirement |
|---|---|
| Interactive (button, link, input) | Reachable by keyboard, visible focus ring |
| Image | `alt` text required (use `''` only for decorative) |
| Form input | `<label htmlFor>` linked to input `id` |
| Modal/dialog | Trap focus, return focus on close, ESC to dismiss |
| Icon-only button | `aria-label` required |
| Color | Contrast ratio >= 4.5:1 for text |

Test with `@testing-library/jest-dom` matcher `toBeAccessible()` or `axe-core` in component tests.

```tsx
import { render } from '@testing-library/react';
import { axe } from 'jest-axe';

test('Button has no a11y violations', async () => {
  const { container } = render(<Button>Save</Button>);
  expect(await axe(container)).toHaveNoViolations();
});
```

---

## Composition over Configuration

Prefer composable subcomponents over many props:

```tsx
// Good
<Card>
  <Card.Header>
    <Card.Title>Order #123</Card.Title>
  </Card.Header>
  <Card.Body>...</Card.Body>
  <Card.Footer><Button>Confirm</Button></Card.Footer>
</Card>

// Avoid
<Card title="Order #123" body="..." footerButton="Confirm" />
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Hard-coded colors | Use design tokens from `shared/styles/tokens.css` |
| Component knows about feature data | Pass via props; keep components dumb |
| Promoting too early to `shared/ui/` | Wait for 3 callers |
| Skipping `aria-label` on icon button | Required, even if hidden visually |
| Inline styles for variants | Use `cva` |
| Missing forwardRef on focusable primitives | Add when used inside forms |

---

## Verification Checklist

- [ ] Lives in correct layer (`shared/ui/` or `features/`)
- [ ] Forwards `className` and HTML props
- [ ] a11y test passes (axe or testing-library)
- [ ] Storybook story (if Storybook is enabled)
- [ ] No hard-coded colors or spacing
