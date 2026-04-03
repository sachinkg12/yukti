/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        primary: {
          50: "rgba(99, 102, 241, 0.05)",
          100: "rgba(99, 102, 241, 0.1)",
          200: "rgba(99, 102, 241, 0.2)",
          300: "rgba(99, 102, 241, 0.3)",
          400: "#818cf8",
          500: "#6366f1",
          600: "#4f46e5",
          700: "#4338ca",
        },
        surface: {
          50: "rgba(255, 255, 255, 0.02)",
          100: "rgba(255, 255, 255, 0.04)",
          200: "rgba(255, 255, 255, 0.06)",
          300: "rgba(255, 255, 255, 0.08)",
          400: "rgba(255, 255, 255, 0.12)",
          800: "#18181b",
          900: "#0a0a0f",
        },
        success: "#34d399",
        warning: "#fbbf24",
        error: "#f87171",
        dim: "#52525b",
        muted: "#71717a",
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
      },
      boxShadow: {
        glow: "0 0 30px rgba(99, 102, 241, 0.08)",
        "glow-lg": "0 0 60px rgba(99, 102, 241, 0.12)",
        card: "0 1px 3px 0 rgb(0 0 0 / 0.3)",
      },
      borderRadius: {
        "2xl": "1.25rem",
        "3xl": "1.5rem",
      },
      spacing: {
        18: "4.5rem",
        22: "5.5rem",
      },
    },
  },
  plugins: [],
}
