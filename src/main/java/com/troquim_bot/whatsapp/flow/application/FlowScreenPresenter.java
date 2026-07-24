package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.whatsapp.flow.application.availability.FlowAvailabilityQuery;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowCatalogProvider;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowProfessionalOption;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowServiceOption;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.WhatsAppFlowProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Monta o {@code data} de cada tela do contrato canônico (SERVICO, AGENDA, CLIENTE,
 * CONFIRMACAO).
 *
 * Concentrado num lugar só por uma razão dura do protocolo: a Meta DESCARTA
 * silenciosamente qualquer campo de {@code data} não declarado no schema daquela tela
 * no Flow JSON. Este arquivo e {@code agendamento-salao.flow.json} são o MESMO contrato
 * em duas linguagens — revisáveis lado a lado.
 *
 * Padrão de interação (o mesmo do exemplo oficial book-appointment da Meta): dropdowns
 * com HABILITAÇÃO PROGRESSIVA — escolher o serviço habilita o profissional; escolher a
 * data habilita os horários — re-renderizando a MESMA tela via data_exchange.
 *
 * Os handlers decidem O QUE mostrar; este presenter decide COMO isso vira payload.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowScreenPresenter {

    /** Campo de erro exibido no topo de uma tela re-renderizada. */
    public static final String CAMPO_ERRO = "error_message";

    /** Booleano que controla a visibilidade da linha de erro (checagem de tipo da 7.3). */
    public static final String CAMPO_TEM_ERRO = "tem_erro";

    /** Opção inerte usada quando um data-source precisaria ficar vazio (a Meta rejeita). */
    private static final String OPCAO_INERTE_ID = "indisponivel";

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM", PT_BR);

    private final FlowCatalogProvider catalogo;
    private final FlowAvailabilityQuery disponibilidade;
    private final WhatsAppFlowProperties properties;

    public FlowScreenPresenter(FlowCatalogProvider catalogo,
                               FlowAvailabilityQuery disponibilidade,
                               WhatsAppFlowProperties properties) {
        this.catalogo = catalogo;
        this.disponibilidade = disponibilidade;
        this.properties = properties;
    }

    /**
     * Tela SERVICO — serviço + profissional opcional.
     *
     * @param profissionalHabilitado {@code false} no INIT (habilita após escolher o
     *                               serviço, via SERVICO_SELECIONADO), {@code true} na
     *                               re-renderização com serviço já escolhido
     */
    public FlowResponse servico(boolean profissionalHabilitado, String erro) {
        List<Map<String, Object>> servicos = new ArrayList<>();
        for (FlowServiceOption s : catalogo.servicos()) {
            servicos.add(opcao(s.id(), s.titulo(), s.duracaoLegivel()));
        }

        Map<String, Object> data = base(erro);
        data.put("servicos", servicos);
        data.put("profissionais", profissionaisTodos());
        data.put("profissional_habilitado", profissionalHabilitado);
        return FlowResponse.tela(FlowScreen.SERVICO, data);
    }

    /**
     * Tela AGENDA — datas elegíveis (dropdown) + horários habilitados após a data.
     *
     * BUSCAR_DATAS chega aqui sem data escolhida (horários desabilitados, com opção
     * inerte); DATA_SELECIONADA re-renderiza com os horários REAIS do dia escolhido.
     */
    public FlowResponse agenda(FlowContexto ctx, String erro) {
        LocalDate hoje = LocalDate.now();
        LocalDate limite = hoje.plusDays(Math.max(1, properties.getJanelaDias()));

        List<Map<String, Object>> datas = new ArrayList<>();
        for (LocalDate dia : disponibilidade.datasDisponiveis(hoje, limite,
                ctx.profissional().professionalId())) {
            datas.add(opcao(dia.toString(), dataPorExtenso(dia), null));
        }
        if (datas.isEmpty()) {
            // Sem nenhuma data com vaga na janela: data-source nunca vai vazio.
            datas.add(opcaoInerte("Sem datas disponíveis"));
        }

        boolean temData = ctx.data() != null;
        List<Map<String, Object>> horarios = new ArrayList<>();
        if (temData) {
            for (LocalTime h : disponibilidade.horariosLivres(ctx.data(),
                    ctx.profissional().professionalId())) {
                horarios.add(opcao(h.toString(), h.toString(), null));
            }
        }
        boolean temHorarios = !horarios.isEmpty();
        if (!temHorarios) {
            horarios.add(opcaoInerte("Escolha uma data primeiro"));
        }

        Map<String, Object> data = base(erro);
        data.putAll(selecao(ctx));
        data.put("datas", datas);
        data.put("horarios", horarios);
        data.put("horarios_habilitado", temHorarios);
        // Rótulo composto no SERVIDOR: o Flow JSON só aceita binding de valor inteiro
        // (${data.x}), nunca interpolação com texto literal. Compor aqui é apresentação,
        // não regra de negócio.
        data.put("servico_profissional",
                ctx.servico().titulo() + " · " + ctx.profissional().titulo());
        return FlowResponse.tela(FlowScreen.AGENDA, data);
    }

    /** Tela CLIENTE — nome (pré-preenchido quando conhecido) + observações opcionais. */
    public FlowResponse cliente(FlowContexto ctx, String nomePreenchido, String erro) {
        Map<String, Object> data = base(erro);
        data.putAll(selecao(ctx));
        data.put("nome_prefill", nomePreenchido == null ? "" : nomePreenchido);
        return FlowResponse.tela(FlowScreen.CLIENTE, data);
    }

    /** Tela CONFIRMACAO — resumo resolvido pelo SERVIDOR (nunca texto do cliente). */
    public FlowResponse confirmacao(FlowContexto ctx, String erro) {
        Map<String, Object> data = base(erro);
        data.putAll(selecao(ctx));
        String data_ = dataPorExtenso(ctx.data());
        String horario = ctx.horario().toString();
        data.put("resumo_servico", ctx.servico().titulo());
        data.put("resumo_profissional", ctx.profissional().titulo());
        data.put("resumo_data", data_);
        data.put("resumo_horario", horario);
        data.put("resumo_duracao", ctx.servico().duracaoLegivel());
        // Linhas compostas no SERVIDOR (binding de valor inteiro; sem interpolação no JSON).
        data.put("resumo_quando", data_ + " às " + horario);
        data.put("resumo_detalhe",
                ctx.profissional().titulo() + " · duração de " + ctx.servico().duracaoLegivel());
        return FlowResponse.tela(FlowScreen.CONFIRMACAO, data);
    }

    /**
     * Campos de escolha reenviados a cada tela. É o mecanismo que mantém o Flow
     * stateless no servidor — e a razão de tudo ser revalidado no passo seguinte.
     */
    private Map<String, Object> selecao(FlowContexto ctx) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (ctx.servico() != null) {
            data.put(FlowDataParser.CAMPO_SERVICO, ctx.servico().id());
            data.put("servico_nome", ctx.servico().titulo());
        }
        if (ctx.profissional() != null) {
            data.put(FlowDataParser.CAMPO_PROFISSIONAL, ctx.profissional().id());
            data.put("profissional_nome", ctx.profissional().titulo());
        }
        if (ctx.data() != null) {
            data.put(FlowDataParser.CAMPO_DATA, ctx.data().toString());
            data.put("data_label", dataPorExtenso(ctx.data()));
        }
        if (ctx.horario() != null) {
            data.put(FlowDataParser.CAMPO_HORARIO, ctx.horario().toString());
        }
        if (ctx.nome() != null) {
            data.put(FlowDataParser.CAMPO_NOME, ctx.nome());
        }
        if (ctx.observacao() != null) {
            data.put(FlowDataParser.CAMPO_OBSERVACOES, ctx.observacao());
        }
        return data;
    }

    private List<Map<String, Object>> profissionaisTodos() {
        List<Map<String, Object>> profissionais = new ArrayList<>();
        // MVP: a lista independe do serviço (um único profissional). A assinatura por
        // serviço vive no catálogo; aqui é só a projeção inicial da tela.
        for (FlowServiceOption s : catalogo.servicos()) {
            for (FlowProfessionalOption p : catalogo.profissionaisPara(s)) {
                if (profissionais.stream().noneMatch(x -> x.get("id").equals(p.id()))) {
                    profissionais.add(opcao(p.id(), p.titulo(), null));
                }
            }
        }
        return profissionais;
    }

    private static Map<String, Object> base(String erro) {
        Map<String, Object> data = new LinkedHashMap<>();
        // Sempre presentes: o schema declara os campos; omiti-los deixaria o erro
        // anterior visível numa tela re-renderizada.
        data.put(CAMPO_ERRO, erro == null ? "" : erro);
        data.put(CAMPO_TEM_ERRO, erro != null && !erro.isBlank());
        return data;
    }

    private static Map<String, Object> opcao(String id, String titulo, String descricao) {
        Map<String, Object> opcao = new LinkedHashMap<>();
        opcao.put("id", id);
        opcao.put("title", titulo);
        if (descricao != null) {
            opcao.put("description", descricao);
        }
        return opcao;
    }

    /** Opção desabilitada individualmente (padrão do exemplo oficial: enabled:false). */
    private static Map<String, Object> opcaoInerte(String titulo) {
        Map<String, Object> opcao = opcao(OPCAO_INERTE_ID, titulo, null);
        opcao.put("enabled", false);
        return opcao;
    }

    static String dataPorExtenso(LocalDate data) {
        String diaSemana = data.getDayOfWeek().getDisplayName(TextStyle.FULL, PT_BR);
        return Character.toUpperCase(diaSemana.charAt(0)) + diaSemana.substring(1)
                + ", " + data.format(DATA_CURTA);
    }
}
