import { useState } from 'react'
import { Button, Chip, ChipRow, Field, Input } from './ui'
import { todayIso } from '../format'

export type Period = { from: string; to: string; label: string }

type Preset = 'today' | '7days' | 'thisMonth' | 'lastMonth'

const presetNames: Record<Preset, string> = {
  today: 'Hoje',
  '7days': 'Últimos 7 dias',
  thisMonth: 'Este mês',
  lastMonth: 'Mês passado',
}

const monthNames = [
  'Janeiro',
  'Fevereiro',
  'Março',
  'Abril',
  'Maio',
  'Junho',
  'Julho',
  'Agosto',
  'Setembro',
  'Outubro',
  'Novembro',
  'Dezembro',
]

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function addDays(iso: string, days: number): string {
  const [year, month, day] = iso.split('-').map(Number)
  const moved = new Date(Date.UTC(year, month - 1, day + days))
  return `${moved.getUTCFullYear()}-${pad(moved.getUTCMonth() + 1)}-${pad(moved.getUTCDate())}`
}

function monthPeriod(year: number, month: number): Period {
  const last = new Date(Date.UTC(year, month, 0)).getUTCDate()
  return {
    from: `${year}-${pad(month)}-01`,
    to: `${year}-${pad(month)}-${pad(last)}`,
    label: `${monthNames[month - 1]} ${year}`,
  }
}

export function brDate(iso: string): string {
  const [year, month, day] = iso.split('-')
  return `${day}/${month}/${year}`
}

export function presetPeriod(preset: Preset): Period {
  const today = todayIso()
  if (preset === 'today') {
    return { from: today, to: today, label: presetNames.today }
  }
  if (preset === '7days') {
    return { from: addDays(today, -6), to: today, label: presetNames['7days'] }
  }
  const [year, month] = today.split('-').map(Number)
  if (preset === 'thisMonth') {
    return monthPeriod(year, month)
  }
  return month === 1 ? monthPeriod(year - 1, 12) : monthPeriod(year, month - 1)
}

const allPresets: Preset[] = ['today', '7days', 'thisMonth', 'lastMonth']

export function periodFromRange(from: string, to: string): Period {
  for (const preset of allPresets) {
    const period = presetPeriod(preset)
    if (period.from === from && period.to === to) {
      return period
    }
  }
  return { from, to, label: `${brDate(from)} a ${brDate(to)}` }
}

export default function PeriodPicker({
  value,
  onChange,
  presets,
}: {
  value: Period
  onChange: (period: Period) => void
  presets: Preset[]
}) {
  const [custom, setCustom] = useState(false)
  const [from, setFrom] = useState(value.from)
  const [to, setTo] = useState(value.to)
  const [error, setError] = useState('')

  function pick(preset: Preset) {
    setCustom(false)
    setError('')
    onChange(presetPeriod(preset))
  }

  function apply() {
    if (from === '' || to === '') {
      setError('Informe as duas datas')
      return
    }
    if (from > to) {
      setError('A data inicial não pode ser depois da final')
      return
    }
    setError('')
    onChange({ from, to, label: `${brDate(from)} a ${brDate(to)}` })
  }

  const presetActive = (preset: Preset) => {
    const period = presetPeriod(preset)
    return !custom && period.from === value.from && period.to === value.to
  }

  return (
    <div>
      <ChipRow>
        {presets.map((preset) => (
          <Chip key={preset} active={presetActive(preset)} onClick={() => pick(preset)}>
            {presetNames[preset]}
          </Chip>
        ))}
        <Chip
          active={custom || !presets.some(presetActive)}
          onClick={() => {
            setFrom(value.from)
            setTo(value.to)
            setCustom(true)
          }}
        >
          Personalizado
        </Chip>
      </ChipRow>
      {custom && (
        <div className="mt-3 grid grid-cols-2 gap-3">
          <Field label="De">
            <Input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
          </Field>
          <Field label="Até">
            <Input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
          </Field>
          {error && (
            <p role="alert" className="col-span-2 text-sm text-error">
              {error}
            </p>
          )}
          <Button className="col-span-2" onClick={apply}>
            Aplicar
          </Button>
        </div>
      )}
      <p className="mt-2 text-sm text-text-secondary">{value.label}</p>
    </div>
  )
}
