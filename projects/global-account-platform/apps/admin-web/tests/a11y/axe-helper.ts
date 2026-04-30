import axe from 'axe-core';

/**
 * Runs axe-core against a DOM subtree and returns the list of violations.
 * Tests assert `violations.length === 0` so a regression surfaces with the
 * violation details attached to the test output.
 *
 * Rules tuned for jsdom: some color-contrast checks require a real layout
 * engine, so we disable them here — visual contrast is guarded by Storybook /
 * Playwright in the real browser.
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
