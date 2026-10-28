import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, request } from '../../api'
import { useToast } from '../../components/Toast'
import { VariantPickerSheet } from '../../components/VariantPicker'
import { Button, Card, Field, Input, PageHeader, Select } from '../../components/ui'
import type { ProductView, VariantResponse, VariantView } from '../../types'

type Mode = 'add' | 'remove'

type Selected = {
  id: string
  label: string
  balance: number
}

const REASONS = ['Perda', 'Avaria', 'Correção de contagem']
const OTHER = 'Outro'

function parseQuantity(text: string): number | null {
  const value = Number(text)
  return text.trim() !== '' && Number.isInteger(value) && value > 0 ? value : null
}

function segmentClass(active: boolean): string {
  return `min-h-[48px] flex-1 rounded-[10px] px-3 text-sm font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-secondary/60 disabled:cursor-not-allowed disabled:opacity-50 ${
    active ? 'bg-secondary text-primary' : 'text-text-secondary'
  }`
}

export default function StockAdjustment() {
  const [params] = useSearchParams()
  const prefillId = params.get('variantId')
  const navigate = useNavigate()
  const { show } = useToast()
  const submitting = useRef(false)

  const [selected, setSelected] = useState<Selected | null>(null)
  const [missing, setMissing] = useState(false)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [mode, setMode] = useState<Mode>('add')
  const [quantity, setQuantity] = useState('')
  const [reasonChoice, setReasonChoice] = useState('')
  const [otherReason, setOtherReason] = useState('')
  const [variantError, setVariantError] = useState('')
  const [quantityError, setQuantityError] = useState('')
  const [reasonError, setReasonError] = useState('')
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!prefillId) {
      return
    }
    let active = true
    async function load() {
      try {
        const variant = await request<VariantResponse>(`/api/variants/${prefillId}`)
        const product = await request<ProductView>(`/api/products/${variant.productId}`)
        if (active) {
          setSelected({
            id: variant.id,
            label: `${product.name} · ${variant.size} · ${variant.color}`,
            balance: variant.balance,
          })
          setMissing(false)
        }
      } catch (caught) {
        if (active && caught instanceof ApiError && caught.status === 404) {
          setSelected(null)
          setMissing(true)
        }
      }
    }
    load()
    return () => {
      active = false
    }
  }, [prefillId])

  function pickVariant(variant: VariantView) {
    setSelected({
      id: variant.id,
      label: `${variant.productName} · ${variant.size} · ${variant.color}`,
      balance: variant.balance,
    })
    setMissing(false)
    setVariantError('')
    setPickerOpen(false)
  }

  const balance = selected?.balance ?? 0
  const canRemove = selected !== null && balance > 0
  const removing = mode === 'remove' && canRemove
  const parsed = parseQuantity(quantity)
  const overLimit = removing && parsed !== null && parsed > balance
  const shownQuantityError = quantityError || (overLimit ? `Máximo para remover: ${balance} un.` : '')
  const result = selected !== null && parsed !== null && !overLimit ? balance + (removing ? -parsed : parsed) : null
  const removeHint =
    selected === null ? '' : balance < 0 ? 'Use Adicionar para corrigir o saldo negativo' : balance === 0 ? 'Sem saldo para remover' : ''

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (submitting.current) {
      return
    }
    setFormError('')
    const quantityValue = parsed
    const reason = reasonChoice === OTHER ? otherReason.trim() : reasonChoice
    const nextVariantError = selected === null ? 'Escolha o produto e a variante' : ''
    const nextQuantityError =
      quantityValue === null
        ? 'Informe uma quantidade inteira maior que zero'
        : overLimit
          ? `Máximo para remover: ${balance} un.`
          : ''
    const nextReasonError =
      reasonChoice === '' ? 'Escolha o motivo' : reasonChoice === OTHER && reason === '' ? 'Descreva o motivo' : ''
    setVariantError(nextVariantError)
    setQuantityError(nextQuantityError)
    setReasonError(nextReasonError)
    if (nextVariantError || nextQuantityError || nextReasonError || selected === null || quantityValue === null) {
      return
    }
    submitting.current = true
    setSaving(true)
    try {
      await request(`/api/variants/${selected.id}/adjustments`, {
        method: 'POST',
        body: { quantity: removing ? -quantityValue : quantityValue, reason },
      })
      show('Ajuste registrado')
      navigate('/stock')
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
    } finally {
      submitting.current = false
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-xl">
      <PageHeader title="Ajuste de estoque" back={{ to: '/stock', label: 'Estoque' }} />
      <form onSubmit={submit} className="space-y-4" noValidate>
        <div>
          <span className="mb-1 block text-sm font-medium text-text">
            Produto e variante<span className="text-error"> *</span>
          </span>
          <button
            type="button"
            onClick={() => setPickerOpen(true)}
            className={`flex min-h-[48px] w-full items-center rounded-[10px] border bg-surface px-3 text-left text-base focus:outline-none focus:ring-2 focus:ring-secondary/60 ${
              variantError ? 'border-error' : 'border-[#D8CBCD]'
            } ${selected ? 'text-text' : 'text-text-secondary'}`}
          >
            {missing ? 'Variante não encontrada' : selected?.label || 'Escolher produto e variante'}
          </button>
          {missing && !variantError && (
            <span className="mt-1 block text-sm text-text-secondary">Escolha outra variante.</span>
          )}
          {variantError && (
            <span role="alert" className="mt-1 block text-sm text-error">
              {variantError}
            </span>
          )}
        </div>

        <Card className="flex items-center justify-between gap-3">
          <span className="text-base text-text-secondary">Quantidade atual</span>
          <span className="text-xl font-semibold text-text">{selected ? balance : '—'}</span>
        </Card>

        <div className="space-y-1">
          <div role="group" aria-label="Tipo de ajuste" className="flex rounded-xl bg-surface-alt p-1">
            <button type="button" aria-pressed={!removing} onClick={() => setMode('add')} className={segmentClass(!removing)}>
              Adicionar
            </button>
            <button
              type="button"
              aria-pressed={removing}
              disabled={!canRemove}
              onClick={() => setMode('remove')}
              className={segmentClass(removing)}
            >
              Remover
            </button>
          </div>
          {removeHint && <span className="block text-sm text-text-secondary">{removeHint}</span>}
        </div>

        <Field label="Quantidade" required error={shownQuantityError}>
          <Input
            inputMode="numeric"
            value={quantity}
            onChange={(event) => {
              setQuantity(event.target.value)
              setQuantityError('')
            }}
            invalid={shownQuantityError !== ''}
          />
        </Field>

        <Field label="Motivo" required error={reasonChoice === '' ? reasonError : ''}>
          <Select
            value={reasonChoice}
            onChange={(event) => {
              setReasonChoice(event.target.value)
              setReasonError('')
            }}
            aria-invalid={reasonChoice === '' && reasonError !== '' ? true : undefined}
          >
            <option value="">Escolha o motivo</option>
            {REASONS.map((reason) => (
              <option key={reason} value={reason}>
                {reason}
              </option>
            ))}
            <option value={OTHER}>{OTHER}</option>
          </Select>
        </Field>

        {reasonChoice === OTHER && (
          <Field label="Descreva o motivo" required error={reasonError}>
            <Input
              value={otherReason}
              onChange={(event) => {
                setOtherReason(event.target.value)
                setReasonError('')
              }}
              invalid={reasonError !== ''}
            />
          </Field>
        )}

        <Card className="flex items-center justify-between gap-3">
          <span className="text-base text-text-secondary">Quantidade resultante</span>
          <span className="text-xl font-semibold text-text">{result === null ? '—' : result}</span>
        </Card>

        {removing && result !== null && selected !== null && (
          <p className="rounded-[14px] bg-surface-alt px-4 py-3 text-base text-text">
            O estoque desta variante passará de {selected.balance} para {result} unidades.
          </p>
        )}

        {formError && (
          <p role="alert" className="rounded-[14px] bg-error-soft px-4 py-3 text-base text-error">
            {formError}
          </p>
        )}
        <Button type="submit" className="w-full" loading={saving}>
          Confirmar ajuste
        </Button>
      </form>

      <VariantPickerSheet open={pickerOpen} onClose={() => setPickerOpen(false)} onPick={pickVariant} />
    </div>
  )
}
