import { Lock } from 'lucide-react'
import { EmptyState, LinkButton } from '../components/ui'

export default function Forbidden() {
  return (
    <EmptyState
      icon={Lock}
      title="Você não tem acesso a esta área"
      message="Fale com quem administra a loja se precisar dela."
      action={<LinkButton to="/">Voltar ao início</LinkButton>}
    />
  )
}
