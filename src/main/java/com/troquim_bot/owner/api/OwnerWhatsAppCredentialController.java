package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.OwnerOtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/app/security/whatsapp")
public class OwnerWhatsAppCredentialController {
    private final OwnerOtpService otp;
    public OwnerWhatsAppCredentialController(OwnerOtpService otp){this.otp=otp;}

    @GetMapping
    public ResponseEntity<?> get(HttpServletRequest request){
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of(
                "enabled",otp.disponivel(),
                "phone",otp.telefoneMascarado(owner.get()).orElse("")
        ));
    }

    @PostMapping("/request")
    public ResponseEntity<?> request(HttpServletRequest request,@RequestBody(required=false) Phone body){
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty()) return ResponseEntity.status(403).build();
        if(body==null || body.phone()==null || body.phone().isBlank()) return ResponseEntity.badRequest().build();
        try{
            return ResponseEntity.accepted().body(Map.of("challengeId",otp.solicitarVinculo(owner.get(),body.phone())));
        }catch(IllegalArgumentException e){
            return ResponseEntity.badRequest().body(Map.of("error","Não foi possível usar este número."));
        }catch(IllegalStateException e){
            return ResponseEntity.status(503).body(Map.of("error","WhatsApp OTP indisponível no momento."));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(HttpServletRequest request,@RequestBody(required=false) Verify body){
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty()) return ResponseEntity.status(403).build();
        if(body==null || body.challengeId()==null || body.code()==null) return ResponseEntity.badRequest().build();
        return otp.verificarVinculo(owner.get(),body.challengeId(),body.code())
                ? ResponseEntity.ok(Map.of("ok",true))
                : ResponseEntity.status(401).body(Map.of("error","Código inválido ou expirado."));
    }

    public record Phone(String phone){}
    public record Verify(UUID challengeId,String code){}
}
