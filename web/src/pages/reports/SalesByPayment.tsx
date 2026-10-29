import { useSearchParams } from 'react-router-dom'
import { Banknote, CreditCard, Receipt, Smartphone } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { request } from '../../api'
import PeriodPicker, { periodFromRange, presetPeriod } from '../../components/PeriodPicker'
import type { Period } from '../../components/PeriodPicker'
import { Card, EmptyState, ErrorState, PageHeader, Skeleton } from '../../components/ui'
import { money } from '../../format'
import { useLoad } from '../../hooks'
import { paymentLabels } from '../../types'
import type { PaymentMethod, SalesByPayment as SalesReport } from '../../types'

const paymentIcons: Record<PaymentMethod, LucideIcon> = {
  PIX: Smartphone,
  CARD: CreditCard,
  CASH: Banknote,
}

function sharePercent(lineTotal: number | string, total: number | string): number {
  const totalValue = Number(total)
  return totalValue > 0 ? Math.round((Number(lineTotal) / totalValue) * 100) : 0
}

export default function SalesByPayment() {
  const [params, setParams] = useSearchParams()

  const initial = presetPeriod('thisMonth')
  const period: Period = periodFromRange(params.get('from') ?? initial.from, params.get('to') ?? initial.to)

  const { data, error, loading, reload } = useLoad(async () => {
    const search = new URLSearchParams({ from: period.from, to: period.to })
    return request<SalesReport>(`/api/reports/sales-by-payment?${search.toString()}`)
  }, [period.from, period.to])

  function update(changes: Record<string, string | null>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value === null) {
        next.delete(key)
      } else {
        next.set(key, value)
      }
    }
    setParams(next, { replace: true })
  }

  const hasSales = !!data && Number(data.total) > 0 && data.orderCount > 0

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Vendas por período"
        back={{ to: '/reports', label: 'Resultado da loja' }}
      />
      <PeriodPicker
        value={period}
        onChange={(next) => update({ from: next.from, to: next.to })}
        presets={['today', '7days', 'thisMonth']}
      />
      <div className="mt-4">
        {loading && !data && <Skeleton lines={3} />}
        {error && <ErrorState message={error} onRetry={reload} />}
        {data && !hasSales && <EmptyState icon={Receipt} title="Nenhuma venda paga no período" />}
        {data && hasSales && (
          <div className="space-y-3">
            <Card className="grid grid-cols-2 gap-2 text-center">
              <div>
                <p className="text-sm text-text-secondary">Total vendido</p>
                <p className="font-semibold text-text">{money(data.total)}</p>
              </div>
              <div>
                <p className="text-sm text-text-secondary">Pedidos pagos</p>
                <p className="font-semibold text-text">{data.orderCount}</p>
              </div>
            </Card>
            <ul className="space-y-3">
              {data.lines.map((line) => {
                const Icon = paymentIcons[line.method]
                const percent = sharePercent(line.total, data.total)
                return (
                  <li key={line.method}>
                    <Card>
                      <div className="flex items-center gap-3">
                        <Icon className="h-6 w-6 shrink-0 text-primary" aria-hidden="true" />
                        <div className="min-w-0 flex-1">
                          <p className="font-semibold text-text">{paymentLabels[line.method]}</p>
                          <p className="text-sm text-text-secondary">
                            {line.orderCount} {line.orderCount === 1 ? 'pedido' : 'pedidos'}
                          </p>
                        </div>
                        <div className="shrink-0 text-right">
                          <p className="font-semibold text-text">{money(line.total)}</p>
                          <p className="text-sm text-text-secondary">{percent}%</p>
                        </div>
                      </div>
                      <div
                        role="progressbar"
                        aria-label={paymentLabels[line.method]}
                        aria-valuemin={0}
                        aria-valuemax={100}
                        aria-valuenow={percent}
                        className="mt-3 h-3 overflow-hidden rounded-full bg-surface-alt"
                      >
                        <div className="h-3 rounded-full bg-secondary" style={{ width: `${percent}%` }} />
                      </div>
                    </Card>
                  </li>
                )
              })}
            </ul>
          </div>
        )}
        {data && <p className="mt-4 text-sm text-text-secondary">Valores com frete, de pedidos pagos no período.</p>}
      </div>
    </div>
  )
}
