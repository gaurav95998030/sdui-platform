import type { SduiNode } from './types';

export interface PaletteEntry {
  type: string;
  label: string;
  group: 'Layout' | 'Content' | 'Interaction' | 'Collections' | 'Forms' | 'Feedback' | 'Custom';
  icon: string;
  /** True when the component accepts children, which makes it a valid drop target. */
  container: boolean;
  template: () => SduiNode;
}

/**
 * The palette the builder offers.
 *
 * It mirrors `DefaultComponents.createRegistry()` in the SDK. Anything a host
 * registers at runtime (like `rating` below) is added here so designers can place
 * it, while the device resolves it through the same registry — the dashboard
 * never needs to know how it renders.
 */
export const CATALOG: PaletteEntry[] = [
  {
    type: 'column',
    label: 'Column',
    group: 'Layout',
    icon: '▤',
    container: true,
    template: () => ({
      type: 'column',
      props: { spacing: 12 },
      modifier: { width: 'match_parent' },
      children: [],
    }),
  },
  {
    type: 'row',
    label: 'Row',
    group: 'Layout',
    icon: '▥',
    container: true,
    template: () => ({
      type: 'row',
      props: { spacing: 12, align: 'center' },
      modifier: { width: 'match_parent' },
      children: [],
    }),
  },
  {
    type: 'box',
    label: 'Box',
    group: 'Layout',
    icon: '▣',
    container: true,
    template: () => ({ type: 'box', props: { align: 'topStart' }, children: [] }),
  },
  {
    type: 'card',
    label: 'Card',
    group: 'Layout',
    icon: '▢',
    container: true,
    template: () => ({
      type: 'card',
      modifier: { width: 'match_parent', cornerRadius: 16, elevation: 0 },
      children: [
        {
          type: 'column',
          modifier: { padding: 16 },
          props: { spacing: 6 },
          children: [{ type: 'text', props: { text: 'Card title' }, style: { typography: 'titleMedium' } }],
        },
      ],
    }),
  },
  {
    type: 'text',
    label: 'Text',
    group: 'Content',
    icon: 'T',
    container: false,
    template: () => ({ type: 'text', props: { text: 'New text' }, style: { typography: 'bodyLarge' } }),
  },
  {
    type: 'image',
    label: 'Image',
    group: 'Content',
    icon: '▨',
    container: false,
    template: () => ({
      type: 'image',
      props: { url: 'https://picsum.photos/seed/sdui/600/300', contentScale: 'crop' },
      modifier: { width: 'match_parent', height: 160, cornerRadius: 12 },
    }),
  },
  {
    type: 'icon',
    label: 'Icon',
    group: 'Content',
    icon: '★',
    container: false,
    template: () => ({ type: 'icon', props: { name: 'star', size: 24 } }),
  },
  {
    type: 'spacer',
    label: 'Spacer',
    group: 'Layout',
    icon: '␣',
    container: false,
    template: () => ({ type: 'spacer', props: { height: 16 } }),
  },
  {
    type: 'divider',
    label: 'Divider',
    group: 'Layout',
    icon: '—',
    container: false,
    template: () => ({ type: 'divider', modifier: { width: 'match_parent' } }),
  },
  {
    type: 'button',
    label: 'Button',
    group: 'Interaction',
    icon: '⬢',
    container: false,
    template: () => ({
      type: 'button',
      props: { text: 'Continue', variant: 'filled' },
      modifier: { width: 'match_parent' },
      actions: { onClick: { type: 'showToast', params: { message: 'Tapped' } } },
    }),
  },
  {
    type: 'iconButton',
    label: 'Icon button',
    group: 'Interaction',
    icon: '◉',
    container: false,
    template: () => ({
      type: 'iconButton',
      props: { icon: 'settings' },
      actions: { onClick: { type: 'noop' } },
    }),
  },
  {
    type: 'chip',
    label: 'Chip',
    group: 'Interaction',
    icon: '⬭',
    container: false,
    template: () => ({ type: 'chip', props: { text: 'Filter', icon: 'filter' } }),
  },
  {
    type: 'lazyColumn',
    label: 'List',
    group: 'Collections',
    icon: '≡',
    container: true,
    template: () => ({
      type: 'lazyColumn',
      props: { items: '{{items}}', spacing: 10 },
      modifier: { width: 'match_parent', weight: 1 },
      children: [
        {
          type: 'card',
          modifier: { width: 'match_parent', cornerRadius: 14 },
          children: [
            {
              type: 'text',
              modifier: { padding: 14 },
              props: { text: '{{item.title}}' },
            },
          ],
        },
      ],
    }),
  },
  {
    type: 'lazyRow',
    label: 'Carousel',
    group: 'Collections',
    icon: '⋯',
    container: true,
    template: () => ({
      type: 'lazyRow',
      props: { items: '{{items}}', spacing: 10 },
      modifier: { width: 'match_parent' },
      children: [{ type: 'card', modifier: { width: 160, cornerRadius: 14 }, children: [] }],
    }),
  },
  {
    type: 'grid',
    label: 'Grid',
    group: 'Collections',
    icon: '⊞',
    container: true,
    template: () => ({
      type: 'grid',
      props: { items: '{{items}}', columns: 2, spacing: 10 },
      modifier: { width: 'match_parent', height: 200 },
      children: [{ type: 'card', modifier: { cornerRadius: 14 }, children: [] }],
    }),
  },
  {
    type: 'textField',
    label: 'Text field',
    group: 'Forms',
    icon: '▭',
    container: false,
    template: () => ({
      type: 'textField',
      props: { name: 'field', label: 'Label', placeholder: 'Type here', rules: [{ type: 'required' }] },
    }),
  },
  {
    type: 'password',
    label: 'Password',
    group: 'Forms',
    icon: '•',
    container: false,
    template: () => ({
      type: 'password',
      props: { name: 'password', label: 'Password', rules: [{ type: 'minLength', value: 8 }] },
    }),
  },
  {
    type: 'otp',
    label: 'OTP',
    group: 'Forms',
    icon: '⌨',
    container: false,
    template: () => ({ type: 'otp', props: { name: 'otp', label: 'Enter the code', length: 6 } }),
  },
  {
    type: 'dropdown',
    label: 'Dropdown',
    group: 'Forms',
    icon: '▼',
    container: false,
    template: () => ({
      type: 'dropdown',
      props: {
        name: 'choice',
        label: 'Choose one',
        options: [
          { label: 'Option A', value: 'a' },
          { label: 'Option B', value: 'b' },
        ],
      },
    }),
  },
  {
    type: 'checkbox',
    label: 'Checkbox',
    group: 'Forms',
    icon: '☑',
    container: false,
    template: () => ({ type: 'checkbox', props: { name: 'agree', label: 'I agree' } }),
  },
  {
    type: 'switch',
    label: 'Switch',
    group: 'Forms',
    icon: '⇄',
    container: false,
    template: () => ({ type: 'switch', props: { name: 'toggle', label: 'Enable' } }),
  },
  {
    type: 'radioGroup',
    label: 'Radio group',
    group: 'Forms',
    icon: '◎',
    container: false,
    template: () => ({
      type: 'radioGroup',
      props: {
        name: 'pick',
        label: 'Pick one',
        options: [
          { label: 'One', value: '1' },
          { label: 'Two', value: '2' },
        ],
      },
    }),
  },
  {
    type: 'datePicker',
    label: 'Date picker',
    group: 'Forms',
    icon: '▦',
    container: false,
    template: () => ({ type: 'datePicker', props: { name: 'date', label: 'Date' } }),
  },
  {
    type: 'progress',
    label: 'Progress',
    group: 'Feedback',
    icon: '◐',
    container: false,
    template: () => ({ type: 'progress', props: { variant: 'circular' } }),
  },
  {
    type: 'skeleton',
    label: 'Skeleton',
    group: 'Feedback',
    icon: '▬',
    container: false,
    template: () => ({
      type: 'skeleton',
      props: { lines: 3, lineHeight: 16 },
      modifier: { width: 'match_parent' },
    }),
  },
  {
    type: 'empty',
    label: 'Empty state',
    group: 'Feedback',
    icon: '∅',
    container: false,
    template: () => ({
      type: 'empty',
      props: { icon: 'info', title: 'Nothing here yet', message: 'Content will appear once available.' },
    }),
  },
  {
    type: 'badge',
    label: 'Badge',
    group: 'Feedback',
    icon: '●',
    container: false,
    template: () => ({ type: 'badge', props: { text: '3' } }),
  },
  {
    type: 'rating',
    label: 'Rating',
    group: 'Custom',
    icon: '✩',
    container: false,
    template: () => ({ type: 'rating', props: { value: 4, max: 5, size: 22 } }),
  },
];

export const CATALOG_BY_TYPE: Record<string, PaletteEntry> = Object.fromEntries(
  CATALOG.map((entry) => [entry.type.toLowerCase(), entry]),
);

const ALIASES: Record<string, string> = {
  vstack: 'column',
  hstack: 'row',
  stack: 'box',
  label: 'text',
  img: 'image',
  list: 'lazycolumn',
  carousel: 'lazyrow',
  input: 'textfield',
  select: 'dropdown',
  toggle: 'switch',
  loader: 'progress',
  shimmer: 'skeleton',
  emptystate: 'empty',
  number: 'textfield',
  surface: 'card',
};

export function entryFor(type: string): PaletteEntry | undefined {
  const key = type.toLowerCase();
  return CATALOG_BY_TYPE[key] ?? CATALOG_BY_TYPE[ALIASES[key] ?? ''];
}

export function isContainer(type: string): boolean {
  return entryFor(type)?.container ?? false;
}

export const GROUPS = ['Layout', 'Content', 'Interaction', 'Collections', 'Forms', 'Feedback', 'Custom'] as const;

/** Action types the inspector offers, matching `ActionTypes` in the SDK. */
export const ACTION_TYPES = [
  'navigate', 'back', 'apiCall', 'setState', 'toggleState', 'clearState',
  'showToast', 'showDialog', 'bottomSheet', 'dismiss', 'share', 'copyClipboard',
  'openUrl', 'openNativeScreen', 'scanQR', 'camera', 'analytics',
  'validateForm', 'submitForm', 'refreshScreen', 'setTheme', 'delay', 'noop',
] as const;

export const EVENT_NAMES = ['onClick', 'onLongClick', 'onChange', 'onComplete', 'onRefresh', 'onLoadMore'] as const;
