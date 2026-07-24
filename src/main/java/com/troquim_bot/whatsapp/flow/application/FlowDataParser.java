package com.troquim_bot.whatsapp.flow.application;

import com.troquim_bot.whatsapp.flow.application.catalog.FlowCatalogProvider;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowProfessionalOption;
import com.troquim_bot.whatsapp.flow.application.catalog.FlowServiceOption;
import com.troquim_bot.whatsapp.flow.infrastructure.crypto.ConditionalOnWhatsAppFlow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Traduz o payload NÃO CONFIÁVEL das telas em valores validados.
 *
 * Ponto único de desconfiança do módulo: todo campo que vem do cliente passa por aqui.
 * Nada lança exceção de runtime por entrada malformada — o resultado é sempre
 * {@link Optional}, para que cada handler decida a mensagem de erro em vez de deixar
 * um 500 vazar para a Meta.
 *
 * CONTRATO CANÔNICO DE CAMPOS (espelhado nos payloads do Flow JSON):
 * {@code servico_id}, {@code profissional_id}, {@code data}, {@code horario},
 * {@code nome}, {@code observacoes}. Nomes únicos, em português, idênticos nos dois
 * lados — nada de {@code date} vs {@code Datas} ou mistura de idiomas.
 */
@Component
@ConditionalOnWhatsAppFlow
public class FlowDataParser {

    public static final String CAMPO_SERVICO = "servico_id";
    public static final String CAMPO_PROFISSIONAL = "profissional_id";
    public static final String CAMPO_DATA = "data";
    public static final String CAMPO_HORARIO = "horario";
    public static final String CAMPO_NOME = "nome";
    public static final String CAMPO_OBSERVACOES = "observacoes";

    private static final int NOME_MAX = 80;
    private static final int OBSERVACOES_MAX = 200;

    private final FlowCatalogProvider catalogo;

    public FlowDataParser(FlowCatalogProvider catalogo) {
        this.catalogo = catalogo;
    }

    public Optional<FlowServiceOption> servico(FlowRequest request) {
        return request.texto(CAMPO_SERVICO).flatMap(catalogo::servicoPorId);
    }

    /**
     * Resolve o profissional NO CONTEXTO do serviço. O campo é OPCIONAL no contrato:
     * ausente → o padrão do catálogo para aquele serviço ("Qualquer profissional").
     * Presente porém inválido/incompatível → vazio, e o handler devolve erro — um id
     * que não atende o serviço nunca passa em silêncio.
     */
    public Optional<FlowProfessionalOption> profissional(FlowRequest request, FlowServiceOption servico) {
        Optional<String> id = request.texto(CAMPO_PROFISSIONAL);
        if (id.isEmpty()) {
            return catalogo.profissionaisPara(servico).stream().findFirst();
        }
        return catalogo.profissionalPara(servico, id.get());
    }

    /**
     * Interpreta a data escolhida. Os ids das opções que ENVIAMOS são ISO
     * {@code yyyy-MM-dd}; epoch em milissegundos é tolerado por robustez de versão de
     * cliente. Datas no passado são rejeitadas aqui, não mais adiante.
     */
    public Optional<LocalDate> data(FlowRequest request) {
        Optional<String> bruto = request.texto(CAMPO_DATA);
        if (bruto.isEmpty()) {
            return Optional.empty();
        }
        String valor = bruto.get();

        LocalDate data;
        if (valor.chars().allMatch(Character::isDigit)) {
            try {
                data = Instant.ofEpochMilli(Long.parseLong(valor)).atZone(ZoneOffset.UTC).toLocalDate();
            } catch (NumberFormatException | ArithmeticException e) {
                return Optional.empty();
            }
        } else {
            try {
                data = LocalDate.parse(valor);
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }

        return data.isBefore(LocalDate.now()) ? Optional.empty() : Optional.of(data);
    }

    /** Interpreta o horário no formato {@code HH:mm} usado nas opções que enviamos. */
    public Optional<LocalTime> horario(FlowRequest request) {
        return request.texto(CAMPO_HORARIO).flatMap(valor -> {
            try {
                return Optional.of(LocalTime.parse(valor));
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        });
    }

    /** Nome informado, truncado a um tamanho razoável. Vazio se o cliente não digitou. */
    public Optional<String> nome(FlowRequest request) {
        return request.texto(CAMPO_NOME).map(n -> truncar(n, NOME_MAX));
    }

    public Optional<String> observacoes(FlowRequest request) {
        return request.texto(CAMPO_OBSERVACOES).map(o -> truncar(o, OBSERVACOES_MAX));
    }

    private static String truncar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
