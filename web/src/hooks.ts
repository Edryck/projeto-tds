import { useCallback, useEffect, useRef, useState } from 'react'

export function useLoad<T>(load: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  const loadRef = useRef(load)

  useEffect(() => {
    loadRef.current = load
  })

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    loadRef.current()
      .then((result) => {
        if (active) setData(result)
      })
      .catch((e: unknown) => {
        if (active) setError(e instanceof Error ? e.message : 'Ocorreu um erro inesperado')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [...deps, tick])

  const reload = useCallback(() => setTick((value) => value + 1), [])

  return { data, error, loading, reload }
}
