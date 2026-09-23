import Link from 'next/link'

export default function MaisPage(){
  return <div className="page">
    <div className="eyebrow">Gestão</div>
    <h1 className="title">Mais</h1>
    <p className="subtitle">Configure o negócio sem precisar de computador.</p>
    <div className="mobileMenuCards">
      <Link className="menuCard" href="/servicos"><strong>Serviços</strong><span>Preço, duração e disponibilidade</span></Link>
      <Link className="menuCard" href="/equipe"><strong>Equipe</strong><span>Profissionais e serviços habilitados</span></Link>
    </div>
  </div>
}
