import axe from 'axe-core';

/**
 * Runs axe-core against a DOM subtree and returns the list of violations.
 * Tests assert `violations.length === 0` so a regression surfaces with the
 * violation details attached to the test output.
 *
 * Rules tuned for jsdom: color-contrast / region require a real layout engine,
 * so they are disabled here — visual contrast is guarded by Playwright in a
 * real browser (Lighthouse a11y >= 90, frontend-app.md § Accessibility).
 */
export async function runAxe(container: HTMLElement): Promise<axe.Result[]> {
  const results = await axe.run(container, {
    rules: {
      'color-contrast': { enabled: false },
      region: { enabled: false },
    },
  });
  return results.violations;
}
