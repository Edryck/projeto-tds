import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { AuthProvider, useAuth } from './auth'
import Layout from './components/Layout'
import { PageHeader } from './components/ui'
import { ToastProvider } from './components/Toast'
import Login from './pages/Login'
import Customers from './pages/Customers'
import ProductForm from './pages/catalog/ProductForm'
import ProductList from './pages/catalog/ProductList'
import VariantForm from './pages/catalog/VariantForm'
import Categories from './pages/catalog/Categories'
import StockEntryForm from './pages/stock/StockEntryForm'
import StockPosition from './pages/stock/StockPosition'
import CashClosingPage from './pages/CashClosing'
import NewSale from './pages/sales/NewSale'
import SaleDetail from './pages/sales/SaleDetail'
import SalesList from './pages/sales/SalesList'
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

function FullScreen({ permission, children }: { permission: string; children: ReactNode }) {
  return (
    <RequireAuth>
      <Layout hideNav>
        <RequirePermission permission={permission}>{children}</RequirePermission>
      </Layout>
    </RequireAuth>
  )
}

function LegacyStockEntry() {
  const { search } = useLocation()
  return <Navigate to={`/stock/entry${search}`} replace />
}

function Home() {
  const { me } = useAuth()
  return <PageHeader title="Início" subtitle={`Olá, ${me?.name}`} />
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
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
            <Route path="/products" element={<Navigate to="/catalog" replace />} />
            <Route
              path="/catalog"
              element={
                <Screen permission="product:view">
                  <ProductList />
                </Screen>
              }
            />
            <Route
              path="/catalog/new"
              element={
                <Screen permission="product:edit">
                  <ProductForm />
                </Screen>
              }
            />
            <Route
              path="/catalog/:id"
              element={
                <Screen permission="product:view">
                  <ProductForm />
                </Screen>
              }
            />
            <Route
              path="/catalog/categories"
              element={
                <Screen permission="product:view">
                  <Categories />
                </Screen>
              }
            />
            <Route
              path="/catalog/:id/variants/new"
              element={
                <Screen permission="product:edit">
                  <VariantForm />
                </Screen>
              }
            />
            <Route
              path="/catalog/:id/variants/:variantId"
              element={
                <Screen permission="product:view">
                  <VariantForm />
                </Screen>
              }
            />
            <Route
              path="/stock"
              element={
                <Screen permission="stock:view">
                  <StockPosition />
                </Screen>
              }
            />
            <Route
              path="/stock/entry"
              element={
                <Screen permission="stock:adjust">
                  <StockEntryForm />
                </Screen>
              }
            />
            <Route path="/stock-entry" element={<LegacyStockEntry />} />
            <Route
              path="/customers"
              element={
                <Screen permission="customer:view">
                  <Customers />
                </Screen>
              }
            />
            <Route path="/orders" element={<Navigate to="/sales" replace />} />
            <Route
              path="/sales"
              element={
                <Screen permission="order:view">
                  <SalesList />
                </Screen>
              }
            />
            <Route
              path="/sales/:id"
              element={
                <Screen permission="order:view">
                  <SaleDetail />
                </Screen>
              }
            />
            <Route
              path="/sales/new"
              element={
                <FullScreen permission="order:create">
                  <NewSale />
                </FullScreen>
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
      </ToastProvider>
    </BrowserRouter>
  )
}
