import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CheckCircle2, Plus, Trash2 } from 'lucide-react'
import { ApiError, fetchPage, newId, request } from '../../api'
import { VariantPickerSheet } from '../../components/VariantPicker'
import { Button, Card, Field, Input, LinkButton, PageHeader, Select, Textarea } from '../../components/ui'
import { money, parseMoney, todayIso } from '../../format'
import { useLoad } from '../../hooks'
import type { ProductView, VariantResponse, VariantView } from '../../types'

type Supplier = { id: string; name: string }

type Item = {
  key: string
  id: string
  variantId: string | null
  label: string
  missing: boolean
  quantity: string
  unitCost: string
  variantError: string
  quantityError: string
  costError: string
}

type Done = { units: number; variants: number }

function toInput(value: number): string {
  return value.toFixed(2).replace('.', ',')
}

function emptyItem(): Item {
  return {
    key: newId(),
    id: newId(),
    variantId: null,
    label: '',
    missing: false,
    quantity: '',
    unitCost: '',
    variantError: '',
    quantityError: '',
    costError: '',
  }
}

function parseQuantity(text: string): number | null {
  const value = Number(text)
  return text.trim() !== '' && Number.isInteger(value) && value > 0 ? value : null
}

function parseCost(text: string): number | null {
  if (text.trim() === '') {
    return null
  }
  const value = parseMoney(text)
  return Number.isFinite(value) && value >= 0 ? value : null
}

export default function StockEntryForm() {
  const [params] = useSearchParams()
  const prefillId = params.get('variantId')

  const suppliers = useLoad(async () => (await fetchPage<Supplier>('/api/suppliers?size=100')).items, [])

  const [entryId, setEntryId] = useState(newId)
  const [supplierId, setSupplierId] = useState('')
  const [entryDate, setEntryDate] = useState(todayIso)
  const [note, setNote] = useState('')
  const [items, setItems] = useState<Item[]>(() => [emptyItem()])
  const [pickingKey, setPickingKey] = useState<string | null>(null)
  const [dateError, setDateError] = useState('')
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [done, setDone] = useState<Done | null>(null)

  useEffect(() => {
    if (!prefillId) {
      return
    }
    let active = true
    async function load() {
      let patch: Partial<Item>
      try {
        const variant = await request<VariantResponse>(`/api/variants/${prefillId}`)
        const product = await request<ProductView>(`/api/products/${variant.productId}`)
        patch = {
          variantId: variant.id,
          label: `${product.name} · ${variant.size} · ${variant.color}`,
          unitCost: toInput(variant.cost),
          missing: false,
        }
      } catch (caught) {
        if (caught instanceof ApiError && caught.status === 404) {
          patch = { variantId: null, label: '', missing: true }
        } else {
          return
        }
      }
      if (active) {
        setItems((current) => current.map((item, index) => (index === 0 ? { ...item, ...patch } : item)))
      }
    }
    load()
    return () => {
      active = false
    }
  }, [prefillId])

  function updateItem(key: string, patch: Partial<Item>) {
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...patch } : item)))
  }

  function pickVariant(variant: VariantView) {
    if (pickingKey) {
      updateItem(pickingKey, {
        variantId: variant.id,
        label: `${variant.productName} · ${variant.size} · ${variant.color}`,
        unitCost: toInput(variant.cost),
        missing: false,
        variantError: '',
        costError: '',
      })
    }
    setPickingKey(null)
  }

  function removeItem(key: string) {
    setItems((current) => current.filter((item) => item.key !== key))
  }

  const validQuantities = items.map((item) => parseQuantity(item.quantity))
  const validCosts = items.map((item) => parseCost(item.unitCost))
  const totalUnits = validQuantities.reduce<number>((sum, value) => sum + (value ?? 0), 0)
  const totalCost = items.reduce((sum, _item, index) => sum + (validQuantities[index] ?? 0) * (validCosts[index] ?? 0), 0)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setFormError('')
    const checked = items.map((item) => ({
      ...item,
      variantError: item.variantId === null ? 'Escolha o produto e a variante' : '',
      quantityError: parseQuantity(item.quantity) === null ? 'Informe uma quantidade inteira maior que zero' : '',
      costError: parseCost(item.unitCost) === null ? 'Informe um custo válido' : '',
    }))
    setItems(checked)
    setDateError(entryDate === '' ? 'Informe a data de recebimento' : '')
    const invalid = checked.some((item) => item.variantError || item.quantityError || item.costError)
    if (invalid || entryDate === '') {
      return
    }
    setSaving(true)
    try {
      await request('/api/stock-entries', {
        method: 'POST',
        body: {
          id: entryId,
          supplierId: supplierId === '' ? null : supplierId,
          entryDate,
          note: note.trim() === '' ? null : note.trim(),
          items: checked.map((item) => ({
            id: item.id,
            variantId: item.variantId,
            quantity: parseQuantity(item.quantity),
            unitCost: parseCost(item.unitCost),
          })),
        },
      })
      const distinct = new Set(checked.map((item) => item.variantId))
      setDone({ units: totalUnits, variants: distinct.size })
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
    } finally {
      setSaving(false)
    }
  }

  function reset() {
    setEntryId(newId())
    setSupplierId('')
    setEntryDate(todayIso())
    setNote('')
    setItems([emptyItem()])
    setDateError('')
    setFormError('')
    setDone(null)
  }

  if (done) {
    return (
      <div className="mx-auto max-w-md space-y-4 py-6 text-center">
        <CheckCircle2 className="mx-auto h-14 w-14 text-accent" aria-hidden="true" />
        <h1 className="font-serif text-2xl font-bold text-text">
          Entrada registrada: {done.units} {done.units === 1 ? 'unidade' : 'unidades'} em {done.variants}{' '}
          {done.variants === 1 ? 'variante' : 'variantes'}
        </h1>
        <div className="flex flex-col gap-2">
          <Button onClick={reset}>Registrar outra</Button>
          <LinkButton to="/stock" variant="ghost">
            Ver estoque
          </LinkButton>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Entrada de mercadoria" back={{ to: '/stock', label: 'Estoque' }} />
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Fornecedor">
          <Select value={supplierId} onChange={(event) => setSupplierId(event.target.value)}>
            <option value="">Sem fornecedor</option>
            {(suppliers.data ?? []).map((supplier) => (
              <option key={supplier.id} value={supplier.id}>
                {supplier.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Data de recebimento" required error={dateError}>
          <Input
            type="date"
            value={entryDate}
            onChange={(event) => setEntryDate(event.target.value)}
            invalid={dateError !== ''}
          />
        </Field>
        <Field label="Observação">
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} />
        </Field>

        <h2 className="pt-2 text-lg font-semibold text-text">Itens</h2>
        {items.map((item, index) => (
          <Card key={item.key} className="space-y-3">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-medium text-text-secondary">Item {index + 1}</span>
              {items.length > 1 && (
                <button
                  type="button"
                  onClick={() => removeItem(item.key)}
                  aria-label={`Remover item ${index + 1}`}
                  className="flex h-12 w-12 items-center justify-center rounded-xl text-error hover:bg-surface-alt focus:outline-none focus:ring-2 focus:ring-secondary/60"
                >
                  <Trash2 className="h-5 w-5" aria-hidden="true" />
                </button>
              )}
            </div>
            <div>
              <span className="mb-1 block text-sm font-medium text-text">
                Produto e variante<span className="text-error"> *</span>
              </span>
              <button
                type="button"
                onClick={() => setPickingKey(item.key)}
                className={`flex min-h-[48px] w-full items-center rounded-[10px] border bg-surface px-3 text-left text-base focus:outline-none focus:ring-2 focus:ring-secondary/60 ${
                  item.variantError ? 'border-error' : 'border-[#D8CBCD]'
                } ${item.label ? 'text-text' : 'text-text-secondary'}`}
              >
                {item.missing ? 'Variante não encontrada' : item.label || 'Escolher produto e variante'}
              </button>
              {item.missing && !item.variantError && (
                <span className="mt-1 block text-sm text-text-secondary">Escolha outra variante.</span>
              )}
              {item.variantError && (
                <span role="alert" className="mt-1 block text-sm text-error">
                  {item.variantError}
                </span>
              )}
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Quantidade" required error={item.quantityError}>
                <Input
                  inputMode="numeric"
                  value={item.quantity}
                  onChange={(event) => updateItem(item.key, { quantity: event.target.value, quantityError: '' })}
                  invalid={item.quantityError !== ''}
                />
              </Field>
              <Field label="Custo unitário" required error={item.costError}>
                <Input
                  inputMode="decimal"
                  value={item.unitCost}
                  onChange={(event) => updateItem(item.key, { unitCost: event.target.value, costError: '' })}
                  invalid={item.costError !== ''}
                />
              </Field>
            </div>
          </Card>
        ))}
        <Button variant="ghost" className="w-full" onClick={() => setItems((current) => [...current, emptyItem()])}>
          <Plus className="h-5 w-5" aria-hidden="true" />
          Adicionar item
        </Button>

        <Card className="space-y-1">
          <div className="flex justify-between text-base text-text">
            <span className="text-text-secondary">Unidades</span>
            <span className="font-semibold">{totalUnits}</span>
          </div>
          <div className="flex justify-between text-base text-text">
            <span className="text-text-secondary">Custo total</span>
            <span className="font-semibold">{money(totalCost)}</span>
          </div>
        </Card>

        {formError && (
          <p role="alert" className="rounded-[14px] bg-error-soft px-4 py-3 text-base text-error">
            {formError}
          </p>
        )}
        <Button type="submit" className="w-full" loading={saving}>
          Registrar entrada
        </Button>
      </form>

      <VariantPickerSheet open={pickingKey !== null} onClose={() => setPickingKey(null)} onPick={pickVariant} />
    </div>
  )
}
