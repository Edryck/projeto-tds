import { CheckCircle2, Clock3, PackageCheck, Truck, XCircle } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export type Me = {
  id: string
  name: string
  email: string
  role: string
  permissions: string[]
}

export const DEFAULT_CUSTOMER_ID = '60000000-0000-0000-0000-000000000001'

export type VariantStatus = 'SOLD_OUT' | 'LOW' | 'AVAILABLE'

export type VariantView = {
  id: string
  productId: string
  productName: string
  categoryName: string
  color: string
  size: string
  model: string
  price: number
  cost: number
  balance: number
  status: VariantStatus
}

export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
export type PaymentMethod = 'PIX' | 'CARD' | 'CASH'
export type ShippingMethod = 'POST_OFFICE' | 'IN_PERSON'

export type ItemView = {
  id: string
  variantId: string
  productName: string
  variantLabel: string
  quantity: number
  unitPrice: number
  unitCost: number
}

export type PaymentView = {
  method: PaymentMethod
  paidAt: string
}

export type ShipmentView = {
  method: ShippingMethod
  trackingCode: string | null
  shippedAt: string | null
  deliveredAt: string | null
}

export type OrderView = {
  id: string
  customerId: string
  customerName: string
  status: OrderStatus
  items: ItemView[]
  subtotal: number
  discount: number
  shippingCost: number
  total: number
  couponCode: string | null
  note: string | null
  orderedAt: string
  payment: PaymentView | null
  shipment: ShipmentView | null
}

export type Customer = {
  id: string
  name: string
  phone: string | null
  email: string | null
  note: string | null
}

export const paymentLabels: Record<PaymentMethod, string> = {
  PIX: 'Pix',
  CARD: 'Cartão',
  CASH: 'Dinheiro',
}

export const shippingLabels: Record<ShippingMethod, string> = {
  POST_OFFICE: 'Correios',
  IN_PERSON: 'Em mãos',
}

export const orderStatusMeta: Record<
  OrderStatus,
  { label: string; tone: 'success' | 'warning' | 'danger' | 'neutral' | 'info'; icon: LucideIcon }
> = {
  PENDING: { label: 'Pendente', tone: 'warning', icon: Clock3 },
  PAID: { label: 'Pago', tone: 'success', icon: CheckCircle2 },
  SHIPPED: { label: 'Enviado', tone: 'info', icon: Truck },
  DELIVERED: { label: 'Entregue', tone: 'success', icon: PackageCheck },
  CANCELLED: { label: 'Cancelado', tone: 'danger', icon: XCircle },
}

export type ProductView = {
  id: string
  categoryId: string
  categoryName: string
  name: string
  description: string | null
  variantCount: number
  minPrice: number | null
  maxPrice: number | null
  totalBalance: number
}

export type CategoryView = {
  id: string
  name: string
  productCount: number
}

export type VariantResponse = {
  id: string
  productId: string
  color: string
  size: string
  model: string | null
  price: number
  cost: number
  balance: number
  status: VariantStatus
}

export type SupplierView = {
  id: string
  name: string
  phone: string | null
  contactName: string | null
  email: string | null
  address: string | null
  note: string | null
  entryCount: number
}

export type CustomerView = {
  id: string
  name: string
  phone: string | null
  email: string | null
  note: string | null
  createdAt: string
  lastOrderAt: string | null
}

export type Address = {
  id: string
  customerId: string
  street: string
  number: string | null
  complement: string | null
  district: string | null
  city: string
  state: string
  zipCode: string | null
}

export type EntryType = 'INCOME' | 'EXPENSE'
export type AccountType = 'STORE' | 'PERSONAL'

export type Account = {
  id: string
  name: string
  type: AccountType
}

export type Entry = {
  id: string
  accountId: string
  categoryId: string
  type: EntryType
  amount: number
  paymentMethod: PaymentMethod | null
  entryDate: string
  description: string | null
  orderId: string | null
  note: string | null
}

export type EntrySummary = {
  income: number
  expense: number
  balance: number
}

export type FinancialCategoryView = {
  id: string
  name: string
  type: EntryType
  entryCount: number
  system: boolean
}

export type Profit = {
  revenue: number | string
  costOfGoods: number | string
  expenses: number | string
  profit: number | string
}

export type PaymentLine = {
  method: PaymentMethod
  total: number | string
  orderCount: number
}

export type SalesByPayment = {
  from: string
  to: string
  total: number | string
  orderCount: number
  lines: PaymentLine[]
}

export const accountLabels: Record<AccountType, string> = {
  STORE: 'Loja',
  PERSONAL: 'Pessoal',
}

export const entryTypeLabels: Record<EntryType, string> = {
  INCOME: 'Receita',
  EXPENSE: 'Despesa',
}
