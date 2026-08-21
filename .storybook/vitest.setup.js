import * as a11yAddonAnnotations from '@storybook/addon-a11y/preview';
import { setProjectAnnotations } from '@storybook/web-components-vite';
import * as projectAnnotations from './preview.js';

setProjectAnnotations([a11yAddonAnnotations, projectAnnotations]);
