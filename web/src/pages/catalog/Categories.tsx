import { useState } from 'react'
import type { FormEvent } from 'react'
import { Layers, Pencil, Plus, Trash2 } from 'lucide-react'
import { ApiError, fetchPage, newId, request } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, ConfirmSheet, EmptyState, ErrorState, Field, Input, PageHeader, Sheet, Skeleton } from '../../components/ui'
import { useLoad } from '../../hooks'
import type { CategoryView } from '../../types'

function productCountText(count: number): string {
  return `${count} ${count === 1 ? 'produto' : 'produtos'}`
}

export default function Categories() {
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('product:edit')

  const categories = useLoad(async () => (await fetchPage<CategoryView>('/api/categories?size=100')).items, [])

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<CategoryView | null>(null)
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState('')
  const [saving, setSaving] = useState(false)

  const [deleteTarget, setDeleteTarget] = useState<CategoryView | null>(null)
  const [deleteError, setDeleteError] = useState('')
  const [deleting, setDeleting] = useState(false)

  function openCreate() {
    setEditing(null)
    setName('')
    setNameError('')
    setFormOpen(true)
  }

  function openEdit(category: CategoryView) {
    setEditing(category)
    setName(category.name)
    setNameError('')
    setFormOpen(true)
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    setNameError(trimmed === '' ? 'Informe o nome' : '')
    if (trimmed === '') {
      return
    }
    setSaving(true)
    try {
      if (editing) {
        await request<CategoryView>(`/api/categories/${editing.id}`, {
          method: 'PUT',
          body: { id: editing.id, name: trimmed },
        })
        toast.show('Categoria salva')
      } else {
        await request<CategoryView>('/api/categories', { method: 'POST', body: { id: newId(), name: trimmed } })
        toast.show('Categoria criada')
      }
      setFormOpen(false)
      categories.reload()
    } catch (caught) {
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setSaving(false)
    }
  }

  function askDelete(category: CategoryView) {
    setDeleteTarget(category)
    setDeleteError('')
  }

  async function remove() {
    if (!deleteTarget) {
      return
    }
    setDeleting(true)
    try {
      await request<void>(`/api/categories/${deleteTarget.id}`, { method: 'DELETE' })
      setDeleteTarget(null)
      toast.show('Categoria excluída')
      categories.reload()
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        setDeleteTarget(null)
        toast.show(caught.message, 'error')
        categories.reload()
        return
      }
      setDeleteError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
      categories.reload()
    } finally {
      setDeleting(false)
    }
  }

  const newButton = canEdit ? (
    <Button onClick={openCreate}>
      <Plus className="h-5 w-5" aria-hidden="true" />
      Nova categoria
    </Button>
  ) : undefined

  const list = categories.data ?? []
  const withProducts = deleteTarget !== null && deleteTarget.productCount > 0
  const movement = deleteTarget?.productCount === 1 ? 'Mova-o' : 'Mova-os'

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Categorias" back={{ to: '/catalog', label: 'Produtos' }} actions={newButton} />

      {categories.loading && !categories.data && <Skeleton lines={3} />}
      {categories.error && <ErrorState message={categories.error} onRetry={categories.reload} />}
      {categories.data && list.length === 0 && (
        <EmptyState icon={Layers} title="Nenhuma categoria ainda" action={newButton} />
      )}
      {categories.data && list.length > 0 && (
        <ul className="space-y-3">
          {list.map((category) => (
            <li key={category.id}>
              <Card className="space-y-3">
                <div className="min-w-0">
                  <p className="truncate font-semibold text-text">{category.name}</p>
                  <p className="text-sm text-text-secondary">{productCountText(category.productCount)}</p>
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
          ))}
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
            <Button type="submit" form="category-form" className="flex-1" loading={saving}>
              Salvar
            </Button>
          </>
        }
      >
        <form id="category-form" onSubmit={save} noValidate>
          <Field label="Nome da categoria" required error={nameError}>
            <Input value={name} onChange={(event) => setName(event.target.value)} invalid={nameError !== ''} />
          </Field>
        </form>
      </Sheet>

      {withProducts ? (
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
          <p className="text-base text-text">
            {`Esta categoria tem ${productCountText(deleteTarget.productCount)}. ${movement} para outra categoria antes de excluir.`}
          </p>
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
