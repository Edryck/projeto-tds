import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate } from 'react-router-dom'
import { Eye, EyeOff } from 'lucide-react'
import { ApiError, logoUrl, swapToFallbackLogo } from '../api'
import { useAuth } from '../auth'
import { Button, Field, Input } from '../components/ui'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const SESSION_EXPIRED_MESSAGE = 'Sua sessão expirou, entre novamente'

function validateEmail(value: string): string {
  if (!value.trim()) return 'Informe o e-mail'
  if (!EMAIL_PATTERN.test(value.trim())) return 'E-mail inválido'
  return ''
}

function validatePassword(value: string): string {
  return value ? '' : 'Informe a senha'
}

function loginErrorMessage(caught: unknown): string {
  if (!(caught instanceof ApiError)) return 'Ocorreu um erro inesperado'
  if (caught.status === 401) return 'E-mail ou senha incorretos'
  if (caught.status === 429) return 'Muitas tentativas. Aguarde um pouco'
  if (caught.status === 0) return 'Sem conexão com o servidor'
  return caught.message
}

export default function Login() {
  const { me, login, sessionExpired } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [emailError, setEmailError] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const [apiError, setApiError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [logoVersion] = useState(() => Date.now())

  if (me) {
    return <Navigate to="/" replace />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const nextEmailError = validateEmail(email)
    const nextPasswordError = validatePassword(password)
    setEmailError(nextEmailError)
    setPasswordError(nextPasswordError)
    setApiError('')
    if (nextEmailError || nextPasswordError || submitting) {
      return
    }
    setSubmitting(true)
    try {
      await login(email.trim(), password)
    } catch (caught) {
      setApiError(loginErrorMessage(caught))
      setSubmitting(false)
    }
  }

  const banner = apiError || (sessionExpired ? SESSION_EXPIRED_MESSAGE : '')

  return (
    <main className="flex min-h-screen items-center justify-center bg-background px-4 py-8">
      <div className="w-full max-w-sm">
        <div className="mx-auto mb-6 flex h-28 w-28 items-center justify-center rounded-2xl bg-surface p-2">
          <img
            src={logoUrl(logoVersion)}
            alt="Chic Charmosa"
            onError={swapToFallbackLogo}
            className="h-full w-full object-contain"
          />
        </div>
        <h1 className="font-serif text-2xl font-bold text-text">Bem-vinda de volta</h1>
        <p className="mb-6 mt-1 text-base text-text-secondary">Entre para gerenciar sua loja</p>

        {banner && (
          <p role="alert" className="mb-4 rounded-[10px] bg-error-soft px-4 py-3 text-sm font-medium text-error">
            {banner}
          </p>
        )}

        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          <Field label="E-mail" required error={emailError}>
            <Input
              type="email"
              inputMode="email"
              autoComplete="username"
              value={email}
              invalid={Boolean(emailError)}
              onChange={(event) => setEmail(event.target.value)}
              onBlur={() => setEmailError(validateEmail(email))}
            />
          </Field>

          <Field label="Senha" required error={passwordError}>
            <div className="relative">
              <Input
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                value={password}
                invalid={Boolean(passwordError)}
                className="pr-14"
                onChange={(event) => setPassword(event.target.value)}
                onBlur={() => setPasswordError(validatePassword(password))}
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                aria-label={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
                className="absolute right-0 top-0 flex h-12 w-12 items-center justify-center rounded-r-[10px] text-text-secondary hover:text-text focus:outline-none focus-visible:ring-2 focus-visible:ring-secondary/40"
              >
                {showPassword ? <EyeOff className="h-5 w-5" aria-hidden="true" /> : <Eye className="h-5 w-5" aria-hidden="true" />}
              </button>
            </div>
          </Field>

          <Button type="submit" className="w-full" loading={submitting}>
            {submitting ? 'Entrando…' : 'Entrar'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-text-secondary">
          Esqueceu a senha? Fale com a administradora da loja.
        </p>
      </div>
    </main>
  )
}
