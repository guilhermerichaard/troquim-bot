package com.troquim_bot.owner.api;

import com.troquim_bot.automation.UpsellApplicationService;
import com.troquim_bot.service.ServiceId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/app/upsells")
public class OwnerUpsellController {
    private final UpsellApplicationService upsells;

    public OwnerUpsellController(UpsellApplicationService upsells){this.upsells=upsells;}

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request){
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty())return ResponseEntity.status(403).build();
        return ResponseEntity.ok(upsells.list(owner.get().businessId()).stream()
                .map(View::from).toList());
    }

    @PutMapping("/{baseServiceId}")
    public ResponseEntity<?> configure(HttpServletRequest request,
                                       @PathVariable String baseServiceId,
                                       @RequestBody Command command){
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty())return ResponseEntity.status(403).build();
        try{
            var view=upsells.configure(
                    owner.get().businessId(),
                    ServiceId.from(UUID.fromString(baseServiceId)),
                    ServiceId.from(UUID.fromString(command.addonServiceId())),
                    command.active());
            return ResponseEntity.ok(View.from(view));
        }catch(RuntimeException invalid){
            return ResponseEntity.badRequest().body(new ErrorResponse(invalid.getMessage()));
        }
    }

    @DeleteMapping("/{baseServiceId}")
    public ResponseEntity<?> remove(HttpServletRequest request,@PathVariable String baseServiceId){
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty())return ResponseEntity.status(403).build();
        try{
            upsells.remove(owner.get().businessId(),ServiceId.from(UUID.fromString(baseServiceId)));
            return ResponseEntity.noContent().build();
        }catch(RuntimeException invalid){
            return ResponseEntity.badRequest().body(new ErrorResponse(invalid.getMessage()));
        }
    }

    public record Command(String addonServiceId,boolean active){}
    public record View(String baseServiceId,String baseServiceName,
                       String addonServiceId,String addonServiceName,boolean active){
        static View from(UpsellApplicationService.RuleView v){
            return new View(v.baseServiceId().getValue().toString(),v.baseServiceName(),
                    v.addonServiceId().getValue().toString(),v.addonServiceName(),v.active());
        }
    }
    public record ErrorResponse(String error){}
}
