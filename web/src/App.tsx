import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import { AuthProvider, useAuth } from './auth'
import Login from './pages/Login'
import Customers from './pages/Customers'
import Orders from './pages/Orders'
import Products from './pages/Products'
import StockEntry from './pages/StockEntry'
import CashClosingPage from './pages/CashClosing'

function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth()
  if (loading) {
    return <p className="p-4 text-gray-900">Carregando...</p>
  }
  if (!me) {
    return <Navigate to="/login" replace />
  }
  return children
}

function Home() {
  const { me, logout, can } = useAuth()
  return (
    <div className="mx-auto max-w-3xl px-4 py-4">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <p className="text-gray-900">
          Olá, <strong>{me?.name}</strong>
        </p>
        <nav aria-label="Menu principal" className="flex flex-wrap items-center gap-2">
          {can('product:view') && (
            <Link
              to="/products"
              className="flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
            >
              Produtos
            </Link>
          )}
          {can('stock:adjust') && (
            <Link
              to="/stock-entry"
              className="flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
            >
              Entrada de estoque
            </Link>
          )}
          {can('customer:view') && (
            <Link
              to="/customers"
              className="flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
            >
              Clientes
            </Link>
          )}
          {can('order:view') && (
            <Link
              to="/orders"
              className="flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
            >
              Pedidos
            </Link>
          )}
          {can('finance:view') && (
            <Link
              to="/cash-closing"
              className="flex h-12 items-center rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
            >
              Caixa
            </Link>
          )}
          <button
            type="button"
            onClick={logout}
            className="h-12 rounded-md border border-gray-500 px-4 font-medium text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-700"
          >
            Sair
          </button>
        </nav>
      </header>
    </div>
  )
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
              <RequireAuth>
                <Home />
              </RequireAuth>
            }
          />
          <Route
            path="/products"
            element={
              <RequireAuth>
                <Products />
              </RequireAuth>
            }
          />
          <Route
            path="/stock-entry"
            element={
              <RequireAuth>
                <StockEntry />
              </RequireAuth>
            }
          />
          <Route
            path="/customers"
            element={
              <RequireAuth>
                <Customers />
              </RequireAuth>
            }
          />
          <Route
            path="/orders"
            element={
              <RequireAuth>
                <Orders />
              </RequireAuth>
            }
          />
          <Route
            path="/cash-closing"
            element={
              <RequireAuth>
                <CashClosingPage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
