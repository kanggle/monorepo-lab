/** @type {import("eslint").Linter.Config} */
module.exports = {
  root: true,
  extends: ['@repo/eslint-config/next'],
  overrides: [
    {
      files: ['src/__tests__/**/*'],
      rules: {
        'react/display-name': 'off',
      },
    },
  ],
};
