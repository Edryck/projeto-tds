import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { newId, request } from '../api'

type Product = { id: string; name: string }
type Variant = { id: string; color: string; size: string }
type ProductPage = { _embedded?: { products: Product[] } }
type VariantPage = { _embedded?: { variants: Variant[] } }
type VariantOption = { id: string; label: string }

const inputClass =
  'h-12 rounded-md border border-gray-500 bg-white px-3 text-base text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const buttonClass =
  'h-12 rounded-md bg-indigo-700 px-4 font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700 disabled:bg-gray-600'
const linkClass =
  'flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const labelClass = 'font-medium text-gray-900'

function messageOf(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado'
}

function todayIso(): string {
  return new Date().toLocaleDateString('en-CA')
}

async function fetchVariantOptions(): Promise<VariantOption[]> {
  const productPage = await request<ProductPage>('/api/products?size=100')
  const products = productPage._embedded?.products ?? []
  const options: VariantOption[] = []
  for (const product of products) {
    const variantPage = await request<VariantPage>(`/api/products/${product.id}/variants?size=100`)
    for (const variant of variantPage._embedded?.variants ?? []) {
      options.push({ id: variant.id, label: `${product.name} · ${variant.color} · ${variant.size}` })
    }
  }
  return options
}

export default function StockEntry() {
  const [options, setOptions] = useState<VariantOption[]>([])
  const [loadError, setLoadError] = useState('')
  const [entryId, setEntryId] = useState(newId)
  const [itemId, setItemId] = useState(newId)
  const [variantId, setVariantId] = useState('')
  const [quantity, setQuantity] = useState('')
  const [unitCost, setUnitCost] = useState('')
  const [entryDate, setEntryDate] = useState(todayIso)
  const [note, setNote] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let ignore = false
    fetchVariantOptions()
      .then((loaded) => {
        if (!ignore) {
          setOptions(loaded)
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

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      await request('/api/stock-entries', {
        method: 'POST',
        body: {
          id: entryId,
          entryDate,
          note: note.trim() || undefined,
          items: [{ id: itemId, variantId, quantity: Number(quantity), unitCost: Number(unitCost) }],
        },
      })
      setEntryId(newId())
      setItemId(newId())
      setVariantId('')
      setQuantity('')
      setUnitCost('')
      setNote('')
      setMessage('Entrada registrada. O saldo já foi atualizado.')
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-4">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Entrada de estoque</h1>
        <Link to="/" className={linkClass}>
          Início
        </Link>
      </header>

      {loadError && (
        <p role="alert" className="mt-4 font-medium text-red-700">
          {loadError}
        </p>
      )}

      <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="stock-variant" className={labelClass}>
            Variante
          </label>
          <select
            id="stock-variant"
            required
            value={variantId}
            onChange={(event) => setVariantId(event.target.value)}
            className={inputClass}
          >
            <option value="">Selecione a variante</option>
            {options.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="stock-quantity" className={labelClass}>
            Quantidade
          </label>
          <input
            id="stock-quantity"
            type="number"
            min="1"
            step="1"
            required
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="stock-unit-cost" className={labelClass}>
            Custo unitário (R$)
          </label>
          <input
            id="stock-unit-cost"
            type="number"
            min="0"
            step="0.01"
            required
            value={unitCost}
            onChange={(event) => setUnitCost(event.target.value)}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="stock-date" className={labelClass}>
            Data da entrada
          </label>
          <input
            id="stock-date"
            type="date"
            required
            value={entryDate}
            onChange={(event) => setEntryDate(event.target.value)}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="stock-note" className={labelClass}>
            Observação (opcional)
          </label>
          <input
            id="stock-note"
            type="text"
            value={note}
            onChange={(event) => setNote(event.target.value)}
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
        <button type="submit" disabled={submitting} className={buttonClass}>
          {submitting ? 'Registrando...' : 'Registrar entrada'}
        </button>
      </form>
    </main>
  )
}
