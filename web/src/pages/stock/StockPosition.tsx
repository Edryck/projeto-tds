import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AlertTriangle, CheckCircle2, PackagePlus, PackageX, Search, SlidersHorizontal, XCircle } from 'lucide-react'
import type { Model } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, Chip, ChipRow, EmptyState, ErrorState, Input, LinkButton, LoadMore, PageHeader, Skeleton, StatusPill } from '../../components/ui'
import { usePagedList } from '../../hooks'
import type { VariantView } from '../../types'

const PAGE_SIZE = 20

const statusFilters = [
  { value: '', label: 'Todos' },
  { value: 'LOW', label: 'Baixo estoque' },
  { value: 'SOLD_OUT', label: 'Sem estoque' },
]

function buildPath(query: string, status: string, page: number): string {
  const search = new URLSearchParams()
  if (query) {
    search.set('q', query)
  }
  if (status) {
    search.set('status', status)
  }
  search.set('page', String(page))
  search.set('size', String(PAGE_SIZE))
  return `/api/variants?${search.toString()}`
}

function stockPill(variant: Model<VariantView>): ReactNode {
  if (variant.balance < 0) {
    return (
      <StatusPill tone="danger" icon={XCircle}>
        Saldo negativo
      </StatusPill>
    )
  }
  if (variant.status === 'SOLD_OUT') {
    return (
      <StatusPill tone="danger" icon={PackageX}>
        Sem estoque
      </StatusPill>
    )
  }
  if (variant.status === 'LOW') {
    return (
      <StatusPill tone="warning" icon={AlertTriangle}>
        Baixo estoque
      </StatusPill>
    )
  }
  return (
    <StatusPill tone="success" icon={CheckCircle2}>
      Disponível
    </StatusPill>
  )
}

export default function StockPosition() {
  const toast = useToast()
  const { can } = useAuth()
  const canAdjust = can('stock:adjust')
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const status = params.get('status') ?? ''
  const [text, setText] = useState(query)

  useEffect(() => {
    const trimmed = text.trim()
    if (trimmed === query) {
      return
    }
    const timer = window.setTimeout(() => {
      const next = new URLSearchParams(params)
      if (trimmed) {
        next.set('q', trimmed)
      } else {
        next.delete('q')
      }
      setParams(next, { replace: true })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [text, query, params, setParams])

  const { data, items, page, error, loading, reload, loadMore, loadingMore } = usePagedList<VariantView>((page) => buildPath(query, status, page), [query, status])

  function selectStatus(value: string) {
    const next = new URLSearchParams(params)
    if (value) {
      next.set('status', value)
    } else {
      next.delete('status')
    }
    setParams(next, { replace: true })
  }

  function clearFilters() {
    setText('')
    setParams({}, { replace: true })
  }

  function onMore() {
    loadMore().catch((caught) => toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error'))
  }

  const variants = items
  const filtered = query !== '' || status !== ''

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Estoque" subtitle="Saldo atual de cada variante" />
      {canAdjust && (
        <div className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
          <LinkButton variant="ghost" to="/stock/entry">
            <PackagePlus className="h-5 w-5" aria-hidden="true" />
            Entrada de mercadoria
          </LinkButton>
          <LinkButton variant="ghost" to="/stock/adjustment">
            <SlidersHorizontal className="h-5 w-5" aria-hidden="true" />
            Ajustar estoque
          </LinkButton>
        </div>
      )}
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
        <Input
          type="search"
          aria-label="Produto ou variante"
          placeholder="Produto ou variante"
          value={text}
          onChange={(event) => setText(event.target.value)}
          className="pl-10"
        />
      </div>
      <div className="mt-3">
        <ChipRow>
          {statusFilters.map((filter) => (
            <Chip key={filter.label} active={status === filter.value} onClick={() => selectStatus(filter.value)}>
              {filter.label}
            </Chip>
          ))}
        </ChipRow>
      </div>
      <div className="mt-4">
        {loading && !data && <Skeleton lines={5} />}
        {error && <ErrorState message={error} onRetry={reload} />}
        {data && variants.length === 0 && !filtered && (
          <EmptyState icon={PackagePlus} title="Nenhuma variante cadastrada" message="Cadastre produtos e variantes no catálogo." />
        )}
        {data && variants.length === 0 && filtered && (
          <EmptyState
            icon={Search}
            title="Nenhuma variante encontrada"
            message="Nada bate com a busca ou o filtro."
            action={
              <Button variant="ghost" onClick={clearFilters}>
                Limpar filtros
              </Button>
            }
          />
        )}
        {data && variants.length > 0 && (
          <ul className="space-y-3">
            {variants.map((variant) => {
              const row = (
                <Card className="flex min-h-[48px] items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-text">{variant.productName}</p>
                    <p className="truncate text-sm text-text-secondary">
                      {variant.color} · {variant.size}
                    </p>
                    <div className="mt-2">{stockPill(variant)}</div>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="text-sm text-text-secondary">Quantidade</p>
                    <p className="text-xl font-semibold tabular-nums text-text">{variant.balance}</p>
                  </div>
                </Card>
              )
              return (
                <li key={variant.id}>
                  {canAdjust ? (
                    <Link
                      to={`/stock/adjustment?variantId=${variant.id}`}
                      className="block rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                    >
                      {row}
                    </Link>
                  ) : (
                    row
                  )}
                </li>
              )
            })}
          </ul>
        )}
        {page && variants.length > 0 && <LoadMore page={page} onMore={onMore} loading={loadingMore} />}
      </div>
    </div>
  )
}
