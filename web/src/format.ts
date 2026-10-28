const TIME_ZONE = 'America/Sao_Paulo'

export function money(value: number | string): string {
  return Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function parseMoney(input: string): number {
  const text = input.replace(/[^\d.,-]/g, '')
  if (text.includes(',')) {
    return Number(text.replace(/\./g, '').replace(',', '.'))
  }
  return Number(text)
}

export function date(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', { timeZone: TIME_ZONE })
}

export function dateTime(iso: string): string {
  const value = new Date(iso)
  const day = value.toLocaleDateString('pt-BR', { timeZone: TIME_ZONE })
  const time = value.toLocaleTimeString('pt-BR', {
    timeZone: TIME_ZONE,
    hour: '2-digit',
    minute: '2-digit',
  })
  return `${day} ${time}`
}

export function shortId(id: string): string {
  return `#${id.replace(/-/g, '').slice(0, 6).toUpperCase()}`
}

export function todayIso(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: TIME_ZONE })
}
