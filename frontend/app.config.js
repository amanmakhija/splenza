module.exports = ({ config }) => {
  if (process.env.GOOGLE_SERVICES_FILE_PATH) {
    config.android.googleServicesFile = process.env.GOOGLE_SERVICES_FILE_PATH;
  }
  return config;
};
