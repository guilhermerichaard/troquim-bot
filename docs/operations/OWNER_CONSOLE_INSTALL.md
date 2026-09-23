# Owner Console — instalação

O console é um frontend Next.js separado do backend Java, mas não possui domínio ou
persistência próprios. Ele atua como BFF e usa apenas a API autenticada
`/api/v1/app/**`.

## Arquitetura

```
browser
  -> troquim-console (Next.js / BFF)
      -> /api/v1/owner/login
      -> /api/v1/app/**
          -> Java Application/Domain
          -> PostgreSQL
```

O businessId nunca vem do browser. Ele é obtido exclusivamente da sessão do owner.

## Produção na EC2

Pré-requisitos:

- backend `troquim-bot` saudável;
- rede Docker externa `troquim-internal`;
- owner provisionado;
- Nginx/Cloudflare apontando um hostname HTTPS para `127.0.0.1:3001`.

Build e subida:

```bash
cd /opt/troquim/releases/<release>
docker compose -f docker-compose.console.yml build troquim-console
docker compose -f docker-compose.console.yml up -d troquim-console
curl -fsS http://127.0.0.1:3001/login >/dev/null
```

Exemplo de bloco Nginx para um vhost já protegido por TLS:

```nginx
location / {
    proxy_pass http://127.0.0.1:3001;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Não misture este vhost com `api.troquim.app`. O console conversa com o backend
servidor-a-servidor pela rede Docker.

## Estado inicial entregue

- login owner real;
- overview real;
- agenda real;
- clientes reais;
- serviços reais;
- equipe real;
- status do WhatsApp;
- isolamento de tenant pela sessão.

Ações mutáveis (criar/editar/mover agenda, editar catálogo, equipe etc.) devem ganhar
casos de uso owner específicos no Java antes de serem habilitadas na UI. Não usar as
rotas ADMIN como atalho.

## Origem visual

A estrutura de navegação e alguns padrões visuais foram inspirados no projeto Pronto
(MIT). Ver `console/THIRD_PARTY_NOTICES.md`.
