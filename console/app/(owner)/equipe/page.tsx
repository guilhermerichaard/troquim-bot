import { troquimFetch } from '@/lib/troquim'
type Professional={id:string;name:string;phone:string;specialties:string[];enabledServiceIds:string[];status:string}
export default async function EquipePage(){
 const items=await troquimFetch<Professional[]>('/professionals')??[]
 return <div className="page"><div className="eyebrow">Operação</div><h1 className="title">Equipe</h1><p className="subtitle">Profissionais e habilitações do negócio.</p>
 {items.length?<table className="table"><thead><tr><th>Profissional</th><th>Telefone</th><th>Especialidades</th><th>Serviços habilitados</th><th>Status</th></tr></thead><tbody>{items.map(x=><tr key={x.id}><td><strong>{x.name}</strong></td><td>{x.phone}</td><td>{x.specialties.join(', ')||'—'}</td><td>{x.enabledServiceIds.length}</td><td>{x.status}</td></tr>)}</tbody></table>:<div className="card empty">Nenhum profissional cadastrado.</div>}</div>
}
