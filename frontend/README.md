# Splenza — Mobile App (React Native + Expo)

## Stack
React Native (Expo SDK 51) · TypeScript · React Navigation · Zustand · TanStack Query ·
React Hook Form · Axios · AsyncStorage · Reanimated · Gesture Handler · react-native-svg

## ✅ What's built

- **Theming**: full light/dark theme system (`src/theme`) using the brand palette you gave me,
  with a third "system" preference that follows the OS, persisted to device storage.
- **Circular reveal theme toggle** (`src/components/CircularRevealProvider.tsx` +
  `ThemeToggle.tsx`): screenshots the current screen, commits the new theme underneath, then
  wipes the screenshot away in an expanding circle centered on the button you tapped - same
  visual language as Zomato / Material You's theme switch. Full explanation of how it works is
  in the comments at the top of `CircularRevealProvider.tsx`.
- **Auth**: Login + Signup screens wired to your real backend (`/api/v1/auth/login`,
  `/signup`), with React Hook Form validation matching the backend's rules (password needs a
  letter + number, phone in international format, etc.), and automatic JWT refresh via an axios
  interceptor - if a request 401s, it transparently refreshes the token and retries, queuing any
  other in-flight requests so they don't all race to refresh separately.
- **Dashboard**: live net balance summary + per-friend balances from `/api/v1/balances/summary`.
- **Groups / Friends**: list views wired to the real endpoints, friend request accept/reject.
- **Profile**: user info, theme toggle, logout.
- App icon / splash / adaptive icon generated from your uploaded logo (padded to square, since
  app icons need to be exactly square - see `assets/icon.png`, `assets/splash.png`).

## 🔜 Not yet built

- Create/edit group, create/edit expense, split-type picker UI (equal/exact/percentage/shares)
- Group detail screen (balances, simplified debts, expense list, settle up)
- Friend search (by email/phone - backend supports it, UI doesn't yet)
- Settlement flow
- Notifications screen
- Receipt upload

## Storage: plain Expo Go compatible

This app uses `@react-native-async-storage/async-storage` (with a small in-memory cache on top
for synchronous reads where needed - see `src/lib/storage.ts`) instead of MMKV. That means **the
whole app runs in the plain Expo Go app** from the App Store / Play Store - no custom dev client,
no EAS build, no Apple Developer account, no Mac required to test on iOS.

If you later need MMKV's extra speed (unlikely to matter until the app is much bigger), swapping
back is a one-file change since every other file only imports `{ storage, StorageKeys,
storageHelpers }` from `src/lib/storage.ts` - just know that going back to MMKV means going back
to needing a custom dev client, since it's a native module Expo Go doesn't include.

## Setup

```bash
npm install
```

Point the app at your backend - edit `app.json`:
```json
"extra": { "apiBaseUrl": "http://localhost:8080" }
```
On a physical device, `localhost` won't reach your laptop's backend - use your machine's LAN IP
(e.g. `http://192.168.1.10:8080`) or an ngrok tunnel. On an Android emulator specifically,
`http://10.0.2.2:8080` reaches your host machine's `localhost`.

Then just:
```bash
npx expo start
```
Scan the QR code with the **Expo Go** app (App Store / Play Store) on your phone. No prebuild, no
native build step, no dev account needed on either platform.

You'll only need `npx expo prebuild` + a dev client / EAS Build later, when you add a native
module that Expo Go doesn't include out of the box (e.g. certain camera/file features for receipt
uploads), or when you build the actual release binary for the Play Store / App Store.

## Project structure

```
src/
  theme/          Colors, ThemeContext (light/dark/system + persisted preference)
  components/      Logo, ThemeToggle, CircularRevealProvider, Button, TextField
  lib/            apiClient (axios + auto-refresh), storage (AsyncStorage wrapper)
  store/          authStore (zustand)
  navigation/     Root/Auth/Main navigators
  screens/
    auth/         Login, Signup
    main/         Dashboard, Groups, Friends, Profile
  types/          TypeScript types mirroring the backend DTOs exactly
assets/
  logo/           Your original uploaded PNGs, kept as-is
  icon.png, splash.png, adaptive-icon.png, favicon.png   Generated square variants
```

## A note on styling

The original stack mentioned NativeWind - it's installed and configured (`tailwind.config.js`,
babel plugin), but most components here use `StyleSheet` + the `theme` object from
`useAppTheme()` directly instead of `className`. That's because our theme has a custom 3-way
light/dark/system toggle with the circular reveal animation, which needs JS-level access to exact
color values (for the SVG mask, gradients, etc.) - NativeWind's class-based dark mode doesn't
give us that hook. You can still use NativeWind classNames for static, non-theme-dependent
styling if you prefer that syntax going forward; both approaches coexist fine.
