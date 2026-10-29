import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

type Tone = 'success' | 'error'

type ToastAction = { label: string; to: string }

type ToastState = { message: string; tone: Tone; action?: ToastAction; leaving?: boolean }

type ToastValue = {
  show: (message: string, tone?: Tone, action?: ToastAction) => void
}

const ToastContext = createContext<ToastValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const show = useCallback((message: string, tone: Tone = 'success', action?: ToastAction) => {
    window.clearTimeout(timer.current)
    setToast({ message, tone, action })
    const total = action ? 6000 : 3000
    timer.current = window.setTimeout(() => {
      setToast((current) => (current ? { ...current, leaving: true } : null))
      timer.current = window.setTimeout(() => setToast(null), 160)
    }, total - 160)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {toast && (
        <div
          role="status"
          className={`fixed inset-x-4 bottom-[calc(5.5rem+env(safe-area-inset-bottom))] z-[60] mx-auto flex max-w-md flex-col items-center gap-2 rounded-xl px-4 py-3 text-center text-base font-medium lg:bottom-6 ${
            toast.leaving ? 'animate-toast-out' : 'animate-toast-in'
          } ${
            toast.tone === 'error' ? 'bg-error text-white' : 'bg-primary text-white'
          }`}
        >
          <p>{toast.message}</p>
          {toast.action && (
            <Link
              to={toast.action.to}
              className="inline-flex min-h-[48px] items-center justify-center rounded-lg bg-secondary px-4 font-semibold text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white"
            >
              {toast.action.label}
            </Link>
          )}
        </div>
      )}
    </ToastContext.Provider>
  )
}

export function useToast(): ToastValue {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast precisa estar dentro de ToastProvider')
  }
  return context
}
