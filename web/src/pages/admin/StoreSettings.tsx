import { useEffect, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { ApiError, logoUrl, request, swapToFallbackLogo, upload } from '../../api'
import { useToast } from '../../components/Toast'
import { Button, Card, ErrorState, Field, Input, PageHeader, Skeleton } from '../../components/ui'
import { useLoad } from '../../hooks'
import type { StoreSettingsView } from '../../types'

const LOGO_TYPES = ['image/png', 'image/jpeg', 'image/webp']
const LOGO_MAX_BYTES = 1024 * 1024
const LOGO_ERROR = 'Logo deve ser PNG, JPG ou WebP de até 1 MB'

export default function StoreSettings() {
  const toast = useToast()
  const settings = useLoad(() => request<StoreSettingsView>('/api/store-settings'), [])

  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [address, setAddress] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [logoVersion, setLogoVersion] = useState(() => Date.now())
  const [logoError, setLogoError] = useState('')
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    if (settings.data) {
      setName(settings.data.name)
      setPhone(settings.data.phone ?? '')
      setEmail(settings.data.email ?? '')
      setAddress(settings.data.address ?? '')
    }
  }, [settings.data])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (name.trim() === '') {
      next.name = 'Informe o nome da loja'
    }
    setErrors(next)
    setFormError('')
    if (Object.keys(next).length > 0) {
      return
    }
    setSaving(true)
    try {
      const saved = await request<StoreSettingsView>('/api/store-settings', {
        method: 'PUT',
        body: { name: name.trim(), phone: phone.trim(), email: email.trim(), address: address.trim() },
      })
      settings.reload()
      setName(saved.name)
      toast.show('Configurações salvas')
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

  async function chooseFile(event: ChangeEvent<HTMLInputElement>) {
    const input = event.target
    const file = input.files?.[0]
    input.value = ''
    if (!file) {
      return
    }
    if (file.size > LOGO_MAX_BYTES || !LOGO_TYPES.includes(file.type)) {
      setLogoError(LOGO_ERROR)
      return
    }
    setLogoError('')
    setUploading(true)
    try {
      await upload<unknown>('/api/store-settings/logo', file)
      setLogoVersion(Date.now())
      toast.show('Logo atualizada')
    } catch (caught) {
      setLogoError(caught instanceof Error ? caught.message : 'Ocorreu um erro inesperado')
    } finally {
      setUploading(false)
    }
  }

  if (settings.loading && !settings.data) {
    return <Skeleton lines={4} />
  }
  if (settings.error && !settings.data) {
    return <ErrorState message={settings.error} onRetry={settings.reload} />
  }

  const previewName = settings.data?.name ?? name
  const previewPhone = settings.data?.phone ?? ''

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title="Configurações da loja" subtitle="Nome, contato e logo da loja" />

      <Card>
        <div className="flex items-center gap-4">
          <div className="flex h-24 w-24 shrink-0 items-center justify-center rounded-2xl bg-surface-alt p-2">
            <img
              key={logoVersion}
              src={logoUrl(logoVersion)}
              alt={previewName}
              onError={swapToFallbackLogo}
              className="h-full w-full object-contain"
            />
          </div>
          <div className="min-w-0">
            <p className="truncate font-serif text-lg font-bold text-text">{previewName}</p>
            {previewPhone && <p className="text-sm text-text-secondary">{previewPhone}</p>}
          </div>
        </div>
        <div className="mt-4">
          <Field label="Substituir arquivo" hint="PNG, JPG ou WebP de até 1 MB" error={logoError}>
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              disabled={uploading}
              onChange={chooseFile}
              className="block w-full text-sm text-text file:mr-3 file:min-h-[48px] file:cursor-pointer file:rounded-[10px] file:border-0 file:bg-secondary file:px-4 file:font-semibold file:text-primary"
            />
          </Field>
          {uploading && <p className="mt-1 text-sm text-text-secondary">Enviando…</p>}
        </div>
      </Card>

      <form onSubmit={submit} noValidate className="mt-4 flex flex-col gap-4">
        <Field label="Nome da loja" required error={errors.name}>
          <Input value={name} maxLength={150} invalid={Boolean(errors.name)} onChange={(event) => setName(event.target.value)} />
        </Field>
        <Field label="Telefone" error={errors.phone}>
          <Input
            type="tel"
            inputMode="tel"
            value={phone}
            maxLength={20}
            invalid={Boolean(errors.phone)}
            onChange={(event) => setPhone(event.target.value)}
          />
        </Field>
        <Field label="E-mail" error={errors.email}>
          <Input
            type="email"
            inputMode="email"
            value={email}
            maxLength={255}
            invalid={Boolean(errors.email)}
            onChange={(event) => setEmail(event.target.value)}
          />
        </Field>
        <Field label="Endereço" error={errors.address}>
          <Input value={address} maxLength={300} invalid={Boolean(errors.address)} onChange={(event) => setAddress(event.target.value)} />
        </Field>
        {formError && (
          <p role="alert" className="rounded-[10px] bg-error-soft px-4 py-3 text-sm font-medium text-error">
            {formError}
          </p>
        )}
        <Button type="submit" className="w-full" loading={saving}>
          Salvar configurações
        </Button>
      </form>
    </div>
  )
}
