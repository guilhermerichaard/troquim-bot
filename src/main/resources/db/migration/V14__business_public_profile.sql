-- =====================================================================
-- V14 — Perfil público do negócio: nome, contato e slug exclusivo para uma futura página
-- pública. Nenhuma página, Controller público ou dado de piloto entra aqui — SOMENTE SCHEMA.
--
-- AGREGADO SEPARADO DE businesses, DE PROPÓSITO: o perfil público é o que um visitante veria,
-- e Business é a identidade INTERNA do tenant. Uma tabela só faria qualquer edição de
-- publicação mexer na mesma linha que controla o ciclo de vida do negócio.
--
-- business_id É A PRÓPRIA PK: um perfil por negócio, sem id próprio — não existe "perfil sem
-- dono" nem "dois perfis do mesmo negócio" para o modelo representar.
--
-- SLUG ÚNICO GLOBALMENTE, GARANTIDO PELO BANCO: a Application nunca decide unicidade
-- sozinha (duas configurações concorrentes do mesmo slug não podem ser resolvidas por um
-- SELECT prévio). O CHECK de minúsculas é a MESMA invariante de BusinessSlug, aplicada
-- também no banco — uma carga fora da aplicação não pode gravar "Meu-Salao" e colidir por
-- case com "meu-salao" sem que o CHECK recuse antes.
--
-- publication_status é DRAFT | PUBLISHED (string, não ordinal, para que inserir um estado
-- novo no enum não reinterprete linhas antigas) — nunca um boolean "publicado".
-- =====================================================================

CREATE TABLE business_public_profiles (
    business_id         UUID         NOT NULL,
    slug                 VARCHAR(63)  NOT NULL,
    nome_publico         VARCHAR(120) NOT NULL,
    descricao_curta      VARCHAR(300),
    telefone_publico     VARCHAR(30),
    endereco_publico     VARCHAR(255),
    publication_status   VARCHAR(20)  NOT NULL,
    criado_em            TIMESTAMP    NOT NULL,
    atualizado_em        TIMESTAMP    NOT NULL,
    CONSTRAINT pk_business_public_profiles PRIMARY KEY (business_id),
    CONSTRAINT fk_business_public_profiles_business
        FOREIGN KEY (business_id) REFERENCES businesses (id),
    CONSTRAINT uq_business_public_profiles_slug UNIQUE (slug),
    CONSTRAINT ck_business_public_profiles_slug_minusculo CHECK (slug = lower(slug)),
    CONSTRAINT ck_business_public_profiles_status
        CHECK (publication_status IN ('DRAFT', 'PUBLISHED'))
);

-- Consulta pública é sempre "slug + status = PUBLISHED" — o índice cobre exatamente essa
-- forma, sem depender de scan pela unique constraint sozinha.
CREATE INDEX idx_business_public_profiles_slug_status
    ON business_public_profiles (slug, publication_status);
