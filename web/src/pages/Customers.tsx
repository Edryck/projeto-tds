import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { newId, request } from '../api'
import { useAuth } from '../auth'

type Customer = { id: string; name: string; phone: string | null; email: string | null; note: string | null }
type CustomerPage = { _embedded?: { customers: Customer[] } }

const inputClass =
  'h-12 rounded-md border border-gray-500 bg-white px-3 text-base text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const buttonClass =
  'h-12 rounded-md bg-indigo-700 px-4 font-medium text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700 disabled:bg-gray-600'
const linkClass =
  'flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700'
const labelClass = 'font-medium text-gray-900'

function messageOf(caught: unknown): string {
  return caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado'
}

export default function Customers() {
  const { can } = useAuth()
  const [customers, setCustomers] = useState<Customer[]>([])
  const [listError, setListError] = useState('')
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [version, setVersion] = useState(0)
  const [customerId, setCustomerId] = useState(newId)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [note, setNote] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let ignore = false
    const params = new URLSearchParams({ size: '100' })
    if (appliedSearch) {
      params.set('name', appliedSearch)
    }
    request<CustomerPage>(`/api/customers?${params}`)
      .then((page) => {
        if (!ignore) {
          setCustomers(page._embedded?.customers ?? [])
          setListError('')
        }
      })
      .catch((caught) => {
        if (!ignore) {
          setListError(messageOf(caught))
        }
      })
    return () => {
      ignore = true
    }
  }, [appliedSearch, version])

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setAppliedSearch(search.trim())
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setMessage('')
    setError('')
    setSubmitting(true)
    try {
      await request('/api/customers', {
        method: 'POST',
        body: {
          id: customerId,
          name: name.trim(),
          phone: phone.trim() || undefined,
          email: email.trim() || undefined,
          note: note.trim() || undefined,
        },
      })
      setCustomerId(newId())
      setName('')
      setPhone('')
      setEmail('')
      setNote('')
      setMessage('Cliente cadastrado.')
      setVersion((current) => current + 1)
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-4">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Clientes</h1>
        <Link to="/" className={linkClass}>
          Início
        </Link>
      </header>

      <form onSubmit={handleSearch} className="mt-6 flex flex-wrap items-end gap-2">
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <label htmlFor="customer-search" className={labelClass}>
            Buscar por nome
          </label>
          <input
            id="customer-search"
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className={inputClass}
          />
        </div>
        <button type="submit" className={buttonClass}>
          Buscar
        </button>
      </form>

      {listError && (
        <p role="alert" className="mt-4 font-medium text-red-700">
          {listError}
        </p>
      )}

      <ul className="mt-4 flex flex-col gap-2">
        {customers.map((customer) => (
          <li key={customer.id} className="rounded-md border border-gray-500 p-3 text-gray-900">
            <p className="font-bold">{customer.name}</p>
            {customer.phone && <p>Telefone: {customer.phone}</p>}
            {customer.email && <p>E-mail: {customer.email}</p>}
            {customer.note && <p>Observação: {customer.note}</p>}
          </li>
        ))}
      </ul>
      {customers.length === 0 && !listError && <p className="mt-4 text-gray-900">Nenhum cliente encontrado.</p>}

      {can('customer:edit') && (
        <form onSubmit={handleSubmit} className="mt-8 flex flex-col gap-4">
          <h2 className="text-xl font-bold text-gray-900">Novo cliente</h2>
          <div className="flex flex-col gap-1">
            <label htmlFor="customer-name" className={labelClass}>
              Nome
            </label>
            <input
              id="customer-name"
              type="text"
              required
              maxLength={150}
              value={name}
              onChange={(event) => setName(event.target.value)}
              className={inputClass}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="customer-phone" className={labelClass}>
              Telefone (opcional)
            </label>
            <input
              id="customer-phone"
              type="tel"
              maxLength={20}
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              className={inputClass}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="customer-email" className={labelClass}>
              E-mail (opcional)
            </label>
            <input
              id="customer-email"
              type="email"
              maxLength={255}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className={inputClass}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label htmlFor="customer-note" className={labelClass}>
              Observação (opcional)
            </label>
            <input
              id="customer-note"
              type="text"
              maxLength={1000}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              className={inputClass}
            />
          </div>
          {message && (
            <p role="status" className="font-medium text-green-800">
              {message}
            </p>
          )}
          {error && (
            <p role="alert" className="font-medium text-red-700">
              {error}
            </p>
          )}
          <button type="submit" disabled={submitting} className={buttonClass}>
            {submitting ? 'Salvando...' : 'Cadastrar cliente'}
          </button>
        </form>
      )}
    </main>
  )
}
