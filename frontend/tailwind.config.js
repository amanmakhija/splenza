/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./App.tsx", "./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        primary: "#4B4FE0",
        "primary-container": "#ECEBFB",
        owed: "#1D9E75",
        owe: "#E24B4A",
        reminder: "#EF9F27",
        surface: "#FAFAF8",
        ink: "#111117",
        "ink-secondary": "#6E6E67",
      },
    },
  },
  plugins: [],
};
