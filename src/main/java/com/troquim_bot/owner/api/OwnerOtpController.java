package com.troquim_bot.owner.api;

import com.troquim_bot.owner.application.OwnerOtpService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/owner/otp")
public class OwnerOtpController {
    private final OwnerOtpService otp;
    public OwnerOtpController(OwnerOtpService otp){this.otp=otp;}

    @GetMapping("/status")
    public Map<String,Boolean> status(){return Map.of("enabled",otp.disponivel());}

    @PostMapping("/request")
    public ResponseEntity<?> request(@RequestBody(required=false) Request body){
        if(body==null || body.phone()==null || body.phone().isBlank()) return ResponseEntity.badRequest().build();
        try {
            UUID challengeId=otp.solicitarLogin(body.phone());
            return ResponseEntity.accepted().body(Map.of("challengeId",challengeId));
        } catch(IllegalArgumentException e){
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody(required=false) Verify body){
        if(body==null || body.challengeId()==null || body.code()==null) return ResponseEntity.badRequest().build();
        return otp.verificarLogin(body.challengeId(),body.code())
                .map(token->ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE,OwnerSessionCookie.create(token,OwnerSessionCookie.MAX_AGE_SECONDS).toString())
                        .body(Map.of("ok",true)))
                .orElseGet(()->ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Código inválido ou expirado.")));
    }

    public record Request(String phone){}
    public record Verify(UUID challengeId,String code){}
}
