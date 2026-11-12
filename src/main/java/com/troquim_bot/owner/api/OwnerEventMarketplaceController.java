package com.troquim_bot.owner.api;

import com.troquim_bot.application.marketplace.EventMarketplaceService;
import com.troquim_bot.marketplace.EventApplication;
import com.troquim_bot.marketplace.EventRequest;
import com.troquim_bot.marketplace.EventRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/app/events")
public class OwnerEventMarketplaceController {
    private final EventMarketplaceService marketplace;

    public OwnerEventMarketplaceController(EventMarketplaceService marketplace) { this.marketplace=marketplace; }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty()) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(marketplace.listOwner(owner.get().businessId()).stream().map(EventView::fromOwner).toList());
    }

    @PostMapping
    public ResponseEntity<?> create(HttpServletRequest request,@RequestBody CreateEventCommand command) {
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty()) return ResponseEntity.status(403).build();
        try {
            EventRequest event=marketplace.createEvent(owner.get().businessId(),command.title(),command.eventType(),command.city(),
                    command.venue(),command.startsAt(),command.endsAt(),command.budgetCents(),
                    command.roles().stream().map(r->new EventMarketplaceService.RoleCommand(r.roleName(),r.quantity(),r.payCents(),r.notes())).toList());
            return ResponseEntity.ok(new CreatedEvent(event.getId().toString(),event.getStatus().name()));
        } catch(RuntimeException invalid) {
            return ResponseEntity.badRequest().body(new ErrorResponse(invalid.getMessage()));
        }
    }

    @PostMapping("/{eventId}/applications/{applicationId}/accept")
    public ResponseEntity<?> accept(HttpServletRequest request,@PathVariable UUID eventId,@PathVariable UUID applicationId) {
        var owner=OwnerSessionCookieFilter.identidadeDe(request);
        if(owner.isEmpty()) return ResponseEntity.status(403).build();
        try {
            var accepted=marketplace.accept(owner.get().businessId(),eventId,applicationId);
            return ResponseEntity.ok(ApplicationView.from(accepted));
        } catch(RuntimeException invalid) {
            return ResponseEntity.badRequest().body(new ErrorResponse(invalid.getMessage()));
        }
    }

    public record CreateEventCommand(String title,String eventType,String city,String venue,
                                     LocalDateTime startsAt,LocalDateTime endsAt,Long budgetCents,
                                     List<RoleCommand> roles) {}
    public record RoleCommand(String roleName,int quantity,Long payCents,String notes) {}
    public record CreatedEvent(String id,String status) {}
    public record ErrorResponse(String error) {}
    public record RoleView(String id,String roleName,int quantity,int filledQuantity,Long payCents,String notes) {
        static RoleView from(EventRole r){return new RoleView(r.getId().toString(),r.getRoleName(),r.getQuantity(),r.getFilledQuantity(),r.getPayCents(),r.getNotes());}
    }
    public record ApplicationView(String id,String roleId,String applicantName,String applicantPhone,String message,String status) {
        static ApplicationView from(EventApplication a){return new ApplicationView(a.getId().toString(),a.getRoleId().toString(),a.getApplicantName(),a.getApplicantPhone(),a.getMessage(),a.getStatus().name());}
    }
    public record EventView(String id,String title,String eventType,String city,String venue,
                            LocalDateTime startsAt,LocalDateTime endsAt,Long budgetCents,String status,
                            List<RoleView> roles,List<ApplicationView> applications) {
        static EventView fromOwner(EventMarketplaceService.EventView v) {
            EventRequest e=v.event();
            return new EventView(e.getId().toString(),e.getTitle(),e.getEventType(),e.getCity(),e.getVenue(),
                    e.getStartsAt(),e.getEndsAt(),e.getBudgetCents(),e.getStatus().name(),
                    v.roles().stream().map(RoleView::from).toList(),v.applications().stream().map(ApplicationView::from).toList());
        }
    }
}
