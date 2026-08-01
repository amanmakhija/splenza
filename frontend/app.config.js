module.exports = ({ config }) => {
  // If the raw JSON string is present in the environment (CI Runner)
  if (process.env.GOOGLE_SERVICES_JSON_STRING) {
    try {
      if (!config.android) config.android = {};

      // Inject the raw parsed JSON object directly into the expo config
      config.android.googleServicesFile = JSON.parse(
        process.env.GOOGLE_SERVICES_JSON_STRING,
      );
      console.log(
        "Successfully injected Google Services configuration from environment string.",
      );
    } catch (error) {
      console.error(
        "Failed to parse GOOGLE_SERVICES_JSON_STRING environment variable:",
        error,
      );
    }
  }

  return config;
};
