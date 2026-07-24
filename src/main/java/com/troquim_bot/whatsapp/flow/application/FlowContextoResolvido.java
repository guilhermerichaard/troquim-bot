package com.troquim_bot.whatsapp.flow.application;

/**
 * Resultado de revalidar as escolhas acumuladas: ou um contexto confiável, ou a tela de
 * erro para onde o cliente deve voltar.
 *
 * Modelado como valor (e não como exceção) porque entrada inválida do cliente é fluxo
 * normal do Flow, não condição excepcional — e porque a tela de retorno depende de QUAL
 * campo falhou, informação que uma exceção genérica perderia.
 */
public record FlowContextoResolvido(FlowContexto contexto, FlowResponse falha) {

    public static FlowContextoResolvido ok(FlowContexto contexto) {
        return new FlowContextoResolvido(contexto, null);
    }

    public static FlowContextoResolvido falhou(FlowResponse falha) {
        return new FlowContextoResolvido(null, falha);
    }

    public boolean valido() {
        return falha == null;
    }
}
