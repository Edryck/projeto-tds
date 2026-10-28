import { useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowDownCircle, ArrowUpCircle, Pencil, Plus, Tags, Trash2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { fetchPage, newId, request } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, ConfirmSheet, EmptyState, ErrorState, Field, Input, PageHeader, Select, Sheet, Skeleton, StatusPill } from '../../components/ui'
import { useLoad } from '../../hooks'
import type { EntryType, FinancialCategoryView } from '../../types'

const typeMeta: Record<EntryType, { label: string; tone: 'success' | 'warning'; icon: LucideIcon }> = {
  INCOME: { label: 'Receita', tone: 'success', icon: ArrowUpCircle },
  EXPENSE: { label: 'Despesa', tone: 'warning', icon: ArrowDownCircle },
}

function entryCountText(count: number): string {
  return `${count} ${count === 1 ? 'lançamento' : 'lançamentos'}`
}

export default function FinancialCategories() {
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('finance:edit')

  const categories = useLoad(
    async () => (await fetchPage<FinancialCategoryView>('/api/financial-categories?size=100')).items,
    [],
  )

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<FinancialCategoryView | null>(null)
  const [name, setName] = useState('')
  const [type, setType] = useState<EntryType>('INCOME')
  const [nameError, setNameError] = useState('')
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<FinancialCategoryView | null>(null)
  const [deleteError, setDeleteError] = useState('')
  const [deleting, setDeleting] = useState(false)

  const typeLocked = editing !== null && (editing.entryCount > 0 || editing.system)

  function openCreate() {
    setEditing(null)
    setName('')
    setType('INCOME')
    setNameError('')
    setFormError('')
    setFormOpen(true)
  }

  function openEdit(category: FinancialCategoryView) {
    setEditing(category)
    setName(category.name)
    setType(category.type)
    setNameError('')
    setFormError('')
    setFormOpen(true)
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    setNameError(trimmed === '' ? 'Informe o nome' : '')
    setFormError('')
    if (trimmed === '') {
      return
    }
    setSaving(true)
    try {
      if (editing) {
        await request<FinancialCategoryView>(`/api/financial-categories/${editing.id}`, {
          method: 'PUT',
          body: { id: editing.id, name: trimmed, type },
        })
        toast.show('Categoria salva')
      } else {
        await request<FinancialCategoryView>('/api/financial-categories', {
          method: 'POST',
          body: { id: newId(), name: trimmed, type },
        })
        toast.show('Categoria criada')
      }
      setFormOpen(false)
      categories.reload()
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
    } finally {
      setSaving(false)
    }
  }

  function askDelete(category: FinancialCategoryView) {
    setDeleteTarget(category)
    setDeleteError('')
  }

  async function remove() {
    if (!deleteTarget) {
      return
    }
    setDeleting(true)
    try {
      await request<void>(`/api/financial-categories/${deleteTarget.id}`, { method: 'DELETE' })
      setDeleteTarget(null)
      toast.show('Categoria excluída')
      categories.reload()
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
      categories.reload()
    } finally {
      setDeleting(false)
    }
  }

  function deleteBlockedReason(category: FinancialCategoryView): string {
    if (category.system) {
      return 'Categoria do sistema não pode ser excluída.'
    }
    if (category.entryCount > 0) {
      return `Esta categoria tem ${entryCountText(category.entryCount)} e não pode ser excluída. Você pode renomeá-la.`
    }
    return ''
  }

  const newButton = canEdit ? (
    <Button onClick={openCreate}>
      <Plus className="h-5 w-5" aria-hidden="true" />
      Nova categoria
    </Button>
  ) : undefined

  const list = categories.data ?? []
  const blockedReason = deleteTarget ? deleteBlockedReason(deleteTarget) : ''

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Categorias financeiras" back={{ to: '/finance', label: 'Financeiro' }} actions={newButton} />

      {categories.loading && !categories.data && <Skeleton lines={3} />}
      {categories.error && <ErrorState message={categories.error} onRetry={categories.reload} />}
      {categories.data && list.length === 0 && (
        <EmptyState icon={Tags} title="Nenhuma categoria ainda" action={newButton} />
      )}
      {categories.data && list.length > 0 && (
        <ul className="space-y-3">
          {list.map((category) => {
            const meta = typeMeta[category.type]
            return (
              <li key={category.id}>
                <Card className="space-y-3">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate font-semibold text-text">{category.name}</p>
                      <p className="text-sm text-text-secondary">{entryCountText(category.entryCount)}</p>
                    </div>
                    <div className="shrink-0">
                      <StatusPill tone={meta.tone} icon={meta.icon}>
                        {meta.label}
                      </StatusPill>
                    </div>
                  </div>
                  {canEdit && (
                    <div className="flex gap-2">
                      <Button variant="ghost" className="flex-1" onClick={() => openEdit(category)} aria-label={`Editar ${category.name}`}>
                        <Pencil className="h-5 w-5" aria-hidden="true" />
                        Editar
                      </Button>
                      <Button variant="ghost" className="flex-1" onClick={() => askDelete(category)} aria-label={`Excluir ${category.name}`}>
                        <Trash2 className="h-5 w-5 text-error" aria-hidden="true" />
                        Excluir
                      </Button>
                    </div>
                  )}
                </Card>
              </li>
            )
          })}
        </ul>
      )}

      <Sheet
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? 'Editar categoria' : 'Nova categoria'}
        footer={
          <>
            <Button variant="ghost" className="flex-1" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancelar
            </Button>
            <Button type="submit" form="financial-category-form" className="flex-1" loading={saving}>
              Salvar
            </Button>
          </>
        }
      >
        <form id="financial-category-form" onSubmit={save} noValidate className="space-y-4">
          {formError && (
            <p role="alert" className="rounded-[10px] bg-error-soft p-3 text-sm text-error">
              {formError}
            </p>
          )}
          <Field label="Nome" required error={nameError}>
            <Input value={name} onChange={(event) => setName(event.target.value)} invalid={nameError !== ''} />
          </Field>
          <Field label="Tipo" required hint={typeLocked ? 'Tipo não pode ser alterado nesta categoria.' : undefined}>
            <Select value={type} onChange={(event) => setType(event.target.value as EntryType)} disabled={typeLocked}>
              <option value="INCOME">Receita</option>
              <option value="EXPENSE">Despesa</option>
            </Select>
          </Field>
        </form>
      </Sheet>

      {deleteTarget && blockedReason !== '' ? (
        <Sheet
          open
          onClose={() => setDeleteTarget(null)}
          title="Excluir categoria"
          footer={
            <Button variant="ghost" className="flex-1" onClick={() => setDeleteTarget(null)}>
              Voltar
            </Button>
          }
        >
          <p className="text-base text-text">{blockedReason}</p>
        </Sheet>
      ) : (
        <ConfirmSheet
          open={deleteTarget !== null}
          title="Excluir categoria"
          message={deleteError || 'A categoria será excluída. Deseja continuar?'}
          confirmLabel="Excluir"
          danger
          busy={deleting}
          onConfirm={remove}
          onClose={() => setDeleteTarget(null)}
        />
      )}
    </div>
  )
}
