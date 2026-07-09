import React, { createContext, useContext, useEffect, useMemo, useState, useCallback } from "react";
import { useColorScheme } from "react-native";
import { AppTheme, ThemeMode, getTheme } from "./colors";
import { storage, StorageKeys } from "@/lib/storage";

export type ThemePreference = "light" | "dark" | "system";

interface ThemeContextValue {
  theme: AppTheme;
  mode: ThemeMode;                  // resolved mode actually in use (light/dark)
  preference: ThemePreference;       // what the user picked (may be "system")
  setPreference: (pref: ThemePreference) => void;
  /** Immediately commits a resolved mode without going through "system" - used by
   *  the circular-reveal toggle, which needs to know the exact next mode up front. */
  setResolvedMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const systemScheme = useColorScheme(); // "light" | "dark" | null

  const [preference, setPreferenceState] = useState<ThemePreference>(() => {
    const stored = storage.getString(StorageKeys.THEME_MODE);
    return (stored as ThemePreference) ?? "system";
  });

  const resolveMode = useCallback(
    (pref: ThemePreference): ThemeMode => (pref === "system" ? (systemScheme === "dark" ? "dark" : "light") : pref),
    [systemScheme]
  );

  const [mode, setMode] = useState<ThemeMode>(() => resolveMode(preference));

  useEffect(() => {
    setMode(resolveMode(preference));
  }, [preference, resolveMode]);

  const setPreference = useCallback((pref: ThemePreference) => {
    setPreferenceState(pref);
    storage.set(StorageKeys.THEME_MODE, pref);
  }, []);

  const setResolvedMode = useCallback((next: ThemeMode) => {
    setMode(next);
    setPreferenceState(next);
    storage.set(StorageKeys.THEME_MODE, next);
  }, []);

  const value = useMemo<ThemeContextValue>(
    () => ({ theme: getTheme(mode), mode, preference, setPreference, setResolvedMode }),
    [mode, preference, setPreference, setResolvedMode]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useAppTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useAppTheme must be used within a ThemeProvider");
  return ctx;
}
