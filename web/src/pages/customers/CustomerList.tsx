import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Plus, Search, Users } from 'lucide-react'
import { fetchPage } from '../../api'
import type { Model, Page } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, EmptyState, ErrorState, Input, LinkButton, LoadMore, PageHeader, Skeleton } from '../../components/ui'
import { date } from '../../format'
import { useLoad } from '../../hooks'
import type { CustomerView } from '../../types'

const PAGE_SIZE = 20

function buildPath(query: string, page: number): string {
  const search = new URLSearchParams()
  if (query) {
    search.set('q', query)
  }
  search.set('page', String(page))
  search.set('size', String(PAGE_SIZE))
  return `/api/customers?${search.toString()}`
}

function initialsOf(name: string): string {
  const [first = '', ...rest] = name.trim().split(/\s+/)
  const last = rest[rest.length - 1] ?? ''
  return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase()
}

function contactLine(customer: Model<CustomerView>): string {
  return customer.phone || customer.email || 'Sem telefone ou e-mail'
}

function lastOrderLine(customer: Model<CustomerView>): string {
  return customer.lastOrderAt ? `Última compra: ${date(customer.lastOrderAt)}` : 'Sem compras'
}

export default function CustomerList() {
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('customer:edit')
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const [text, setText] = useState(query)
  const [extra, setExtra] = useState<Model<CustomerView>[]>([])
  const [lastPage, setLastPage] = useState<Page<CustomerView>['page'] | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

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

  const { data, error, loading, reload } = useLoad(() => {
    setExtra([])
    setLastPage(null)
    return fetchPage<CustomerView>(buildPath(query, 0))
  }, [query])

  function clearFilters() {
    setText('')
    setParams({}, { replace: true })
  }

  async function loadMore() {
    const current = lastPage ?? data?.page
    if (!current) {
      return
    }
    setLoadingMore(true)
    try {
      const next = await fetchPage<CustomerView>(buildPath(query, current.number + 1))
      setExtra((list) => [...list, ...next.items])
      setLastPage(next.page)
    } catch (caught) {
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setLoadingMore(false)
    }
  }

  const customers = data ? [...data.items, ...extra] : []
  const filtered = query !== ''
  const newButton = canEdit ? (
    <LinkButton to="/customers/new">
      <Plus className="h-5 w-5" aria-hidden="true" />
      Novo cliente
    </LinkButton>
  ) : undefined

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Clientes" actions={newButton} />
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
        <Input
          type="search"
          aria-label="Buscar cliente"
          placeholder="Nome, telefone ou e-mail"
          value={text}
          onChange={(event) => setText(event.target.value)}
          className="pl-10"
        />
      </div>
      <div className="mt-4">
        {loading && !data && <Skeleton lines={5} />}
        {error && <ErrorState message={error} onRetry={reload} />}
        {data && customers.length === 0 && !filtered && (
          <EmptyState icon={Users} title="Nenhuma cliente ainda" action={newButton} />
        )}
        {data && customers.length === 0 && filtered && (
          <EmptyState
            icon={Search}
            title="Nenhuma cliente encontrada"
            message="Nada bate com a busca."
            action={<Button variant="ghost" onClick={clearFilters}>Limpar busca</Button>}
          />
        )}
        {data && customers.length > 0 && (
          <ul className="space-y-3">
            {customers.map((customer) => (
              <li key={customer.id}>
                <Link
                  to={`/customers/${customer.id}`}
                  className="block rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  <Card className="flex min-h-[48px] items-center gap-3">
                    <span
                      aria-hidden="true"
                      className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-secondary-soft font-semibold text-primary"
                    >
                      {initialsOf(customer.name)}
                    </span>
                    <div className="min-w-0">
                      <p className="truncate font-semibold text-text">{customer.name}</p>
                      <p className="truncate text-sm text-text-secondary">{contactLine(customer)}</p>
                      <p className="truncate text-sm text-text-secondary">{lastOrderLine(customer)}</p>
                    </div>
                  </Card>
                </Link>
              </li>
            ))}
          </ul>
        )}
        {data && customers.length > 0 && <LoadMore page={lastPage ?? data.page} onMore={loadMore} loading={loadingMore} />}
      </div>
    </div>
  )
}
