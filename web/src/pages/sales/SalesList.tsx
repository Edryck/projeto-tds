import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Plus, Receipt, Search } from 'lucide-react'
import { useToast } from '../../components/Toast'
import {
  Button,
  Card,
  Chip,
  ChipRow,
  EmptyState,
  ErrorState,
  Input,
  LinkButton,
  LoadMore,
  PageHeader,
  Skeleton,
  StatusPill,
} from '../../components/ui'
import { dateTime, money, shortId } from '../../format'
import { usePagedList, itemMotion } from '../../hooks'
import { orderStatusMeta } from '../../types'
import type { OrderStatus, OrderView } from '../../types'

const statusOrder: OrderStatus[] = ['PENDING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED']
const PAGE_SIZE = 20

function buildPath(q: string, status: string, page: number): string {
  const search = new URLSearchParams()
  if (q) {
    search.set('q', q)
  }
  if (status) {
    search.set('status', status)
  }
  search.set('page', String(page))
  search.set('size', String(PAGE_SIZE))
  return `/api/orders?${search.toString()}`
}

export default function SalesList() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const rawStatus = params.get('status') ?? ''
  const status = statusOrder.includes(rawStatus as OrderStatus) ? rawStatus : ''
  const [text, setText] = useState(q)

  useEffect(() => {
    const trimmed = text.trim()
    if (trimmed === q) {
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
  }, [text, q, params, setParams])

  const { data, items, page, error, loading, reload, loadMore, loadingMore } = usePagedList<OrderView>((page) => buildPath(q, status, page), [q, status])

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

  const orders = items
  const filtered = q !== '' || status !== ''

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Vendas"
        actions={
          <LinkButton to="/sales/new">
            <Plus className="h-5 w-5" aria-hidden="true" />
            Nova venda
          </LinkButton>
        }
      />
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
        <Input
          type="search"
          aria-label="Número do pedido ou cliente"
          placeholder="Número do pedido ou cliente"
          value={text}
          onChange={(event) => setText(event.target.value)}
          className="pl-10"
        />
      </div>
      <div className="mt-3">
        <ChipRow>
          <Chip active={status === ''} onClick={() => selectStatus('')}>
            Todos
          </Chip>
          {statusOrder.map((value) => (
            <Chip key={value} active={status === value} onClick={() => selectStatus(value)}>
              {orderStatusMeta[value].label}
            </Chip>
          ))}
        </ChipRow>
      </div>
      <div className="mt-4">
        {loading && !data && <Skeleton lines={5} />}
        {error && <ErrorState message={error} onRetry={reload} />}
        {data && orders.length === 0 && !filtered && (
          <EmptyState
            icon={Receipt}
            title="Nenhuma venda ainda"
            action={<LinkButton to="/sales/new">Nova venda</LinkButton>}
          />
        )}
        {data && orders.length === 0 && filtered && (
          <EmptyState
            icon={Search}
            title="Nenhuma venda encontrada"
            message="Nada bate com a busca ou o filtro."
            action={<Button variant="ghost" onClick={clearFilters}>Limpar filtros</Button>}
          />
        )}
        {data && orders.length > 0 && (
          <ul className="space-y-3">
            {orders.map((order, index) => {
              const meta = orderStatusMeta[order.status]
              return (
                <li key={order.id} {...itemMotion(index, PAGE_SIZE)}>
                  <Link
                    to={`/sales/${order.id}`}
                    className="pressable block rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  >
                    <Card className="flex min-h-[48px] items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-semibold text-text">{shortId(order.id)}</p>
                        <p className="truncate text-sm text-text">{order.customerName}</p>
                        <p className="text-sm text-text-secondary">{dateTime(order.orderedAt)}</p>
                      </div>
                      <div className="flex shrink-0 flex-col items-end gap-2">
                        <p className="font-semibold text-text">{money(order.total)}</p>
                        <StatusPill tone={meta.tone} icon={meta.icon}>
                          {meta.label}
                        </StatusPill>
                      </div>
                    </Card>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
        {page && orders.length > 0 && <LoadMore page={page} onMore={onMore} loading={loadingMore} />}
      </div>
    </div>
  )
}
