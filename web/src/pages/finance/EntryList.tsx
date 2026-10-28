import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ArrowDownCircle, ArrowUpCircle, Plus, Wallet } from 'lucide-react'
import { fetchPage, request } from '../../api'
import type { Model, Page } from '../../api'
import { useAuth } from '../../auth'
import PeriodPicker, { brDate, periodFromRange, presetPeriod } from '../../components/PeriodPicker'
import type { Period } from '../../components/PeriodPicker'
import { useToast } from '../../components/Toast'
import { Card, Chip, ChipRow, EmptyState, ErrorState, LinkButton, LoadMore, PageHeader, Segmented, Skeleton } from '../../components/ui'
import { money } from '../../format'
import { useLoad } from '../../hooks'
import { accountLabels } from '../../types'
import type { Account, Entry, EntrySummary, EntryType, FinancialCategoryView } from '../../types'

const PAGE_SIZE = 20

type TypeFilter = 'ALL' | EntryType

function buildPath(accountId: string, period: Period, type: TypeFilter, page: number): string {
  const search = new URLSearchParams()
  search.set('accountId', accountId)
  search.set('from', period.from)
  search.set('to', period.to)
  if (type !== 'ALL') {
    search.set('type', type)
  }
  search.set('page', String(page))
  search.set('size', String(PAGE_SIZE))
  search.append('sort', 'entryDate,desc')
  search.append('sort', 'createdAt,desc')
  return `/api/financial-entries?${search.toString()}`
}

export default function EntryList() {
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('finance:edit')
  const [params, setParams] = useSearchParams()
  const [extra, setExtra] = useState<Model<Entry>[]>([])
  const [lastPage, setLastPage] = useState<Page<Entry>['page'] | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

  const initial = presetPeriod('thisMonth')
  const period = periodFromRange(params.get('from') ?? initial.from, params.get('to') ?? initial.to)
  const rawType = params.get('type')
  const type: TypeFilter = rawType === 'INCOME' || rawType === 'EXPENSE' ? rawType : 'ALL'

  const accounts = useLoad(async () => (await fetchPage<Account>('/api/financial-accounts')).items, [])
  const categories = useLoad(async () => (await fetchPage<FinancialCategoryView>('/api/financial-categories?size=100')).items, [])

  const store = accounts.data?.find((account) => account.type === 'STORE')
  const accountId = params.get('account') ?? store?.id ?? accounts.data?.[0]?.id ?? ''

  const { data, error, loading, reload } = useLoad(async () => {
    setExtra([])
    setLastPage(null)
    if (accountId === '') {
      return null
    }
    return fetchPage<Entry>(buildPath(accountId, period, type, 0))
  }, [accountId, period.from, period.to, type])

  const summary = useLoad(async () => {
    if (accountId === '') {
      return null
    }
    const search = new URLSearchParams({ accountId, from: period.from, to: period.to })
    return request<EntrySummary>(`/api/financial-entries/summary?${search.toString()}`)
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

  async function loadMore() {
    const current = lastPage ?? data?.page
    if (!current) {
      return
    }
    setLoadingMore(true)
    try {
      const next = await fetchPage<Entry>(buildPath(accountId, period, type, current.number + 1))
      setExtra((list) => [...list, ...next.items])
      setLastPage(next.page)
    } catch (caught) {
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setLoadingMore(false)
    }
  }

  const categoryNames = new Map((categories.data ?? []).map((category) => [category.id, category.name]))
  const entries = data ? [...data.items, ...extra] : []
  const newButton = canEdit ? (
    <LinkButton to="/finance/new">
      <Plus className="h-5 w-5" aria-hidden="true" />
      Novo lançamento
    </LinkButton>
  ) : undefined

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Lançamentos" actions={newButton} />
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
          presets={['thisMonth', 'lastMonth']}
        />
      </div>
      <Card className="mt-3 grid grid-cols-3 gap-2 text-center">
        <div>
          <p className="text-sm text-text-secondary">Receitas</p>
          <p className="font-semibold text-text">{summary.data ? money(summary.data.income) : '—'}</p>
        </div>
        <div>
          <p className="text-sm text-text-secondary">Despesas</p>
          <p className="font-semibold text-error">{summary.data ? money(summary.data.expense) : '—'}</p>
        </div>
        <div>
          <p className="text-sm text-text-secondary">Saldo</p>
          <p className="font-semibold text-text">{summary.data ? money(summary.data.balance) : '—'}</p>
        </div>
      </Card>
      <div className="mt-3">
        <ChipRow>
          <Chip active={type === 'ALL'} onClick={() => update({ type: null })}>
            Todos
          </Chip>
          <Chip active={type === 'INCOME'} onClick={() => update({ type: 'INCOME' })}>
            Receitas
          </Chip>
          <Chip active={type === 'EXPENSE'} onClick={() => update({ type: 'EXPENSE' })}>
            Despesas
          </Chip>
        </ChipRow>
      </div>
      <div className="mt-4">
        {(loading || accounts.loading) && !data && <Skeleton lines={5} />}
        {error && <ErrorState message={error} onRetry={reload} />}
        {data && entries.length === 0 && (
          <EmptyState
            icon={Wallet}
            title="Nenhum lançamento"
            message="Nada neste período com esses filtros."
            action={newButton}
          />
        )}
        {data && entries.length > 0 && (
          <ul className="space-y-3">
            {entries.map((entry) => {
              const income = entry.type === 'INCOME'
              const Icon = income ? ArrowUpCircle : ArrowDownCircle
              const target = entry.orderId ? `/sales/${entry.orderId}` : `/finance/${entry.id}`
              return (
                <li key={entry.id}>
                  <Link
                    to={target}
                    className="block rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  >
                    <Card className="flex min-h-[48px] items-center gap-3">
                      <Icon className={`h-6 w-6 shrink-0 ${income ? 'text-accent' : 'text-error'}`} aria-hidden="true" />
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-semibold text-text">{entry.description || categoryNames.get(entry.categoryId) || 'Sem descrição'}</p>
                        <p className="truncate text-sm text-text-secondary">
                          {income ? 'Receita' : 'Despesa'} · {categoryNames.get(entry.categoryId) ?? '—'} · {brDate(entry.entryDate)}
                        </p>
                      </div>
                      <p className={`shrink-0 font-semibold ${income ? 'text-text' : 'text-error'}`}>
                        {income ? '+' : '−'} {money(entry.amount)}
                      </p>
                    </Card>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
        {data && entries.length > 0 && <LoadMore page={lastPage ?? data.page} onMore={loadMore} loading={loadingMore} />}
      </div>
    </div>
  )
}
