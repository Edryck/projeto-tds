import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Ban, Clock3, Plus, Receipt, RefreshCw, ShoppingBag, TrendingDown, TrendingUp } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { fetchPage, logoUrl, request, swapToFallbackLogo } from '../api'
import { useAuth } from '../auth'
import { Card, EmptyState, ErrorState, LinkButton, Skeleton, StatusPill } from '../components/ui'
import { money, todayIso } from '../format'
import { useLoad } from '../hooks'
import { orderStatusMeta } from '../types'
import type { Account, CancellationView, OrderView, Profit, SalesByPayment, StoreSettingsView } from '../types'

const TIME_ZONE = 'America/Sao_Paulo'

type Load<T> = {
  data: T | null
  error: string | null
  loading: boolean
  reload: () => void
}

function isVisible(load: { data: unknown; error: string | null; loading: boolean }): boolean {
  return load.error !== null || load.loading || load.data !== null
}

function currentHour(): number {
  return Number(new Intl.DateTimeFormat('en-US', { hour: 'numeric', hourCycle: 'h23', timeZone: TIME_ZONE }).format(new Date()))
}

function greetingFor(hour: number): string {
  if (hour >= 5 && hour < 12) {
    return 'Bom dia'
  }
  if (hour >= 12 && hour < 18) {
    return 'Boa tarde'
  }
  return 'Boa noite'
}

function todayLabel(): string {
  return new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: TIME_ZONE }).format(new Date())
}

function timeLabel(date: Date): string {
  return date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', timeZone: TIME_ZONE })
}

function dayMonth(iso: string): string {
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}`
}

function Stat({
  icon: Icon,
  iconClassName,
  label,
  value,
  footnote,
}: {
  icon: LucideIcon
  iconClassName: string
  label: string
  value: string
  footnote: string
}) {
  return (
    <div className="flex h-full flex-col gap-2">
      <div className="flex items-center gap-2 text-sm font-medium text-text-secondary">
        <Icon className={`h-5 w-5 shrink-0 ${iconClassName}`} aria-hidden="true" />
        {label}
      </div>
      <p className="font-serif text-2xl font-bold text-text">{value}</p>
      <p className="text-sm text-text-secondary">{footnote}</p>
    </div>
  )
}

function Panel<T>({ load, render }: { load: Load<T>; render: (data: T) => ReactNode }) {
  if (load.error !== null) {
    return <ErrorState message={load.error} onRetry={load.reload} />
  }
  if (load.data === null) {
    return <Skeleton lines={1} />
  }
  return <>{render(load.data)}</>
}

export default function Home() {
  const { me, can } = useAuth()
  const canFinance = can('finance:view')
  const canOrders = can('order:view')
  const canCreate = can('order:create')

  const [updatedAt, setUpdatedAt] = useState(() => new Date())
  const [logoVersion] = useState(() => Date.now())
  const settings = useLoad(() => request<StoreSettingsView>('/api/store-settings'), [])
  const storeName = settings.data?.name ?? 'Chic Charmosa'

  const salesToday = useLoad(async () => {
    if (!canFinance) {
      return null
    }
    const today = todayIso()
    const search = new URLSearchParams({ from: today, to: today })
    return request<SalesByPayment>(`/api/reports/sales-by-payment?${search.toString()}`)
  }, [canFinance])

  const profit = useLoad(async () => {
    if (!canFinance) {
      return null
    }
    const accounts = await fetchPage<Account>('/api/financial-accounts')
    const store = accounts.items.find((account) => account.type === 'STORE')
    if (!store) {
      return null
    }
    const today = todayIso()
    const search = new URLSearchParams({ accountId: store.id, from: `${today.slice(0, 8)}01`, to: today })
    return request<Profit>(`/api/finance/profit?${search.toString()}`)
  }, [canFinance])

  const pending = useLoad(async () => {
    if (!canOrders) {
      return null
    }
    return fetchPage<OrderView>('/api/orders?status=PENDING&page=0&size=1')
  }, [canOrders])

  const canCancel = can('order:cancel')
  const cancellations = useLoad(async () => {
    if (!canCancel) {
      return 0
    }
    try {
      const page = await fetchPage<CancellationView>('/api/cancellation-requests?status=PENDING&page=0&size=1')
      return page.page.totalElements
    } catch {
      return 0
    }
  }, [canCancel])

  const recent = useLoad(async () => {
    if (!canOrders) {
      return null
    }
    return fetchPage<OrderView>('/api/orders?page=0&size=5&sort=orderedAt,desc')
  }, [canOrders])

  function refresh() {
    setUpdatedAt(new Date())
    salesToday.reload()
    profit.reload()
    pending.reload()
    cancellations.reload()
    recent.reload()
  }

  const firstName = me?.name.split(' ')[0] ?? ''
  const recentItems = recent.data?.items ?? []
  const profitValue = profit.data ? Number(profit.data.profit) : 0
  const hasStats = (canFinance && (isVisible(salesToday) || isVisible(profit))) || (canOrders && isVisible(pending))
  const showRecent = canOrders && (recent.loading || recent.error !== null || recentItems.length > 0)
  const showEmptyToday =
    salesToday.data?.orderCount === 0 || (recent.data !== null && recentItems.length === 0)

  return (
    <div className="mx-auto max-w-5xl">
      <header className="flex flex-wrap items-center gap-3">
        <img
          src={logoUrl(logoVersion)}
          alt={storeName}
          onError={swapToFallbackLogo}
          className="h-12 w-12 shrink-0 rounded-xl object-cover"
        />
        <div className="min-w-0 flex-1">
          <h1 className="font-serif text-2xl font-bold text-text">
            {greetingFor(currentHour())}, {firstName}
          </h1>
          <p className="text-sm text-text-secondary">{todayLabel()}</p>
        </div>
        {canCreate && (
          <LinkButton to="/sales/new">
            <Plus className="h-5 w-5" aria-hidden="true" />
            Nova venda
          </LinkButton>
        )}
      </header>

      <div className="mt-4 space-y-4">
        {canCancel && cancellations.data !== null && cancellations.data > 0 && (
          <Link
            to="/admin/cancellations"
            className="flex min-h-[48px] items-center gap-3 rounded-[14px] bg-warning-soft px-4 py-3 font-semibold text-warning focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <Ban className="h-5 w-5 shrink-0" aria-hidden="true" />
            {cancellations.data === 1 ? '1 cancelamento aguardando' : `${cancellations.data} cancelamentos aguardando`}
          </Link>
        )}
        {(canFinance || canOrders) && (
          <div className="flex items-center justify-between gap-3 text-sm text-text-secondary">
            <span>Atualizado às {timeLabel(updatedAt)}</span>
            <button
              type="button"
              onClick={refresh}
              aria-label="Atualizar"
              className="flex h-12 w-12 items-center justify-center rounded-xl text-text hover:bg-surface-alt"
            >
              <RefreshCw
                className={`h-5 w-5 ${salesToday.loading || profit.loading || pending.loading || recent.loading ? 'animate-spin' : ''}`}
                aria-hidden="true"
              />
            </button>
          </div>
        )}

        {hasStats && (
          <ul className="grid gap-3 lg:grid-cols-3">
            {canFinance && isVisible(salesToday) && (
              <li>
                <Card className="h-full">
                  <Panel
                    load={salesToday}
                    render={(report) => (
                      <Stat
                        icon={ShoppingBag}
                        iconClassName="text-primary"
                        label="Vendas do dia"
                        value={money(report.total)}
                        footnote={`${report.orderCount} ${report.orderCount === 1 ? 'pedido pago' : 'pedidos pagos'}`}
                      />
                    )}
                  />
                </Card>
              </li>
            )}
            {canFinance && isVisible(profit) && (
              <li>
                <Card className="h-full">
                  <Panel
                    load={profit}
                    render={(result) => (
                      <Stat
                        icon={profitValue >= 0 ? TrendingUp : TrendingDown}
                        iconClassName={profitValue >= 0 ? 'text-accent' : 'text-error'}
                        label={profitValue >= 0 ? 'Lucro' : 'Prejuízo'}
                        value={money(result.profit)}
                        footnote={`${dayMonth(`${todayIso().slice(0, 8)}01`)} a ${dayMonth(todayIso())}`}
                      />
                    )}
                  />
                </Card>
              </li>
            )}
            {canOrders && isVisible(pending) && (
              <li>
                <Link
                  to="/sales?status=PENDING"
                  className="block h-full rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  <Card className="h-full">
                    <Panel
                      load={pending}
                      render={(page) => (
                        <Stat
                          icon={Clock3}
                          iconClassName="text-warning"
                          label="Pedidos pendentes"
                          value={String(page.page.totalElements)}
                          footnote="Ver pedidos pendentes"
                        />
                      )}
                    />
                  </Card>
                </Link>
              </li>
            )}
          </ul>
        )}
      </div>

      {showEmptyToday && (
        <div className="mt-4">
          <EmptyState
            icon={Receipt}
            title="Nenhuma venda hoje ainda"
            action={canCreate ? <LinkButton to="/sales/new">Nova venda</LinkButton> : undefined}
          />
        </div>
      )}

      {showRecent && (
        <section className="mt-4" aria-labelledby="recent-orders-title">
          <Card>
            <div className="flex items-center justify-between gap-3">
              <h2 id="recent-orders-title" className="font-serif text-lg font-bold text-text">
                Pedidos recentes
              </h2>
              <Link to="/sales" className="text-sm font-semibold text-primary hover:underline">
                Ver todos os pedidos
              </Link>
            </div>
            <div className="mt-2">
              <Panel
                load={recent}
                render={(page) => (
                  <ul className="divide-y divide-border">
                    {page.items.map((order) => {
                      const meta = orderStatusMeta[order.status]
                      return (
                        <li key={order.id}>
                          <Link
                            to={`/sales/${order.id}`}
                            className="flex min-h-[48px] items-center justify-between gap-3 py-2 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                          >
                            <div className="min-w-0">
                              <p className="truncate font-semibold text-text">{order.customerName}</p>
                              <p className="text-sm text-text-secondary">
                                {timeLabel(new Date(order.orderedAt))}
                              </p>
                            </div>
                            <div className="flex shrink-0 flex-col items-end gap-2">
                              <p className="font-semibold text-text">{money(order.total)}</p>
                              <StatusPill tone={meta.tone} icon={meta.icon}>
                                {meta.label}
                              </StatusPill>
                            </div>
                          </Link>
                        </li>
                      )
                    })}
                  </ul>
                )}
              />
            </div>
          </Card>
        </section>
      )}
    </div>
  )
}
