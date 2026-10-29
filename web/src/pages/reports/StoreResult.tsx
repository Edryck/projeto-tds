import { Link, useSearchParams } from 'react-router-dom'
import { ArrowRight, Receipt } from 'lucide-react'
import { fetchPage, request } from '../../api'
import type { Model } from '../../api'
import PeriodPicker, { periodFromRange, presetPeriod } from '../../components/PeriodPicker'
import type { Period } from '../../components/PeriodPicker'
import { Card, EmptyState, ErrorState, PageHeader, Segmented, Skeleton } from '../../components/ui'
import { money } from '../../format'
import { useLoad } from '../../hooks'
import { accountLabels } from '../../types'
import type { Account, Profit } from '../../types'

function ResultRow({ label, value, strong }: { label: string; value: number | string; strong?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3 py-2">
      <p className={strong ? 'font-semibold text-text' : 'text-text'}>{label}</p>
      <p className={strong ? 'font-semibold text-text' : 'text-text'}>{money(value)}</p>
    </div>
  )
}

function isEmptyResult(profit: Profit): boolean {
  return Number(profit.revenue) === 0 && Number(profit.costOfGoods) === 0 && Number(profit.expenses) === 0
}

export default function StoreResult() {
  const [params, setParams] = useSearchParams()

  const initial = presetPeriod('thisMonth')
  const period: Period = periodFromRange(params.get('from') ?? initial.from, params.get('to') ?? initial.to)

  const accounts = useLoad(async () => (await fetchPage<Account>('/api/financial-accounts')).items, [])

  const store = accounts.data?.find((account: Model<Account>) => account.type === 'STORE')
  const accountId = params.get('account') ?? store?.id ?? accounts.data?.[0]?.id ?? ''

  const { data, error, loading, reload } = useLoad(async () => {
    if (accountId === '') {
      return null
    }
    const search = new URLSearchParams({ accountId, from: period.from, to: period.to })
    return request<Profit>(`/api/finance/profit?${search.toString()}`)
  }, [accountId, period.from, period.to])

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

  const profitValue = data ? Number(data.profit) : 0
  const profitLabel = profitValue >= 0 ? 'Lucro' : 'Prejuízo'

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Resultado da loja"
        actions={
          <Link
            to={`/reports/sales?from=${period.from}&to=${period.to}`}
            className="inline-flex min-h-[48px] items-center gap-2 rounded-xl border border-border bg-surface px-4 text-sm font-semibold text-text hover:bg-surface-alt"
          >
            Vendas por período
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        }
      />
      {accounts.data && accounts.data.length > 1 && (
        <Segmented
          options={accounts.data.map((account) => ({ value: account.id, label: accountLabels[account.type] }))}
          value={accountId}
          onChange={(value) => update({ account: value })}
        />
      )}
      <div className="mt-3">
        <PeriodPicker
          value={period}
          onChange={(next) => update({ from: next.from, to: next.to })}
          presets={['today', '7days', 'thisMonth']}
        />
      </div>
      <div className="mt-4">
        {(loading || accounts.loading) && !data && <Skeleton lines={4} />}
        {error && <ErrorState message={error} onRetry={reload} />}
        {data && isEmptyResult(data) && (
          <EmptyState icon={Receipt} title="Sem dados para o período" />
        )}
        {data && !isEmptyResult(data) && (
          <div className="space-y-3">
            <Card className="text-center">
              <p className="text-sm text-text-secondary">{profitLabel}</p>
              <p className={`font-serif text-3xl font-bold ${profitValue >= 0 ? 'text-accent' : 'text-error'}`}>
                {money(data.profit)}
              </p>
            </Card>
            <Card className="divide-y divide-border">
              <ResultRow label="Receita de vendas" value={data.revenue} />
              <ResultRow label="(−) Custo das mercadorias vendidas" value={data.costOfGoods} />
              <ResultRow label="(−) Despesas" value={data.expenses} />
              <ResultRow label="(=) Resultado estimado" value={data.profit} strong />
            </Card>
            <p className="text-sm text-text-secondary">Receita sem frete. Despesas de frete não entram no cálculo.</p>
            <p className="text-sm text-text-secondary">
              Resumo gerencial com base nos lançamentos registrados. Não é documento fiscal ou contábil.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
