import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import { AuthProvider, useAuth } from './auth'
import Layout from './components/Layout'
import { PageHeader } from './components/ui'
import Login from './pages/Login'
import Customers from './pages/Customers'
import Orders from './pages/Orders'
import Products from './pages/Products'
import StockEntry from './pages/StockEntry'
import CashClosingPage from './pages/CashClosing'
import NotFound from './pages/NotFound'
import Forbidden from './pages/Forbidden'

function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth()
  if (loading) {
    return <p className="p-4 text-text">Carregando...</p>
  }
  if (!me) {
    return <Navigate to="/login" replace />
  }
  return children
}

function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const { can } = useAuth()
  if (!can(permission)) {
    return <Forbidden />
  }
  return children
}

function Screen({ permission, children }: { permission?: string; children: ReactNode }) {
  return (
    <RequireAuth>
      <Layout>{permission ? <RequirePermission permission={permission}>{children}</RequirePermission> : children}</Layout>
    </RequireAuth>
  )
}

function Home() {
  const { me } = useAuth()
  return <PageHeader title="Início" subtitle={`Olá, ${me?.name}`} />
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            path="/"
            element={
              <Screen>
                <Home />
              </Screen>
            }
          />
          <Route
            path="/products"
            element={
              <Screen permission="product:view">
                <Products />
              </Screen>
            }
          />
          <Route
            path="/stock-entry"
            element={
              <Screen permission="stock:adjust">
                <StockEntry />
              </Screen>
            }
          />
          <Route
            path="/customers"
            element={
              <Screen permission="customer:view">
                <Customers />
              </Screen>
            }
          />
          <Route
            path="/orders"
            element={
              <Screen permission="order:view">
                <Orders />
              </Screen>
            }
          />
          <Route
            path="/cash-closing"
            element={
              <Screen permission="finance:view">
                <CashClosingPage />
              </Screen>
            }
          />
          <Route
            path="*"
            element={
              <Screen>
                <NotFound />
              </Screen>
            }
          />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
