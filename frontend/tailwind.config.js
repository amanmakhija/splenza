/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./App.tsx", "./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        primary: "#6C63FF",   // Primary Purple
        secondary: "#4F46E5", // Secondary Blue
        accent: "#22C55E",    // Accent Green
        dark: "#0F172A",
        light: "#F8FAFC"
      }
    }
  },
  plugins: []
};
