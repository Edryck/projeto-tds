import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { newId, request } from '../api'
import { useAuth } from '../auth'

type Category = { id: string; name: string }
type Product = {
  id: string
  categoryId: string
  categoryName: string
  name: string
  description: string | null
}
type Variant = {
  id: string
  productId: string
  color: string
  size: string
  model: string | null
  price: number
  balance: number
}
type CatalogProduct = Product & { variants: Variant[] }
type Catalog = { categories: Category[]; products: CatalogProduct[] }

type CategoryPage = { _embedded?: { categories: Category[] } }
type ProductPage = { _embedded?: { products: Product[] } }
type VariantPage = { _embedded?: { variants: Variant[] } }

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const inputClass =
  'h-12 rounded-md border border-gray-500 bg-white px-3 text-base text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const buttonClass =
  'h-12 rounded-md bg-indigo-700 px-4 font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700 disabled:bg-gray-600'
const linkClass =
  'flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const labelClass = 'font-medium text-gray-900'

function messageOf(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado'
}

async function fetchCatalog(name: string): Promise<Catalog> {
  const categoryPage = await request<CategoryPage>('/api/categories?size=100')
  const params = new URLSearchParams({ size: '100' })
  if (name) {
    params.set('name', name)
  }
  const productPage = await request<ProductPage>(`/api/products?${params}`)
  const products = productPage._embedded?.products ?? []
  const catalogProducts: CatalogProduct[] = []
  for (const product of products) {
    const variantPage = await request<VariantPage>(`/api/products/${product.id}/variants?size=100`)
    catalogProducts.push({ ...product, variants: variantPage._embedded?.variants ?? [] })
  }
  return { categories: categoryPage._embedded?.categories ?? [], products: catalogProducts }
}

export default function Products() {
  const { can } = useAuth()
  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let ignore = false
    fetchCatalog(appliedSearch)
      .then((loaded) => {
        if (!ignore) {
          setCatalog(loaded)
          setError('')
        }
      })
      .catch((caught) => {
        if (!ignore) {
          setError(messageOf(caught))
        }
      })
    return () => {
      ignore = true
    }
  }, [appliedSearch, version])

  function reload() {
    setVersion((current) => current + 1)
  }

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setAppliedSearch(search.trim())
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-4">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Produtos</h1>
        <Link to="/" className={linkClass}>
          Início
        </Link>
      </header>

      {error && (
        <p role="alert" className="mt-4 font-medium text-red-700">
          {error}
        </p>
      )}

      <form onSubmit={handleSearch} className="mt-6 flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="flex flex-1 flex-col gap-1">
          <label htmlFor="product-search" className={labelClass}>
            Buscar por nome
          </label>
          <input
            id="product-search"
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className={inputClass}
          />
        </div>
        <button type="submit" className={buttonClass}>
          Buscar
        </button>
      </form>

      {catalog && (
        <section aria-labelledby="products-title" className="mt-6">
          <h2 id="products-title" className="text-xl font-bold text-gray-900">
            Lista
          </h2>
          {catalog.products.length === 0 ? (
            <p className="mt-2 text-gray-900">Nenhum produto encontrado.</p>
          ) : (
            <ul className="mt-4 flex flex-col gap-4">
              {catalog.products.map((product) => (
                <li key={product.id} className="rounded-md border border-gray-500 p-4">
                  <h3 className="text-lg font-bold text-gray-900">{product.name}</h3>
                  <p className="text-gray-900">{product.categoryName}</p>
                  {product.description && <p className="text-gray-900">{product.description}</p>}
                  {product.variants.length === 0 ? (
                    <p className="mt-2 text-gray-900">Nenhuma variante cadastrada.</p>
                  ) : (
                    <ul className="mt-3 flex flex-col gap-2">
                      {product.variants.map((variant) => (
                        <li key={variant.id} className="rounded-md bg-gray-100 p-3">
                          <p className="font-medium text-gray-900">
                            {variant.color} · {variant.size}
                            {variant.model ? ` · ${variant.model}` : ''}
                          </p>
                          <p className="text-gray-900">{currency.format(variant.price)}</p>
                          {variant.balance <= 0 ? (
                            <p className="font-medium text-red-700">Esgotado</p>
                          ) : (
                            <p className="text-gray-900">Saldo: {variant.balance}</p>
                          )}
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {can('product:edit') && catalog && (
        <section aria-labelledby="create-title" className="mt-10 flex flex-col gap-8">
          <h2 id="create-title" className="text-xl font-bold text-gray-900">
            Cadastrar
          </h2>
          <CategoryForm onCreated={reload} />
          <ProductForm categories={catalog.categories} onCreated={reload} />
          <VariantForm products={catalog.products} onCreated={reload} />
        </section>
      )}
    </main>
  )
}

function CategoryForm({ onCreated }: { onCreated: () => void }) {
  const [id, setId] = useState(newId)
  const [name, setName] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      await request('/api/categories', { method: 'POST', body: { id, name: name.trim() } })
      setId(newId())
      setName('')
      setMessage('Categoria criada.')
      onCreated()
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h3 className="text-lg font-bold text-gray-900">Nova categoria</h3>
      <div className="flex flex-col gap-1">
        <label htmlFor="category-name" className={labelClass}>
          Nome da categoria
        </label>
        <input
          id="category-name"
          type="text"
          required
          value={name}
          onChange={(event) => setName(event.target.value)}
          className={inputClass}
        />
      </div>
      {message && (
        <p role="status" className="font-medium text-green-800">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="font-medium text-red-700">
          {error}
        </p>
      )}
      <button type="submit" disabled={submitting} className={buttonClass}>
        {submitting ? 'Salvando...' : 'Criar categoria'}
      </button>
    </form>
  )
}

function ProductForm({ categories, onCreated }: { categories: Category[]; onCreated: () => void }) {
  const [id, setId] = useState(newId)
  const [categoryId, setCategoryId] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      await request('/api/products', {
        method: 'POST',
        body: { id, categoryId, name: name.trim(), description: description.trim() || undefined },
      })
      setId(newId())
      setCategoryId('')
      setName('')
      setDescription('')
      setMessage('Produto criado.')
      onCreated()
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h3 className="text-lg font-bold text-gray-900">Novo produto</h3>
      <div className="flex flex-col gap-1">
        <label htmlFor="product-category" className={labelClass}>
          Categoria
        </label>
        <select
          id="product-category"
          required
          value={categoryId}
          onChange={(event) => setCategoryId(event.target.value)}
          className={inputClass}
        >
          <option value="">Selecione a categoria</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="product-name" className={labelClass}>
          Nome do produto
        </label>
        <input
          id="product-name"
          type="text"
          required
          value={name}
          onChange={(event) => setName(event.target.value)}
          className={inputClass}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="product-description" className={labelClass}>
          Descrição (opcional)
        </label>
        <input
          id="product-description"
          type="text"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          className={inputClass}
        />
      </div>
      {message && (
        <p role="status" className="font-medium text-green-800">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="font-medium text-red-700">
          {error}
        </p>
      )}
      <button type="submit" disabled={submitting} className={buttonClass}>
        {submitting ? 'Salvando...' : 'Criar produto'}
      </button>
    </form>
  )
}

function VariantForm({ products, onCreated }: { products: CatalogProduct[]; onCreated: () => void }) {
  const [id, setId] = useState(newId)
  const [productId, setProductId] = useState('')
  const [color, setColor] = useState('')
  const [size, setSize] = useState('')
  const [model, setModel] = useState('')
  const [price, setPrice] = useState('')
  const [cost, setCost] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      await request(`/api/products/${productId}/variants`, {
        method: 'POST',
        body: {
          id,
          color: color.trim(),
          size: size.trim(),
          model: model.trim() || undefined,
          price: Number(price),
          cost: Number(cost),
        },
      })
      setId(newId())
      setProductId('')
      setColor('')
      setSize('')
      setModel('')
      setPrice('')
      setCost('')
      setMessage('Variante criada.')
      onCreated()
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <h3 className="text-lg font-bold text-gray-900">Nova variante</h3>
      <div className="flex flex-col gap-1">
        <label htmlFor="variant-product" className={labelClass}>
          Produto
        </label>
        <select
          id="variant-product"
          required
          value={productId}
          onChange={(event) => setProductId(event.target.value)}
          className={inputClass}
        >
          <option value="">Selecione o produto</option>
          {products.map((product) => (
            <option key={product.id} value={product.id}>
              {product.name}
            </option>
          ))}
        </select>
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="variant-color" className={labelClass}>
          Cor
        </label>
        <input
          id="variant-color"
          type="text"
          required
          value={color}
          onChange={(event) => setColor(event.target.value)}
          className={inputClass}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="variant-size" className={labelClass}>
          Tamanho
        </label>
        <input
          id="variant-size"
          type="text"
          required
          value={size}
          onChange={(event) => setSize(event.target.value)}
          className={inputClass}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="variant-model" className={labelClass}>
          Modelo (opcional)
        </label>
        <input
          id="variant-model"
          type="text"
          value={model}
          onChange={(event) => setModel(event.target.value)}
          className={inputClass}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="variant-price" className={labelClass}>
          Preço de venda (R$)
        </label>
        <input
          id="variant-price"
          type="number"
          min="0"
          step="0.01"
          required
          value={price}
          onChange={(event) => setPrice(event.target.value)}
          className={inputClass}
        />
      </div>
      <div className="flex flex-col gap-1">
        <label htmlFor="variant-cost" className={labelClass}>
          Custo (R$)
        </label>
        <input
          id="variant-cost"
          type="number"
          min="0"
          step="0.01"
          required
          value={cost}
          onChange={(event) => setCost(event.target.value)}
          className={inputClass}
        />
      </div>
      {message && (
        <p role="status" className="font-medium text-green-800">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="font-medium text-red-700">
          {error}
        </p>
      )}
      <button type="submit" disabled={submitting || !productId} className={buttonClass}>
        {submitting ? 'Salvando...' : 'Criar variante'}
      </button>
    </form>
  )
}
