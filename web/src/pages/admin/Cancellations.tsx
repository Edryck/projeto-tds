import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Ban } from 'lucide-react'
import { ApiError, fetchPage, follow } from '../../api'
import type { Model } from '../../api'
import { useToast } from '../../components/Toast'
import { Button, Card, ConfirmSheet, EmptyState, ErrorState, Field, PageHeader, Sheet, Skeleton, Textarea } from '../../components/ui'
import { dateTime, money, shortId } from '../../format'
import { useLoad } from '../../hooks'
import type { CancellationView } from '../../types'

type Decision = { item: Model<CancellationView>; kind: 'approve' | 'reject' }

export default function Cancellations() {
  const toast = useToast()
  const list = useLoad(
    async () => (await fetchPage<CancellationView>('/api/cancellation-requests?status=PENDING&page=0&size=50')).items,
    [],
  )
  const [decision, setDecision] = useState<Decision | null>(null)
  const [response, setResponse] = useState('')
  const [busy, setBusy] = useState(false)

  function openDecision(item: Model<CancellationView>, kind: 'approve' | 'reject') {
    setResponse('')
    setDecision({ item, kind })
  }

  async function decide() {
    if (!decision) {
      return
    }
    setBusy(true)
    try {
      const body = decision.kind === 'reject' && response.trim() !== '' ? { response: response.trim() } : undefined
      await follow(decision.item._links[decision.kind], { method: 'POST', body })
      setDecision(null)
      toast.show(decision.kind === 'approve' ? 'Cancelamento aprovado' : 'Solicitação recusada')
      list.reload()
    } catch (caught) {
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
      if (caught instanceof ApiError && caught.status === 409) {
        setDecision(null)
        list.reload()
      }
    } finally {
      setBusy(false)
    }
  }

  const items = list.data ?? []

  return (
    <div className="mx-auto max-w-3xl space-y-4 pb-4">
      <PageHeader title="Cancelamentos" subtitle="Solicitações aguardando decisão" />
      {list.loading && list.data === null && <Skeleton lines={4} />}
      {list.error !== null && list.data === null && <ErrorState message={list.error} onRetry={list.reload} />}
      {list.data !== null && items.length === 0 && <EmptyState icon={Ban} title="Nenhuma solicitação pendente" />}
      <ul className="space-y-3">
        {items.map((item) => (
          <li key={item.id}>
            <Card>
              <Link
                to={`/sales/${item.orderId}`}
                className="block rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-text">{item.orderCustomerName}</p>
                    <p className="text-sm text-text-secondary">Pedido {shortId(item.orderId)}</p>
                  </div>
                  <p className="shrink-0 font-semibold text-text">{money(item.orderTotal)}</p>
                </div>
                <p className="mt-2 text-sm text-text-secondary">
                  {item.requestedByName} · {dateTime(item.requestedAt)}
                </p>
                <p className="mt-1 break-words text-base text-text">{item.reason}</p>
              </Link>
              <div className="mt-3 flex gap-2">
                <Button variant="ghost" className="flex-1" onClick={() => openDecision(item, 'reject')}>
                  Recusar
                </Button>
                <Button variant="danger" className="flex-1" onClick={() => openDecision(item, 'approve')}>
                  Aprovar
                </Button>
              </div>
            </Card>
          </li>
        ))}
      </ul>

      <ConfirmSheet
        open={decision?.kind === 'approve'}
        title="Aprovar cancelamento?"
        message="Aprovar cancelamento? O pedido será cancelado e o estoque volta."
        confirmLabel="Aprovar"
        danger
        busy={busy}
        onConfirm={decide}
        onClose={() => setDecision(null)}
      />

      <Sheet
        open={decision?.kind === 'reject'}
        onClose={() => setDecision(null)}
        title="Recusar solicitação"
        footer={
          <Button className="flex-1" loading={busy} onClick={decide}>
            Recusar
          </Button>
        }
      >
        <Field label="Resposta (opcional)">
          <Textarea value={response} maxLength={500} onChange={(event) => setResponse(event.target.value)} />
        </Field>
      </Sheet>
    </div>
  )
}
