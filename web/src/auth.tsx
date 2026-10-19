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

const DEVICE_KEY = 'deviceId'

type Me = {
  id: string
  name: string
  email: string
  role: string
  permissions: string[]
}

type AuthContextValue = {
  me: Me | null
  loading: boolean
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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(hasRefreshToken())

  useEffect(() => {
    setOnSessionLost(() => setMe(null))
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
      body: { email, password, deviceId: getDeviceId(), deviceName: 'Navegador' },
      anonymous: true,
    })
    saveTokens(tokens)
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

  return <AuthContext.Provider value={{ me, loading, login, logout, can }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth precisa estar dentro de AuthProvider')
  }
  return context
}
