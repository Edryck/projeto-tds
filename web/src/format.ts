const TIME_ZONE = 'America/Sao_Paulo'

export function money(value: number | string): string {
  return Number(value).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function parseMoney(input: string): number {
  const text = input.replace(/R\$/g, '').replace(/\s/g, '')
  if (!/^-?[\d.,]+$/.test(text) || !/\d/.test(text)) {
    return NaN
  }
  let normalized = text
  if (text.includes(',')) {
    normalized = text.replace(/\./g, '').replace(',', '.')
  } else if (/^-?\d{1,3}(\.\d{3})+$/.test(text)) {
    normalized = text.replace(/\./g, '')
  }
  const value = Number(normalized)
  return Number.isFinite(value) ? Math.round(value * 100) / 100 : NaN
}

export function brDate(iso: string): string {
  const [year, month, day] = iso.split('-')
  return `${day}/${month}/${year}`
}

export function date(iso: string): string {
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) {
    return brDate(iso)
  }
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
