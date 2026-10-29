import { useEffect, useRef, useState } from 'react'
import { CheckCircle2, Lock, TriangleAlert } from 'lucide-react'
import { ApiError, fetchPage, newId, request } from '../../api'
import { useAuth } from '../../auth'
import { Button, Card, ConfirmSheet, ErrorState, Field, Input, LinkButton, PageHeader, Segmented, Skeleton, StatusPill, Textarea } from '../../components/ui'
import { brDate, money, parseMoney, todayIso } from '../../format'
import { useLoad } from '../../hooks'
import { accountLabels } from '../../types'
import type { Account } from '../../types'

type Preview = {
  accountId: string
  closingDate: string
  pixTotal: number
  cardTotal: number
  cashTotal: number
  expenseTotal: number
  closingId: string | null
}

type Closing = {
  id: string
  closingDate: string
  pixTotal: number
  cardTotal: number
  cashTotal: number
  expenseTotal: number
  countedPix: number | null
  countedCard: number | null
  countedCash: number | null
  pixDifference: number | null
  cardDifference: number | null
  cashDifference: number | null
  totalDifference: number | null
  note: string | null
}

type Loaded = { preview: Preview; closing: Closing | null }
type MethodKey = 'pix' | 'card' | 'cash'

const methodRows: { key: MethodKey; label: string }[] = [
  { key: 'pix', label: 'Pix' },
  { key: 'card', label: 'Cartão' },
  { key: 'cash', label: 'Dinheiro' },
]

function parseCounted(text: string): number | null {
  if (text.trim() === '') {
    return null
  }
  const value = parseMoney(text)
  if (!Number.isFinite(value) || value < 0) {
    return null
  }
  return value
}

function cents(value: number): number {
  return Math.round(value * 100) / 100
}

function Difference({ value }: { value: number | null }) {
  if (value === null) {
    return <span className="text-text-secondary">—</span>
  }
  const rounded = cents(value)
  if (rounded === 0) {
    return <StatusPill tone="success" icon={CheckCircle2}>Confere</StatusPill>
  }
  if (rounded > 0) {
    return <StatusPill tone="warning" icon={TriangleAlert}>{`+${money(rounded)} a mais`}</StatusPill>
  }
  return <StatusPill tone="danger" icon={TriangleAlert}>{`−${money(Math.abs(rounded))} a menos`}</StatusPill>
}

function Line({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3 text-base text-text">
      <span className="text-text-secondary">{label}</span>
      <span className="font-semibold">{children}</span>
    </div>
  )
}

function ClosingSummary({ closing }: { closing: Closing }) {
  const expected: Record<MethodKey, number> = { pix: closing.pixTotal, card: closing.cardTotal, cash: closing.cashTotal }
  const counted: Record<MethodKey, number | null> = {
    pix: closing.countedPix,
    card: closing.countedCard,
    cash: closing.countedCash,
  }
  const differences: Record<MethodKey, number | null> = {
    pix: closing.pixDifference,
    card: closing.cardDifference,
    cash: closing.cashDifference,
  }
  const expectedTotal = closing.pixTotal + closing.cardTotal + closing.cashTotal
  const hasCounted = closing.countedPix !== null && closing.countedCard !== null && closing.countedCash !== null
  const countedTotal = hasCounted ? (closing.countedPix ?? 0) + (closing.countedCard ?? 0) + (closing.countedCash ?? 0) : null

  return (
    <div className="space-y-3">
      {methodRows.map((row) => (
        <Card key={row.key} className="space-y-2">
          <h2 className="text-base font-semibold text-text">{row.label}</h2>
          <Line label="Esperado">{money(expected[row.key])}</Line>
          <Line label="Contado">{counted[row.key] === null ? '—' : money(counted[row.key] ?? 0)}</Line>
          <div className="flex justify-end">
            <Difference value={differences[row.key]} />
          </div>
        </Card>
      ))}
      <Card className="space-y-2">
        <Line label="Despesas do dia">{money(closing.expenseTotal)}</Line>
        <Line label="Total esperado">{money(expectedTotal)}</Line>
        <Line label="Total contado">{countedTotal === null ? '—' : money(countedTotal)}</Line>
        <div className="flex justify-end">
          <Difference value={closing.totalDifference} />
        </div>
        {closing.note && <p className="border-t border-border pt-2 text-base text-text">{closing.note}</p>}
      </Card>
    </div>
  )
}

export default function CashClosing() {
  const { can } = useAuth()
  const canClose = can('finance:edit')
  const accounts = useLoad(async () => (await fetchPage<Account>('/api/financial-accounts')).items, [])
  const [accountId, setAccountId] = useState('')
  const [closingDate, setClosingDate] = useState(todayIso())
  const [counted, setCounted] = useState<Record<MethodKey, string>>({ pix: '', card: '', cash: '' })
  const [note, setNote] = useState('')
  const [tried, setTried] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [done, setDone] = useState<Closing | null>(null)
  const closingId = useRef(newId())

  useEffect(() => {
    if (accountId === '' && accounts.data) {
      const store = accounts.data.find((account) => account.type === 'STORE')
      setAccountId((store ?? accounts.data[0])?.id ?? '')
    }
  }, [accounts.data, accountId])

  const loaded = useLoad<Loaded | null>(async () => {
    if (accountId === '' || closingDate === '') {
      return null
    }
    const preview = await request<Preview>(`/api/cash-closings/preview?accountId=${accountId}&date=${closingDate}`)
    const closing = preview.closingId ? await request<Closing>(`/api/cash-closings/${preview.closingId}`) : null
    return { preview, closing }
  }, [accountId, closingDate])

  function resetForm() {
    closingId.current = newId()
    setCounted({ pix: '', card: '', cash: '' })
    setNote('')
    setTried(false)
    setFormError('')
    setDone(null)
  }

  function changeAccount(value: string) {
    setAccountId(value)
    resetForm()
  }

  function changeDate(value: string) {
    setClosingDate(value)
    resetForm()
  }

  const preview = loaded.data?.preview ?? null
  const saved = loaded.data?.closing ?? null
  const expected: Record<MethodKey, number> = preview
    ? { pix: preview.pixTotal, card: preview.cardTotal, cash: preview.cashTotal }
    : { pix: 0, card: 0, cash: 0 }
  const values: Record<MethodKey, number | null> = {
    pix: parseCounted(counted.pix),
    card: parseCounted(counted.card),
    cash: parseCounted(counted.cash),
  }
  const allFilled = values.pix !== null && values.card !== null && values.cash !== null
  const expectedTotal = expected.pix + expected.card + expected.cash
  const countedTotal = allFilled ? (values.pix ?? 0) + (values.card ?? 0) + (values.cash ?? 0) : null

  function review() {
    setTried(true)
    setFormError('')
    if (allFilled) {
      setConfirming(true)
    }
  }

  async function confirm() {
    if (!preview) {
      return
    }
    setSaving(true)
    try {
      const trimmed = note.trim()
      const closing = await request<Closing>('/api/cash-closings', {
        method: 'POST',
        body: {
          id: closingId.current,
          accountId,
          closingDate,
          countedPix: values.pix,
          countedCard: values.card,
          countedCash: values.cash,
          note: trimmed === '' ? null : trimmed,
        },
      })
      setConfirming(false)
      setDone(closing)
    } catch (caught) {
      setConfirming(false)
      setFormError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
      if (caught instanceof ApiError && caught.status === 409) {
        loaded.reload()
      }
    } finally {
      setSaving(false)
    }
  }

  if (done) {
    return (
      <div className="mx-auto max-w-md space-y-4 py-6">
        <div className="text-center">
          <CheckCircle2 className="mx-auto h-14 w-14 text-accent" aria-hidden="true" />
          <h1 className="mt-3 font-serif text-2xl font-bold text-text">Fechamento registrado</h1>
          <p className="mt-1 text-base text-text-secondary">{brDate(done.closingDate)}</p>
        </div>
        <ClosingSummary closing={done} />
        <LinkButton to="/finance" className="w-full">
          Voltar aos lançamentos
        </LinkButton>
      </div>
    )
  }

  const showAccounts = accounts.data !== null && accounts.data.length > 1

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Fechamento de caixa" back={{ to: '/finance', label: 'Lançamentos' }} />
      <div className="space-y-4">
        {showAccounts && (
          <Segmented
            options={(accounts.data ?? []).map((account) => ({ value: account.id, label: accountLabels[account.type] }))}
            value={accountId}
            onChange={changeAccount}
          />
        )}
        <Field label="Data">
          <Input type="date" value={closingDate} onChange={(event) => changeDate(event.target.value)} />
        </Field>
      </div>

      <div className="mt-4">
        {accounts.error && !accounts.data && <ErrorState message={accounts.error} onRetry={accounts.reload} />}
        {loaded.loading && !loaded.data && <Skeleton lines={4} />}
        {loaded.error && !loaded.data && <ErrorState message={loaded.error} onRetry={loaded.reload} />}
        {formError && (
          <p role="alert" className="mb-3 rounded-[10px] bg-error-soft px-3 py-2 text-sm text-error">
            {formError}
          </p>
        )}

        {preview && saved && (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-base font-semibold text-text">
              <Lock className="h-5 w-5" aria-hidden="true" />
              Caixa fechado em {brDate(saved.closingDate)}
            </div>
            <ClosingSummary closing={saved} />
          </div>
        )}

        {preview && !saved && (
          <form
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              review()
            }}
            className="space-y-3"
          >
            {methodRows.map((row) => {
              const value = values[row.key]
              const missing = tried && value === null
              return (
                <Card key={row.key} className="space-y-3">
                  <h2 className="text-base font-semibold text-text">{row.label}</h2>
                  <Line label="Esperado">{money(expected[row.key])}</Line>
                  {canClose && (
                    <>
                      <Field label="Contado" error={missing ? 'Informe o valor contado' : undefined} hint="Ex.: 540,00">
                        <Input
                          inputMode="decimal"
                          value={counted[row.key]}
                          onChange={(event) => setCounted({ ...counted, [row.key]: event.target.value })}
                          invalid={missing}
                        />
                      </Field>
                      <div className="flex justify-end">
                        <Difference value={value === null ? null : value - expected[row.key]} />
                      </div>
                    </>
                  )}
                </Card>
              )
            })}
            <Card className="space-y-2">
              <Line label="Despesas do dia">{money(preview.expenseTotal)}</Line>
              <Line label="Total esperado">{money(expectedTotal)}</Line>
              {canClose && (
                <>
                  <Line label="Total contado">{countedTotal === null ? '—' : money(countedTotal)}</Line>
                  <div className="flex justify-end">
                    <Difference value={countedTotal === null ? null : countedTotal - expectedTotal} />
                  </div>
                </>
              )}
            </Card>
            {canClose && (
              <>
                <Field label="Observação">
                  <Textarea value={note} maxLength={1000} onChange={(event) => setNote(event.target.value)} />
                </Field>
                <Button type="submit" className="w-full">
                  Conferir fechamento
                </Button>
              </>
            )}
          </form>
        )}
      </div>

      <ConfirmSheet
        open={confirming}
        title="Confirmar fechamento?"
        message="Depois de confirmar, os lançamentos deste dia ficam bloqueados."
        confirmLabel="Confirmar"
        busy={saving}
        onConfirm={confirm}
        onClose={() => setConfirming(false)}
      />
    </div>
  )
}
