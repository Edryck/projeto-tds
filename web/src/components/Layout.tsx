import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { LogOut, Menu, Plus, WifiOff } from 'lucide-react'
import { useAuth } from '../auth'
import { bottomItems, createItems, menuGroups, visible } from '../nav'
import type { NavItem } from '../nav'
import { Button, Sheet } from './ui'

function useOnline(): boolean {
  const [online, setOnline] = useState(navigator.onLine)
  useEffect(() => {
    const on = () => setOnline(true)
    const off = () => setOnline(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => {
      window.removeEventListener('online', on)
      window.removeEventListener('offline', off)
    }
  }, [])
  return online
}

function MenuList({ onNavigate }: { onNavigate?: () => void }) {
  const { can } = useAuth()
  return (
    <div className="space-y-4">
      {menuGroups.map((group) => {
        const items = visible(group.items, can)
        if (items.length === 0) {
          return null
        }
        return (
          <section key={group.title}>
            <h3 className="mb-1 px-3 text-xs font-semibold uppercase tracking-wide text-text-secondary">{group.title}</h3>
            <ul>
              {items.map((item) => (
                <li key={item.to}>
                  <SideLink item={item} onNavigate={onNavigate} />
                </li>
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}

function SideLink({ item, onNavigate }: { item: NavItem; onNavigate?: () => void }) {
  const Icon = item.icon
  return (
    <NavLink
      to={item.to}
      end={item.to === '/' || item.end}
      onClick={onNavigate}
      className={({ isActive }) =>
        `flex min-h-[48px] items-center gap-3 rounded-xl px-3 text-base font-medium text-text ${
          isActive ? 'bg-secondary-soft' : 'hover:bg-surface-alt'
        }`
      }
    >
      <Icon className="h-5 w-5" aria-hidden="true" />
      {item.label}
    </NavLink>
  )
}

function UserFooter({ onLogout }: { onLogout: () => void }) {
  const { me } = useAuth()
  return (
    <div className="flex w-full items-center justify-between gap-3">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-text">{me?.name}</p>
        <p className="truncate text-xs text-text-secondary">{me?.role}</p>
      </div>
      <Button variant="ghost" onClick={onLogout}>
        <LogOut className="h-5 w-5" aria-hidden="true" />
        Sair
      </Button>
    </div>
  )
}

export default function Layout({ children, hideNav }: { children: ReactNode; hideNav?: boolean }) {
  const { can, logout } = useAuth()
  const online = useOnline()
  const { pathname } = useLocation()
  const [createOpen, setCreateOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  const offlineBanner = !online && (
    <div role="status" className="flex items-center justify-center gap-2 bg-warning-soft px-4 py-2 text-sm font-medium text-warning">
      <WifiOff className="h-4 w-4" aria-hidden="true" />
      Sem conexão. Algumas ações podem falhar.
    </div>
  )

  if (hideNav) {
    return (
      <div className="min-h-screen">
        {offlineBanner}
        <main key={pathname} className="mx-auto w-full max-w-[1200px] animate-rise-in px-4 py-4">
          {children}
        </main>
      </div>
    )
  }

  const createVisible = visible(createItems, can)
  const bottomVisible = visible(bottomItems, can)
  const leftItems = bottomVisible.filter((item) => item.to === '/' || item.to === '/sales')
  const rightItems = bottomVisible.filter((item) => item.to === '/stock')

  function bottomLink(item: NavItem) {
    const Icon = item.icon
    return (
      <NavLink
        key={item.to}
        to={item.to}
        end={item.to === '/'}
        className={({ isActive }) =>
          `flex min-h-[48px] min-w-[56px] flex-col items-center justify-center gap-0.5 ${
            isActive ? 'text-text' : 'text-text-secondary'
          }`
        }
      >
        {({ isActive }) => (
          <>
            <Icon className="h-5 w-5" aria-hidden="true" />
            <span className="text-[11px] font-medium leading-none">{item.label}</span>
            <span
              aria-hidden="true"
              className={`h-1 w-1 rounded-full ${isActive ? 'bg-secondary' : 'bg-transparent'}`}
            />
          </>
        )}
      </NavLink>
    )
  }

  const createSheet = (
    <Sheet open={createOpen} onClose={() => setCreateOpen(false)} title="O que você quer registrar?">
      <ul className="space-y-2">
        {createVisible.map((item, index) => {
          const Icon = item.icon
          return (
            <li key={item.to}>
              <Link
                to={item.to}
                onClick={() => setCreateOpen(false)}
                className={`pressable flex min-h-[48px] items-center gap-3 rounded-xl px-4 text-base font-semibold text-text ${
                  index === 0 && item.to === '/sales/new' ? 'bg-secondary' : 'border border-border bg-surface'
                }`}
              >
                <Icon className="h-5 w-5" aria-hidden="true" />
                {item.label}
              </Link>
            </li>
          )
        })}
      </ul>
    </Sheet>
  )

  return (
    <div className="min-h-screen">
      <aside className="fixed inset-y-0 left-0 hidden w-64 flex-col border-r border-border bg-surface lg:flex">
        <div className="px-4 py-4">
          <p className="font-serif text-xl font-bold text-text">Chic Charmosa</p>
        </div>
        {createVisible.length > 0 && (
          <div className="px-4 pb-3">
            <Button className="w-full" onClick={() => setCreateOpen(true)}>
              <Plus className="h-5 w-5" aria-hidden="true" />
              Novo
            </Button>
          </div>
        )}
        <nav aria-label="Menu principal" className="flex-1 space-y-4 overflow-y-auto px-2 pb-4">
          <ul>
            {bottomVisible.map((item) => (
              <li key={item.to}>
                <SideLink item={item} />
              </li>
            ))}
          </ul>
          <MenuList />
        </nav>
        <div className="border-t border-border p-4">
          <UserFooter onLogout={logout} />
        </div>
      </aside>

      <div className="lg:pl-64">
        {offlineBanner}
        <main key={pathname} className="mx-auto w-full max-w-[1200px] animate-rise-in px-4 py-4 pb-28 lg:pb-8">
          {children}
        </main>
      </div>

      <nav
        aria-label="Navegação principal"
        className="fixed inset-x-0 bottom-0 z-40 flex justify-center px-4 pb-[calc(env(safe-area-inset-bottom)+8px)] lg:hidden"
      >
        <div className="flex w-full max-w-md items-center justify-around rounded-full border border-border bg-surface px-2 py-1 shadow-md">
          {leftItems.map(bottomLink)}
          {createVisible.length > 0 && (
            <button
              type="button"
              aria-label="Criar"
              onClick={() => setCreateOpen(true)}
              className="flex h-12 w-12 items-center justify-center -translate-y-2 rounded-full bg-secondary text-primary shadow-md"
            >
              <Plus className="h-6 w-6" aria-hidden="true" />
            </button>
          )}
          {rightItems.map(bottomLink)}
          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            className="flex min-h-[48px] min-w-[56px] flex-col items-center justify-center gap-0.5 text-text-secondary"
          >
            <Menu className="h-5 w-5" aria-hidden="true" />
            <span className="text-[11px] font-medium leading-none">Menu</span>
            <span aria-hidden="true" className="h-1 w-1" />
          </button>
        </div>
      </nav>

      {createSheet}
      <Sheet open={menuOpen} onClose={() => setMenuOpen(false)} title="Menu" footer={<UserFooter onLogout={logout} />}>
        <MenuList onNavigate={() => setMenuOpen(false)} />
      </Sheet>
    </div>
  )
}
