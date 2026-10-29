import { useState } from 'react'
import type { FormEvent } from 'react'
import { CheckCircle2, Pencil, Plus, ShieldCheck, Store, UserX, Users as UsersIcon } from 'lucide-react'
import { ApiError, fetchPage, newId, request } from '../../api'
import { useAuth } from '../../auth'
import { useToast } from '../../components/Toast'
import { Button, Card, ConfirmSheet, EmptyState, ErrorState, Field, Input, PageHeader, Sheet, Skeleton, StatusPill } from '../../components/ui'
import { useLoad } from '../../hooks'
import { ADMIN_ROLE_ID, CASHIER_ROLE_ID } from '../../types'
import type { UserResponse } from '../../types'

const roles = [
  { id: ADMIN_ROLE_ID, label: 'Admin', description: 'Administra o sistema, usuários e configurações.', icon: ShieldCheck },
  { id: CASHIER_ROLE_ID, label: 'Caixa', description: 'Vende, cuida do estoque, clientes e caixa. Cancelamentos precisam de aprovação.', icon: Store },
]

function roleLabel(roleName: string): string {
  return roleName === 'ADMIN' ? 'Admin' : 'Caixa'
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  const first = parts[0]?.charAt(0) ?? ''
  const last = parts.length > 1 ? parts[parts.length - 1].charAt(0) : ''
  return (first + last).toUpperCase()
}

function Avatar({ name }: { name: string }) {
  return (
    <span
      aria-hidden="true"
      className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-secondary text-sm font-semibold text-primary"
    >
      {initials(name)}
    </span>
  )
}

export default function Users() {
  const toast = useToast()
  const { me } = useAuth()

  const users = useLoad(async () => (await fetchPage<UserResponse>('/api/users?size=100')).items, [])

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<UserResponse | null>(null)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [roleId, setRoleId] = useState(CASHIER_ROLE_ID)
  const [active, setActive] = useState(true)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [confirmDeactivate, setConfirmDeactivate] = useState(false)

  const isSelf = editing !== null && editing.id === me?.id

  function openCreate() {
    setEditing(null)
    setName('')
    setEmail('')
    setPassword('')
    setRoleId(CASHIER_ROLE_ID)
    setActive(true)
    setErrors({})
    setFormError('')
    setFormOpen(true)
  }

  function openEdit(user: UserResponse) {
    setEditing(user)
    setName(user.name)
    setEmail(user.email)
    setPassword('')
    setRoleId(user.roleId)
    setActive(user.active)
    setErrors({})
    setFormError('')
    setFormOpen(true)
  }

  function validate(): Record<string, string> {
    const found: Record<string, string> = {}
    if (name.trim() === '') {
      found.name = 'Informe o nome'
    }
    if (email.trim() === '') {
      found.email = 'Informe o e-mail'
    }
    if (!editing && password.length < 8) {
      found.password = 'A senha precisa de pelo menos 8 caracteres'
    }
    if (editing && password !== '' && password.length < 8) {
      found.password = 'A senha precisa de pelo menos 8 caracteres'
    }
    return found
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    setFormError('')
    const found = validate()
    setErrors(found)
    if (Object.keys(found).length > 0) {
      return
    }
    if (editing && editing.active && !active) {
      setConfirmDeactivate(true)
      return
    }
    save()
  }

  async function save() {
    setConfirmDeactivate(false)
    setSaving(true)
    try {
      if (editing) {
        const body: Record<string, unknown> = { email: email.trim(), name: name.trim(), roleId, active }
        if (password !== '') {
          body.password = password
        }
        await request<UserResponse>(`/api/users/${editing.id}`, { method: 'PUT', body })
        toast.show('Usuário salvo')
      } else {
        await request<UserResponse>('/api/users', {
          method: 'POST',
          body: { id: newId(), email: email.trim(), password, name: name.trim(), roleId, active: true },
        })
        toast.show('Usuário criado')
      }
      setFormOpen(false)
      users.reload()
    } catch (caught) {
      if (caught instanceof ApiError && Object.keys(caught.fields).length > 0) {
        setErrors(caught.fields)
      } else {
        setFormError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
      }
    } finally {
      setSaving(false)
    }
  }

  const newButton = (
    <Button onClick={openCreate}>
      <Plus className="h-5 w-5" aria-hidden="true" />
      Novo usuário
    </Button>
  )

  const list = users.data ?? []

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Usuários" actions={newButton} />

      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        {roles.map((role) => (
          <Card key={role.id} className="flex items-start gap-3">
            <role.icon className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
            <div>
              <p className="font-semibold text-text">{role.label}</p>
              <p className="text-sm text-text-secondary">{role.description}</p>
            </div>
          </Card>
        ))}
      </div>

      {users.loading && !users.data && <Skeleton lines={3} />}
      {users.error && <ErrorState message={users.error} onRetry={users.reload} />}
      {users.data && list.length === 0 && <EmptyState icon={UsersIcon} title="Nenhum usuário ainda" action={newButton} />}
      {users.data && list.length > 0 && (
        <ul className="space-y-3">
          {list.map((user) => (
            <li key={user.id}>
              <Card className="space-y-3">
                <div className="flex items-center gap-3">
                  <Avatar name={user.name} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold text-text">{user.name}</p>
                    <p className="truncate text-sm text-text-secondary">{user.email}</p>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <StatusPill tone="info" icon={user.roleName === 'ADMIN' ? ShieldCheck : Store}>
                    {roleLabel(user.roleName)}
                  </StatusPill>
                  <StatusPill tone={user.active ? 'success' : 'neutral'} icon={user.active ? CheckCircle2 : UserX}>
                    {user.active ? 'Ativo' : 'Inativo'}
                  </StatusPill>
                </div>
                <Button variant="ghost" className="w-full" onClick={() => openEdit(user)} aria-label={`Editar ${user.name}`}>
                  <Pencil className="h-5 w-5" aria-hidden="true" />
                  Editar
                </Button>
              </Card>
            </li>
          ))}
        </ul>
      )}

      <Sheet
        open={formOpen}
        onClose={() => setFormOpen(false)}
        title={editing ? 'Editar usuário' : 'Novo usuário'}
        footer={
          <>
            <Button variant="ghost" className="flex-1" onClick={() => setFormOpen(false)} disabled={saving}>
              Cancelar
            </Button>
            <Button type="submit" form="user-form" className="flex-1" loading={saving}>
              Salvar
            </Button>
          </>
        }
      >
        <form id="user-form" onSubmit={submit} noValidate className="space-y-4">
          {formError && (
            <p role="alert" className="rounded-[10px] bg-error-soft p-3 text-sm text-error">
              {formError}
            </p>
          )}
          <Field label="Nome" required error={errors.name}>
            <Input value={name} onChange={(event) => setName(event.target.value)} invalid={Boolean(errors.name)} />
          </Field>
          <Field label="E-mail" required error={errors.email}>
            <Input
              type="email"
              inputMode="email"
              autoCapitalize="none"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              invalid={Boolean(errors.email)}
            />
          </Field>
          <Field
            label={editing ? 'Nova senha' : 'Senha inicial'}
            required={!editing}
            hint={editing ? 'Deixe em branco para manter a senha atual.' : undefined}
            error={errors.password}
          >
            <Input
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              invalid={Boolean(errors.password)}
            />
          </Field>

          <fieldset disabled={isSelf} className="space-y-2">
            <legend className="mb-1 text-sm font-medium text-text">
              Papel<span className="text-error"> *</span>
            </legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {roles.map((role) => (
                <button
                  key={role.id}
                  type="button"
                  aria-pressed={roleId === role.id}
                  onClick={() => setRoleId(role.id)}
                  className={`min-h-[48px] rounded-xl border p-3 text-left disabled:opacity-60 ${
                    roleId === role.id ? 'border-primary bg-secondary-soft' : 'border-border bg-surface'
                  }`}
                >
                  <span className="block font-semibold text-text">{role.label}</span>
                  <span className="block text-sm text-text-secondary">{role.description}</span>
                </button>
              ))}
            </div>
          </fieldset>

          {editing && (
            <fieldset disabled={isSelf} className="space-y-2">
              <legend className="mb-1 text-sm font-medium text-text">Situação</legend>
              <div className="grid grid-cols-2 gap-2">
                {[
                  { value: true, label: 'Ativo' },
                  { value: false, label: 'Inativo' },
                ].map((option) => (
                  <button
                    key={option.label}
                    type="button"
                    aria-pressed={active === option.value}
                    onClick={() => setActive(option.value)}
                    className={`min-h-[48px] rounded-xl border px-3 font-semibold text-text disabled:opacity-60 ${
                      active === option.value ? 'border-primary bg-secondary-soft' : 'border-border bg-surface'
                    }`}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
              {isSelf && <p className="text-sm text-text-secondary">Você não pode alterar o próprio papel ou situação.</p>}
            </fieldset>
          )}
        </form>
      </Sheet>

      <ConfirmSheet
        open={confirmDeactivate}
        title="Desativar usuário"
        message="A pessoa será desconectada de todos os aparelhos."
        confirmLabel="Desativar"
        danger
        busy={saving}
        onConfirm={save}
        onClose={() => setConfirmDeactivate(false)}
      />
    </div>
  )
}
