import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ApiError, newId, request } from '../../api'
import { useToast } from '../../components/Toast'
import { Button, ErrorState, Field, Input, PageHeader, Skeleton, Textarea } from '../../components/ui'
import { useLoad } from '../../hooks'
import type { SupplierView } from '../../types'

type SupplierFormValues = {
  name: string
  contactName: string
  phone: string
  email: string
  address: string
  note: string
}

const emptyValues: SupplierFormValues = {
  name: '',
  contactName: '',
  phone: '',
  email: '',
  address: '',
  note: '',
}

function formatPhone(value: string): string {
  const digits = value.replace(/\D/g, '')
  if (digits.length > 11) {
    return value.slice(0, 20)
  }
  if (digits.length <= 2) {
    return digits
  }
  const splitAt = digits.length > 10 ? 7 : 6
  if (digits.length <= splitAt) {
    return `(${digits.slice(0, 2)}) ${digits.slice(2)}`
  }
  return `(${digits.slice(0, 2)}) ${digits.slice(2, splitAt)}-${digits.slice(splitAt)}`
}

function nullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function toValues(supplier: SupplierView): SupplierFormValues {
  return {
    name: supplier.name,
    contactName: supplier.contactName ?? '',
    phone: supplier.phone ?? '',
    email: supplier.email ?? '',
    address: supplier.address ?? '',
    note: supplier.note ?? '',
  }
}

export default function SupplierForm() {
  const { id } = useParams()
  const isNew = id === undefined
  const navigate = useNavigate()
  const toast = useToast()
  const supplier = useLoad(async () => (isNew ? null : request<SupplierView>(`/api/suppliers/${id}`)), [id])

  const [values, setValues] = useState<SupplierFormValues>(emptyValues)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (supplier.data) {
      setValues(toValues(supplier.data))
    }
  }, [supplier.data])

  const backTo = isNew ? '/suppliers' : `/suppliers/${id}`

  function update(field: keyof SupplierFormValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving) {
      return
    }
    if (values.name.trim() === '') {
      setErrors({ name: 'Informe o nome' })
      return
    }
    setErrors({})
    const body = {
      name: values.name.trim(),
      phone: nullable(values.phone),
      contactName: nullable(values.contactName),
      email: nullable(values.email),
      address: nullable(values.address),
      note: nullable(values.note),
    }
    setSaving(true)
    try {
      if (isNew) {
        const supplierId = newId()
        await request<SupplierView>('/api/suppliers', { method: 'POST', body: { id: supplierId, ...body } })
        toast.show('Fornecedor cadastrado')
        navigate(`/suppliers/${supplierId}`, { replace: true })
      } else {
        await request<SupplierView>(`/api/suppliers/${id}`, { method: 'PUT', body: { id, ...body } })
        toast.show('Fornecedor salvo')
        navigate(`/suppliers/${id}`, { replace: true })
      }
    } catch (caught) {
      if (caught instanceof ApiError && Object.keys(caught.fields).length > 0) {
        setErrors(caught.fields)
        toast.show('Revise os campos destacados', 'error')
      } else {
        toast.show(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado', 'error')
      }
    } finally {
      setSaving(false)
    }
  }

  if (!isNew && supplier.loading && !supplier.data) {
    return <Skeleton lines={4} />
  }
  if (!isNew && supplier.error && !supplier.data) {
    return <ErrorState message={supplier.error} onRetry={supplier.reload} />
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={isNew ? 'Novo fornecedor' : 'Editar fornecedor'}
        back={{ to: backTo, label: isNew ? 'Fornecedores' : 'Ficha' }}
      />
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Field label="Nome" required error={errors.name}>
          <Input
            value={values.name}
            maxLength={150}
            invalid={Boolean(errors.name)}
            onChange={(event) => update('name', event.target.value)}
          />
        </Field>
        <Field label="Nome de contato" error={errors.contactName}>
          <Input
            value={values.contactName}
            maxLength={150}
            invalid={Boolean(errors.contactName)}
            onChange={(event) => update('contactName', event.target.value)}
          />
        </Field>
        <Field label="Telefone" error={errors.phone} hint="Ex.: (45) 99999-9999">
          <Input
            type="tel"
            inputMode="tel"
            value={values.phone}
            maxLength={20}
            invalid={Boolean(errors.phone)}
            onChange={(event) => update('phone', formatPhone(event.target.value))}
          />
        </Field>
        <Field label="E-mail" error={errors.email}>
          <Input
            type="email"
            inputMode="email"
            autoComplete="email"
            value={values.email}
            maxLength={255}
            invalid={Boolean(errors.email)}
            onChange={(event) => update('email', event.target.value)}
          />
        </Field>
        <Field label="Endereço" error={errors.address}>
          <Input
            value={values.address}
            maxLength={300}
            invalid={Boolean(errors.address)}
            onChange={(event) => update('address', event.target.value)}
          />
        </Field>
        <Field label="Observações" error={errors.note}>
          <Textarea
            value={values.note}
            maxLength={1000}
            invalid={Boolean(errors.note)}
            onChange={(event) => update('note', event.target.value)}
          />
        </Field>
        <div className="flex gap-2">
          <Button variant="ghost" className="flex-1" onClick={() => navigate(backTo)} disabled={saving}>
            Cancelar
          </Button>
          <Button type="submit" className="flex-1" loading={saving}>
            Salvar fornecedor
          </Button>
        </div>
      </form>
    </div>
  )
}
