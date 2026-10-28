import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Plus, Search, Truck } from 'lucide-react'
import { fetchPage } from '../../api'
import type { Model, Page } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, EmptyState, ErrorState, Input, LinkButton, LoadMore, PageHeader, Skeleton } from '../../components/ui'
import { useLoad } from '../../hooks'
import type { SupplierView } from '../../types'

const PAGE_SIZE = 20

function buildPath(name: string, page: number): string {
  const search = new URLSearchParams()
  if (name) {
    search.set('name', name)
  }
  search.set('page', String(page))
  search.set('size', String(PAGE_SIZE))
  return `/api/suppliers?${search.toString()}`
}

function contactLine(supplier: Model<SupplierView>): string {
  return supplier.phone || supplier.email || 'Sem telefone ou e-mail'
}

export default function SupplierList() {
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('stock:adjust')
  const [params, setParams] = useSearchParams()
  const name = params.get('name') ?? ''
  const [text, setText] = useState(name)
  const [extra, setExtra] = useState<Model<SupplierView>[]>([])
  const [lastPage, setLastPage] = useState<Page<SupplierView>['page'] | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

  useEffect(() => {
    const trimmed = text.trim()
    if (trimmed === name) {
      return
    }
    const timer = window.setTimeout(() => {
      const next = new URLSearchParams(params)
      if (trimmed) {
        next.set('name', trimmed)
      } else {
        next.delete('name')
      }
      setParams(next, { replace: true })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [text, name, params, setParams])

  const { data, error, loading, reload } = useLoad(() => {
    setExtra([])
    setLastPage(null)
    return fetchPage<SupplierView>(buildPath(name, 0))
  }, [name])

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
      const next = await fetchPage<SupplierView>(buildPath(name, current.number + 1))
      setExtra((list) => [...list, ...next.items])
      setLastPage(next.page)
    } catch (caught) {
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setLoadingMore(false)
    }
  }

  const suppliers = data ? [...data.items, ...extra] : []
  const filtered = name !== ''
  const newButton = canEdit ? (
    <LinkButton to="/suppliers/new">
      <Plus className="h-5 w-5" aria-hidden="true" />
      Novo fornecedor
    </LinkButton>
  ) : undefined

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Fornecedores" actions={newButton} />
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
        <Input
          type="search"
          aria-label="Nome do fornecedor"
          placeholder="Nome do fornecedor"
          value={text}
          onChange={(event) => setText(event.target.value)}
          className="pl-10"
        />
      </div>
      <div className="mt-4">
        {loading && !data && <Skeleton lines={5} />}
        {error && <ErrorState message={error} onRetry={reload} />}
        {data && suppliers.length === 0 && !filtered && (
          <EmptyState icon={Truck} title="Nenhum fornecedor ainda" action={newButton} />
        )}
        {data && suppliers.length === 0 && filtered && (
          <EmptyState
            icon={Search}
            title="Nenhum fornecedor encontrado"
            message="Nada bate com a busca."
            action={<Button variant="ghost" onClick={clearFilters}>Limpar busca</Button>}
          />
        )}
        {data && suppliers.length > 0 && (
          <ul className="space-y-3">
            {suppliers.map((supplier) => (
              <li key={supplier.id}>
                <Link
                  to={`/suppliers/${supplier.id}`}
                  className="block rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  <Card className="flex min-h-[48px] items-center gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-semibold text-text">{supplier.name}</p>
                      <p className="truncate text-sm text-text-secondary">{contactLine(supplier)}</p>
                    </div>
                  </Card>
                </Link>
              </li>
            ))}
          </ul>
        )}
        {data && suppliers.length > 0 && <LoadMore page={lastPage ?? data.page} onMore={loadMore} loading={loadingMore} />}
      </div>
    </div>
  )
}
