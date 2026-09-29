package com.troquim_bot.owner.application;

import com.troquim_bot.owner.domain.OwnerPhoneCredential;
import com.troquim_bot.owner.domain.OwnerUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class OwnerOtpService {
    private final OwnerPhoneCredentialRepository credentials;
    private final OwnerOtpChallengeStore challenges;
    private final OwnerUserRepository owners;
    private final OwnerAuthService auth;
    private final Optional<OwnerAuthenticationCodeGateway> gateway;
    private final OwnerOtpProperties properties;
    private final SecureRandom random = new SecureRandom();

    public OwnerOtpService(OwnerPhoneCredentialRepository credentials,
                           OwnerOtpChallengeStore challenges,
                           OwnerUserRepository owners,
                           OwnerAuthService auth,
                           Optional<OwnerAuthenticationCodeGateway> gateway,
                           OwnerOtpProperties properties) {
        this.credentials = credentials;
        this.challenges = challenges;
        this.owners = owners;
        this.auth = auth;
        this.gateway = gateway;
        this.properties = properties;
    }

    public boolean disponivel() {
        return properties.configured() && gateway.isPresent();
    }

    @Transactional
    public UUID solicitarLogin(String phoneRaw) {
        String phone = normalizePhone(phoneRaw);
        UUID publicChallenge = UUID.randomUUID();
        String code = novoCodigo();
        String phoneHash = mac("phone:" + phone);
        Optional<OwnerPhoneCredential> credential = credentials.buscarPorTelefone(phone);
        if (!disponivel() || credential.isEmpty()) {
            mac(publicChallenge + ":" + code);
            return publicChallenge;
        }
        OwnerPhoneCredential c = credential.get();
        Optional<OwnerUser> owner = owners.buscarPorId(c.ownerId())
                .filter(OwnerUser::podeAutenticar)
                .filter(o -> o.pertenceAoTenant(c.businessId()));
        if (owner.isEmpty()) return publicChallenge;
        if (challenges.contarDesde(phoneHash, LocalDateTime.now().minusMinutes(10))
                >= properties.getMaxRequestsPer10Minutes()) return publicChallenge;

        LocalDateTime now = LocalDateTime.now();
        challenges.invalidarAtivos(c.ownerId(), c.businessId(), OwnerOtpPurpose.LOGIN, now);
        OwnerOtpChallenge challenge = new OwnerOtpChallenge(publicChallenge, c.ownerId(), c.businessId(),
                phone, phoneHash, mac(publicChallenge + ":" + code), OwnerOtpPurpose.LOGIN,
                now, now.plusMinutes(properties.getTtlMinutes()), properties.getMaxAttempts(), null);
        challenges.salvar(challenge);
        if (!gateway.orElseThrow().enviar(phone, code)) {
            challenges.salvar(challenge.consumido(now));
        }
        return publicChallenge;
    }

    @Transactional
    public Optional<String> verificarLogin(UUID challengeId, String code) {
        if (!disponivel() || challengeId == null || code == null || !code.matches("[0-9]{6}")) return Optional.empty();
        OwnerOtpChallenge challenge = challenges.buscar(challengeId).orElse(null);
        if (challenge == null || challenge.purpose() != OwnerOtpPurpose.LOGIN || !challenge.utilizavel(LocalDateTime.now())) {
            mac(challengeId + ":" + code);
            return Optional.empty();
        }
        if (!secureEquals(challenge.codeMac(), mac(challengeId + ":" + code))) {
            challenges.salvar(challenge.falhou());
            return Optional.empty();
        }
        Optional<OwnerUser> owner = owners.buscarPorId(challenge.ownerId())
                .filter(OwnerUser::podeAutenticar)
                .filter(o -> o.pertenceAoTenant(challenge.businessId()));
        if (owner.isEmpty()) return Optional.empty();
        challenges.salvar(challenge.consumido(LocalDateTime.now()));
        return auth.emitirSessao(owner.get());
    }

    @Transactional
    public UUID solicitarVinculo(AuthenticatedOwner identity, String phoneRaw) {
        if (!disponivel()) throw new IllegalStateException("WhatsApp OTP indisponível");
        OwnerUser owner = owners.buscarPorId(identity.ownerId())
                .filter(OwnerUser::podeAutenticar)
                .filter(o -> o.pertenceAoTenant(identity.businessId()))
                .orElseThrow(() -> new IllegalArgumentException("owner inválido"));
        String phone = normalizePhone(phoneRaw);
        Optional<OwnerPhoneCredential> existing = credentials.buscarPorTelefone(phone);
        if (existing.isPresent() && !existing.get().ownerId().equals(owner.getId())) {
            throw new IllegalArgumentException("telefone indisponível");
        }
        UUID id = UUID.randomUUID();
        String code = novoCodigo();
        String phoneHash = mac("phone:" + phone);
        if (challenges.contarDesde(phoneHash, LocalDateTime.now().minusMinutes(10))
                >= properties.getMaxRequestsPer10Minutes()) {
            throw new IllegalStateException("limite temporário");
        }
        LocalDateTime now = LocalDateTime.now();
        challenges.invalidarAtivos(owner.getId(), owner.getBusinessId(), OwnerOtpPurpose.ENROLLMENT, now);
        OwnerOtpChallenge challenge = new OwnerOtpChallenge(id, owner.getId(), owner.getBusinessId(),
                phone, phoneHash, mac(id + ":" + code), OwnerOtpPurpose.ENROLLMENT,
                now, now.plusMinutes(properties.getTtlMinutes()), properties.getMaxAttempts(), null);
        challenges.salvar(challenge);
        if (!gateway.orElseThrow().enviar(phone, code)) {
            challenges.salvar(challenge.consumido(now));
            throw new IllegalStateException("falha no envio");
        }
        return id;
    }

    @Transactional
    public boolean verificarVinculo(AuthenticatedOwner identity, UUID challengeId, String code) {
        if (!disponivel() || challengeId == null || code == null || !code.matches("[0-9]{6}")) return false;
        OwnerOtpChallenge challenge = challenges.buscar(challengeId).orElse(null);
        if (challenge == null || challenge.purpose() != OwnerOtpPurpose.ENROLLMENT
                || !challenge.ownerId().equals(identity.ownerId())
                || !challenge.businessId().equals(identity.businessId())
                || !challenge.utilizavel(LocalDateTime.now())) return false;
        if (!secureEquals(challenge.codeMac(), mac(challengeId + ":" + code))) {
            challenges.salvar(challenge.falhou());
            return false;
        }
        credentials.salvar(new OwnerPhoneCredential(identity.ownerId(), identity.businessId(),
                challenge.phoneE164(), LocalDateTime.now()));
        challenges.salvar(challenge.consumido(LocalDateTime.now()));
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<String> telefoneMascarado(AuthenticatedOwner identity) {
        return credentials.buscarPorDono(identity.ownerId(), identity.businessId()).map(OwnerPhoneCredential::masked);
    }

    private String novoCodigo() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    static String normalizePhone(String raw) {
        if (raw == null) throw new IllegalArgumentException("telefone obrigatório");
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 8 || digits.length() > 15 || digits.startsWith("0")) {
            throw new IllegalArgumentException("telefone inválido");
        }
        return digits;
    }

    private String mac(String value) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(properties.getPepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(hmac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }

    private static boolean secureEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
