/**
 * Splentra brand palette. Keep this as the single source of truth - tailwind.config.js
 * mirrors these values for className usage, this file is for direct style/JS usage
 * (SVG fills, animated values, chart colors, etc. where Tailwind classes don't reach).
 */
export const brand = {
  primaryPurple: "#6C63FF",
  secondaryBlue: "#4F46E5",
  accentGreen: "#22C55E",
  dark: "#0F172A",
  light: "#F8FAFC"
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
  secondary: string;
  accent: string;
  danger: string;
  success: string;
  warning: string;
}

export const lightTheme: AppTheme = {
  mode: "light",
  background: brand.light,
  surface: "#FFFFFF",
  surfaceElevated: "#FFFFFF",
  border: "#E2E8F0",
  textPrimary: brand.dark,
  textSecondary: "#475569",
  textMuted: "#94A3B8",
  primary: brand.primaryPurple,
  secondary: brand.secondaryBlue,
  accent: brand.accentGreen,
  danger: "#EF4444",
  success: brand.accentGreen,
  warning: "#F59E0B"
};

export const darkTheme: AppTheme = {
  mode: "dark",
  background: brand.dark,
  surface: "#1E293B",
  surfaceElevated: "#273449",
  border: "#334155",
  textPrimary: brand.light,
  textSecondary: "#CBD5E1",
  textMuted: "#64748B",
  primary: brand.primaryPurple,
  secondary: brand.secondaryBlue,
  accent: brand.accentGreen,
  danger: "#F87171",
  success: "#4ADE80",
  warning: "#FBBF24"
};

export const getTheme = (mode: ThemeMode): AppTheme => (mode === "dark" ? darkTheme : lightTheme);
