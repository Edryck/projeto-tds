import { useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Pencil, Trash2 } from 'lucide-react'
import { request } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, ConfirmSheet, ErrorState, LinkButton, PageHeader, Skeleton } from '../../components/ui'
import { useLoad } from '../../hooks'
import type { SupplierView } from '../../types'

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1">
      <dt className="text-sm text-text-secondary">{label}</dt>
      <dd className="text-base text-text">{children}</dd>
    </div>
  )
}

function entriesText(count: number): string {
  return count === 1 ? '1 entrada' : `${count} entradas`
}

export default function SupplierDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('stock:adjust')
  const { data, error, loading, reload } = useLoad(() => request<SupplierView>(`/api/suppliers/${id}`), [id])
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)

  async function remove() {
    setDeleting(true)
    try {
      await request<void>(`/api/suppliers/${id}`, { method: 'DELETE' })
      toast.show('Fornecedor excluído')
      navigate('/suppliers', { replace: true })
    } catch (caught) {
      setConfirming(false)
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setDeleting(false)
    }
  }

  if (loading && !data) {
    return <Skeleton lines={5} />
  }
  if (error && !data) {
    return <ErrorState message={error} onRetry={reload} />
  }
  if (!data) {
    return null
  }

  const phoneDigits = data.phone ? data.phone.replace(/\D/g, '') : ''
  const confirmMessage =
    data.entryCount > 0
      ? `Este fornecedor tem ${entriesText(data.entryCount)}. O histórico mantém o nome dele.`
      : 'O fornecedor deixa de aparecer nas listas. Deseja continuar?'

  return (
    <div className="mx-auto max-w-3xl space-y-4 pb-4">
      <PageHeader title={data.name} subtitle={entriesText(data.entryCount)} back={{ to: '/suppliers', label: 'Fornecedores' }} />

      <Card>
        <dl className="space-y-4">
          <Info label="Nome de contato">{data.contactName || 'Não informado'}</Info>
          <Info label="Telefone">
            {phoneDigits ? (
              <a href={`tel:${phoneDigits}`} className="inline-flex min-h-[48px] items-center text-primary underline">
                {data.phone}
              </a>
            ) : (
              'Não informado'
            )}
          </Info>
          <Info label="E-mail">
            {data.email ? (
              <a href={`mailto:${data.email}`} className="inline-flex min-h-[48px] items-center break-all text-primary underline">
                {data.email}
              </a>
            ) : (
              'Não informado'
            )}
          </Info>
          <Info label="Endereço">{data.address || 'Não informado'}</Info>
          <Info label="Observações">{data.note || 'Nenhuma'}</Info>
        </dl>
      </Card>

      {canEdit && (
        <div className="flex gap-2">
          <LinkButton to={`/suppliers/${id}/edit`} variant="ghost" className="flex-1">
            <Pencil className="h-5 w-5" aria-hidden="true" />
            Editar
          </LinkButton>
          <Button variant="danger" className="flex-1" onClick={() => setConfirming(true)}>
            <Trash2 className="h-5 w-5" aria-hidden="true" />
            Excluir
          </Button>
        </div>
      )}

      <ConfirmSheet
        open={confirming}
        title="Excluir fornecedor?"
        message={confirmMessage}
        confirmLabel="Excluir"
        danger
        busy={deleting}
        onConfirm={remove}
        onClose={() => setConfirming(false)}
      />
    </div>
  )
}
