import type { CSSProperties } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchPage } from './api'
import type { Model, Page } from './api'

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

export function usePagedList<T>(buildPath: (page: number) => string | null, deps: unknown[]) {
  const generation = useRef(0)
  const [extra, setExtra] = useState<Model<T>[]>([])
  const [lastPage, setLastPage] = useState<Page<T>['page'] | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)

  const first = useLoad(async () => {
    generation.current += 1
    setExtra([])
    setLastPage(null)
    setLoadingMore(false)
    const path = buildPath(0)
    return path === null ? null : fetchPage<T>(path)
  }, deps)

  async function loadMore() {
    const current = lastPage ?? first.data?.page
    const path = current ? buildPath(current.number + 1) : null
    if (!path || loadingMore) {
      return
    }
    const mine = generation.current
    setLoadingMore(true)
    try {
      const next = await fetchPage<T>(path)
      if (mine === generation.current) {
        setExtra((list) => [...list, ...next.items])
        setLastPage(next.page)
      }
    } catch (caught) {
      if (mine === generation.current) {
        throw caught
      }
    } finally {
      if (mine === generation.current) {
        setLoadingMore(false)
      }
    }
  }

  const items = first.data ? [...first.data.items, ...extra] : []
  const page = lastPage ?? first.data?.page ?? null
  return { data: first.data, items, page, error: first.error, loading: first.loading, reload: first.reload, loadMore, loadingMore }
}

export function staggerStyle(index: number): CSSProperties {
  return { animationDelay: `${Math.min(index, 8) * 30}ms` }
}

export function itemMotion(index: number, firstPageSize = 8): { className: string; style?: CSSProperties } {
  if (index < firstPageSize) {
    return { className: 'animate-rise-in [animation-fill-mode:both]', style: staggerStyle(index) }
  }
  return { className: 'animate-fade-in' }
}
