import { useEffect, useState } from 'react'
import { UserPlus, Users } from 'lucide-react'
import { fetchPage, newId, request } from '../api'
import { useLoad } from '../hooks'
import type { Customer } from '../types'
import { useToast } from './Toast'
import { Button, EmptyState, ErrorState, Field, Input, Sheet, Skeleton } from './ui'

export function CustomerPicker({
  open,
  onClose,
  onPick,
}: {
  open: boolean
  onClose: () => void
  onPick: (customer: Customer) => void
}) {
  const toast = useToast()
  const [query, setQuery] = useState('')
  const [term, setTerm] = useState('')
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [nameError, setNameError] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => setTerm(query.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [query])

  useEffect(() => {
    if (!open) {
      setQuery('')
      setTerm('')
      setCreating(false)
      setName('')
      setPhone('')
      setNameError('')
    }
  }, [open])

  const { data, error, loading, reload } = useLoad(async () => {
    if (!open) {
      return []
    }
    const page = await fetchPage<Customer>(`/api/customers?q=${encodeURIComponent(term)}&size=30`)
    return page.items
  }, [open, term])

  async function create() {
    if (name.trim() === '') {
      setNameError('Informe o nome')
      return
    }
    setSaving(true)
    try {
      const customer = await request<Customer>('/api/customers', {
        method: 'POST',
        body: { id: newId(), name: name.trim(), phone: phone.trim() || null, email: null, note: null },
      })
      onPick(customer)
    } catch (e) {
      toast.show(e instanceof Error ? e.message : 'Não foi possível cadastrar', 'error')
    } finally {
      setSaving(false)
    }
  }

  if (creating) {
    return (
      <Sheet
        open={open}
        onClose={onClose}
        title="Cadastrar cliente"
        footer={
          <>
            <Button variant="ghost" className="flex-1" onClick={() => setCreating(false)} disabled={saving}>
              Voltar
            </Button>
            <Button className="flex-1" onClick={create} loading={saving}>
              Cadastrar
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Field label="Nome" required error={nameError}>
            <Input
              value={name}
              onChange={(event) => {
                setName(event.target.value)
                setNameError('')
              }}
              invalid={nameError !== ''}
              autoFocus
            />
          </Field>
          <Field label="Telefone">
            <Input type="tel" inputMode="tel" value={phone} onChange={(event) => setPhone(event.target.value)} />
          </Field>
        </div>
      </Sheet>
    )
  }

  return (
    <Sheet open={open} onClose={onClose} title="Escolher cliente">
      <div className="space-y-4">
        <Input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Buscar pelo nome"
          aria-label="Buscar cliente"
          autoFocus
        />
        <Button variant="secondary" className="w-full" onClick={() => setCreating(true)}>
          <UserPlus className="h-5 w-5" aria-hidden="true" />
          Cadastrar rápido
        </Button>
        {error && <ErrorState message={error} onRetry={reload} />}
        {!error && loading && !data && <Skeleton lines={3} />}
        {!error && data && data.length === 0 && (
          <EmptyState icon={Users} title="Nenhuma cliente encontrada" message="Cadastre uma nova pelo botão acima." />
        )}
        {!error && data && data.length > 0 && (
          <ul className="space-y-2" aria-busy={loading}>
            {data.map((customer) => (
              <li key={customer.id}>
                <button
                  type="button"
                  onClick={() => onPick(customer)}
                  className="flex min-h-[48px] w-full flex-col items-start rounded-[14px] border border-border bg-surface px-4 py-2 text-left hover:bg-surface-alt focus:outline-none focus:ring-2 focus:ring-secondary/60"
                >
                  <span className="text-base font-medium text-text">{customer.name}</span>
                  {customer.phone && <span className="text-sm text-text-secondary">{customer.phone}</span>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Sheet>
  )
}
