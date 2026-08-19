const { withAndroidManifest } = require("expo/config-plugins");

// expo-audio's own AndroidManifest.xml unconditionally declares
// FOREGROUND_SERVICE_MEDIA_PLAYBACK and an AudioControlsService (for
// lock-screen/notification media playback controls), regardless of any
// config option - it's there to support the library's playback feature,
// which this app doesn't use anywhere (only short in-app voice recording,
// see VoiceEntrySheet). Google Play's foreground-service-permission policy
// requires either a genuine justification (with a demo video showing the
// actual background-playback behavior) or removal - since there's no real
// feature to demonstrate, removal is the correct and honest fix, not a
// fabricated declaration.
//
// A plain array filter on our own generated manifest wouldn't work here:
// this permission/service isn't present in the app's own
// AndroidManifest.xml at `expo prebuild` time - it's injected later by
// Android Gradle's manifest merger, pulled in from expo-audio's AAR. The
// standard way to cancel out a dependency-provided manifest entry is an
// explicit `tools:node="remove"` override in the app's own manifest, which
// this plugin adds.
const PERMISSION_TO_REMOVE =
  "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK";
const SERVICE_TO_REMOVE = "expo.modules.audio.service.AudioControlsService";

module.exports = function withoutMediaPlaybackForegroundService(config) {
  return withAndroidManifest(config, (config) => {
    const manifest = config.modResults.manifest;

    manifest.$["xmlns:tools"] =
      manifest.$["xmlns:tools"] || "http://schemas.android.com/tools";

    if (!Array.isArray(manifest["uses-permission"])) {
      manifest["uses-permission"] = [];
    }
    manifest["uses-permission"] = manifest["uses-permission"].filter(
      (p) => p.$["android:name"] !== PERMISSION_TO_REMOVE,
    );
    manifest["uses-permission"].push({
      $: {
        "android:name": PERMISSION_TO_REMOVE,
        "tools:node": "remove",
      },
    });

    const application = manifest.application && manifest.application[0];
    if (application) {
      if (!Array.isArray(application.service)) {
        application.service = [];
      }
      application.service = application.service.filter(
        (s) => s.$["android:name"] !== SERVICE_TO_REMOVE,
      );
      application.service.push({
        $: {
          "android:name": SERVICE_TO_REMOVE,
          "tools:node": "remove",
        },
      });
    }

    return config;
  });
};
