import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { follow, newId, request } from '../api'
import { useAuth } from '../auth'

type HalLink = { href: string }
type OrderItem = { id: string; variantId: string; quantity: number; unitPrice: number }
type Order = {
  id: string
  customerName: string
  status: string
  items: OrderItem[]
  subtotal: number
  discount: number
  shippingCost: number
  total: number
  note: string | null
  orderedAt: string
  _links: Record<string, HalLink>
}
type Customer = { id: string; name: string }
type VariantOption = { id: string; label: string }
type Product = { id: string; name: string }
type Variant = { id: string; color: string; size: string }
type DraftItem = { id: string; variantId: string; quantity: string }

type OrderPage = { _embedded?: { orders: Order[] } }
type CustomerPage = { _embedded?: { customers: Customer[] } }
type ProductPage = { _embedded?: { products: Product[] } }
type VariantPage = { _embedded?: { variants: Variant[] } }

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })

const statusLabels: Record<string, string> = {
  PENDING: 'Pendente',
  PAID: 'Pago',
  SHIPPED: 'Enviado',
  DELIVERED: 'Entregue',
  CANCELLED: 'Cancelado',
}
const paymentLabels: Record<string, string> = { PIX: 'Pix', CARD: 'Cartão', CASH: 'Dinheiro' }
const shippingLabels: Record<string, string> = { POST_OFFICE: 'Correios', IN_PERSON: 'Retirada em mãos' }
const actionLabels: Record<string, string> = {
  pay: 'Pagar',
  ship: 'Enviar',
  deliver: 'Marcar como entregue',
  cancel: 'Cancelar pedido',
  'shipping-cost': 'Alterar frete',
}
const actionOrder = ['pay', 'ship', 'deliver', 'shipping-cost', 'cancel']

const inputClass =
  'h-12 rounded-md border border-gray-500 bg-white px-3 text-base text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const buttonClass =
  'h-12 rounded-md bg-indigo-700 px-4 font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700 disabled:bg-gray-600'
const outlineButtonClass =
  'h-12 rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700 disabled:text-gray-600'
const linkClass =
  'flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const labelClass = 'font-medium text-gray-900'

function messageOf(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado'
}

async function fetchVariantOptions(): Promise<VariantOption[]> {
  const productPage = await request<ProductPage>('/api/products?size=100')
  const options: VariantOption[] = []
  for (const product of productPage._embedded?.products ?? []) {
    const variantPage = await request<VariantPage>(`/api/products/${product.id}/variants?size=100`)
    for (const variant of variantPage._embedded?.variants ?? []) {
      options.push({ id: variant.id, label: `${product.name} · ${variant.color} · ${variant.size}` })
    }
  }
  return options
}

function newDraftItem(): DraftItem {
  return { id: newId(), variantId: '', quantity: '1' }
}

function OrderDetail({
  order,
  variantLabels,
  onChange,
}: {
  order: Order
  variantLabels: Record<string, string>
  onChange: (order: Order) => void
}) {
  const [method, setMethod] = useState('')
  const [trackingCode, setTrackingCode] = useState('')
  const [shippingCost, setShippingCost] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const links = order._links
  const actions = actionOrder.filter((rel) => links[rel])

  async function run(rel: string, httpMethod: string, body?: unknown) {
    setError('')
    setBusy(true)
    try {
      const updated = await follow<Order>(links[rel], { method: httpMethod, body })
      setMethod('')
      setTrackingCode('')
      setShippingCost('')
      onChange(updated)
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setBusy(false)
    }
  }

  const handlers: Record<string, () => void> = {
    pay: () => run('pay', 'POST', { method }),
    ship: () => run('ship', 'POST', { method, trackingCode: trackingCode.trim() || undefined }),
    deliver: () => run('deliver', 'POST'),
    'shipping-cost': () => run('shipping-cost', 'PATCH', { shippingCost: Number(shippingCost) }),
    cancel: () => run('cancel', 'POST'),
  }

  return (
    <section aria-labelledby="order-detail-title" className="mt-6 rounded-md border border-gray-500 p-4 text-gray-900">
      <h2 id="order-detail-title" className="text-xl font-bold">
        Pedido de {order.customerName}
      </h2>
      <p>
        Status: <strong>{statusLabels[order.status] ?? order.status}</strong>
      </p>
      <ul className="mt-2">
        {order.items.map((item) => (
          <li key={item.id}>
            {item.quantity} x {variantLabels[item.variantId] ?? 'Variante'} ({currency.format(item.unitPrice)})
          </li>
        ))}
      </ul>
      <dl className="mt-3 grid grid-cols-2 gap-1">
        <dt>Subtotal</dt>
        <dd className="text-right">{currency.format(order.subtotal)}</dd>
        <dt>Desconto</dt>
        <dd className="text-right">{currency.format(order.discount)}</dd>
        <dt>Frete</dt>
        <dd className="text-right">{currency.format(order.shippingCost)}</dd>
        <dt className="font-bold">Total</dt>
        <dd className="text-right font-bold">{currency.format(order.total)}</dd>
      </dl>

      {actions.length > 0 && (
        <div className="mt-4 flex flex-col gap-4">
          {(links['pay'] || links['ship']) && (
            <div className="flex flex-col gap-1">
              <label htmlFor="action-method" className={labelClass}>
                {links['pay'] ? 'Forma de pagamento' : 'Forma de envio'}
              </label>
              <select
                id="action-method"
                value={method}
                onChange={(event) => setMethod(event.target.value)}
                className={inputClass}
              >
                <option value="">Selecione</option>
                {Object.entries(links['pay'] ? paymentLabels : shippingLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
          )}
          {links['ship'] && (
            <div className="flex flex-col gap-1">
              <label htmlFor="action-tracking" className={labelClass}>
                Código de rastreio (opcional)
              </label>
              <input
                id="action-tracking"
                type="text"
                value={trackingCode}
                onChange={(event) => setTrackingCode(event.target.value)}
                className={inputClass}
              />
            </div>
          )}
          {links['shipping-cost'] && (
            <div className="flex flex-col gap-1">
              <label htmlFor="action-shipping" className={labelClass}>
                Novo valor do frete (R$)
              </label>
              <input
                id="action-shipping"
                type="number"
                min="0"
                step="0.01"
                value={shippingCost}
                onChange={(event) => setShippingCost(event.target.value)}
                className={inputClass}
              />
            </div>
          )}
          <div className="flex flex-wrap gap-2">
            {actions.map((rel) => (
              <button
                key={rel}
                type="button"
                onClick={handlers[rel]}
                disabled={
                  busy ||
                  ((rel === 'pay' || rel === 'ship') && !method) ||
                  (rel === 'shipping-cost' && shippingCost === '')
                }
                className={rel === 'cancel' ? outlineButtonClass : buttonClass}
              >
                {actionLabels[rel]}
              </button>
            ))}
          </div>
        </div>
      )}
      {error && (
        <p role="alert" className="mt-3 font-medium text-red-700">
          {error}
        </p>
      )}
    </section>
  )
}

export default function Orders() {
  const { can } = useAuth()
  const [orders, setOrders] = useState<Order[]>([])
  const [customers, setCustomers] = useState<Customer[]>([])
  const [options, setOptions] = useState<VariantOption[]>([])
  const [loadError, setLoadError] = useState('')
  const [selectedId, setSelectedId] = useState('')
  const [orderId, setOrderId] = useState(newId)
  const [customerId, setCustomerId] = useState('')
  const [items, setItems] = useState<DraftItem[]>([newDraftItem()])
  const [shippingCost, setShippingCost] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let ignore = false
    Promise.all([
      request<OrderPage>('/api/orders?size=50&sort=orderedAt,desc'),
      can('order:create') ? request<CustomerPage>('/api/customers?size=100') : Promise.resolve(null),
      fetchVariantOptions(),
    ])
      .then(([orderPage, customerPage, loadedOptions]) => {
        if (!ignore) {
          setOrders(orderPage._embedded?.orders ?? [])
          setCustomers(customerPage?._embedded?.customers ?? [])
          setOptions(loadedOptions)
        }
      })
      .catch((caught) => {
        if (!ignore) {
          setLoadError(messageOf(caught))
        }
      })
    return () => {
      ignore = true
    }
  }, [can])

  const variantLabels: Record<string, string> = {}
  for (const option of options) {
    variantLabels[option.id] = option.label
  }
  const selected = orders.find((order) => order.id === selectedId)

  function replaceOrder(updated: Order) {
    setOrders((current) => current.map((order) => (order.id === updated.id ? updated : order)))
  }

  function updateItem(id: string, changes: Partial<DraftItem>) {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...changes } : item)))
  }

  function removeItem(id: string) {
    setItems((current) => current.filter((item) => item.id !== id))
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      const created = await request<Order>('/api/orders', {
        method: 'POST',
        body: {
          id: orderId,
          customerId,
          shippingCost: shippingCost === '' ? 0 : Number(shippingCost),
          items: items.map((item) => ({ id: item.id, variantId: item.variantId, quantity: Number(item.quantity) })),
        },
      })
      setOrders((current) => [created, ...current.filter((order) => order.id !== created.id)])
      setSelectedId(created.id)
      setOrderId(newId())
      setCustomerId('')
      setItems([newDraftItem()])
      setShippingCost('')
      setMessage('Pedido criado.')
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-4">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Pedidos</h1>
        <Link to="/" className={linkClass}>
          Início
        </Link>
      </header>

      {loadError && (
        <p role="alert" className="mt-4 font-medium text-red-700">
          {loadError}
        </p>
      )}

      <ul className="mt-6 flex flex-col gap-2">
        {orders.map((order) => (
          <li key={order.id}>
            <button
              type="button"
              onClick={() => setSelectedId(order.id)}
              aria-pressed={order.id === selectedId}
              className="flex min-h-12 w-full flex-wrap items-center justify-between gap-2 rounded-md border border-gray-500 px-3 py-2 text-left text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700 aria-pressed:border-indigo-700 aria-pressed:bg-indigo-50"
            >
              <span className="font-medium">{order.customerName}</span>
              <span>
                {statusLabels[order.status] ?? order.status} · {currency.format(order.total)}
              </span>
            </button>
          </li>
        ))}
      </ul>
      {orders.length === 0 && !loadError && <p className="mt-4 text-gray-900">Nenhum pedido ainda.</p>}

      {selected && <OrderDetail key={selected.id} order={selected} variantLabels={variantLabels} onChange={replaceOrder} />}

      {can('order:create') && (
        <form onSubmit={handleSubmit} className="mt-8 flex flex-col gap-4">
          <h2 className="text-xl font-bold text-gray-900">Novo pedido</h2>
          <div className="flex flex-col gap-1">
            <label htmlFor="order-customer" className={labelClass}>
              Cliente
            </label>
            <select
              id="order-customer"
              required
              value={customerId}
              onChange={(event) => setCustomerId(event.target.value)}
              className={inputClass}
            >
              <option value="">Selecione o cliente</option>
              {customers.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </select>
          </div>

          {items.map((item, index) => (
            <fieldset key={item.id} className="flex flex-col gap-3 rounded-md border border-gray-500 p-3">
              <legend className="px-1 font-medium text-gray-900">Item {index + 1}</legend>
              <div className="flex flex-col gap-1">
                <label htmlFor={`item-variant-${item.id}`} className={labelClass}>
                  Variante
                </label>
                <select
                  id={`item-variant-${item.id}`}
                  required
                  value={item.variantId}
                  onChange={(event) => updateItem(item.id, { variantId: event.target.value })}
                  className={inputClass}
                >
                  <option value="">Selecione a variante</option>
                  {options.map((option) => (
                    <option key={option.id} value={option.id}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="flex flex-col gap-1">
                <label htmlFor={`item-quantity-${item.id}`} className={labelClass}>
                  Quantidade
                </label>
                <input
                  id={`item-quantity-${item.id}`}
                  type="number"
                  min="1"
                  step="1"
                  required
                  value={item.quantity}
                  onChange={(event) => updateItem(item.id, { quantity: event.target.value })}
                  className={inputClass}
                />
              </div>
              {items.length > 1 && (
                <button type="button" onClick={() => removeItem(item.id)} className={outlineButtonClass}>
                  Remover item {index + 1}
                </button>
              )}
            </fieldset>
          ))}
          <button type="button" onClick={() => setItems((current) => [...current, newDraftItem()])} className={outlineButtonClass}>
            Adicionar item
          </button>

          <div className="flex flex-col gap-1">
            <label htmlFor="order-shipping" className={labelClass}>
              Frete (R$, opcional)
            </label>
            <input
              id="order-shipping"
              type="number"
              min="0"
              step="0.01"
              value={shippingCost}
              onChange={(event) => setShippingCost(event.target.value)}
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
            {submitting ? 'Criando...' : 'Criar pedido'}
          </button>
        </form>
      )}
    </main>
  )
}
