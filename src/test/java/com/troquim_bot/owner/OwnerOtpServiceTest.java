package com.troquim_bot.owner;

import com.troquim_bot.business.BusinessId;
import com.troquim_bot.owner.application.*;
import com.troquim_bot.owner.domain.*;
import com.troquim_bot.owner.infrastructure.BCryptPasswordHasher;
import com.troquim_bot.owner.support.InMemoryOwnerSessionStore;
import com.troquim_bot.owner.support.InMemoryOwnerUserRepository;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class OwnerOtpServiceTest {

    private InMemoryOwnerUserRepository users;
    private InMemoryOwnerSessionStore sessions;
    private MemoryPhones phones;
    private MemoryChallenges challenges;
    private CapturingGateway gateway;
    private OwnerOtpService otp;
    private OwnerAuthService auth;
    private OwnerUser owner;

    @BeforeEach
    void setup() {
        users = new InMemoryOwnerUserRepository();
        sessions = new InMemoryOwnerSessionStore();
        phones = new MemoryPhones();
        challenges = new MemoryChallenges();
        gateway = new CapturingGateway();
        var hasher = new BCryptPasswordHasher();
        owner = OwnerUser.novo(TestTenants.PILOT, "owner@troquim.test", hasher.hash("senha-segura"));
        users.salvar(owner);
        auth = new OwnerAuthService(users, sessions, hasher);

        var props = new OwnerOtpProperties();
        props.setEnabled(true);
        props.setPepper("0123456789abcdef0123456789abcdef");
        props.setTemplateName("troquim_owner_auth");
        props.setTemplateLanguage("pt_BR");
        props.setTtlMinutes(5);
        props.setMaxAttempts(3);
        props.setMaxRequestsPer10Minutes(3);

        otp = new OwnerOtpService(phones, challenges, users, auth, Optional.of(gateway), props);
    }

    @Test
    void vinculaTelefoneVerificadoEUsaOMesmoSistemaDeSessaoNoLogin() {
        var identity = new AuthenticatedOwner(owner.getId(), owner.getBusinessId());
        UUID enrollment = otp.solicitarVinculo(identity, "+55 (11) 99999-1111");
        String enrollmentCode = gateway.lastCode;
        assertTrue(otp.verificarVinculo(identity, enrollment, enrollmentCode));
        assertEquals("•••• 1111", otp.telefoneMascarado(identity).orElseThrow());

        UUID login = otp.solicitarLogin("5511999991111");
        String loginCode = gateway.lastCode;
        String token = otp.verificarLogin(login, loginCode).orElseThrow();

        assertEquals(identity, auth.resolver(token).orElseThrow());
        assertTrue(otp.verificarLogin(login, loginCode).isEmpty(), "OTP consumido não pode ser reutilizado");
    }

    @Test
    void codigoErradoConsomeTentativas() {
        var identity = new AuthenticatedOwner(owner.getId(), owner.getBusinessId());
        UUID enrollment = otp.solicitarVinculo(identity, "5511999991111");
        assertFalse(otp.verificarVinculo(identity, enrollment, "000000"));
        assertFalse(otp.verificarVinculo(identity, enrollment, "000001"));
        assertFalse(otp.verificarVinculo(identity, enrollment, "000002"));
        assertFalse(otp.verificarVinculo(identity, enrollment, gateway.lastCode));
    }

    @Test
    void numeroDesconhecidoTemRespostaGenericaMasNaoDisparaMensagem() {
        int before = gateway.sent;
        UUID challenge = otp.solicitarLogin("5511888887777");
        assertNotNull(challenge);
        assertEquals(before, gateway.sent);
        assertTrue(otp.verificarLogin(challenge, "123456").isEmpty());
    }

    @Test
    void telefoneNaoPodeSerTomadoPorOutroOwner() {
        var a = new AuthenticatedOwner(owner.getId(), owner.getBusinessId());
        UUID first = otp.solicitarVinculo(a, "5511999991111");
        assertTrue(otp.verificarVinculo(a, first, gateway.lastCode));

        var hasher = new BCryptPasswordHasher();
        OwnerUser other = OwnerUser.novo(TestTenants.OUTRO, "other@troquim.test", hasher.hash("outra-senha"));
        users.salvar(other);
        var b = new AuthenticatedOwner(other.getId(), other.getBusinessId());

        assertThrows(IllegalArgumentException.class, () -> otp.solicitarVinculo(b, "5511999991111"));
    }

    private static final class CapturingGateway implements OwnerAuthenticationCodeGateway {
        String lastCode;
        int sent;
        public boolean enviar(String phoneE164, String code) {
            lastCode = code;
            sent++;
            return true;
        }
    }

    private static final class MemoryPhones implements OwnerPhoneCredentialRepository {
        private final Map<String,OwnerPhoneCredential> byPhone = new ConcurrentHashMap<>();
        private final Map<OwnerUserId,OwnerPhoneCredential> byOwner = new ConcurrentHashMap<>();
        public Optional<OwnerPhoneCredential> buscarPorTelefone(String phoneE164){ return Optional.ofNullable(byPhone.get(phoneE164)); }
        public Optional<OwnerPhoneCredential> buscarPorDono(OwnerUserId ownerId, BusinessId businessId){
            return Optional.ofNullable(byOwner.get(ownerId)).filter(c -> c.businessId().equals(businessId));
        }
        public void salvar(OwnerPhoneCredential credential){
            OwnerPhoneCredential existing = byPhone.get(credential.phoneE164());
            if(existing != null && !existing.ownerId().equals(credential.ownerId())) throw new IllegalStateException("telefone duplicado");
            OwnerPhoneCredential old = byOwner.put(credential.ownerId(), credential);
            if(old != null) byPhone.remove(old.phoneE164());
            byPhone.put(credential.phoneE164(), credential);
        }
    }

    private static final class MemoryChallenges implements OwnerOtpChallengeStore {
        private final Map<UUID,OwnerOtpChallenge> values = new ConcurrentHashMap<>();
        public void salvar(OwnerOtpChallenge challenge){ values.put(challenge.id(), challenge); }
        public Optional<OwnerOtpChallenge> buscar(UUID id){ return Optional.ofNullable(values.get(id)); }
        public long contarDesde(String phoneHash, LocalDateTime since){
            return values.values().stream().filter(c -> c.phoneHash().equals(phoneHash) && c.createdAt().isAfter(since)).count();
        }
        public void invalidarAtivos(OwnerUserId ownerId, BusinessId businessId, OwnerOtpPurpose purpose, LocalDateTime now){
            values.replaceAll((id,c) -> c.ownerId().equals(ownerId) && c.businessId().equals(businessId)
                    && c.purpose()==purpose && c.utilizavel(now) ? c.consumido(now) : c);
        }
    }
}
