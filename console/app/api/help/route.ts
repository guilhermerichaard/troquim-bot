import { NextResponse } from 'next/server'

type RequestBody={message?:string;page?:string}

const normalize=(value:string)=>value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase()

export async function POST(request:Request){
  const body=await request.json().catch(()=>({})) as RequestBody
  const message=(body.message??'').trim()
  const page=(body.page??'').trim()
  if(!message)return NextResponse.json({answer:'Me diga no que você precisa de ajuda.'},{status:400})

  const q=normalize(message)
  let answer:string

  if(q.includes('criar')&&q.includes('agendamento')||q.includes('novo agendamento')){
    answer='Toque em “Novo” no menu inferior. Escolha serviço, profissional, data, horário e cliente. O Troquim só mostra horários realmente disponíveis.'
  }else if(q.includes('reagendar')||q.includes('mudar horario')){
    answer='Abra Agenda, toque em “Reagendar” no agendamento e escolha uma nova data e horário. Se o novo horário falhar, o agendamento original é preservado.'
  }else if(q.includes('cancelar')){
    answer='Abra Agenda e toque em “Cancelar”. O cancelamento passa pelo domínio do Troquim e o horário liberado pode acionar a lista de espera.'
  }else if(q.includes('servico')){
    answer='Abra Mais → Serviços. Você pode criar, editar preço e duração, pausar e reativar. Esse catálogo é a mesma fonte usada pelo WhatsApp.'
  }else if(q.includes('profissional')||q.includes('equipe')){
    answer='Abra Mais → Equipe. Cadastre o profissional e marque quais serviços ele atende. A disponibilidade do WhatsApp respeita essas habilitações.'
  }else if(q.includes('cliente')){
    answer='Abra Clientes para cadastrar alguém ou crie o cliente direto durante um novo agendamento.'
  }else if(q.includes('whatsapp')&&(q.includes('nao')||q.includes('desconect'))){
    answer='Veja o status do canal na tela Hoje. Se estiver “NÃO_CONECTADO”, o painel continua funcionando, mas o atendimento automático não receberá novas mensagens até o canal voltar.'
  }else if(q.includes('agenda')){
    answer='A Agenda mostra a operação por dia. Você pode criar, reagendar ou cancelar sem editar o banco diretamente; todas as ações usam os mesmos casos de uso do backend.'
  }else{
    const context=page==='agenda'?'Você está na Agenda. Posso explicar criação, reagendamento, cancelamento ou horários livres.'
      :page==='servicos'?'Você está em Serviços. Posso ajudar com preço, duração, ativação e catálogo.'
      :page==='equipe'?'Você está em Equipe. Posso ajudar com profissionais e serviços habilitados.'
      :page==='clientes'?'Você está em Clientes. Posso ajudar com cadastro e uso no agendamento.'
      :'Posso ajudar com agenda, clientes, serviços, equipe e WhatsApp.'
    answer=context+' Pergunte do jeito que você falaria com alguém do suporte.'
  }

  return NextResponse.json({answer})
}
