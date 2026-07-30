import {
  getCrashlytics,
  log,
  recordError,
  setUserId,
  setCrashlyticsCollectionEnabled,
} from "@react-native-firebase/crashlytics";

const crashlytics = getCrashlytics();

// Crashlytics collects in dev builds too by default, which just adds noise
// to your dashboard from local testing. Turn it off outside production.
setCrashlyticsCollectionEnabled(crashlytics, !__DEV__);

export function reportError(error: unknown, context?: string) {
  if (context) log(crashlytics, context);
  const err = error instanceof Error ? error : new Error(String(error));
  recordError(crashlytics, err);
  if (__DEV__) console.error(context ?? "Error:", err);
}

export function identifyUserForCrashReports(userId: string | null) {
  setUserId(crashlytics, userId ?? "");
}

/**
 * Catches what the React ErrorBoundary can't: errors thrown in event handlers,
 * async code, promise rejections, and native JS-thread errors.
 */
export function setupGlobalErrorHandlers() {
  const previousHandler = ErrorUtils.getGlobalHandler();
  ErrorUtils.setGlobalHandler((error, isFatal) => {
    reportError(error, isFatal ? "Fatal JS error" : "Non-fatal JS error");
    previousHandler(error, isFatal);
  });

  const rejectionTracking = require("promise/setimmediate/rejection-tracking");
  rejectionTracking.enable({
    allRejections: true,
    onUnhandled: (id: number, error: unknown) => {
      reportError(error, "Unhandled promise rejection");
    },
    onHandled: () => {},
  });
}
