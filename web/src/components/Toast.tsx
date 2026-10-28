import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'

type Tone = 'success' | 'error'

type ToastValue = {
  show: (message: string, tone?: Tone) => void
}

const ToastContext = createContext<ToastValue | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ message: string; tone: Tone } | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const show = useCallback((message: string, tone: Tone = 'success') => {
    window.clearTimeout(timer.current)
    setToast({ message, tone })
    timer.current = window.setTimeout(() => setToast(null), 3000)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {toast && (
        <div
          role="status"
          className={`fixed inset-x-4 bottom-[calc(5.5rem+env(safe-area-inset-bottom))] z-[60] mx-auto max-w-md rounded-xl px-4 py-3 text-center text-base font-medium lg:bottom-6 ${
            toast.tone === 'error' ? 'bg-error text-white' : 'bg-primary text-white'
          }`}
        >
          {toast.message}
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
