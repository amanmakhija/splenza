/**
 * Splenza brand palette. Keep this as the single source of truth - tailwind.config.js
 * mirrors these values for className usage, this file is for direct style/JS usage
 * (SVG fills, animated values, chart colors, etc. where Tailwind classes don't reach).
 */
export const brand = {
  primary: "#4B4FE0", // indigo-violet - buttons, active nav icon, FAB
  primaryContainer: "#ECEBFB", // selected chips, subtle highlights
  owed: "#1D9E75", // "you're owed" - positive balances, settled confirmations
  owe: "#E24B4A", // "you owe" - negative balances, overdue nudges
  reminder: "#EF9F27", // nudges/reminders only - stays distinct from owed/owe
  surfaceLight: "#FAFAF8",
  ink: "#111117",
  inkSecondary: "#6E6E67",
} as const;

export type ThemeMode = "light" | "dark";

export interface AppTheme {
  mode: ThemeMode;
  background: string;
  surface: string;
  surfaceElevated: string;
  border: string;
  textPrimary: string;
  textSecondary: string;
  textMuted: string;
  primary: string;
  primaryContainer: string;
  accent: string; // kept for backward compat with older screens; equals `primary`
  owed: string;
  owe: string;
  reminder: string;
  danger: string;
  success: string;
  warning: string;
}

export const lightTheme: AppTheme = {
  mode: "light",
  background: brand.surfaceLight,
  surface: "#FFFFFF",
  surfaceElevated: "#FFFFFF",
  border: "#E6E4DC",
  textPrimary: brand.ink,
  textSecondary: brand.inkSecondary,
  textMuted: "#9B9A91",
  primary: brand.primary,
  primaryContainer: brand.primaryContainer,
  accent: brand.primary,
  owed: brand.owed,
  owe: brand.owe,
  reminder: brand.reminder,
  danger: brand.owe,
  success: brand.owed,
  warning: brand.reminder,
};

export const darkTheme: AppTheme = {
  mode: "dark",
  // Cards sit one step up from the background rather than pure black, per spec -
  // background #111117, card surfaces #1B1B22.
  background: "#111117",
  surface: "#1B1B22",
  surfaceElevated: "#22222B",
  border: "#2E2E38",
  textPrimary: "#F5F5F3",
  textSecondary: "#B5B4AC",
  textMuted: "#77766E",
  primary: brand.primary,
  primaryContainer: "#2B2A63",
  accent: brand.primary,
  owed: brand.owed,
  owe: brand.owe,
  reminder: brand.reminder,
  danger: brand.owe,
  success: brand.owed,
  warning: brand.reminder,
};

export const getTheme = (mode: ThemeMode): AppTheme =>
  mode === "dark" ? darkTheme : lightTheme;
