import { troquimFetch } from '@/lib/troquim'
type Customer={id:string;name:string;phone:string;notes:string|null;status:string}
export default async function ClientesPage(){
  const items=await troquimFetch<Customer[]>('/customers')??[]
  return <div className="page"><div className="eyebrow">CRM</div><h1 className="title">Clientes</h1><p className="subtitle">Base real do tenant autenticado.</p>
  {items.length?<table className="table"><thead><tr><th>Nome</th><th>Telefone</th><th>Observações</th><th>Status</th></tr></thead><tbody>{items.map(x=><tr key={x.id}><td>{x.name}</td><td>{x.phone}</td><td>{x.notes||'—'}</td><td>{x.status}</td></tr>)}</tbody></table>:<div className="card empty">Nenhum cliente cadastrado.</div>}</div>
}
