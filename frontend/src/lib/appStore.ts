import { Linking, Platform } from "react-native";

const ANDROID_PACKAGE = "com.splenza.app";

/**
 * Opens the app's store listing - the Play Store app directly on Android
 * (falling back to the web listing if the app isn't installed, e.g. on an
 * emulator without Play Store), and the App Store on iOS.
 *
 * NOTE: the iOS URL is a placeholder until Splenza has a real App Store
 * listing/numeric ID - replace "splenza" below with
 * "https://apps.apple.com/app/idXXXXXXXXXX" once that exists.
 */
export function openAppStoreListing() {
  const url =
    Platform.OS === "android"
      ? `market://details?id=${ANDROID_PACKAGE}`
      : "https://apps.apple.com/app/splenza";

  Linking.openURL(url).catch(() => {
    Linking.openURL(
      `https://play.google.com/store/apps/details?id=${ANDROID_PACKAGE}`,
    );
  });
}
