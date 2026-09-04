package com.mysend.room;

import com.mysend.config.AppProperties;
import com.mysend.security.CookieSupport;
import com.mysend.security.DeviceIdentityService;
import com.mysend.security.OwnerIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService service;
    private final DeviceIdentityService identities;
    private final AppProperties properties;

    public RoomController(
            RoomService service,
            DeviceIdentityService identities,
            AppProperties properties
    ) {
        this.service = service;
        this.identities = identities;
        this.properties = properties;
    }

    @PostMapping
    ResponseEntity<RoomView> create(
            @Valid @RequestBody CreateRoomRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        OwnerIdentity owner = identities.resolve(servletRequest, servletResponse);
        Room room = service.create(owner, new RoomService.CreateRoom(
                request.visibility(),
                request.password(),
                request.lifetimeMinutes(),
                request.accessLimit()
        ));
        return ResponseEntity.status(201).body(RoomView.from(room, true));
    }

    @PostMapping("/enter")
    ResponseEntity<RoomView> enter(
            @Valid @RequestBody EnterRoomRequest request,
            HttpServletResponse response
    ) {
        RoomService.EnteredRoom entered = service.enter(request.accessCode(), request.password());
        Duration duration = Duration.between(Instant.now(), entered.room().expiresAt());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                CookieSupport.httpOnly(
                        RoomAccessCookie.name(entered.room().accessCode()),
                        entered.token(),
                        duration.isNegative() ? Duration.ZERO : duration,
                        properties.cookieSecure()
                ).toString()
        );
        return ResponseEntity.ok(RoomView.from(entered.room(), false));
    }

    @GetMapping
    List<RoomView> list(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        return service.listOwned(owner).stream()
                .map(room -> RoomView.from(room, true))
                .toList();
    }

    @GetMapping("/{accessCode}")
    RoomView get(
            @PathVariable String accessCode,
            @RequestHeader(name = "X-Room-Token", required = false) String roomToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        String token = roomToken != null
                ? roomToken
                : RoomAccessCookie.read(request, accessCode).orElse(null);
        Room room = service.getAuthorized(accessCode, owner, token);
        return RoomView.from(room, room.isOwnedBy(owner.ownerKey()));
    }

    @GetMapping("/{accessCode}/revision")
    RoomService.RoomRevision getRevision(
            @PathVariable String accessCode,
            @RequestHeader(name = "X-Room-Token", required = false) String roomToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        String token = roomToken != null
                ? roomToken
                : RoomAccessCookie.read(request, accessCode).orElse(null);
        return service.getRevision(accessCode, owner, token);
    }

    @PatchMapping("/{accessCode}/clipboard")
    RoomView updateClipboard(
            @PathVariable String accessCode,
            @Valid @RequestBody ClipboardRequest clipboard,
            @RequestHeader(name = "X-Room-Token", required = false) String roomToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        String token = roomToken != null
                ? roomToken
                : RoomAccessCookie.read(request, accessCode).orElse(null);
        Room room = service.updateClipboard(
                accessCode,
                owner,
                token,
                clipboard.text(),
                clipboard.version()
        );
        return RoomView.from(room, room.isOwnedBy(owner.ownerKey()));
    }

    @PatchMapping("/{accessCode}/settings")
    RoomView updateSettings(
            @PathVariable String accessCode,
            @Valid @RequestBody UpdateRoomRequest update,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        Room room = service.updateSettings(accessCode, owner, new RoomService.UpdateRoom(
                update.visibility(),
                update.password(),
                update.lifetimeMinutes(),
                update.accessLimit(),
                update.version()
        ));
        return RoomView.from(room, true);
    }

    @DeleteMapping("/{accessCode}")
    ResponseEntity<Void> close(
            @PathVariable String accessCode,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        service.close(accessCode, owner);
        return ResponseEntity.noContent().build();
    }

    record CreateRoomRequest(
            @NotNull RoomVisibility visibility,
            @Size(max = 100) String password,
            @Min(5) @Max(180) int lifetimeMinutes,
            @Min(1) @Max(1_000) int accessLimit
    ) {
    }

    record EnterRoomRequest(
            @Pattern(regexp = "(?i)\\d{4}[a-z]") String accessCode,
            @Size(max = 100) String password
    ) {
    }

    record ClipboardRequest(
            @NotNull @Size(max = 100_000) String text,
            @Min(0) long version
    ) {
    }

    record UpdateRoomRequest(
            @NotNull RoomVisibility visibility,
            @Size(max = 100) String password,
            @Min(5) @Max(180) int lifetimeMinutes,
            @Min(1) @Max(1_000) int accessLimit,
            @Min(0) long version
    ) {
    }

    public record RoomView(
            String id,
            String accessCode,
            String plan,
            String visibility,
            boolean passwordProtected,
            int accessLimit,
            int accessCount,
            int remainingEntries,
            String clipboardText,
            long fileBytes,
            long fileLimitBytes,
            int clipboardLimit,
            Instant createdAt,
            Instant expiresAt,
            boolean owner,
            long version
    ) {
        static RoomView from(Room room, boolean owner) {
            return new RoomView(
                    room.id(),
                    room.accessCode(),
                    room.plan().name(),
                    room.visibility().name(),
                    room.passwordHash() != null,
                    room.accessLimit(),
                    room.accessCount(),
                    room.remainingEntries(),
                    room.clipboardText(),
                    room.fileBytes(),
                    room.plan().limits().roomFileBytes(),
                    room.plan().limits().clipboardCharacters(),
                    room.createdAt(),
                    room.expiresAt(),
                    owner,
                    room.version()
            );
        }
    }
}
