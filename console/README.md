# Troquim Console

Painel do owner do Troquim. O frontend não possui banco próprio e não contém regra de
agenda. Toda verdade operacional vem de `/api/v1/app/**` no backend Java.

## Desenvolvimento

1. Inicie o backend Troquim em `http://localhost:8080`.
2. Copie `.env.example` para `.env.local`.
3. Execute:

```bash
npm install
npm run dev
```

4. Abra `http://localhost:3000/login`.

O login é feito no backend Java. O Next.js atua como BFF e mantém a sessão do owner em
cookie HttpOnly do console; nenhum token ADMIN é enviado ao navegador.

## Docker

```bash
docker build -t troquim-console .
docker run --rm -p 3000:3000 \
  -e TROQUIM_BACKEND_URL=http://host.docker.internal:8080 \
  troquim-console
```

## Fonte de verdade

- Agenda: Java Domain/Application
- Clientes: Java Domain/Application
- Serviços: Java Domain/Application
- Equipe: Java Domain/Application
- Autenticação/tenant: sessão owner Java
- Console: somente apresentação/BFF

Não adicionar Supabase, banco paralelo ou regra de booking ao console.
