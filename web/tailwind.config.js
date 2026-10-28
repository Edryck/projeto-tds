export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#382B31',
        'primary-hover': '#4B3942',
        secondary: '#B97891',
        'secondary-soft': '#F1E2E8',
        accent: '#718A68',
        'accent-soft': '#E5EDE1',
        background: '#F8F5F2',
        surface: '#FFFFFF',
        'surface-alt': '#F0EAE7',
        border: '#E2D7D9',
        text: '#382B31',
        'text-secondary': '#786B72',
        error: '#B5474B',
        'error-soft': '#F8E6E5',
        warning: '#A56D28',
        'warning-soft': '#F8EEDC',
      },
      fontFamily: {
        serif: ['Georgia', 'serif'],
        sans: ['Inter', 'Arial', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
