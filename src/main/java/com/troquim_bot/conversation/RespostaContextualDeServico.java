package com.troquim_bot.conversation;

import com.troquim_bot.common.valueobject.Money;

import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Respostas de recepção para perguntas frequentes fora do formulário de agendamento.
 *
 * Não altera estado, não confirma reserva e não inventa dados do estabelecimento.
 * Valor, duração e serviços vêm exclusivamente do catálogo ofertável do tenant.
 * Horários e mudanças de reserva continuam nos casos de uso canônicos.
 */
final class RespostaContextualDeServico {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private RespostaContextualDeServico() {}

    static Optional<String> responder(String mensagem, ConversationBookingGateway gateway) {
        String texto = normalizar(mensagem);
        if (texto.isBlank() || texto.matches("[0-9]+") || texto.startsWith("turbo_")
                || texto.startsWith("nav_") || texto.startsWith("waitlist_")) {
            return Optional.empty();
        }

        // Nunca reinterpretar uma ordem de agendar/cancelar/remarcar como mera FAQ.
        if (contem(texto, "quero agendar", "quero marcar", "agendar ", "marcar ",
                "remarcar", "reagendar", "cancelar", "desmarcar", "reservar")) {
            return Optional.empty();
        }

        if (contem(texto, "alerg", "irritac", "irritado", "coceira", "gestante", "gravida",
                "gravidez", "contraindic", "ferida", "infecc", "reacao", "sensibilidade")) {
            return Optional.of(
                    "Obrigada por avisar. Antes de indicar ou confirmar um procedimento, "
                    + "a profissional precisa avaliar seu caso. Não consigo garantir segurança "
                    + "por mensagem. Qual serviço você gostaria de consultar?");
        }

        if (contem(texto, "vou atrasar", "atrasada", "atrasado", "chegar atras", "minutos de atraso")) {
            return Optional.of(
                    "Obrigada por avisar! Não posso garantir a manutenção do horário sem consultar "
                    + "a agenda e a profissional. Quantos minutos de atraso você prevê?");
        }

        if (contem(texto, "quebrou uma unha", "unha quebrou", "descolou", "caiu a extensao",
                "caiu meu cilio", "caiu meu cilios", "caiu a unha", "preciso refazer tudo")) {
            if (contem(texto, "unha", "esmalte", "alongamento")) {
                return Optional.of("Vamos entender o que aconteceu antes de indicar um reparo. "
                        + "Quebrou a estrutura da unha ou foi só o esmalte?");
            }
            return Optional.of("Pode ser manutenção ou nova aplicação, dependendo de como os fios ficaram. "
                    + "Há quantos dias foi feita a aplicação e qual técnica você usou?");
        }

        boolean perguntaDePreco = contem(texto, "quanto custa", "qual preco", "qual valor",
                "valores", "precos", "tabela de preco", "quanto fica", "orcamento");
        boolean perguntaDeDuracao = contem(texto, "quanto tempo leva", "quanto tempo demora",
                "qual a duracao", "demora quanto", "leva quanto tempo");
        boolean perguntaDeServicos = contem(texto, "quais servicos", "o que voces fazem",
                "que servicos", "lista de servicos", "catalogo de servicos");

        if (!perguntaDePreco && !perguntaDeDuracao && !perguntaDeServicos) {
            if (contem(texto, "estacionamento", "acessibilidade", "endereco", "onde fica",
                    "aceitam crianca", "atendem crianca", "formas de pagamento", "aceita cartao")) {
                return Optional.of("Ainda não tenho essa informação confirmada no cadastro. "
                        + "É melhor verificar com o estabelecimento do que te passar um dado errado.");
            }
            return Optional.empty();
        }

        if (gateway == null) {
            return Optional.of("Não tenho o catálogo desse estabelecimento disponível agora. "
                    + "Qual serviço você procura? Posso te orientar sem inventar valores.");
        }
        List<ConversationBookingGateway.ServicoDetalhado> catalogo = gateway.listarServicosDetalhados();
        if (catalogo == null || catalogo.isEmpty()) {
            return Optional.of("O estabelecimento ainda não configurou serviços disponíveis neste atendimento. "
                    + "Não vou te passar preços ou durações sem confirmação.");
        }

        if (perguntaDeServicos) {
            return Optional.of("Os serviços cadastrados são: "
                    + resumirServicos(catalogo) + ". Qual deles você gostaria de conhecer?");
        }

        Optional<ConversationBookingGateway.ServicoDetalhado> encontrado = buscarServico(texto, catalogo);
        if (encontrado.isEmpty()) {
            return Optional.of("Para te informar certinho, qual serviço você quer consultar? "
                    + "Temos: " + resumirServicos(catalogo) + ".");
        }
        var servico = encontrado.get();
        if (perguntaDeDuracao) {
            if (servico.duracaoMinutos() <= 0) {
                return Optional.of("A duração de " + servico.nome()
                        + " ainda não foi informada no cadastro.");
            }
            return Optional.of(servico.nome() + " está cadastrado com duração de "
                    + servico.duracaoMinutos() + " minutos. Quer consultar horários?");
        }
        Optional<Money> preco = servico.preco();
        if (preco == null || preco.isEmpty()) {
            return Optional.of("O serviço " + servico.nome() + " está cadastrado, "
                    + "mas o valor ainda não foi informado. Prefiro confirmar antes de passar um preço.");
        }
        return Optional.of(servico.nome() + " está cadastrado por "
                + formatar(preco.get()) + ". Quer ver os horários disponíveis?");
    }

    private static String resumirServicos(List<ConversationBookingGateway.ServicoDetalhado> catalogo) {
        String names = catalogo.stream().limit(5).map(ConversationBookingGateway.ServicoDetalhado::nome)
                .reduce((a, b) -> a + ", " + b).orElse("nenhum serviço");
        return catalogo.size() > 5 ? names + " e outros" : names;
    }

    private static Optional<ConversationBookingGateway.ServicoDetalhado> buscarServico(
            String texto, List<ConversationBookingGateway.ServicoDetalhado> servicos) {
        record Match(ConversationBookingGateway.ServicoDetalhado servico, int score) {}
        List<Match> matches = servicos.stream().map(s -> {
            String name = normalizar(s.nome());
            int score = texto.contains(name) ? 100 : 0;
            for (String word : name.split("\\s+")) {
                if (word.length() >= 4 && texto.contains(word)) score += 1;
            }
            return new Match(s, score);
        }).filter(m -> m.score() > 0)
                .sorted(Comparator.comparingInt(Match::score).reversed()).toList();
        if (matches.isEmpty() || (matches.size() > 1
                && matches.get(0).score() == matches.get(1).score())) return Optional.empty();
        return Optional.of(matches.get(0).servico());
    }

    private static String formatar(Money money) {
        NumberFormat f = NumberFormat.getCurrencyInstance(PT_BR);
        f.setCurrency(Currency.getInstance(money.getCurrency()));
        return f.format(money.getAmount());
    }

    private static boolean contem(String texto, String... termos) {
        for (String termo : termos) {
            if (texto.contains(termo)) return true;
        }
        return false;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(PT_BR).trim();
    }
}
