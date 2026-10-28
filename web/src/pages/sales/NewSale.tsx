import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { CheckCircle2, ChevronRight, Clock3, Minus, Plus, ShoppingBag, Trash2, UserRound } from 'lucide-react'
import { ApiError, fetchPage, newId, request } from '../../api'
import { CustomerPicker } from '../../components/CustomerPicker'
import { useToast } from '../../components/Toast'
import { Button, Card, Field, Input, LinkButton, PageHeader, Sheet } from '../../components/ui'
import { VariantPicker } from '../../components/VariantPicker'
import { money, parseMoney, shortId } from '../../format'
import { DEFAULT_CUSTOMER_ID, paymentLabels } from '../../types'
import type { Customer, OrderView, PaymentMethod, VariantView } from '../../types'

type CartLine = { variant: VariantView; quantity: number }
type Step = 'items' | 'payment' | 'done'
type Result = { order: OrderView; method: PaymentMethod | null; received: number | null; payFailed: boolean }

const defaultCustomer: Customer = {
  id: DEFAULT_CUSTOMER_ID,
  name: 'Consumidor não identificado',
  phone: null,
  email: null,
  note: null,
}

const methods: PaymentMethod[] = ['PIX', 'CARD', 'CASH']

const fixedBar =
  'fixed inset-x-0 bottom-0 z-40 border-t border-border bg-surface px-4 pt-3 pb-[calc(env(safe-area-inset-bottom)+12px)]'

function variantLabel(variant: VariantView): string {
  return `${variant.size} · ${variant.color}`
}

function remainingText(balance: number): string {
  if (balance <= 0) {
    return 'Esgotado'
  }
  return balance === 1 ? 'Só resta 1 unidade' : `Só restam ${balance} unidades`
}

export default function NewSale() {
  const toast = useToast()
  const [params] = useSearchParams()
  const [step, setStep] = useState<Step>('items')
  const [query, setQuery] = useState('')
  const [cart, setCart] = useState<CartLine[]>([])
  const [customer, setCustomer] = useState<Customer>(defaultCustomer)
  const [customerOpen, setCustomerOpen] = useState(false)
  const [cartOpen, setCartOpen] = useState(false)
  const [stockNotes, setStockNotes] = useState<Record<string, number>>({})
  const [orderId, setOrderId] = useState('')
  const [method, setMethod] = useState<PaymentMethod | null>(null)
  const [received, setReceived] = useState('')
  const [couponCode, setCouponCode] = useState('')
  const [couponError, setCouponError] = useState('')
  const [shipping, setShipping] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<Result | null>(null)
  const busyRef = useRef(false)

  const customerParam = params.get('customerId')

  useEffect(() => {
    if (!customerParam) {
      return
    }
    request<Customer>(`/api/customers/${customerParam}`)
      .then(setCustomer)
      .catch(() => {})
  }, [customerParam])

  const subtotal = cart.reduce((sum, line) => sum + line.variant.price * line.quantity, 0)
  const itemCount = cart.reduce((sum, line) => sum + line.quantity, 0)
  const shippingValue = shipping.trim() === '' ? 0 : parseMoney(shipping)
  const shippingInvalid = Number.isNaN(shippingValue) || shippingValue < 0
  const total = subtotal + (shippingInvalid ? 0 : shippingValue)
  const receivedValue = received.trim() === '' ? null : parseMoney(received)
  const hasCoupon = couponCode.trim() !== ''
  const cashShort = !hasCoupon && method === 'CASH' && receivedValue !== null && receivedValue < total
  const hasSoldOut = cart.some((line) => line.variant.balance <= 0)
  const change = method === 'CASH' && receivedValue !== null && receivedValue >= total ? receivedValue - total : null
  const canProceed = cart.length > 0 && !hasSoldOut

  function addVariant(variant: VariantView) {
    setStockNotes({})
    const line = cart.find((item) => item.variant.id === variant.id)
    if (line && line.quantity >= variant.balance) {
      toast.show('Saldo máximo atingido', 'error')
      return
    }
    if (line) {
      setCart(cart.map((item) => (item.variant.id === variant.id ? { ...item, quantity: item.quantity + 1 } : item)))
    } else {
      setCart([...cart, { variant, quantity: 1 }])
    }
  }

  function changeQuantity(variantId: string, delta: number) {
    setStockNotes({})
    setCart(
      cart.map((line) => {
        if (line.variant.id !== variantId) {
          return line
        }
        const next = Math.min(Math.max(line.quantity + delta, 1), Math.max(line.variant.balance, 1))
        return { ...line, quantity: next }
      }),
    )
  }

  function removeLine(variantId: string) {
    setStockNotes({})
    setCart(cart.filter((line) => line.variant.id !== variantId))
  }

  function goToPayment() {
    setCartOpen(false)
    setOrderId(newId())
    setStep('payment')
  }

  function reset() {
    setStep('items')
    setQuery('')
    setCart([])
    setCustomer(defaultCustomer)
    setStockNotes({})
    setMethod(null)
    setReceived('')
    setCouponCode('')
    setCouponError('')
    setShipping('')
    setResult(null)
  }

  async function refreshBalances() {
    const names = Array.from(new Set(cart.map((line) => line.variant.productName)))
    const fresh = new Map<string, VariantView>()
    for (const name of names) {
      try {
        const page = await fetchPage<VariantView>(`/api/variants?q=${encodeURIComponent(name)}&size=100`)
        page.items.forEach((variant) => fresh.set(variant.id, variant))
      } catch {
        continue
      }
    }
    const notes: Record<string, number> = {}
    const updated = cart.map((line) => {
      const variant = fresh.get(line.variant.id) ?? line.variant
      if (line.quantity > variant.balance) {
        notes[variant.id] = variant.balance
      }
      return { ...line, variant }
    })
    setCart(updated)
    setStockNotes(notes)
    setStep('items')
    if (Object.keys(notes).length > 0) {
      setCartOpen(true)
    }
  }

  async function submit(pay: boolean) {
    if (busyRef.current) {
      return
    }
    if (shippingInvalid) {
      toast.show('Frete inválido', 'error')
      return
    }
    busyRef.current = true
    setBusy(true)
    setCouponError('')
    let order: OrderView
    try {
      order = await request<OrderView>('/api/orders', {
        method: 'POST',
        body: {
          id: orderId,
          customerId: customer.id,
          note: null,
          shippingCost: shippingValue,
          couponCode: couponCode.trim() === '' ? null : couponCode.trim(),
          items: cart.map((line) => ({ id: newId(), variantId: line.variant.id, quantity: line.quantity })),
        },
      })
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        toast.show('Estoque mudou. Confira os itens.', 'error')
        await refreshBalances()
      } else if (e instanceof ApiError && e.message.includes('Cupom')) {
        setCouponError(e.message)
      } else {
        toast.show(e instanceof Error ? e.message : 'Não foi possível salvar a venda', 'error')
      }
      busyRef.current = false
      setBusy(false)
      return
    }

    let payFailed = false
    let finalOrder = order
    if (pay && method) {
      try {
        finalOrder = await request<OrderView>(`/api/orders/${order.id}/pay`, { method: 'POST', body: { method } })
      } catch {
        payFailed = true
      }
    }
    setResult({
      order: finalOrder,
      method: pay && !payFailed ? method : null,
      received: pay && !payFailed && method === 'CASH' ? receivedValue : null,
      payFailed,
    })
    setCart([])
    setStep('done')
    busyRef.current = false
    setBusy(false)
  }

  if (step === 'done' && result) {
    return <Done result={result} onNew={reset} />
  }

  const cartContent = (
    <div className="space-y-4">
      <button
        type="button"
        onClick={() => {
          setCartOpen(false)
          setCustomerOpen(true)
        }}
        className="flex min-h-[48px] w-full items-center gap-3 rounded-[14px] border border-border bg-surface px-4 text-left hover:bg-surface-alt focus:outline-none focus:ring-2 focus:ring-secondary/60"
      >
        <UserRound className="h-5 w-5 shrink-0 text-secondary" aria-hidden="true" />
        <span className="min-w-0 flex-1">
          <span className="block text-xs text-text-secondary">Cliente</span>
          <span className="block truncate text-base font-medium text-text">{customer.name}</span>
        </span>
        <ChevronRight className="h-5 w-5 text-text-secondary" aria-hidden="true" />
      </button>

      {cart.length === 0 ? (
        <p className="py-6 text-center text-sm text-text-secondary">Nenhum item no carrinho.</p>
      ) : (
        <ul className="space-y-3">
          {cart.map((line) => (
            <li key={line.variant.id} className="rounded-[14px] border border-border p-3">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-base font-semibold text-text">{line.variant.productName}</p>
                  <p className="text-sm text-text-secondary">
                    {variantLabel(line.variant)} · {money(line.variant.price)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => removeLine(line.variant.id)}
                  aria-label={`Remover ${line.variant.productName}`}
                  className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-error hover:bg-error-soft focus:outline-none focus:ring-2 focus:ring-secondary/60"
                >
                  <Trash2 className="h-5 w-5" aria-hidden="true" />
                </button>
              </div>
              <div className="mt-2 flex items-center justify-between">
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => changeQuantity(line.variant.id, -1)}
                    disabled={line.quantity <= 1}
                    aria-label="Diminuir quantidade"
                    className="flex h-12 w-12 items-center justify-center rounded-xl border border-border hover:bg-surface-alt focus:outline-none focus:ring-2 focus:ring-secondary/60 disabled:opacity-40"
                  >
                    <Minus className="h-5 w-5" aria-hidden="true" />
                  </button>
                  <span className="w-8 text-center text-base font-semibold text-text">{line.quantity}</span>
                  <button
                    type="button"
                    onClick={() => changeQuantity(line.variant.id, 1)}
                    disabled={line.quantity >= line.variant.balance}
                    aria-label="Aumentar quantidade"
                    className="flex h-12 w-12 items-center justify-center rounded-xl border border-border hover:bg-surface-alt focus:outline-none focus:ring-2 focus:ring-secondary/60 disabled:opacity-40"
                  >
                    <Plus className="h-5 w-5" aria-hidden="true" />
                  </button>
                </div>
                <span className="text-base font-semibold text-text">{money(line.variant.price * line.quantity)}</span>
              </div>
              {stockNotes[line.variant.id] !== undefined && (
                <p role="alert" className="mt-2 text-sm font-medium text-error">
                  {remainingText(stockNotes[line.variant.id])}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="flex items-baseline justify-between border-t border-border pt-3">
        <span className="text-base text-text-secondary">Total</span>
        <span className="font-serif text-2xl font-bold text-text">{money(subtotal)}</span>
      </div>
    </div>
  )

  const customerSheet = (
    <CustomerPicker
      open={customerOpen}
      onClose={() => setCustomerOpen(false)}
      onPick={(picked) => {
        setCustomer(picked)
        setCustomerOpen(false)
      }}
    />
  )

  if (step === 'payment') {
    const canFinish = method !== null && !cashShort && canProceed
    return (
      <div className="pb-32 lg:pb-0">
        <PageHeader title="Pagamento" subtitle={customer.name} />
        <Card className="mb-4">
          <ul className="space-y-1">
            {cart.map((line) => (
              <li key={line.variant.id} className="flex justify-between gap-3 text-base text-text">
                <span className="min-w-0 truncate">
                  {line.quantity}x {line.variant.productName} ({variantLabel(line.variant)})
                </span>
                <span className="shrink-0">{money(line.variant.price * line.quantity)}</span>
              </li>
            ))}
          </ul>
          <div className="mt-3 flex items-baseline justify-between border-t border-border pt-3">
            <span className="text-base text-text-secondary">Total</span>
            <span className="font-serif text-2xl font-bold text-text">{money(total)}</span>
          </div>
        </Card>

        <div className="mb-4 grid grid-cols-3 gap-2" role="group" aria-label="Forma de pagamento">
          {methods.map((item) => (
            <button
              key={item}
              type="button"
              aria-pressed={method === item}
              onClick={() => setMethod(item)}
              className={`min-h-[56px] rounded-xl border text-base font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-secondary/60 ${
                method === item ? 'border-secondary bg-secondary text-primary' : 'border-border bg-surface text-text'
              }`}
            >
              {paymentLabels[item]}
            </button>
          ))}
        </div>

        {method === 'CASH' && (
          <div className="mb-4 space-y-2">
            <Field label="Valor recebido" error={cashShort ? 'Valor recebido menor que o total' : undefined}>
              <Input
                inputMode="decimal"
                value={received}
                onChange={(event) => setReceived(event.target.value)}
                invalid={cashShort}
                placeholder="0,00"
              />
            </Field>
            {change !== null && (
              <p className="text-base font-semibold text-text">
                {hasCoupon ? 'Troco estimado' : 'Troco'}: {money(change)}
              </p>
            )}
          </div>
        )}

        <details className="mb-4 rounded-[14px] border border-border bg-surface">
          <summary className="flex min-h-[48px] cursor-pointer items-center px-4 text-base font-medium text-text focus:outline-none focus:ring-2 focus:ring-secondary/60">
            Cupom e frete
          </summary>
          <div className="space-y-4 px-4 pb-4">
            <Field label="Cupom" error={couponError || undefined}>
              <Input
                value={couponCode}
                onChange={(event) => {
                  setCouponCode(event.target.value)
                  setCouponError('')
                }}
                invalid={couponError !== ''}
                autoCapitalize="characters"
              />
            </Field>
            <Field label="Frete" error={shippingInvalid ? 'Frete inválido' : undefined}>
              <Input
                inputMode="decimal"
                value={shipping}
                onChange={(event) => setShipping(event.target.value)}
                invalid={shippingInvalid}
                placeholder="0,00"
              />
            </Field>
          </div>
        </details>

        <div className={`${fixedBar} lg:static lg:border-0 lg:p-0`}>
          <div className="mx-auto flex max-w-md flex-col gap-2 lg:mx-0">
            <Button onClick={() => submit(true)} disabled={!canFinish} loading={busy}>
              Finalizar venda
            </Button>
            <Button variant="ghost" onClick={() => submit(false)} disabled={busy || !canProceed}>
              Salvar como pendente
            </Button>
            <Button variant="ghost" onClick={() => setStep('items')} disabled={busy}>
              Voltar aos itens
            </Button>
          </div>
        </div>
        {customerSheet}
      </div>
    )
  }

  return (
    <div className="pb-40 lg:pb-0">
      <PageHeader title="Nova venda" back={{ to: '/sales', label: 'Vendas' }} />
      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-4">
          <Input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Buscar produto pelo nome..."
            aria-label="Buscar produto pelo nome"
          />
          <VariantPicker query={query} onPick={addVariant} />
        </div>

        <aside className="hidden lg:block">
          <Card className="sticky top-4 space-y-4">
            <h2 className="font-serif text-lg font-bold text-text">Carrinho</h2>
            {cartContent}
            <Button className="w-full" onClick={goToPayment} disabled={!canProceed}>
              Continuar para pagamento
            </Button>
          </Card>
        </aside>
      </div>

      <div className={`${fixedBar} lg:hidden`}>
        <div className="mx-auto flex max-w-md flex-col gap-2">
          <button
            type="button"
            onClick={() => setCartOpen(true)}
            className="flex min-h-[48px] items-center justify-between gap-2 rounded-xl bg-surface-alt px-4 text-base font-medium text-text focus:outline-none focus:ring-2 focus:ring-secondary/60"
          >
            <span className="flex items-center gap-2">
              <ShoppingBag className="h-5 w-5" aria-hidden="true" />
              {itemCount} {itemCount === 1 ? 'item' : 'itens'} · {money(subtotal)}
            </span>
            <span className="text-sm font-semibold">Ver carrinho</span>
          </button>
          <Button onClick={goToPayment} disabled={!canProceed}>
            Continuar para pagamento
          </Button>
        </div>
      </div>

      <Sheet open={cartOpen} onClose={() => setCartOpen(false)} title="Carrinho">
        {cartContent}
      </Sheet>
      {customerSheet}
    </div>
  )
}

function Done({ result, onNew }: { result: Result; onNew: () => void }) {
  const { order, method, received, payFailed } = result
  const change = received !== null && received >= order.total ? received - order.total : null
  const pending = payFailed || method === null
  return (
    <div className="mx-auto max-w-md space-y-4 py-6 text-center">
      {pending ? (
        <Clock3 className="mx-auto h-14 w-14 text-warning" aria-hidden="true" />
      ) : (
        <CheckCircle2 className="mx-auto h-14 w-14 text-accent" aria-hidden="true" />
      )}
      <h1 className="font-serif text-2xl font-bold text-text">
        {pending ? 'Venda salva como pendente' : 'Venda concluída'}
      </h1>
      {payFailed && (
        <p role="alert" className="rounded-[14px] bg-error-soft px-4 py-3 text-base text-error">
          Venda salva como pendente. Pagamento não registrado.{' '}
          <Link to={`/sales/${order.id}`} className="font-semibold underline">
            Abrir pedido
          </Link>
        </p>
      )}
      <Card className="space-y-2 text-left">
        <div className="flex justify-between text-base text-text">
          <span className="text-text-secondary">Pedido</span>
          <span className="font-semibold">{shortId(order.id)}</span>
        </div>
        <div className="flex justify-between text-base text-text">
          <span className="text-text-secondary">Total</span>
          <span className="font-semibold">{money(order.total)}</span>
        </div>
        {method && (
          <div className="flex justify-between text-base text-text">
            <span className="text-text-secondary">Forma</span>
            <span className="font-semibold">{paymentLabels[method]}</span>
          </div>
        )}
        {change !== null && (
          <div className="flex justify-between text-base text-text">
            <span className="text-text-secondary">Troco</span>
            <span className="font-semibold">{money(change)}</span>
          </div>
        )}
      </Card>
      <div className="flex flex-col gap-2">
        <Button onClick={onNew}>Nova venda</Button>
        <LinkButton variant="ghost" to={`/sales/${order.id}`}>
          Ver pedido
        </LinkButton>
      </div>
    </div>
  )
}
