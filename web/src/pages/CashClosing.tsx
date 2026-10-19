import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { newId, request } from '../api'
import { useAuth } from '../auth'

type Account = { id: string; name: string }
type AccountPage = { _embedded?: { accounts: Account[] } }
type CashClosing = {
  id: string
  accountId: string
  closingDate: string
  pixTotal: number
  cardTotal: number
  cashTotal: number
  expenseTotal: number
  balance: number
}
type ClosingPage = { _embedded?: { cashClosings: CashClosing[] } }
type Profit = { revenue: number; costOfGoods: number; expenses: number; profit: number }

const inputClass =
  'h-12 rounded-md border border-gray-500 bg-white px-3 text-base text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const buttonClass =
  'h-12 rounded-md bg-indigo-700 px-4 font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700 disabled:bg-gray-600'
const linkClass =
  'flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const labelClass = 'font-medium text-gray-900'
const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

function money(value: number): string {
  return currency.format(value)
}

function messageOf(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado'
}

function todayIso(): string {
  return new Date().toLocaleDateString('en-CA')
}

function formatDate(iso: string): string {
  return iso.split('-').reverse().join('/')
}

function ClosingTotals({ closing }: { closing: CashClosing }) {
  return (
    <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-gray-900">
      <dt>Pix</dt>
      <dd className="font-medium">{money(closing.pixTotal)}</dd>
      <dt>Cartão</dt>
      <dd className="font-medium">{money(closing.cardTotal)}</dd>
      <dt>Dinheiro</dt>
      <dd className="font-medium">{money(closing.cashTotal)}</dd>
      <dt>Despesas</dt>
      <dd className="font-medium">{money(closing.expenseTotal)}</dd>
      <dt>Saldo</dt>
      <dd className="font-medium">{money(closing.balance)}</dd>
    </dl>
  )
}

export default function CashClosingPage() {
  const { can } = useAuth()
  const canClose = can('finance:edit')
  const canSeeProfit = can('finance:view')
  const [accounts, setAccounts] = useState<Account[]>([])
  const [accountId, setAccountId] = useState('')
  const [closingDate, setClosingDate] = useState(todayIso)
  const [closingId, setClosingId] = useState(newId)
  const [closings, setClosings] = useState<CashClosing[]>([])
  const [profit, setProfit] = useState<Profit | null>(null)
  const [result, setResult] = useState<CashClosing | null>(null)
  const [loadError, setLoadError] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let ignore = false
    request<AccountPage>('/api/financial-accounts')
      .then((page) => {
        if (!ignore) {
          const loaded = page._embedded?.accounts ?? []
          setAccounts(loaded)
          setAccountId(loaded[0]?.id ?? '')
        }
      })
      .catch((caught) => {
        if (!ignore) {
          setLoadError(messageOf(caught))
        }
      })
    return () => {
      ignore = true
    }
  }, [])

  useEffect(() => {
    if (!accountId) {
      return
    }
    let ignore = false
    request<ClosingPage>(`/api/cash-closings?accountId=${accountId}&sort=closingDate,desc&size=30`)
      .then((page) => {
        if (!ignore) {
          setClosings(page._embedded?.cashClosings ?? [])
        }
      })
      .catch((caught) => {
        if (!ignore) {
          setLoadError(messageOf(caught))
        }
      })
    return () => {
      ignore = true
    }
  }, [accountId, result])

  useEffect(() => {
    if (!canSeeProfit || !accountId) {
      return
    }
    let ignore = false
    request<Profit>(`/api/finance/profit?accountId=${accountId}&from=${closingDate}&to=${closingDate}`)
      .then((loaded) => {
        if (!ignore) {
          setProfit(loaded)
        }
      })
      .catch((caught) => {
        if (!ignore) {
          setLoadError(messageOf(caught))
        }
      })
    return () => {
      ignore = true
    }
  }, [accountId, closingDate, canSeeProfit, result])

  async function handleClose(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      const closing = await request<CashClosing>('/api/cash-closings', {
        method: 'POST',
        body: { id: closingId, accountId, closingDate },
      })
      setResult(closing)
      setClosingId(newId())
      setMessage('Caixa fechado. Os totais estão abaixo.')
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-4">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Fechamento de caixa</h1>
        <Link to="/" className={linkClass}>
          Início
        </Link>
      </header>

      {loadError && (
        <p role="alert" className="mt-4 font-medium text-red-700">
          {loadError}
        </p>
      )}

      <form onSubmit={handleClose} className="mt-6 flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="closing-account" className={labelClass}>
            Conta
          </label>
          <select
            id="closing-account"
            required
            value={accountId}
            onChange={(event) => {
              setAccountId(event.target.value)
              setClosingId(newId())
            }}
            className={inputClass}
          >
            <option value="">Selecione a conta</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="closing-date" className={labelClass}>
            Dia do fechamento
          </label>
          <input
            id="closing-date"
            type="date"
            required
            value={closingDate}
            onChange={(event) => {
              setClosingDate(event.target.value)
              setClosingId(newId())
            }}
            className={inputClass}
          />
        </div>
        {message && (
          <p role="status" className="font-medium text-green-800">
            {message}
          </p>
        )}
        {error && (
          <p role="alert" className="font-medium text-red-700">
            {error}
          </p>
        )}
        {canClose && (
          <button type="submit" disabled={submitting} className={buttonClass}>
            {submitting ? 'Fechando...' : 'Fechar caixa'}
          </button>
        )}
      </form>

      {result && (
        <section aria-labelledby="closing-result-title" className="mt-6 rounded-md border border-gray-500 bg-white p-4">
          <h2 id="closing-result-title" className="text-lg font-bold text-gray-900">
            Totais de {formatDate(result.closingDate)}
          </h2>
          <ClosingTotals closing={result} />
        </section>
      )}

      {canSeeProfit && profit && (
        <section aria-labelledby="profit-title" className="mt-6 rounded-md border border-gray-500 bg-white p-4">
          <h2 id="profit-title" className="text-lg font-bold text-gray-900">
            Lucro do dia {formatDate(closingDate)}
          </h2>
          <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-gray-900">
            <dt>Receita</dt>
            <dd className="font-medium">{money(profit.revenue)}</dd>
            <dt>Custo das mercadorias</dt>
            <dd className="font-medium">{money(profit.costOfGoods)}</dd>
            <dt>Despesas</dt>
            <dd className="font-medium">{money(profit.expenses)}</dd>
            <dt>Lucro</dt>
            <dd className="font-medium">{money(profit.profit)}</dd>
          </dl>
        </section>
      )}

      <section aria-labelledby="closings-title" className="mt-6">
        <h2 id="closings-title" className="text-lg font-bold text-gray-900">
          Fechamentos da conta
        </h2>
        {closings.length === 0 && <p className="mt-2 text-gray-900">Nenhum fechamento nesta conta.</p>}
        <ul className="mt-2 flex flex-col gap-3">
          {closings.map((closing) => (
            <li key={closing.id} className="rounded-md border border-gray-500 bg-white p-4">
              <p className="font-bold text-gray-900">{formatDate(closing.closingDate)}</p>
              <ClosingTotals closing={closing} />
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}
