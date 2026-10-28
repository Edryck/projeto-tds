import { SearchX } from 'lucide-react'
import { EmptyState, LinkButton } from '../components/ui'

export default function NotFound() {
  return (
    <EmptyState
      icon={SearchX}
      title="Página não encontrada"
      message="O endereço não existe ou foi movido."
      action={<LinkButton to="/">Voltar ao início</LinkButton>}
    />
  )
}
