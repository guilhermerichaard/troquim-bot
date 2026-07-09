package com.troquim_bot.application.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WhatsAppContactResolver")
class WhatsAppContactResolverTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Nested
    @DisplayName("resolveContactNumber")
    class ResolveContactNumber {

        @Test
        @DisplayName("deve priorizar remoteJidAlt quando existir e terminar com @s.whatsapp.net")
        void devePriorizarRemoteJidAlt() throws Exception {
            String payload = """
                {
                    "event": "messages.upsert",
                    "data": {
                        "key": {
                            "remoteJid": "5511916698055@lid",
                            "fromMe": false,
                            "id": "abc123"
                        },
                        "remoteJidAlt": "5511916698055@s.whatsapp.net",
                        "message": {
                            "conversation": "Olá"
                        }
                    },
                    "sender": "5511916698055@s.whatsapp.net"
                }
                """;

            JsonNode root = mapper.readTree(payload);
            String numero = WhatsAppContactResolver.resolveContactNumber(root);

            assertEquals("5511916698055", numero);
        }

        @Test
        @DisplayName("deve usar remoteJid quando terminar com @s.whatsapp.net")
        void deveUsarRemoteJidValido() throws Exception {
            String payload = """
                {
                    "event": "messages.upsert",
                    "data": {
                        "key": {
                            "remoteJid": "5511916698055@s.whatsapp.net",
                            "fromMe": false,
                            "id": "abc123"
                        },
                        "message": {
                            "conversation": "Olá"
                        }
                    },
                    "sender": "5511916698055@s.whatsapp.net"
                }
                """;

            JsonNode root = mapper.readTree(payload);
            String numero = WhatsAppContactResolver.resolveContactNumber(root);

            assertEquals("5511916698055", numero);
        }

        @Test
        @DisplayName("deve usar remoteJid quando for numero puro (sem @)")
        void deveUsarRemoteJidNumeroPuro() throws Exception {
            String payload = """
                {
                    "event": "messages.upsert",
                    "data": {
                        "key": {
                            "remoteJid": "5511916698055",
                            "fromMe": false,
                            "id": "abc123"
                        },
                        "message": {
                            "conversation": "Olá"
                        }
                    },
                    "sender": "5511916698055@s.whatsapp.net"
                }
                """;

            JsonNode root = mapper.readTree(payload);
            String numero = WhatsAppContactResolver.resolveContactNumber(root);

            assertEquals("5511916698055", numero);
        }

        @Test
        @DisplayName("deve extrair digitos de remoteJid @lid quando remoteJidAlt nao existir")
        void deveExtrairDigitosDeLid() throws Exception {
            String payload = """
                {
                    "event": "messages.upsert",
                    "data": {
                        "key": {
                            "remoteJid": "5511916698055@lid",
                            "fromMe": false,
                            "id": "abc123"
                        },
                        "message": {
                            "conversation": "Olá"
                        }
                    },
                    "sender": "5511916698055@s.whatsapp.net"
                }
                """;

            JsonNode root = mapper.readTree(payload);
            String numero = WhatsAppContactResolver.resolveContactNumber(root);

            assertEquals("5511916698055", numero);
        }

        @Test
        @DisplayName("deve usar sender como fallback quando remoteJid e remoteJidAlt sao invalidos")
        void deveUsarSenderFallback() throws Exception {
            String payload = """
                {
                    "event": "messages.upsert",
                    "data": {
                        "key": {
                            "remoteJid": "invalido@lid",
                            "fromMe": false,
                            "id": "abc123"
                        },
                        "message": {
                            "conversation": "Olá"
                        }
                    },
                    "sender": "5511916698055@s.whatsapp.net"
                }
                """;

            JsonNode root = mapper.readTree(payload);
            String numero = WhatsAppContactResolver.resolveContactNumber(root);

            assertEquals("5511916698055", numero);
        }

        @Test
        @DisplayName("deve retornar null quando nenhum contato for encontrado")
        void deveRetornarNullQuandoNaoEncontrar() throws Exception {
            String payload = """
                {
                    "event": "messages.upsert",
                    "data": {
                        "key": {
                            "remoteJid": "@lid",
                            "fromMe": false,
                            "id": "abc123"
                        },
                        "message": {
                            "conversation": "Olá"
                        }
                    },
                    "sender": "@lid"
                }
                """;

            JsonNode root = mapper.readTree(payload);
            String numero = WhatsAppContactResolver.resolveContactNumber(root);

            assertNull(numero);
        }

        @Test
        @DisplayName("deve ignorar remoteJidAlt que nao termina com @s.whatsapp.net")
        void deveIgnorarRemoteJidAltInvalido() throws Exception {
            String payload = """
                {
                    "event": "messages.upsert",
                    "data": {
                        "key": {
                            "remoteJid": "5511916698055@s.whatsapp.net",
                            "fromMe": false,
                            "id": "abc123"
                        },
                        "remoteJidAlt": "5511916698055@lid",
                        "message": {
                            "conversation": "Olá"
                        }
                    },
                    "sender": "5511916698055@s.whatsapp.net"
                }
                """;

            JsonNode root = mapper.readTree(payload);
            String numero = WhatsAppContactResolver.resolveContactNumber(root);

            // Deve ignorar remoteJidAlt invalido e usar remoteJid valido
            assertEquals("5511916698055", numero);
        }
    }

    @Nested
    @DisplayName("normalizeForOutgoing")
    class NormalizeForOutgoing {

        @Test
        @DisplayName("deve remover @s.whatsapp.net")
        void deveRemoverSuffixWhatsApp() {
            assertEquals("5511916698055",
                WhatsAppContactResolver.normalizeForOutgoing("5511916698055@s.whatsapp.net"));
        }

        @Test
        @DisplayName("deve remover @lid")
        void deveRemoverSuffixLid() {
            assertEquals("5511916698055",
                WhatsAppContactResolver.normalizeForOutgoing("5511916698055@lid"));
        }

        @Test
        @DisplayName("deve manter numero puro")
        void deveManterNumeroPuro() {
            assertEquals("5511916698055",
                WhatsAppContactResolver.normalizeForOutgoing("5511916698055"));
        }

        @Test
        @DisplayName("deve remover caracteres nao numericos")
        void deveRemoverNaoNumericos() {
            assertEquals("5511916698055",
                WhatsAppContactResolver.normalizeForOutgoing("55(11)91669-8055"));
        }

        @Test
        @DisplayName("deve retornar null para entrada null")
        void deveRetornarNullParaNull() {
            assertNull(WhatsAppContactResolver.normalizeForOutgoing(null));
        }

        @Test
        @DisplayName("nunca deve retornar JID completo com @")
        void nuncaDeveRetornarJidCompleto() {
            String resultado = WhatsAppContactResolver.normalizeForOutgoing("5511916698055@s.whatsapp.net");
            assertFalse(resultado.contains("@"), "Nunca deve conter @ no resultado para envio");
        }
    }

    @Nested
    @DisplayName("extractRemoteJid")
    class ExtractRemoteJid {

        @Test
        @DisplayName("deve extrair remoteJid do payload")
        void deveExtrairRemoteJid() throws Exception {
            String payload = """
                {
                    "data": {
                        "key": {
                            "remoteJid": "5511916698055@s.whatsapp.net"
                        }
                    }
                }
                """;

            JsonNode root = mapper.readTree(payload);
            assertEquals("5511916698055@s.whatsapp.net", WhatsAppContactResolver.extractRemoteJid(root));
        }

        @Test
        @DisplayName("deve retornar null quando data.key nao existe")
        void deveRetornarNullQuandoSemKey() throws Exception {
            String payload = "{}";
            JsonNode root = mapper.readTree(payload);
            assertNull(WhatsAppContactResolver.extractRemoteJid(root));
        }
    }

    @Nested
    @DisplayName("extractRemoteJidAlt")
    class ExtractRemoteJidAlt {

        @Test
        @DisplayName("deve extrair remoteJidAlt do payload")
        void deveExtrairRemoteJidAlt() throws Exception {
            String payload = """
                {
                    "data": {
                        "remoteJidAlt": "5511916698055@s.whatsapp.net"
                    }
                }
                """;

            JsonNode root = mapper.readTree(payload);
            assertEquals("5511916698055@s.whatsapp.net", WhatsAppContactResolver.extractRemoteJidAlt(root));
        }

        @Test
        @DisplayName("deve retornar null quando remoteJidAlt nao existe")
        void deveRetornarNullQuandoNaoExiste() throws Exception {
            String payload = "{}";
            JsonNode root = mapper.readTree(payload);
            assertNull(WhatsAppContactResolver.extractRemoteJidAlt(root));
        }
    }

    @Nested
    @DisplayName("extractSender")
    class ExtractSender {

        @Test
        @DisplayName("deve extrair sender do payload")
        void deveExtrairSender() throws Exception {
            String payload = """
                {
                    "sender": "5511916698055@s.whatsapp.net"
                }
                """;

            JsonNode root = mapper.readTree(payload);
            assertEquals("5511916698055@s.whatsapp.net", WhatsAppContactResolver.extractSender(root));
        }

        @Test
        @DisplayName("deve retornar null quando sender nao existe")
        void deveRetornarNullQuandoNaoExiste() throws Exception {
            String payload = "{}";
            JsonNode root = mapper.readTree(payload);
            assertNull(WhatsAppContactResolver.extractSender(root));
        }
    }

    @Nested
    @DisplayName("extractDigits")
    class ExtractDigits {

        @Test
        @DisplayName("deve extrair apenas digitos")
        void deveExtrairDigitos() {
            assertEquals("5511916698055",
                WhatsAppContactResolver.extractDigits("55(11)91669-8055"));
        }

        @Test
        @DisplayName("deve retornar string vazia para sem digitos")
        void deveRetornarVazioParaSemDigitos() {
            assertEquals("",
                WhatsAppContactResolver.extractDigits("abc@lid"));
        }

        @Test
        @DisplayName("deve retornar null para null")
        void deveRetornarNullParaNull() {
            assertNull(WhatsAppContactResolver.extractDigits(null));
        }
    }

    @Nested
    @DisplayName("stripSuffix")
    class StripSuffix {

        @Test
        @DisplayName("deve remover sufixo apos @")
        void deveRemoverSufixo() {
            assertEquals("5511916698055",
                WhatsAppContactResolver.stripSuffix("5511916698055@s.whatsapp.net"));
        }

        @Test
        @DisplayName("deve retornar original quando nao tem @")
        void deveRetornarOriginalSemArroba() {
            assertEquals("5511916698055",
                WhatsAppContactResolver.stripSuffix("5511916698055"));
        }

        @Test
        @DisplayName("deve retornar null para null")
        void deveRetornarNullParaNull() {
            assertNull(WhatsAppContactResolver.stripSuffix(null));
        }
    }
}