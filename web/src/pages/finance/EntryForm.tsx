import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { ArrowDownCircle, ArrowUpCircle } from 'lucide-react'
import { ApiError, fetchPage, newId, request } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, ConfirmSheet, ErrorState, Field, Input, PageHeader, Select, Skeleton, Textarea } from '../../components/ui'
import { parseMoney, todayIso } from '../../format'
import { useLoad } from '../../hooks'
import { paymentLabels } from '../../types'
import type { Account, Entry, EntryType, FinancialCategoryView, PaymentMethod } from '../../types'

function toInput(value: number): string {
  return value.toFixed(2).replace('.', ',')
}

export default function EntryForm() {
  const { id } = useParams()
  const isNew = id === undefined
  const navigate = useNavigate()
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('finance:edit')

  const entry = useLoad(async () => (isNew ? null : request<Entry>(`/api/financial-entries/${id}`)), [id])
  const categories = useLoad(async () => (await fetchPage<FinancialCategoryView>('/api/financial-categories?size=100')).items, [])
  const accounts = useLoad(async () => (await fetchPage<Account>('/api/financial-accounts')).items, [])

  const [type, setType] = useState<EntryType>('EXPENSE')
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [entryDate, setEntryDate] = useState(todayIso())
  const [categoryId, setCategoryId] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('')
  const [note, setNote] = useState('')
  const [accountId, setAccountId] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    if (entry.data) {
      setType(entry.data.type)
      setDescription(entry.data.description ?? '')
      setAmount(toInput(entry.data.amount))
      setEntryDate(entry.data.entryDate)
      setCategoryId(entry.data.categoryId)
      setPaymentMethod(entry.data.paymentMethod ?? '')
      setNote(entry.data.note ?? '')
      setAccountId(entry.data.accountId)
    }
  }, [entry.data])

  useEffect(() => {
    if (isNew && accounts.data && accountId === '') {
      const store = accounts.data.find((account) => account.type === 'STORE')
      setAccountId((store ?? accounts.data[0])?.id ?? '')
    }
  }, [isNew, accounts.data, accountId])

  function changeType(next: EntryType) {
    setType(next)
    setCategoryId('')
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    const trimmedDescription = description.trim()
    const value = parseMoney(amount)
    const next: Record<string, string> = {}
    if (trimmedDescription === '') {
      next.description = 'Informe a descrição'
    }
    if (amount.trim() === '' || !Number.isFinite(value) || value <= 0) {
      next.amount = 'Informe um valor maior que zero'
    }
    if (entryDate === '') {
      next.entryDate = 'Informe a data'
    }
    if (categoryId === '') {
      next.categoryId = 'Escolha a categoria'
    }
    setErrors(next)
    setFormError('')
    if (Object.keys(next).length > 0 || accountId === '') {
      return
    }
    const trimmedNote = note.trim()
    const body = {
      accountId,
      categoryId,
      type,
      amount: value,
      paymentMethod: paymentMethod === '' ? null : paymentMethod,
      entryDate,
      description: trimmedDescription,
      note: trimmedNote === '' ? null : trimmedNote,
    }
    setSaving(true)
    try {
      if (isNew) {
        await request<Entry>('/api/financial-entries', { method: 'POST', body: { id: newId(), ...body } })
        toast.show('Lançamento salvo')
      } else {
        await request<Entry>(`/api/financial-entries/${id}`, { method: 'PUT', body: { id, ...body } })
        toast.show('Lançamento salvo')
      }
      navigate('/finance', { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError && Object.keys(caught.fields).length > 0) {
        setErrors(caught.fields)
      } else {
        setFormError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
      }
    } finally {
      setSaving(false)
    }
  }

  async function remove() {
    setDeleting(true)
    try {
      await request<void>(`/api/financial-entries/${id}`, { method: 'DELETE' })
      toast.show('Lançamento excluído')
      navigate('/finance', { replace: true })
    } catch (caught) {
      setConfirmingDelete(false)
      setFormError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
    } finally {
      setDeleting(false)
    }
  }

  if (!isNew && entry.loading && !entry.data) {
    return <Skeleton lines={4} />
  }
  if (!isNew && entry.error && !entry.data) {
    return <ErrorState message={entry.error} onRetry={entry.reload} />
  }
  if (entry.data?.orderId) {
    return <Navigate to={`/sales/${entry.data.orderId}`} replace />
  }

  const typeOptions: { value: EntryType; label: string; icon: typeof ArrowUpCircle }[] = [
    { value: 'INCOME', label: 'Receita', icon: ArrowUpCircle },
    { value: 'EXPENSE', label: 'Despesa', icon: ArrowDownCircle },
  ]
  const filteredCategories = (categories.data ?? []).filter((category) => category.type === type)

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={isNew ? 'Novo lançamento' : canEdit ? 'Editar lançamento' : 'Lançamento'}
        back={{ to: '/finance', label: 'Lançamentos' }}
      />
      <form onSubmit={submit} className="space-y-4" noValidate>
        <div role="group" aria-label="Tipo" className="grid grid-cols-2 gap-3">
          {typeOptions.map((option) => {
            const Icon = option.icon
            const active = type === option.value
            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={active}
                disabled={!canEdit}
                onClick={() => changeType(option.value)}
                className={`flex min-h-[64px] flex-col items-center justify-center gap-1 rounded-[14px] border text-base font-semibold transition-colors disabled:cursor-not-allowed ${
                  active ? 'border-secondary bg-secondary text-primary' : 'border-border bg-surface text-text'
                }`}
              >
                <Icon className="h-6 w-6" aria-hidden="true" />
                {option.label}
              </button>
            )
          })}
        </div>
        <Field label="Descrição" required error={errors.description}>
          <Input
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            invalid={Boolean(errors.description)}
            disabled={!canEdit}
          />
        </Field>
        <Field label="Valor" required error={errors.amount} hint="Ex.: 150,00">
          <Input
            inputMode="decimal"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            invalid={Boolean(errors.amount)}
            disabled={!canEdit}
          />
        </Field>
        <Field label="Data" required error={errors.entryDate}>
          <Input
            type="date"
            value={entryDate}
            onChange={(event) => setEntryDate(event.target.value)}
            invalid={Boolean(errors.entryDate)}
            disabled={!canEdit}
          />
        </Field>
        <Field label="Categoria" required error={errors.categoryId}>
          <Select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} disabled={!canEdit}>
            <option value="">Escolha</option>
            {filteredCategories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Forma de pagamento">
          <Select value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value)} disabled={!canEdit}>
            <option value="">Não informar</option>
            {(Object.keys(paymentLabels) as PaymentMethod[]).map((method) => (
              <option key={method} value={method}>
                {paymentLabels[method]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Observação" error={errors.note}>
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} disabled={!canEdit} />
        </Field>
        {formError && (
          <p role="alert" className="rounded-[10px] bg-error-soft px-3 py-2 text-sm text-error">
            {formError}
          </p>
        )}
        {canEdit && (
          <div className="flex gap-2">
            <Button variant="ghost" className="flex-1" onClick={() => navigate('/finance')} disabled={saving}>
              Cancelar
            </Button>
            <Button type="submit" className="flex-1" loading={saving}>
              Salvar lançamento
            </Button>
          </div>
        )}
      </form>

      {canEdit && !isNew && (
        <div className="mt-8">
          <Button variant="danger" className="w-full" onClick={() => setConfirmingDelete(true)}>
            Excluir lançamento
          </Button>
        </div>
      )}
      <ConfirmSheet
        open={confirmingDelete}
        title="Excluir lançamento"
        message="O lançamento deixa de aparecer no extrato e no resumo. Deseja continuar?"
        confirmLabel="Excluir"
        danger
        busy={deleting}
        onConfirm={remove}
        onClose={() => setConfirmingDelete(false)}
      />
    </div>
  )
}
