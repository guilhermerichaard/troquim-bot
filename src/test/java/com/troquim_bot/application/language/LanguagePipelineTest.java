package com.troquim_bot.application.language;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguagePipelineTest {

    private final LanguagePipeline pipeline = new LanguagePipeline();

    @Test
    void deveConverterParaLowercase() {
        assertEquals("oi tudo bem", pipeline.process("OI TUDO BEM"));
    }

    @Test
    void deveRemoverAcentos() {
        assertEquals("ola amanha as 10h", pipeline.process("Olá, amanhã às 10h"));
    }

    @Test
    void deveExpandirAbreviacoesConhecidas() {
        assertEquals("voce tem hora hoje no whatsapp", pipeline.process("vc tem hr hj no zap?"));
    }

    @Test
    void deveReduzirCaracteresRepetidos() {
        assertEquals("oi tudo bem", pipeline.process("Ooooi, tudooooo beeem???"));
    }

    @Test
    void deveManterDicionarioPequenoAteTrintaEntradas() {
        BusinessDictionary dictionary = new BusinessDictionary();

        assertTrue(dictionary.size() <= 30);
        assertEquals("voce", dictionary.lookup("vc").orElseThrow());
        assertEquals("sobrancelha", dictionary.lookup("sobr").orElseThrow());
    }

    @Test
    void deveRejeitarDicionarioMaiorQueTrintaEntradas() {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < 31; i++) {
            entries.put("k" + i, "v" + i);
        }

        assertThrows(IllegalArgumentException.class, () -> new BusinessDictionary(entries));
    }

    @Test
    void deveTratarTextoNuloComoVazio() {
        assertEquals("", pipeline.process(null));
    }

    @Test
    void deveProcessarTextoCompletoComTodasTransformacoes() {
        assertEquals("obrigado valeu horas hora", pipeline.process("OBG!!! vlw???? hrs... hr"));
    }

    @Test
    void deveCanonicalizarAbreviaturasDeServicos() {
        assertEquals("agendar manicure unha", pipeline.process("agendr manicuri unhas"));
    }

    @Test
    void deveCanonicalizarAbreviaturasDeEstetica() {
        assertEquals("maquiagem estetica depilacao massagem cabelo",
                     pipeline.process("maquiag estetic depil massag cabelo"));
    }

    @Test
    void deveManterPalavrasNaoAbreviadas() {
        assertEquals("ola mundo", pipeline.process("ola mundo"));
    }

    @Test
    void deveTratarTextoVazioComoVazio() {
        assertEquals("", pipeline.process(""));
    }

    @Test
    void deveTratarTextoComApenasEspacosComoVazio() {
        assertEquals("", pipeline.process("   "));
    }

    @Test
    void deveNormalizarFraseCompletaDeAgendamento() {
        assertEquals("voce que agendar manicure hoje",
                     pipeline.process("vc q agendar manicuri hj"));
    }

    @Test
    void deveNormalizarFraseComAcentosERepeticao() {
        assertEquals("obrigado tudo bemm", pipeline.process("Obrigadooo tudooooo bemm"));
    }

    @Test
    void devePreservarNumeros() {
        assertEquals("as 10 horas", pipeline.process("às 10 hrs"));
    }
}
