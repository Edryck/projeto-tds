import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { newId, request } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, ConfirmSheet, ErrorState, Field, Input, PageHeader, Skeleton } from '../../components/ui'
import { parseMoney } from '../../format'
import { useLoad } from '../../hooks'
import type { ProductView, VariantResponse } from '../../types'

function parseAmount(text: string): number | null {
  if (text.trim() === '') {
    return null
  }
  const value = parseMoney(text)
  return Number.isFinite(value) && value >= 0 ? value : null
}

function toInput(value: number): string {
  return value.toFixed(2).replace('.', ',')
}

export default function VariantForm() {
  const { id, variantId } = useParams()
  const isNew = variantId === undefined
  const navigate = useNavigate()
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('product:edit')

  const product = useLoad(() => request<ProductView>(`/api/products/${id}`), [id])
  const variant = useLoad(async () => (isNew ? null : request<VariantResponse>(`/api/variants/${variantId}`)), [variantId])

  const [size, setSize] = useState('')
  const [color, setColor] = useState('')
  const [model, setModel] = useState('')
  const [price, setPrice] = useState('')
  const [cost, setCost] = useState('')
  const [sizeError, setSizeError] = useState('')
  const [colorError, setColorError] = useState('')
  const [priceError, setPriceError] = useState('')
  const [costError, setCostError] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    if (variant.data) {
      setSize(variant.data.size)
      setColor(variant.data.color)
      setModel(variant.data.model ?? '')
      setPrice(toInput(variant.data.price))
      setCost(toInput(variant.data.cost))
    }
  }, [variant.data])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const trimmedSize = size.trim()
    const trimmedColor = color.trim()
    const priceValue = parseAmount(price)
    const costValue = parseAmount(cost)
    setSizeError(trimmedSize === '' ? 'Informe o tamanho' : '')
    setColorError(trimmedColor === '' ? 'Informe a cor' : '')
    setPriceError(priceValue === null ? 'Informe um preço válido' : '')
    setCostError(costValue === null ? 'Informe um custo válido' : '')
    if (trimmedSize === '' || trimmedColor === '' || priceValue === null || costValue === null) {
      return
    }
    const trimmedModel = model.trim()
    const body = {
      size: trimmedSize,
      color: trimmedColor,
      model: trimmedModel === '' ? null : trimmedModel,
      price: priceValue,
      cost: costValue,
    }
    setSaving(true)
    try {
      if (isNew) {
        const newVariantId = newId()
        await request<VariantResponse>(`/api/products/${id}/variants`, {
          method: 'POST',
          body: { id: newVariantId, ...body },
        })
        toast.show('Variante criada', 'success', {
          label: 'Registrar entrada de estoque',
          to: `/stock/entry?variantId=${newVariantId}`,
        })
        navigate(`/catalog/${id}`, { replace: true })
      } else {
        await request<VariantResponse>(`/api/variants/${variantId}`, { method: 'PUT', body: { id: variantId, ...body } })
        toast.show('Variante salva')
        navigate(`/catalog/${id}`)
      }
    } catch (caught) {
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setSaving(false)
    }
  }

  async function remove() {
    setDeleting(true)
    try {
      await request<void>(`/api/variants/${variantId}`, { method: 'DELETE' })
      toast.show('Variante excluída')
      navigate(`/catalog/${id}`, { replace: true })
    } catch (caught) {
      setConfirmingDelete(false)
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setDeleting(false)
    }
  }

  if (!isNew && variant.loading && !variant.data) {
    return <Skeleton lines={4} />
  }
  if (!isNew && variant.error && !variant.data) {
    return <ErrorState message={variant.error} onRetry={variant.reload} />
  }

  const productName = product.data?.name

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={isNew ? 'Nova variante' : 'Editar variante'}
        subtitle={productName}
        back={{ to: `/catalog/${id}`, label: 'Produto' }}
      />
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Tamanho" required error={sizeError}>
          <Input value={size} onChange={(event) => setSize(event.target.value)} invalid={sizeError !== ''} disabled={!canEdit} />
        </Field>
        <Field label="Cor" required error={colorError}>
          <Input value={color} onChange={(event) => setColor(event.target.value)} invalid={colorError !== ''} disabled={!canEdit} />
        </Field>
        <Field label="Modelo">
          <Input value={model} onChange={(event) => setModel(event.target.value)} disabled={!canEdit} />
        </Field>
        <Field label="Preço de venda" required error={priceError} hint="Ex.: 89,90">
          <Input
            inputMode="decimal"
            value={price}
            onChange={(event) => setPrice(event.target.value)}
            invalid={priceError !== ''}
            disabled={!canEdit}
          />
        </Field>
        <Field label="Custo" required error={costError} hint="Usado no cálculo de lucro. Não aparece na venda.">
          <Input
            inputMode="decimal"
            value={cost}
            onChange={(event) => setCost(event.target.value)}
            invalid={costError !== ''}
            disabled={!canEdit}
          />
        </Field>
        {!isNew && variant.data && (
          <Card>
            <p className="text-sm text-text-secondary">Saldo atual</p>
            <p className="text-lg font-semibold text-text">{variant.data.balance}</p>
          </Card>
        )}
        {canEdit && (
          <div className="flex gap-2">
            <Button variant="ghost" className="flex-1" onClick={() => navigate(`/catalog/${id}`)} disabled={saving}>
              Cancelar
            </Button>
            <Button type="submit" className="flex-1" loading={saving}>
              Salvar variante
            </Button>
          </div>
        )}
      </form>

      {canEdit && !isNew && (
        <div className="mt-8">
          <Button variant="danger" className="w-full" onClick={() => setConfirmingDelete(true)}>
            Excluir variante
          </Button>
        </div>
      )}
      <ConfirmSheet
        open={confirmingDelete}
        title="Excluir variante"
        message="A variante deixa de aparecer no catálogo. Deseja continuar?"
        confirmLabel="Excluir"
        danger
        busy={deleting}
        onConfirm={remove}
        onClose={() => setConfirmingDelete(false)}
      />
    </div>
  )
}
