const config = {
  stories: ['../design/storybook/**/*.stories.js'],
  addons: ['@storybook/addon-a11y', '@storybook/addon-vitest'],
  framework: {
    name: '@storybook/web-components-vite',
    options: {},
  },
  core: {
    disableTelemetry: true,
  },
};

export default config;
