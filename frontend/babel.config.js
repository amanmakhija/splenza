module.exports = function (api) {
  const isProduction = api.env("production");
  api.cache.using(() => process.env.BABEL_ENV || process.env.NODE_ENV);

  return {
    presets: ["babel-preset-expo"],
    plugins: [
      "nativewind/babel",
      [
        "module-resolver",
        {
          root: ["."],
          alias: { "@": "./src" },
          extensions: [".ts", ".tsx", ".js", ".jsx", ".json"],
        },
      ],
      // Strip console.* in production bundles. Keeps `error`/`warn` so a device
      // logs collector (if you ever attach one) or Metro-in-dev still sees them;
      // remove those from the exclude list too if you want a fully silent prod build.
      isProduction && [
        "transform-remove-console",
        { exclude: ["error", "warn"] },
      ],
      // Reanimated plugin MUST be listed last
      "react-native-reanimated/plugin",
    ].filter(Boolean),
  };
};
