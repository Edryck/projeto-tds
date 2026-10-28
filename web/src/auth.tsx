import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import {
  clearTokens,
  hasRefreshToken,
  newId,
  refreshSession,
  request,
  saveTokens,
  setOnSessionLost,
} from './api'
import type { Me } from './types'

const DEVICE_KEY = 'deviceId'

type AuthContextValue = {
  me: Me | null
  loading: boolean
  sessionExpired: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  can: (permission: string) => boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

function getDeviceId(): string {
  let deviceId = localStorage.getItem(DEVICE_KEY)
  if (!deviceId) {
    deviceId = newId()
    localStorage.setItem(DEVICE_KEY, deviceId)
  }
  return deviceId
}

function browserName(agent: string): string {
  if (agent.includes('Edg/')) return 'Edge'
  if (agent.includes('Firefox/')) return 'Firefox'
  if (agent.includes('Chrome/')) return 'Chrome'
  if (agent.includes('Safari/')) return 'Safari'
  return ''
}

function systemName(agent: string): string {
  if (agent.includes('Android')) return 'Android'
  if (/iPhone|iPad|iPod/.test(agent)) return 'iOS'
  if (agent.includes('Windows')) return 'Windows'
  if (agent.includes('Mac OS X')) return 'macOS'
  if (agent.includes('Linux')) return 'Linux'
  return ''
}

function deviceName(): string {
  const agent = navigator.userAgent
  const browser = browserName(agent)
  if (!browser) return 'Navegador'
  const system = systemName(agent)
  return system ? `${browser} · ${system}` : browser
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(hasRefreshToken())
  const [sessionExpired, setSessionExpired] = useState(false)

  useEffect(() => {
    setOnSessionLost(() => {
      setMe(null)
      setSessionExpired(true)
    })
    if (!hasRefreshToken()) {
      return
    }
    refreshSession()
      .then(() => request<Me>('/api/auth/me'))
      .then(setMe)
      .catch(() => setMe(null))
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await request<{ accessToken: string; refreshToken: string }>('/api/auth/login', {
      method: 'POST',
      body: { email, password, deviceId: getDeviceId(), deviceName: deviceName() },
      anonymous: true,
    })
    saveTokens(tokens)
    setSessionExpired(false)
    setMe(await request<Me>('/api/auth/me'))
  }, [])

  const logout = useCallback(async () => {
    try {
      await request<void>('/api/auth/logout', { method: 'POST' })
    } catch {
      setMe(null)
    } finally {
      clearTokens()
      setMe(null)
    }
  }, [])

  const can = useCallback((permission: string) => me?.permissions.includes(permission) ?? false, [me])

  return (
    <AuthContext.Provider value={{ me, loading, sessionExpired, login, logout, can }}>{children}</AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth precisa estar dentro de AuthProvider')
  }
  return context
}
