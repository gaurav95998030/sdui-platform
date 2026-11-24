import type { ThemeSpec } from '../types';

/**
 * Web-side mirror of `ColorResolver.kt` and the Material baseline.
 *
 * The canvas must resolve `"primary"`, `"#4C5BD4"` and `"brandGold"` exactly the
 * way the device does, otherwise a designer tunes colours against a preview that
 * lies. Only the resolution rules are duplicated — the values still come from the
 * server-owned theme.
 */

const LIGHT_BASELINE: Record<string, string> = {
  primary: '#6750A4', onPrimary: '#FFFFFF', primaryContainer: '#EADDFF', onPrimaryContainer: '#21005D',
  secondary: '#625B71', onSecondary: '#FFFFFF', secondaryContainer: '#E8DEF8', onSecondaryContainer: '#1D192B',
  tertiary: '#7D5260', onTertiary: '#FFFFFF',
  background: '#FEF7FF', onBackground: '#1D1B20',
  surface: '#FEF7FF', onSurface: '#1D1B20',
  surfaceVariant: '#E7E0EC', onSurfaceVariant: '#49454F',
  surfaceContainer: '#F3EDF7', surfaceContainerLow: '#F7F2FA', surfaceContainerHigh: '#ECE6F0',
  error: '#B3261E', onError: '#FFFFFF', errorContainer: '#F9DEDC',
  outline: '#79747E', outlineVariant: '#CAC4D0', scrim: '#000000',
};

const DARK_BASELINE: Record<string, string> = {
  primary: '#D0BCFF', onPrimary: '#381E72', primaryContainer: '#4F378B', onPrimaryContainer: '#EADDFF',
  secondary: '#CCC2DC', onSecondary: '#332D41', secondaryContainer: '#4A4458', onSecondaryContainer: '#E8DEF8',
  tertiary: '#EFB8C8', onTertiary: '#492532',
  background: '#141218', onBackground: '#E6E0E9',
  surface: '#141218', onSurface: '#E6E0E9',
  surfaceVariant: '#49454F', onSurfaceVariant: '#CAC4D0',
  surfaceContainer: '#211F26', surfaceContainerLow: '#1D1B20', surfaceContainerHigh: '#2B2930',
  error: '#F2B8B5', onError: '#601410', errorContainer: '#8C1D18',
  outline: '#938F99', outlineVariant: '#49454F', scrim: '#000000',
};

export interface ResolvedTheme {
  scheme: Record<string, string>;
  tokens: Record<string, string>;
  shapes: Record<string, number>;
  isDark: boolean;
}

export function resolveTheme(spec: ThemeSpec | undefined, isDark: boolean): ResolvedTheme {
  const baseline = isDark ? DARK_BASELINE : LIGHT_BASELINE;
  const overrides = (isDark ? spec?.dark : spec?.light) ?? {};
  const scheme = { ...baseline };
  for (const [role, value] of Object.entries(overrides)) {
    if (typeof value === 'string' && value) scheme[role] = value;
  }
  return {
    scheme,
    tokens: spec?.tokens ?? {},
    shapes: { extraSmall: 4, small: 8, medium: 12, large: 16, extraLarge: 28, ...(spec?.shapes ?? {}) },
    isDark,
  };
}

export function resolveColor(
  raw: string | undefined | null,
  theme: ResolvedTheme,
  depth = 0,
): string | undefined {
  if (!raw || depth > 4) return undefined;
  const value = raw.trim();
  if (value.startsWith('#')) return value;

  const name = value.replace(/^[@$]/, '').replace(/^color\//, '');
  if (theme.scheme[name]) return theme.scheme[name];

  const literal: Record<string, string> = {
    transparent: 'transparent', clear: 'transparent', none: 'transparent',
    black: '#000000', white: '#FFFFFF', red: '#E53935', green: '#43A047',
    blue: '#1E88E5', yellow: '#FDD835', orange: '#FB8C00', purple: '#8E24AA',
    gray: '#9E9E9E', grey: '#9E9E9E',
  };
  if (literal[name.toLowerCase()]) return literal[name.toLowerCase()];

  const token = theme.tokens[name];
  if (token) return resolveColor(token, theme, depth + 1);
  return undefined;
}

/** Material 3 type scale, in the units the canvas needs. */
export const TYPOGRAPHY: Record<string, { size: number; weight: number; lineHeight: number }> = {
  displayLarge: { size: 57, weight: 400, lineHeight: 64 },
  displayMedium: { size: 45, weight: 400, lineHeight: 52 },
  displaySmall: { size: 36, weight: 400, lineHeight: 44 },
  headlineLarge: { size: 32, weight: 400, lineHeight: 40 },
  headlineMedium: { size: 28, weight: 400, lineHeight: 36 },
  headlineSmall: { size: 24, weight: 400, lineHeight: 32 },
  titleLarge: { size: 22, weight: 400, lineHeight: 28 },
  titleMedium: { size: 16, weight: 500, lineHeight: 24 },
  titleSmall: { size: 14, weight: 500, lineHeight: 20 },
  bodyLarge: { size: 16, weight: 400, lineHeight: 24 },
  bodyMedium: { size: 14, weight: 400, lineHeight: 20 },
  bodySmall: { size: 12, weight: 400, lineHeight: 16 },
  labelLarge: { size: 14, weight: 500, lineHeight: 20 },
  labelMedium: { size: 12, weight: 500, lineHeight: 16 },
  labelSmall: { size: 11, weight: 500, lineHeight: 16 },
};

export const FONT_WEIGHTS: Record<string, number> = {
  thin: 100, light: 300, normal: 400, medium: 500,
  semibold: 600, bold: 700, extrabold: 800, black: 900,
};
