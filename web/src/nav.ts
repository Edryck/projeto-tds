import {
  Boxes,
  FilePlus2,
  Home,
  PackagePlus,
  Settings2,
  ShoppingBag,
  Tag,
  UserPlus,
  Wallet,
  Users,
  Package,
  Layers,
  Truck,
  Landmark,
  CalendarCheck,
  BarChart3,
  ShieldCheck,
  Ban,
  Sliders,
  Receipt,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export type NavItem = { label: string; to: string; icon: LucideIcon; permission?: string; end?: boolean }

export const bottomItems: NavItem[] = [
  { label: 'Início', to: '/', icon: Home },
  { label: 'Vendas', to: '/sales', icon: Receipt, permission: 'order:view' },
  { label: 'Estoque', to: '/stock', icon: Boxes, permission: 'stock:view' },
]

export const createItems: NavItem[] = [
  { label: 'Nova venda', to: '/sales/new', icon: ShoppingBag, permission: 'order:create' },
  { label: 'Entrada de mercadoria', to: '/stock/entry', icon: PackagePlus, permission: 'stock:adjust' },
  { label: 'Ajuste de estoque', to: '/stock/adjustment', icon: Settings2, permission: 'stock:adjust' },
  { label: 'Novo cliente', to: '/customers/new', icon: UserPlus, permission: 'customer:edit' },
  { label: 'Novo produto', to: '/catalog/new', icon: Tag, permission: 'product:edit' },
  { label: 'Lançamento financeiro', to: '/finance/new', icon: FilePlus2, permission: 'finance:edit' },
]

export const menuGroups: { title: string; items: NavItem[] }[] = [
  {
    title: 'Cadastros',
    items: [
      { label: 'Clientes', to: '/customers', icon: Users, permission: 'customer:view' },
      { label: 'Catálogo', to: '/catalog', icon: Package, permission: 'product:view', end: true },
      { label: 'Categorias', to: '/catalog/categories', icon: Layers, permission: 'product:view' },
      { label: 'Fornecedores', to: '/suppliers', icon: Truck, permission: 'stock:view' },
    ],
  },
  {
    title: 'Financeiro',
    items: [
      { label: 'Lançamentos', to: '/finance', icon: Wallet, permission: 'finance:view', end: true },
      { label: 'Fechamento', to: '/finance/closing', icon: CalendarCheck, permission: 'finance:view' },
      { label: 'Categorias financeiras', to: '/finance/categories', icon: Landmark, permission: 'finance:view' },
    ],
  },
  {
    title: 'Relatórios',
    items: [{ label: 'Relatórios', to: '/reports', icon: BarChart3, permission: 'finance:view' }],
  },
  {
    title: 'Administração',
    items: [
      { label: 'Usuários', to: '/admin/users', icon: ShieldCheck, permission: 'user:manage' },
      { label: 'Solicitações de cancelamento', to: '/admin/cancellations', icon: Ban, permission: 'order:cancel' },
      { label: 'Configurações', to: '/admin/settings', icon: Sliders, permission: 'settings:manage' },
    ],
  },
]

export function visible(items: NavItem[], can: (permission: string) => boolean): NavItem[] {
  return items.filter((item) => !item.permission || can(item.permission))
}
