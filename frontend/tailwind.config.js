/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        ink: {
          950: '#080C16',
          900: '#0B1120',
          800: '#131B2E',
          700: '#1C2740',
          600: '#2A3757',
        },
        amber: {
          400: '#F7B733',
          500: '#F5A623',
          600: '#D98C0F',
        },
        signal: {
          yes: '#3DDC97',
          no: '#FF6B7A',
        },
        ash: {
          100: '#E7EAF0',
          300: '#AEB6C7',
          500: '#8B93A7',
          700: '#5A6478',
        },
      },
      fontFamily: {
        display: ['"Space Grotesk"', 'sans-serif'],
        body: ['Inter', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      },
    },
  },
  plugins: [],
}
