// Runs once before the test framework is installed in each test file.

// AsyncStorage has no real native module in Jest — use the official mock.
jest.mock("@react-native-async-storage/async-storage", () =>
  require("@react-native-async-storage/async-storage/jest/async-storage-mock"),
);

// Firebase/Crashlytics has no real native module in Jest either — stub the
// functions we actually call so importing crashReporting.ts doesn't crash.
jest.mock("@react-native-firebase/crashlytics", () => ({
  getCrashlytics: jest.fn(() => ({})),
  log: jest.fn(),
  recordError: jest.fn(),
  setUserId: jest.fn(),
  setCrashlyticsCollectionEnabled: jest.fn(),
}));

jest.mock("@react-native-firebase/app", () => ({
  getApp: jest.fn(() => ({})),
}));

jest.mock("expo-constants", () => ({
  __esModule: true,
  default: {
    expoConfig: {
      extra: {
        apiBaseUrl: "https://api.splenza.in",
        googleWebClientId: "test-client-id",
        androidClientId: "test-client-id",
      },
    },
  },
}));

// Required for @testing-library/react-native + React 19 async rendering
// (useEffect-driven state updates in tests) to work correctly with act().
global.IS_REACT_ACT_ENVIRONMENT = true;
