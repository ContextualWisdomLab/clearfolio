import '../src/main/resources/static/assets/viewer/viewer.css';

const preview = {
  tags: ['test'],
  parameters: {
    layout: 'fullscreen',
    a11y: {
      test: 'error',
      options: {
        runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'],
      },
    },
  },
};

export default preview;
