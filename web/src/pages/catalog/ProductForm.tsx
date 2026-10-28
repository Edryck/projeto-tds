import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AlertTriangle, CheckCircle2, Plus, XCircle } from 'lucide-react'
import { fetchPage, newId, request } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, ConfirmSheet, ErrorState, Field, Input, LinkButton, PageHeader, Select, Skeleton, StatusPill, Textarea } from '../../components/ui'
import { money } from '../../format'
import { useLoad } from '../../hooks'
import type { CategoryView, ProductView, VariantResponse, VariantStatus } from '../../types'

const statusMeta: Record<VariantStatus, { label: string; tone: 'success' | 'warning' | 'danger'; icon: typeof XCircle }> = {
  SOLD_OUT: { label: 'Esgotado', tone: 'danger', icon: XCircle },
  LOW: { label: 'Baixo', tone: 'warning', icon: AlertTriangle },
  AVAILABLE: { label: 'Disponível', tone: 'success', icon: CheckCircle2 },
}

function variantLabel(variant: VariantResponse): string {
  return [variant.size, variant.color, variant.model].filter((part) => part).join(' · ')
}

export default function ProductForm() {
  const { id } = useParams()
  const isNew = id === undefined
  const navigate = useNavigate()
  const toast = useToast()
  const { can } = useAuth()
  const canEdit = can('product:edit')

  const categories = useLoad(async () => (await fetchPage<CategoryView>('/api/categories?size=100')).items, [])
  const product = useLoad(async () => (isNew ? null : request<ProductView>(`/api/products/${id}`)), [id])
  const variants = useLoad(
    async () => (isNew ? [] : (await fetchPage<VariantResponse>(`/api/products/${id}/variants`)).items),
    [id],
  )

  const [name, setName] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [description, setDescription] = useState('')
  const [nameError, setNameError] = useState('')
  const [categoryError, setCategoryError] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    if (product.data) {
      setName(product.data.name)
      setCategoryId(product.data.categoryId)
      setDescription(product.data.description ?? '')
    }
  }, [product.data])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const trimmed = name.trim()
    setNameError(trimmed === '' ? 'Informe o nome' : '')
    setCategoryError(categoryId === '' ? 'Escolha a categoria' : '')
    if (trimmed === '' || categoryId === '') {
      return
    }
    const body = { categoryId, name: trimmed, description: description.trim() === '' ? null : description.trim() }
    setSaving(true)
    try {
      if (isNew) {
        const productId = newId()
        await request<ProductView>('/api/products', { method: 'POST', body: { id: productId, ...body } })
        toast.show('Produto criado. Agora adicione as variantes')
        navigate(`/catalog/${productId}`, { replace: true })
      } else {
        await request<ProductView>(`/api/products/${id}`, { method: 'PUT', body: { id, ...body } })
        toast.show('Produto salvo')
        navigate('/catalog')
      }
    } catch (caught) {
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setSaving(false)
    }
  }

  async function remove() {
    setDeleting(true)
    try {
      await request<void>(`/api/products/${id}`, { method: 'DELETE' })
      toast.show('Produto excluído')
      navigate('/catalog', { replace: true })
    } catch (caught) {
      setConfirmingDelete(false)
      toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
    } finally {
      setDeleting(false)
    }
  }

  if (!isNew && product.loading && !product.data) {
    return <Skeleton lines={4} />
  }
  if (!isNew && product.error && !product.data) {
    return <ErrorState message={product.error} onRetry={product.reload} />
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={isNew ? 'Novo produto' : 'Editar produto'}
        back={{ to: '/catalog', label: 'Produtos' }}
      />
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Nome" required error={nameError}>
          <Input value={name} onChange={(event) => setName(event.target.value)} invalid={nameError !== ''} disabled={!canEdit} />
        </Field>
        <Field label="Categoria" required error={categoryError}>
          <Select value={categoryId} onChange={(event) => setCategoryId(event.target.value)} disabled={!canEdit}>
            <option value="">Escolha a categoria</option>
            {(categories.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </Field>
        {categories.error && <p role="alert" className="text-sm text-error">{categories.error}</p>}
        <Field label="Descrição">
          <Textarea value={description} onChange={(event) => setDescription(event.target.value)} disabled={!canEdit} />
        </Field>
        {canEdit && (
          <div className="flex gap-2">
            <Button variant="ghost" className="flex-1" onClick={() => navigate('/catalog')} disabled={saving}>
              Cancelar
            </Button>
            <Button type="submit" className="flex-1" loading={saving}>
              Salvar produto
            </Button>
          </div>
        )}
      </form>

      <section className="mt-8" aria-labelledby="variants-title">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h2 id="variants-title" className="font-serif text-lg font-bold text-text">
            Variantes
          </h2>
          {canEdit && !isNew && (
            <LinkButton to={`/catalog/${id}/variants/new`} variant="secondary">
              <Plus className="h-5 w-5" aria-hidden="true" />
              Adicionar variante
            </LinkButton>
          )}
          {canEdit && isNew && (
            <Button variant="secondary" disabled>
              <Plus className="h-5 w-5" aria-hidden="true" />
              Adicionar variante
            </Button>
          )}
        </div>
        {isNew && <p className="text-sm text-text-secondary">Salve o produto para adicionar variantes</p>}
        {!isNew && variants.loading && !variants.data && <Skeleton lines={2} />}
        {!isNew && variants.error && <ErrorState message={variants.error} onRetry={variants.reload} />}
        {!isNew && variants.data && variants.data.length === 0 && (
          <p className="text-sm text-text-secondary">Nenhuma variante ainda.</p>
        )}
        {!isNew && variants.data && variants.data.length > 0 && (
          <ul className="space-y-3">
            {variants.data.map((variant) => {
              const meta = statusMeta[variant.status]
              return (
                <li key={variant.id}>
                  <Link
                    to={`/catalog/${id}/variants/${variant.id}`}
                    className="block rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  >
                    <Card className="flex min-h-[48px] items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-text">{variantLabel(variant)}</p>
                        <p className="text-sm text-text-secondary">
                          {money(variant.price)} · Saldo {variant.balance}
                        </p>
                      </div>
                      <StatusPill tone={meta.tone} icon={meta.icon}>
                        {meta.label}
                      </StatusPill>
                    </Card>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {canEdit && !isNew && (
        <div className="mt-8">
          <Button variant="danger" className="w-full" onClick={() => setConfirmingDelete(true)}>
            Excluir produto
          </Button>
        </div>
      )}
      <ConfirmSheet
        open={confirmingDelete}
        title="Excluir produto"
        message="O produto e suas variantes deixam de aparecer no catálogo. Deseja continuar?"
        confirmLabel="Excluir"
        danger
        busy={deleting}
        onConfirm={remove}
        onClose={() => setConfirmingDelete(false)}
      />
    </div>
  )
}
