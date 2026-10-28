import { useEffect, useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { MapPin, Pencil, Plus, Receipt, Trash2 } from 'lucide-react'
import { ApiError, fetchPage, newId, request } from '../../api'
import type { Model } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import {
  Button,
  Card,
  ConfirmSheet,
  EmptyState,
  ErrorState,
  Field,
  Input,
  LinkButton,
  PageHeader,
  Sheet,
  Skeleton,
  StatusPill,
  Textarea,
} from '../../components/ui'
import { date, dateTime, money, shortId } from '../../format'
import { useLoad } from '../../hooks'
import { DEFAULT_CUSTOMER_ID, orderStatusMeta } from '../../types'
import type { Address, CustomerView, OrderView } from '../../types'

type CustomerValues = { name: string; phone: string; email: string; note: string }
type AddressValues = {
  street: string
  number: string
  complement: string
  district: string
  city: string
  state: string
  zipCode: string
}
type ProfileState = { edit?: boolean; addressError?: string } | null

const emptyCustomer: CustomerValues = { name: '', phone: '', email: '', note: '' }
const emptyAddress: AddressValues = {
  street: '',
  number: '',
  complement: '',
  district: '',
  city: '',
  state: '',
  zipCode: '',
}
const HISTORY_SIZE = 10

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

function formatZip(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 8)
  return digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits
}

function formatState(value: string): string {
  return value.replace(/[^a-zA-Z]/g, '').slice(0, 2).toUpperCase()
}

function nullable(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : trimmed
}

function messageOf(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado'
}

function toAddressValues(address: Address): AddressValues {
  return {
    street: address.street,
    number: address.number ?? '',
    complement: address.complement ?? '',
    district: address.district ?? '',
    city: address.city,
    state: address.state,
    zipCode: address.zipCode ?? '',
  }
}

function toAddressBody(values: AddressValues) {
  return {
    street: values.street.trim(),
    number: nullable(values.number),
    complement: nullable(values.complement),
    district: nullable(values.district),
    city: values.city.trim(),
    state: values.state,
    zipCode: nullable(values.zipCode),
  }
}

function isAddressTouched(values: AddressValues): boolean {
  return Object.values(values).some((value) => value.trim() !== '')
}

function addressErrors(values: AddressValues): Record<string, string> {
  const errors: Record<string, string> = {}
  if (values.street.trim() === '') {
    errors.street = 'Informe a rua'
  }
  if (values.city.trim() === '') {
    errors.city = 'Informe a cidade'
  }
  if (values.state.length !== 2) {
    errors.state = 'Informe a UF com 2 letras'
  }
  return errors
}

function formatAddress(address: Address): string {
  const first = [address.street, address.number].filter(Boolean).join(', ')
  const second = [address.complement, address.district].filter(Boolean).join(' - ')
  const third = `${address.city}/${address.state}`
  return [first, second, third, address.zipCode].filter(Boolean).join(' · ')
}

function Info({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1">
      <dt className="text-sm text-text-secondary">{label}</dt>
      <dd className="text-base text-text">{children}</dd>
    </div>
  )
}

function AddressFields({
  values,
  errors,
  onChange,
}: {
  values: AddressValues
  errors: Record<string, string>
  onChange: (field: keyof AddressValues, value: string) => void
}) {
  return (
    <div className="space-y-4">
      <Field label="CEP" error={errors.zipCode} hint="Ex.: 85850-000">
        <Input
          inputMode="numeric"
          value={values.zipCode}
          maxLength={9}
          invalid={Boolean(errors.zipCode)}
          onChange={(event) => onChange('zipCode', formatZip(event.target.value))}
        />
      </Field>
      <Field label="Rua" required error={errors.street}>
        <Input
          value={values.street}
          maxLength={200}
          invalid={Boolean(errors.street)}
          onChange={(event) => onChange('street', event.target.value)}
        />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field label="Número" error={errors.number}>
          <Input
            value={values.number}
            maxLength={20}
            invalid={Boolean(errors.number)}
            onChange={(event) => onChange('number', event.target.value)}
          />
        </Field>
        <Field label="Complemento" error={errors.complement}>
          <Input
            value={values.complement}
            maxLength={100}
            invalid={Boolean(errors.complement)}
            onChange={(event) => onChange('complement', event.target.value)}
          />
        </Field>
      </div>
      <Field label="Bairro" error={errors.district}>
        <Input
          value={values.district}
          maxLength={100}
          invalid={Boolean(errors.district)}
          onChange={(event) => onChange('district', event.target.value)}
        />
      </Field>
      <div className="grid grid-cols-[1fr_96px] gap-3">
        <Field label="Cidade" required error={errors.city}>
          <Input
            value={values.city}
            maxLength={100}
            invalid={Boolean(errors.city)}
            onChange={(event) => onChange('city', event.target.value)}
          />
        </Field>
        <Field label="UF" required error={errors.state}>
          <Input
            value={values.state}
            maxLength={2}
            autoCapitalize="characters"
            invalid={Boolean(errors.state)}
            onChange={(event) => onChange('state', formatState(event.target.value))}
          />
        </Field>
      </div>
    </div>
  )
}

function AddressSheet({
  customerId,
  address,
  onClose,
  onSaved,
}: {
  customerId: string
  address: Address | null
  onClose: () => void
  onSaved: () => void
}) {
  const toast = useToast()
  const addressId = useRef(address?.id ?? newId())
  const [values, setValues] = useState<AddressValues>(address ? toAddressValues(address) : emptyAddress)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  function update(field: keyof AddressValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving) {
      return
    }
    const found = addressErrors(values)
    if (Object.keys(found).length > 0) {
      setErrors(found)
      return
    }
    setErrors({})
    setSaving(true)
    try {
      const body = { id: addressId.current, ...toAddressBody(values) }
      if (address) {
        await request<Address>(`/api/customers/${customerId}/addresses/${address.id}`, { method: 'PUT', body })
      } else {
        await request<Address>(`/api/customers/${customerId}/addresses`, { method: 'POST', body })
      }
      toast.show('Endereço salvo')
      onSaved()
    } catch (caught) {
      if (caught instanceof ApiError && Object.keys(caught.fields).length > 0) {
        setErrors(caught.fields)
        toast.show('Revise os campos destacados', 'error')
      } else {
        toast.show(messageOf(caught), 'error')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Sheet
      open
      onClose={saving ? () => {} : onClose}
      title={address ? 'Editar endereço' : 'Novo endereço'}
      footer={
        <>
          <Button variant="ghost" className="flex-1" onClick={onClose} disabled={saving}>
            Cancelar
          </Button>
          <Button type="submit" form="address-form" className="flex-1" loading={saving}>
            Salvar endereço
          </Button>
        </>
      }
    >
      <form id="address-form" onSubmit={submit} noValidate>
        <AddressFields values={values} errors={errors} onChange={update} />
      </form>
    </Sheet>
  )
}

function SalesHistory({ customerId, canCreate }: { customerId: string; canCreate: boolean }) {
  const toast = useToast()
  const [extra, setExtra] = useState<Model<OrderView>[]>([])
  const [lastPage, setLastPage] = useState<{ number: number; totalPages: number } | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const { data, error, loading, reload } = useLoad(() => {
    setExtra([])
    setLastPage(null)
    return fetchPage<OrderView>(`/api/orders?customerId=${customerId}&page=0&size=${HISTORY_SIZE}`)
  }, [customerId])

  async function loadMore() {
    const current = lastPage ?? data?.page
    if (!current) {
      return
    }
    setLoadingMore(true)
    try {
      const next = await fetchPage<OrderView>(
        `/api/orders?customerId=${customerId}&page=${current.number + 1}&size=${HISTORY_SIZE}`,
      )
      setExtra((list) => [...list, ...next.items])
      setLastPage(next.page)
    } catch (caught) {
      toast.show(messageOf(caught), 'error')
    } finally {
      setLoadingMore(false)
    }
  }

  const orders = data ? [...data.items, ...extra] : []
  const page = lastPage ?? data?.page

  return (
    <section aria-labelledby="history-title" className="space-y-3">
      <h2 id="history-title" className="font-serif text-lg font-bold text-text">
        Histórico de vendas
      </h2>
      {loading && !data && <Skeleton lines={3} />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && orders.length === 0 && (
        <EmptyState
          icon={Receipt}
          title="Ainda não comprou"
          action={canCreate ? <LinkButton to={`/sales/new?customerId=${customerId}`}>Nova venda</LinkButton> : undefined}
        />
      )}
      {orders.length > 0 && (
        <ul className="space-y-3">
          {orders.map((order) => {
            const meta = orderStatusMeta[order.status]
            return (
              <li key={order.id}>
                <Link
                  to={`/sales/${order.id}`}
                  className="block rounded-[14px] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  <Card className="flex min-h-[48px] items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-semibold text-text">{shortId(order.id)}</p>
                      <p className="text-sm text-text-secondary">{dateTime(order.orderedAt)}</p>
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-2">
                      <p className="font-semibold text-text">{money(order.total)}</p>
                      <StatusPill tone={meta.tone} icon={meta.icon}>
                        {meta.label}
                      </StatusPill>
                    </div>
                  </Card>
                </Link>
              </li>
            )
          })}
        </ul>
      )}
      {page && page.number + 1 < page.totalPages && (
        <div className="flex justify-center">
          <Button variant="ghost" onClick={loadMore} loading={loadingMore}>
            Ver mais
          </Button>
        </div>
      )}
    </section>
  )
}

function Addresses({
  customerId,
  canEdit,
  initialError,
}: {
  customerId: string
  canEdit: boolean
  initialError: string
}) {
  const toast = useToast()
  const { data, error, loading, reload } = useLoad(
    async () => (await fetchPage<Address>(`/api/customers/${customerId}/addresses`)).items,
    [customerId],
  )
  const [sheet, setSheet] = useState<{ address: Address | null } | null>(null)
  const [removing, setRemoving] = useState<Address | null>(null)
  const [busy, setBusy] = useState(false)

  async function remove() {
    if (!removing) {
      return
    }
    setBusy(true)
    try {
      await request<void>(`/api/customers/${customerId}/addresses/${removing.id}`, { method: 'DELETE' })
      toast.show('Endereço removido')
      setRemoving(null)
      reload()
    } catch (caught) {
      setRemoving(null)
      toast.show(messageOf(caught), 'error')
    } finally {
      setBusy(false)
    }
  }

  const addresses = data ?? []

  return (
    <section aria-labelledby="addresses-title" className="space-y-3">
      <h2 id="addresses-title" className="font-serif text-lg font-bold text-text">
        Endereços
      </h2>
      {initialError && (
        <p role="alert" className="rounded-[10px] bg-error-soft p-3 text-sm text-error">
          O cliente foi salvo, mas o endereço não: {initialError}
        </p>
      )}
      {loading && !data && <Skeleton lines={2} />}
      {error && <ErrorState message={error} onRetry={reload} />}
      {data && addresses.length === 0 && (
        <EmptyState icon={MapPin} title="Nenhum endereço cadastrado" />
      )}
      {addresses.length > 0 && (
        <ul className="space-y-3">
          {addresses.map((address) => (
            <li key={address.id}>
              <Card className="space-y-3">
                <p className="text-base text-text">{formatAddress(address)}</p>
                {canEdit && (
                  <div className="flex gap-2">
                    <Button variant="ghost" className="flex-1" onClick={() => setSheet({ address })}>
                      <Pencil className="h-5 w-5" aria-hidden="true" />
                      Editar
                    </Button>
                    <Button variant="danger" className="flex-1" onClick={() => setRemoving(address)}>
                      <Trash2 className="h-5 w-5" aria-hidden="true" />
                      Remover
                    </Button>
                  </div>
                )}
              </Card>
            </li>
          ))}
        </ul>
      )}
      {canEdit && (
        <Button variant="secondary" className="w-full" onClick={() => setSheet({ address: null })}>
          <Plus className="h-5 w-5" aria-hidden="true" />
          Adicionar endereço
        </Button>
      )}
      {sheet && (
        <AddressSheet
          key={sheet.address?.id ?? 'new'}
          customerId={customerId}
          address={sheet.address}
          onClose={() => setSheet(null)}
          onSaved={() => {
            setSheet(null)
            reload()
          }}
        />
      )}
      <ConfirmSheet
        open={removing !== null}
        title="Remover endereço?"
        message="O endereço deixa de aparecer na ficha. Deseja continuar?"
        confirmLabel="Remover"
        danger
        busy={busy}
        onConfirm={remove}
        onClose={() => setRemoving(null)}
      />
    </section>
  )
}

function CustomerFields({
  values,
  errors,
  onChange,
}: {
  values: CustomerValues
  errors: Record<string, string>
  onChange: (field: keyof CustomerValues, value: string) => void
}) {
  return (
    <div className="space-y-4">
      <Field label="Nome" required error={errors.name}>
        <Input
          value={values.name}
          maxLength={150}
          invalid={Boolean(errors.name)}
          onChange={(event) => onChange('name', event.target.value)}
        />
      </Field>
      <Field label="Telefone" error={errors.phone} hint="Ex.: (45) 99999-9999">
        <Input
          type="tel"
          inputMode="tel"
          value={values.phone}
          maxLength={20}
          invalid={Boolean(errors.phone)}
          onChange={(event) => onChange('phone', formatPhone(event.target.value))}
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
          onChange={(event) => onChange('email', event.target.value)}
        />
      </Field>
      <Field label="Observações" error={errors.note}>
        <Textarea
          value={values.note}
          maxLength={1000}
          invalid={Boolean(errors.note)}
          onChange={(event) => onChange('note', event.target.value)}
        />
      </Field>
    </div>
  )
}

function toCustomerBody(values: CustomerValues) {
  return {
    name: values.name.trim(),
    phone: nullable(values.phone),
    email: nullable(values.email),
    note: nullable(values.note),
  }
}

export default function CustomerProfile() {
  const { id } = useParams()
  const isNew = id === undefined
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()
  const { can } = useAuth()
  const state = location.state as ProfileState
  const isDefault = id === DEFAULT_CUSTOMER_ID
  const canEdit = can('customer:edit') && !isDefault
  const customer = useLoad(async () => (isNew ? null : request<CustomerView>(`/api/customers/${id}`)), [id])

  const newCustomerId = useRef(newId())
  const newAddressId = useRef(newId())
  const [editing, setEditing] = useState(isNew || Boolean(state?.edit))
  const [values, setValues] = useState<CustomerValues>(emptyCustomer)
  const [address, setAddress] = useState<AddressValues>(emptyAddress)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [addressFieldErrors, setAddressFieldErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (customer.data) {
      setValues({
        name: customer.data.name,
        phone: customer.data.phone ?? '',
        email: customer.data.email ?? '',
        note: customer.data.note ?? '',
      })
    }
  }, [customer.data])

  function updateValue(field: keyof CustomerValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
  }

  function updateAddress(field: keyof AddressValues, value: string) {
    setAddress((current) => ({ ...current, [field]: value }))
  }

  function cancelEdit() {
    if (isNew) {
      navigate('/customers')
      return
    }
    if (customer.data) {
      setValues({
        name: customer.data.name,
        phone: customer.data.phone ?? '',
        email: customer.data.email ?? '',
        note: customer.data.note ?? '',
      })
    }
    setErrors({})
    setEditing(false)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving) {
      return
    }
    const found: Record<string, string> = {}
    if (values.name.trim() === '') {
      found.name = 'Informe o nome'
    }
    const sendAddress = isNew && isAddressTouched(address)
    const foundAddress = sendAddress ? addressErrors(address) : {}
    setErrors(found)
    setAddressFieldErrors(foundAddress)
    if (Object.keys(found).length > 0 || Object.keys(foundAddress).length > 0) {
      return
    }
    const body = toCustomerBody(values)
    setSaving(true)
    let created = false
    try {
      if (isNew) {
        await request<CustomerView>('/api/customers', { method: 'POST', body: { id: newCustomerId.current, ...body } })
        created = true
        if (sendAddress) {
          await request<Address>(`/api/customers/${newCustomerId.current}/addresses`, {
            method: 'POST',
            body: { id: newAddressId.current, ...toAddressBody(address) },
          })
        }
        toast.show('Cliente cadastrado')
        navigate(`/customers/${newCustomerId.current}`, { replace: true })
      } else {
        await request<CustomerView>(`/api/customers/${id}`, { method: 'PUT', body: { id, ...body } })
        toast.show('Cliente salvo')
        setEditing(false)
        customer.reload()
      }
    } catch (caught) {
      if (created) {
        navigate(`/customers/${newCustomerId.current}`, {
          replace: true,
          state: { edit: true, addressError: messageOf(caught) },
        })
      } else if (caught instanceof ApiError && Object.keys(caught.fields).length > 0) {
        setErrors(caught.fields)
        toast.show('Revise os campos destacados', 'error')
      } else {
        toast.show(messageOf(caught), 'error')
      }
    } finally {
      setSaving(false)
    }
  }

  if (!isNew && customer.loading && !customer.data) {
    return <Skeleton lines={5} />
  }
  if (!isNew && customer.error && !customer.data) {
    return <ErrorState message={customer.error} onRetry={customer.reload} />
  }
  if (!isNew && !customer.data) {
    return null
  }

  const data = customer.data
  const customerId = data?.id ?? ''

  return (
    <div className="mx-auto max-w-3xl space-y-6 pb-4">
      <PageHeader
        title={isNew ? 'Novo cliente' : (data?.name ?? '')}
        subtitle={data ? `Cliente desde ${date(data.createdAt)}` : undefined}
        back={{ to: '/customers', label: 'Clientes' }}
      />

      {editing ? (
        <form onSubmit={submit} className="space-y-6" noValidate>
          <CustomerFields values={values} errors={errors} onChange={updateValue} />
          {isNew && (
            <section aria-labelledby="first-address-title" className="space-y-4">
              <h2 id="first-address-title" className="font-serif text-lg font-bold text-text">
                Endereço (opcional)
              </h2>
              <AddressFields values={address} errors={addressFieldErrors} onChange={updateAddress} />
            </section>
          )}
          <div className="flex gap-2">
            <Button variant="ghost" className="flex-1" onClick={cancelEdit} disabled={saving}>
              Cancelar
            </Button>
            <Button type="submit" className="flex-1" loading={saving}>
              {isNew ? 'Salvar cliente' : 'Salvar alterações'}
            </Button>
          </div>
        </form>
      ) : (
        data && (
          <>
            <Card>
              <dl className="space-y-4">
                <Info label="Telefone">
                  {data.phone ? (
                    <a
                      href={`tel:${data.phone.replace(/\D/g, '')}`}
                      className="inline-flex min-h-[48px] items-center text-primary underline"
                    >
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
                <Info label="Observações">{data.note || 'Nenhuma'}</Info>
              </dl>
            </Card>
            {canEdit && (
              <Button variant="ghost" className="w-full" onClick={() => setEditing(true)}>
                <Pencil className="h-5 w-5" aria-hidden="true" />
                Editar dados
              </Button>
            )}
          </>
        )
      )}

      {!isNew && data && (
        <>
          <Addresses customerId={customerId} canEdit={canEdit} initialError={state?.addressError ?? ''} />
          <SalesHistory customerId={customerId} canCreate={can('order:create')} />
        </>
      )}
    </div>
  )
}
