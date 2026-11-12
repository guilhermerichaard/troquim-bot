package com.troquim_bot.marketplace.api;

import com.troquim_bot.application.marketplace.EventMarketplaceService;
import com.troquim_bot.marketplace.EventRequest;
import com.troquim_bot.marketplace.EventRole;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/marketplace/opportunities")
public class PublicEventMarketplaceController {
    private final EventMarketplaceService marketplace;

    public PublicEventMarketplaceController(EventMarketplaceService marketplace){this.marketplace=marketplace;}

    @GetMapping
    public List<OpportunityView> list(){
        return marketplace.opportunities().stream().map(OpportunityView::from).toList();
    }

    @PostMapping("/{eventId}/roles/{roleId}/apply")
    public ResponseEntity<?> apply(@PathVariable UUID eventId,@PathVariable UUID roleId,@RequestBody ApplyCommand command){
        try{
            var created=marketplace.apply(eventId,roleId,command.name(),command.phone(),command.message());
            return ResponseEntity.ok(new ApplicationCreated(created.getId().toString(),created.getStatus().name()));
        }catch(RuntimeException invalid){
            return ResponseEntity.badRequest().body(new ErrorResponse(invalid.getMessage()));
        }
    }

    public record ApplyCommand(String name,String phone,String message){}
    public record ApplicationCreated(String id,String status){}
    public record ErrorResponse(String error){}
    public record RoleView(String id,String roleName,int quantity,int filledQuantity,Long payCents,String notes){
        static RoleView from(EventRole r){return new RoleView(r.getId().toString(),r.getRoleName(),r.getQuantity(),r.getFilledQuantity(),r.getPayCents(),r.getNotes());}
    }
    public record OpportunityView(String id,String title,String eventType,String city,String venue,
                                  LocalDateTime startsAt,LocalDateTime endsAt,Long budgetCents,String status,
                                  List<RoleView> roles){
        static OpportunityView from(EventMarketplaceService.EventView v){
            EventRequest e=v.event();
            return new OpportunityView(e.getId().toString(),e.getTitle(),e.getEventType(),e.getCity(),e.getVenue(),
                    e.getStartsAt(),e.getEndsAt(),e.getBudgetCents(),e.getStatus().name(),
                    v.roles().stream().map(RoleView::from).toList());
        }
    }
}
