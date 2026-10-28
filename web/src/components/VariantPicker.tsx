import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import { fetchPage } from '../api'
import { money } from '../format'
import { useLoad } from '../hooks'
import type { VariantView } from '../types'
import { EmptyState, ErrorState, Input, Sheet, Skeleton } from './ui'

const MIN_CHARS = 2

function useDebounced(value: string, delay: number): string {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])
  return debounced
}

type Group = { productId: string; productName: string; variants: VariantView[] }

function groupByProduct(variants: VariantView[]): Group[] {
  const groups: Group[] = []
  for (const variant of variants) {
    let group = groups.find((item) => item.productId === variant.productId)
    if (!group) {
      group = { productId: variant.productId, productName: variant.productName, variants: [] }
      groups.push(group)
    }
    group.variants.push(variant)
  }
  return groups
}

function variantText(variant: VariantView): string {
  return `${variant.size} · ${variant.color} — ${money(variant.price)} · ${variant.balance} un.`
}

export function VariantPicker({ query, onPick }: { query: string; onPick: (variant: VariantView) => void }) {
  const term = useDebounced(query.trim(), 300)
  const active = term.length >= MIN_CHARS

  const { data, error, loading, reload } = useLoad(async () => {
    if (!active) {
      return []
    }
    const page = await fetchPage<VariantView>(`/api/variants?q=${encodeURIComponent(term)}&size=30`)
    return page.items
  }, [term])

  if (!active) {
    return <p className="py-4 text-center text-sm text-text-secondary">Digite pelo menos 2 letras para buscar.</p>
  }
  if (error) {
    return <ErrorState message={error} onRetry={reload} />
  }
  if (loading && !data) {
    return <Skeleton lines={3} />
  }
  const groups = groupByProduct(data ?? [])
  if (groups.length === 0) {
    return <EmptyState icon={Search} title="Nada encontrado" message="Tente outro nome, cor ou tamanho." />
  }

  return (
    <div className="space-y-4" aria-busy={loading}>
      {groups.map((group) => (
        <section key={group.productId}>
          <h3 className="mb-2 text-base font-semibold text-text">{group.productName}</h3>
          <div className="flex flex-wrap gap-2">
            {group.variants.map((variant) => {
              const soldOut = variant.status === 'SOLD_OUT'
              return (
                <button
                  key={variant.id}
                  type="button"
                  disabled={soldOut}
                  onClick={() => onPick(variant)}
                  className="min-h-[48px] rounded-full border border-border bg-surface px-4 text-sm font-medium text-text transition-colors hover:bg-surface-alt focus:outline-none focus:ring-2 focus:ring-secondary/60 disabled:cursor-not-allowed disabled:bg-surface-alt disabled:text-text-secondary"
                >
                  {soldOut ? `${variant.size} · ${variant.color} — Esgotado` : variantText(variant)}
                </button>
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}

export function VariantPickerSheet({
  open,
  onClose,
  onPick,
}: {
  open: boolean
  onClose: () => void
  onPick: (variant: VariantView) => void
}) {
  const [query, setQuery] = useState('')

  return (
    <Sheet open={open} onClose={onClose} title="Buscar produto">
      <div className="space-y-4">
        <Input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Nome, cor ou tamanho"
          aria-label="Buscar produto"
          autoFocus
        />
        <VariantPicker query={query} onPick={onPick} />
      </div>
    </Sheet>
  )
}
