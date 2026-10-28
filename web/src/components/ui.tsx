import { useEffect, useId, useRef } from 'react'
import type {
  ButtonHTMLAttributes,
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, Loader2, X } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { Page } from '../api'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'

const buttonBase =
  'inline-flex min-h-[48px] items-center justify-center gap-2 rounded-xl px-5 text-base font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50'

const variantClass: Record<Variant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover',
  secondary: 'bg-secondary text-primary hover:bg-secondary/80',
  danger: 'bg-error text-white hover:bg-error/90',
  ghost: 'border border-border bg-surface text-text hover:bg-surface-alt',
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant
  loading?: boolean
}

export function Button({ variant = 'primary', loading, disabled, className = '', children, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      className={`${buttonBase} ${variantClass[variant]} ${className}`}
    >
      {loading && <Loader2 className="h-5 w-5 animate-spin" aria-hidden="true" />}
      {children}
    </button>
  )
}

export function LinkButton({
  variant = 'primary',
  to,
  className = '',
  children,
}: {
  variant?: Variant
  to: string
  className?: string
  children: ReactNode
}) {
  return (
    <Link to={to} className={`${buttonBase} ${variantClass[variant]} ${className}`}>
      {children}
    </Link>
  )
}

export function Field({
  label,
  required,
  hint,
  error,
  children,
}: {
  label: string
  required?: boolean
  hint?: string
  error?: string
  children: ReactNode
}) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-text">
        {label}
        {required && <span className="text-error"> *</span>}
      </span>
      {children}
      {hint && !error && <span className="mt-1 block text-sm text-text-secondary">{hint}</span>}
      {error && (
        <span role="alert" className="mt-1 block text-sm text-error">
          {error}
        </span>
      )}
    </label>
  )
}

const fieldBase =
  'w-full rounded-[10px] border border-[#D8CBCD] bg-surface px-3 text-base text-text placeholder:text-text-secondary focus:border-secondary focus:outline-none focus:ring-2 focus:ring-secondary/40 disabled:bg-surface-alt'

export function Input({ invalid, className = '', ...rest }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return (
    <input
      {...rest}
      aria-invalid={invalid || undefined}
      className={`${fieldBase} min-h-[48px] ${invalid ? 'border-error' : ''} ${className}`}
    />
  )
}

export function Textarea({
  invalid,
  className = '',
  ...rest
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return (
    <textarea
      {...rest}
      aria-invalid={invalid || undefined}
      className={`${fieldBase} min-h-[96px] py-3 ${invalid ? 'border-error' : ''} ${className}`}
    />
  )
}

export function Select({ className = '', ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...rest} className={`${fieldBase} min-h-[48px] ${className}`} />
}

export function Chip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`min-h-[48px] shrink-0 rounded-full border px-4 text-sm font-medium transition-colors ${
        active ? 'border-secondary bg-secondary text-primary' : 'border-border bg-surface text-text'
      }`}
    >
      {children}
    </button>
  )
}

export function ChipRow({ children }: { children: ReactNode }) {
  return <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1">{children}</div>
}

type Tone = 'success' | 'warning' | 'danger' | 'neutral' | 'info'

const toneClass: Record<Tone, string> = {
  success: 'bg-accent-soft text-primary',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-error-soft text-error',
  neutral: 'bg-surface-alt text-text-secondary',
  info: 'bg-secondary-soft text-primary',
}

export function StatusPill({ tone, icon: Icon, children }: { tone: Tone; icon: LucideIcon; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-3 py-1 text-sm font-medium ${toneClass[tone]}`}>
      <Icon className="h-4 w-4" aria-hidden="true" />
      {children}
    </span>
  )
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-[14px] border border-border bg-surface p-4 ${className}`}>{children}</div>
}

export function PageHeader({
  title,
  subtitle,
  back,
  actions,
}: {
  title: string
  subtitle?: string
  back?: { to: string; label: string }
  actions?: ReactNode
}) {
  return (
    <header className="mb-4">
      {back && (
        <Link to={back.to} className="mb-2 inline-flex min-h-[48px] items-center gap-1 text-sm font-medium text-text-secondary">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          {back.label}
        </Link>
      )}
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="font-serif text-2xl font-bold text-text">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-text-secondary">{subtitle}</p>}
        </div>
        {actions && <div className="flex shrink-0 gap-2">{actions}</div>}
      </div>
    </header>
  )
}

const focusableSelector =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

export function Sheet({
  open,
  onClose,
  title,
  children,
  footer,
}: {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
  footer?: ReactNode
}) {
  const panelRef = useRef<HTMLDivElement>(null)
  const onCloseRef = useRef(onClose)
  const titleId = useId()

  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    if (!open) {
      return
    }
    const opener = document.activeElement as HTMLElement | null
    const panel = panelRef.current
    panel?.focus()

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab' || !panel) {
        return
      }
      const items = Array.from(panel.querySelectorAll<HTMLElement>(focusableSelector))
      if (items.length === 0) {
        event.preventDefault()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      if (event.shiftKey && (document.activeElement === first || document.activeElement === panel)) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = ''
      opener?.focus()
    }
  }, [open])

  if (!open) {
    return null
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center lg:items-center">
      <div className="absolute inset-0 bg-primary/40" onClick={onClose} aria-hidden="true" />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="relative flex max-h-[90vh] w-full flex-col rounded-t-2xl bg-surface pb-[env(safe-area-inset-bottom)] focus:outline-none lg:max-w-lg lg:rounded-2xl lg:pb-0"
      >
        <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-2">
          <h2 id={titleId} className="font-serif text-lg font-bold text-text">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar"
            className="flex h-12 w-12 items-center justify-center rounded-xl text-text hover:bg-surface-alt"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
        <div className="overflow-y-auto p-4">{children}</div>
        {footer && <div className="flex gap-2 border-t border-border p-4">{footer}</div>}
      </div>
    </div>
  )
}

export function ConfirmSheet({
  open,
  title,
  message,
  confirmLabel,
  danger,
  busy,
  onConfirm,
  onClose,
}: {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  busy?: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Sheet
      open={open}
      onClose={onClose}
      title={title}
      footer={
        <>
          <Button variant="ghost" className="flex-1" onClick={onClose} disabled={busy}>
            Voltar
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} className="flex-1" onClick={onConfirm} loading={busy}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p className="text-base text-text">{message}</p>
    </Sheet>
  )
}

export function EmptyState({
  icon: Icon,
  title,
  message,
  action,
}: {
  icon: LucideIcon
  title: string
  message?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
      <Icon className="h-10 w-10 text-secondary" aria-hidden="true" />
      <h2 className="font-serif text-lg font-bold text-text">{title}</h2>
      {message && <p className="text-sm text-text-secondary">{message}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-[14px] bg-error-soft px-4 py-8 text-center">
      <p className="text-base text-error">{message}</p>
      <Button variant="ghost" onClick={onRetry}>
        Tentar de novo
      </Button>
    </div>
  )
}

export function Skeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="space-y-3" aria-busy="true" aria-label="Carregando">
      {Array.from({ length: lines }, (_, index) => (
        <div key={index} className="h-14 rounded-[14px] bg-surface-alt" />
      ))}
    </div>
  )
}

export function LoadMore({
  page,
  onMore,
  loading,
}: {
  page: Page<unknown>['page']
  onMore: () => void
  loading: boolean
}) {
  if (page.number + 1 >= page.totalPages) {
    return null
  }
  return (
    <div className="mt-4 flex justify-center">
      <Button variant="ghost" onClick={onMore} loading={loading}>
        Carregar mais
      </Button>
    </div>
  )
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[]
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div role="group" className="flex rounded-xl bg-surface-alt p-1">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
          className={`min-h-[44px] flex-1 rounded-[10px] px-3 text-sm font-semibold transition-colors ${
            option.value === value ? 'bg-secondary text-primary' : 'text-text-secondary'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
