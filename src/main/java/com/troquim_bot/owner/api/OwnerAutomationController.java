package com.troquim_bot.owner.api;

import com.troquim_bot.automation.BookingAutomationApplicationService;
import com.troquim_bot.automation.BookingAutomationPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/app/automation")
public class OwnerAutomationController {
    private final BookingAutomationApplicationService automation;

    public OwnerAutomationController(BookingAutomationApplicationService automation) {
        this.automation=automation;
    }

    @GetMapping
    public ResponseEntity<?> get(HttpServletRequest request) {
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(View.from(automation.get(owner.get().businessId())));
    }

    @PutMapping
    public ResponseEntity<?> update(HttpServletRequest request,@RequestBody Command command) {
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty()) return ResponseEntity.status(403).build();
        try{
            var saved=automation.update(owner.get().businessId(),
                    command.reminderEnabled(),
                    command.reminderHoursBefore(),
                    command.cancellationMinHours(),
                    command.upsellEnabled());
            return ResponseEntity.ok(View.from(saved));
        }catch(IllegalArgumentException invalid){
            return ResponseEntity.badRequest().body(new ErrorResponse(invalid.getMessage()));
        }
    }

    public record Command(boolean reminderEnabled,int reminderHoursBefore,
                          int cancellationMinHours,boolean upsellEnabled){}
    public record View(boolean reminderEnabled,int reminderHoursBefore,
                       int cancellationMinHours,boolean upsellEnabled){
        static View from(BookingAutomationPolicy p){
            return new View(p.reminderEnabled(),p.reminderHoursBefore(),
                    p.cancellationMinHours(),p.upsellEnabled());
        }
    }
    public record ErrorResponse(String error){}
}
