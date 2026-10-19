const API_URL = import.meta.env.VITE_API_URL
const REFRESH_KEY = 'refreshToken'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

type Options = {
  method?: string
  body?: unknown
  anonymous?: boolean
}

type Tokens = {
  accessToken: string
  refreshToken: string
}

let accessToken: string | null = null
let refreshing: Promise<void> | null = null
let onSessionLost: () => void = () => {}

export function newId(): string {
  return crypto.randomUUID()
}

export function setOnSessionLost(handler: () => void) {
  onSessionLost = handler
}

export function hasRefreshToken(): boolean {
  return localStorage.getItem(REFRESH_KEY) !== null
}

export function saveTokens(tokens: Tokens) {
  accessToken = tokens.accessToken
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken)
}

export function clearTokens() {
  accessToken = null
  localStorage.removeItem(REFRESH_KEY)
}

async function send(path: string, options: Options): Promise<Response> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (!options.anonymous && accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }
  try {
    return await fetch(`${API_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, 'Não foi possível conectar ao servidor')
  }
}

async function toError(response: Response): Promise<ApiError> {
  let message = 'Ocorreu um erro inesperado'
  try {
    const problem = await response.json()
    if (problem.detail) {
      message = problem.detail
    }
  } catch {
    message = 'Ocorreu um erro inesperado'
  }
  return new ApiError(response.status, message)
}

export function refreshSession(): Promise<void> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null
    })
  }
  return refreshing
}

async function doRefresh() {
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) {
    throw new ApiError(401, 'Sessão expirada. Entre novamente.')
  }
  const response = await send('/api/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
    anonymous: true,
  })
  if (!response.ok) {
    const error = await toError(response)
    if (response.status === 401 || response.status === 403) {
      clearTokens()
      onSessionLost()
    }
    throw error
  }
  saveTokens(await response.json())
}

export async function request<T>(path: string, options: Options = {}): Promise<T> {
  let response = await send(path, options)
  if (response.status === 401 && !options.anonymous) {
    await refreshSession()
    response = await send(path, options)
  }
  if (!response.ok) {
    throw await toError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json()
}

export function follow<T>(link: { href: string }, options: Options = {}): Promise<T> {
  return request<T>(link.href.replace(API_URL, ''), options)
}
