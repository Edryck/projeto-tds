import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Ban, CheckCircle2, Clock3, PackageCheck, Pencil, Truck } from 'lucide-react'
import { follow, hasLink, request } from '../../api'
import type { Model } from '../../api'
import { useToast } from '../../components/Toast'
import { Button, Card, ConfirmSheet, ErrorState, Field, Input, PageHeader, Sheet, Skeleton, StatusPill, Textarea } from '../../components/ui'
import { dateTime, money, parseMoney, shortId } from '../../format'
import { useLoad } from '../../hooks'
import { orderStatusMeta, paymentLabels, shippingLabels } from '../../types'
import type { OrderView, PaymentMethod, ShippingMethod } from '../../types'

type Action = 'pay' | 'ship' | 'deliver' | 'shipping-cost' | 'cancel' | 'request-cancel'

const paymentMethods: PaymentMethod[] = ['PIX', 'CARD', 'CASH']
const shippingMethods: ShippingMethod[] = ['POST_OFFICE', 'IN_PERSON']

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex justify-between gap-3 ${strong ? 'text-lg font-bold' : 'text-base'} text-text`}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function MethodButtons<T extends string>({
  options,
  labels,
  value,
  onChange,
}: {
  options: T[]
  labels: Record<T, string>
  value: T | null
  onChange: (value: T) => void
}) {
  return (
    <div className="space-y-2">
      {options.map((option) => (
        <Button
          key={option}
          variant={value === option ? 'primary' : 'ghost'}
          aria-pressed={value === option}
          className="w-full"
          onClick={() => onChange(option)}
        >
          {labels[option]}
        </Button>
      ))}
    </div>
  )
}

export default function SaleDetail() {
  const { id } = useParams()
  const toast = useToast()
  const { data, error, loading, reload } = useLoad(() => request<Model<OrderView>>(`/api/orders/${id}`), [id])
  const [updated, setUpdated] = useState<Model<OrderView> | null>(null)
  const [sheet, setSheet] = useState<Action | null>(null)
  const [busy, setBusy] = useState(false)
  const [payMethod, setPayMethod] = useState<PaymentMethod | null>(null)
  const [shipMethod, setShipMethod] = useState<ShippingMethod | null>(null)
  const [tracking, setTracking] = useState('')
  const [shippingText, setShippingText] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [reason, setReason] = useState('')

  const order = updated && updated.id === id ? updated : data

  function open(action: Action) {
    setFieldError('')
    setPayMethod(null)
    setShipMethod(null)
    setTracking('')
    setReason('')
    setShippingText(order ? String(order.shippingCost).replace('.', ',') : '')
    setSheet(action)
  }

  async function run(action: Action, method: string, body?: unknown) {
    if (!order) {
      return
    }
    setBusy(true)
    try {
      const result = await follow<Model<OrderView>>(order._links[action], { method, body })
      setUpdated(result)
      setSheet(null)
      const messages: Record<Action, string> = {
        pay: 'Pagamento registrado',
        ship: 'Pedido marcado como enviado',
        deliver: 'Pedido marcado como entregue',
        'shipping-cost': 'Frete atualizado',
        cancel: 'Pedido cancelado',
        'request-cancel': 'Cancelamento solicitado',
      }
      toast.show(messages[action])
    } catch (caught) {
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setBusy(false)
    }
  }

  function confirmShip() {
    if (!shipMethod) {
      return
    }
    if (shipMethod === 'POST_OFFICE' && tracking.trim() === '') {
      setFieldError('Informe o código de rastreio')
      return
    }
    run('ship', 'POST', { method: shipMethod, trackingCode: shipMethod === 'POST_OFFICE' ? tracking.trim() : null })
  }

  function confirmShippingCost() {
    const value = shippingText.trim() === '' ? 0 : parseMoney(shippingText)
    if (Number.isNaN(value) || value < 0) {
      setFieldError('Valor inválido')
      return
    }
    run('shipping-cost', 'PATCH', { shippingCost: value })
  }

  function confirmRequestCancel() {
    if (reason.trim() === '') {
      setFieldError('Informe o motivo')
      return
    }
    run('request-cancel', 'POST', { reason: reason.trim() })
  }

  if (loading && !order) {
    return <Skeleton lines={5} />
  }
  if (error && !order) {
    return <ErrorState message={error} onRetry={reload} />
  }
  if (!order) {
    return null
  }

  const meta = orderStatusMeta[order.status]
  const canPay = hasLink(order, 'pay')
  const canShip = hasLink(order, 'ship')
  const canDeliver = hasLink(order, 'deliver')
  const canEditShipping = hasLink(order, 'shipping-cost')
  const canCancel = hasLink(order, 'cancel')
  const canRequestCancel = hasLink(order, 'request-cancel') && !canCancel
  const pendingCancellation = order.pendingCancellation

  return (
    <div className="mx-auto max-w-3xl space-y-4 pb-4">
      <PageHeader title={shortId(order.id)} subtitle={dateTime(order.orderedAt)} back={{ to: '/sales', label: 'Vendas' }} />
      <div className="flex items-center justify-between gap-3">
        <StatusPill tone={meta.tone} icon={meta.icon}>
          {meta.label}
        </StatusPill>
        <p className="text-2xl font-bold text-text">{money(order.total)}</p>
      </div>

      {pendingCancellation && (
        <div role="status" className="flex gap-3 rounded-[14px] bg-warning-soft p-4 text-warning">
          <Clock3 className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <div className="min-w-0">
            <p className="font-semibold">
              Cancelamento solicitado por {pendingCancellation.requestedByName}: aguardando aprovação
            </p>
            <p className="mt-1 break-words text-sm">{pendingCancellation.reason}</p>
          </div>
        </div>
      )}

      {(canPay || canShip || canDeliver || canEditShipping || canCancel || canRequestCancel) && (
        <div className="space-y-2">
          {canPay && (
            <Button className="w-full" onClick={() => open('pay')}>
              <CheckCircle2 className="h-5 w-5" aria-hidden="true" />
              Marcar como pago
            </Button>
          )}
          {canShip && (
            <Button className="w-full" onClick={() => open('ship')}>
              <Truck className="h-5 w-5" aria-hidden="true" />
              Marcar como enviado
            </Button>
          )}
          {canDeliver && (
            <Button className="w-full" onClick={() => open('deliver')}>
              <PackageCheck className="h-5 w-5" aria-hidden="true" />
              Marcar como entregue
            </Button>
          )}
          {canRequestCancel && (
            <Button variant="ghost" className="w-full text-error" onClick={() => open('request-cancel')}>
              <Ban className="h-5 w-5" aria-hidden="true" />
              Solicitar cancelamento
            </Button>
          )}
          {(canEditShipping || canCancel) && (
            <div className="flex gap-2">
              {canEditShipping && (
                <Button variant="ghost" className="flex-1" onClick={() => open('shipping-cost')}>
                  <Pencil className="h-5 w-5" aria-hidden="true" />
                  Editar frete
                </Button>
              )}
              {canCancel && (
                <Button variant="ghost" className="flex-1 text-error" onClick={() => open('cancel')}>
                  <Ban className="h-5 w-5" aria-hidden="true" />
                  Cancelar
                </Button>
              )}
            </div>
          )}
        </div>
      )}

      <Card>
        <h2 className="mb-2 font-serif text-lg font-bold text-text">Cliente</h2>
        <Link
          to={`/customers/${order.customerId}`}
          className="inline-flex min-h-[48px] items-center text-base font-medium text-primary underline"
        >
          {order.customerName}
        </Link>
      </Card>

      <Card>
        <h2 className="mb-2 font-serif text-lg font-bold text-text">Itens</h2>
        <ul className="divide-y divide-border">
          {order.items.map((item) => (
            <li key={item.id} className="flex justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="text-base font-medium text-text">{item.productName}</p>
                <p className="text-sm text-text-secondary">
                  {item.variantLabel} · {item.quantity} x {money(item.unitPrice)}
                </p>
              </div>
              <p className="shrink-0 text-base font-medium text-text">{money(item.quantity * item.unitPrice)}</p>
            </li>
          ))}
        </ul>
      </Card>

      <Card>
        <h2 className="mb-2 font-serif text-lg font-bold text-text">Resumo</h2>
        <dl className="space-y-1">
          <Row label="Subtotal" value={money(order.subtotal)} />
          {order.discount > 0 && (
            <Row
              label={order.couponCode ? `Desconto (${order.couponCode})` : 'Desconto'}
              value={`- ${money(order.discount)}`}
            />
          )}
          <Row label="Frete" value={money(order.shippingCost)} />
          <Row label="Total" value={money(order.total)} strong />
        </dl>
      </Card>

      <Card>
        <h2 className="mb-2 font-serif text-lg font-bold text-text">Pagamento</h2>
        {order.payment ? (
          <p className="text-base text-text">
            {paymentLabels[order.payment.method]} · {dateTime(order.payment.paidAt)}
          </p>
        ) : (
          <p className="text-base text-text-secondary">Pagamento não registrado</p>
        )}
      </Card>

      <Card>
        <h2 className="mb-2 font-serif text-lg font-bold text-text">Entrega</h2>
        {order.shipment ? (
          <div className="space-y-1 text-base text-text">
            <p>{shippingLabels[order.shipment.method]}</p>
            {order.shipment.trackingCode && <p>Rastreio: {order.shipment.trackingCode}</p>}
            {order.shipment.shippedAt && <p>Enviado em {dateTime(order.shipment.shippedAt)}</p>}
            {order.shipment.deliveredAt && <p>Entregue em {dateTime(order.shipment.deliveredAt)}</p>}
          </div>
        ) : (
          <p className="text-base text-text-secondary">Ainda não enviado</p>
        )}
      </Card>

      <Sheet
        open={sheet === 'pay'}
        onClose={() => setSheet(null)}
        title="Forma de pagamento"
        footer={
          <Button className="flex-1" disabled={!payMethod} loading={busy} onClick={() => run('pay', 'POST', { method: payMethod })}>
            Confirmar pagamento
          </Button>
        }
      >
        <MethodButtons options={paymentMethods} labels={paymentLabels} value={payMethod} onChange={setPayMethod} />
      </Sheet>

      <Sheet
        open={sheet === 'ship'}
        onClose={() => setSheet(null)}
        title="Forma de envio"
        footer={
          <Button className="flex-1" disabled={!shipMethod} loading={busy} onClick={confirmShip}>
            Confirmar envio
          </Button>
        }
      >
        <div className="space-y-4">
          <MethodButtons
            options={shippingMethods}
            labels={shippingLabels}
            value={shipMethod}
            onChange={(value) => {
              setShipMethod(value)
              setFieldError('')
            }}
          />
          {shipMethod === 'POST_OFFICE' && (
            <Field label="Código de rastreio" required error={fieldError}>
              <Input value={tracking} invalid={fieldError !== ''} onChange={(event) => setTracking(event.target.value)} />
            </Field>
          )}
        </div>
      </Sheet>

      <Sheet
        open={sheet === 'shipping-cost'}
        onClose={() => setSheet(null)}
        title="Editar frete"
        footer={
          <Button className="flex-1" loading={busy} onClick={confirmShippingCost}>
            Salvar frete
          </Button>
        }
      >
        <Field label="Valor do frete (R$)" error={fieldError}>
          <Input
            inputMode="decimal"
            value={shippingText}
            invalid={fieldError !== ''}
            onChange={(event) => setShippingText(event.target.value)}
          />
        </Field>
      </Sheet>

      <Sheet
        open={sheet === 'request-cancel'}
        onClose={() => setSheet(null)}
        title="Solicitar cancelamento"
        footer={
          <Button className="flex-1" loading={busy} onClick={confirmRequestCancel}>
            Enviar solicitação
          </Button>
        }
      >
        <Field label="Motivo" required error={fieldError}>
          <Textarea
            value={reason}
            maxLength={500}
            invalid={fieldError !== ''}
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
      </Sheet>

      <ConfirmSheet
        open={sheet === 'deliver'}
        title="Marcar como entregue?"
        message="O pedido será finalizado."
        confirmLabel="Marcar como entregue"
        busy={busy}
        onConfirm={() => run('deliver', 'POST')}
        onClose={() => setSheet(null)}
      />

      <ConfirmSheet
        open={sheet === 'cancel'}
        title="Cancelar pedido?"
        message="O estoque dos itens volta automaticamente."
        confirmLabel="Cancelar pedido"
        danger
        busy={busy}
        onConfirm={() => run('cancel', 'POST')}
        onClose={() => setSheet(null)}
      />
    </div>
  )
}
