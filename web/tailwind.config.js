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
      transitionDuration: {
        fast: '120ms',
        base: '200ms',
        slow: '280ms',
      },
      transitionTimingFunction: {
        out: 'cubic-bezier(.33,1,.68,1)',
        spring: 'cubic-bezier(.34,1.56,.64,1)',
      },
      keyframes: {
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'fade-out': { from: { opacity: '1' }, to: { opacity: '0' } },
        'rise-in': {
          from: { opacity: '0', transform: 'translateY(calc(8px * var(--motion-travel, 1)))' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'sheet-up': {
          from: { transform: 'translateY(calc(100% * var(--motion-travel, 1)))' },
          to: { transform: 'translateY(0)' },
        },
        'sheet-down': {
          from: { transform: 'translateY(0)' },
          to: { transform: 'translateY(calc(100% * var(--motion-travel, 1)))' },
        },
        'pop-in': {
          from: { opacity: '0', transform: 'scale(calc(1 - .04 * var(--motion-travel, 1)))' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
        'pop-out': {
          from: { opacity: '1', transform: 'scale(1)' },
          to: { opacity: '0', transform: 'scale(calc(1 - .04 * var(--motion-travel, 1)))' },
        },
        'toast-in': {
          from: { opacity: '0', transform: 'translateY(calc(12px * var(--motion-travel, 1)))' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'toast-out': { from: { opacity: '1' }, to: { opacity: '0' } },
        shimmer: {
          from: { backgroundPosition: '100% 0' },
          to: { backgroundPosition: '0% 0' },
        },
        'check-draw': { to: { strokeDashoffset: '0' } },
        shake: {
          '0%, 100%': { transform: 'translateX(0)' },
          '20%': { transform: 'translateX(calc(-4px * var(--motion-travel, 1)))' },
          '40%': { transform: 'translateX(calc(4px * var(--motion-travel, 1)))' },
          '60%': { transform: 'translateX(calc(-3px * var(--motion-travel, 1)))' },
          '80%': { transform: 'translateX(calc(3px * var(--motion-travel, 1)))' },
        },
        'value-flash': {
          from: { opacity: '.5', transform: 'scale(calc(1 + .04 * var(--motion-travel, 1)))' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
      },
      animation: {
        'fade-in': 'fade-in 200ms cubic-bezier(.33,1,.68,1)',
        'fade-out': 'fade-out 160ms cubic-bezier(.33,1,.68,1) forwards',
        'rise-in': 'rise-in 200ms cubic-bezier(.33,1,.68,1)',
        'sheet-up': 'sheet-up 280ms cubic-bezier(.34,1.56,.64,1)',
        'sheet-down': 'sheet-down 200ms cubic-bezier(.33,1,.68,1) forwards',
        'pop-in': 'pop-in 200ms cubic-bezier(.33,1,.68,1)',
        'pop-out': 'pop-out 160ms cubic-bezier(.33,1,.68,1) forwards',
        'toast-in': 'toast-in 200ms cubic-bezier(.34,1.56,.64,1)',
        'toast-out': 'toast-out 160ms cubic-bezier(.33,1,.68,1) forwards',
        shimmer: 'shimmer 1.2s linear infinite',
        'check-draw': 'check-draw 320ms cubic-bezier(.33,1,.68,1) both',
        shake: 'shake 240ms cubic-bezier(.33,1,.68,1)',
        'value-flash': 'value-flash 280ms cubic-bezier(.33,1,.68,1)',
      },
    },
  },
  plugins: [],
}
