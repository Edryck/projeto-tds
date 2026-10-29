import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Plus, Search, Shirt } from 'lucide-react'
import { fetchPage } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, Chip, ChipRow, EmptyState, ErrorState, Input, LinkButton, LoadMore, PageHeader, Skeleton } from '../../components/ui'
import { money } from '../../format'
import { useLoad, usePagedList, itemMotion } from '../../hooks'
import type { CategoryView, ProductView } from '../../types'

const PAGE_SIZE = 20

function buildPath(name: string, categoryId: string, page: number): string {
  const search = new URLSearchParams()
  if (name) {
    search.set('name', name)
  }
  if (categoryId) {
    search.set('categoryId', categoryId)
  }
  search.set('page', String(page))
  search.set('size', String(PAGE_SIZE))
  return `/api/products?${search.toString()}`
}

function priceRange(product: ProductView): string {
  if (product.variantCount === 0 || product.minPrice === null || product.maxPrice === null) {
    return 'Sem variantes'
  }
  if (product.minPrice === product.maxPrice) {
    return money(product.minPrice)
  }
  return `${money(product.minPrice)} – ${money(product.maxPrice)}`
}

export default function ProductList() {
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('product:edit')
  const [params, setParams] = useSearchParams()
  const name = params.get('name') ?? ''
  const categoryId = params.get('categoryId') ?? ''
  const [text, setText] = useState(name)

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

  const categories = useLoad(async () => (await fetchPage<CategoryView>('/api/categories?size=100')).items, [])

  const { data, items, page, error, loading, reload, loadMore, loadingMore } = usePagedList<ProductView>((page) => buildPath(name, categoryId, page), [name, categoryId])

  function selectCategory(value: string) {
    const next = new URLSearchParams(params)
    if (value) {
      next.set('categoryId', value)
    } else {
      next.delete('categoryId')
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

  const products = items
  const filtered = name !== '' || categoryId !== ''
  const newButton = canEdit ? (
    <LinkButton to="/catalog/new">
      <Plus className="h-5 w-5" aria-hidden="true" />
      Novo produto
    </LinkButton>
  ) : undefined

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Produtos" actions={newButton} />
      <div className="relative">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-text-secondary" aria-hidden="true" />
        <Input
          type="search"
          aria-label="Nome do produto"
          placeholder="Nome do produto"
          value={text}
          onChange={(event) => setText(event.target.value)}
          className="pl-10"
        />
      </div>
      {categories.data && categories.data.length > 0 && (
        <div className="mt-3">
          <ChipRow>
            <Chip active={categoryId === ''} onClick={() => selectCategory('')}>
              Todas
            </Chip>
            {categories.data.map((category) => (
              <Chip key={category.id} active={categoryId === category.id} onClick={() => selectCategory(category.id)}>
                {category.name}
              </Chip>
            ))}
          </ChipRow>
        </div>
      )}
      <div className="mt-4">
        {loading && !data && <Skeleton lines={5} />}
        {error && <ErrorState message={error} onRetry={reload} />}
        {data && products.length === 0 && !filtered && (
          <EmptyState icon={Shirt} title="Nenhum produto ainda" action={newButton} />
        )}
        {data && products.length === 0 && filtered && (
          <EmptyState
            icon={Search}
            title="Nenhum produto encontrado"
            message="Nada bate com a busca ou o filtro."
            action={<Button variant="ghost" onClick={clearFilters}>Limpar filtros</Button>}
          />
        )}
        {data && products.length > 0 && (
          <ul className="space-y-3">
            {products.map((product, index) => {
              const soldOut = product.variantCount > 0 && product.totalBalance <= 0
              return (
                <li key={product.id} {...itemMotion(index, PAGE_SIZE)}>
                  <Link
                    to={`/catalog/${product.id}`}
                    className="pressable block rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  >
                    <Card className="flex min-h-[48px] items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-text">{product.name}</p>
                        <p className="truncate text-sm text-text-secondary">{product.categoryName}</p>
                      </div>
                      <div className="shrink-0 text-right">
                        <p className="font-semibold text-text">{priceRange(product)}</p>
                        {product.variantCount > 0 && (
                          <p className={`text-sm ${soldOut ? 'font-medium text-error' : 'text-text-secondary'}`}>
                            {soldOut ? 'Esgotado' : `${product.totalBalance} em estoque`}
                          </p>
                        )}
                      </div>
                    </Card>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
        {page && products.length > 0 && <LoadMore page={page} onMore={onMore} loading={loadingMore} />}
      </div>
    </div>
  )
}
