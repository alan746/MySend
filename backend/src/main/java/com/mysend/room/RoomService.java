package com.mysend.room;

import com.mysend.common.ApiException;
import com.mysend.security.OwnerIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomRepository rooms;
    private final RoomAccessService roomAccess;
    private final AccessCodeGenerator codes;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RoomService(
            RoomRepository rooms,
            RoomAccessService roomAccess,
            AccessCodeGenerator codes,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.rooms = rooms;
        this.roomAccess = roomAccess;
        this.codes = codes;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Room create(OwnerIdentity owner, CreateRoom command) {
        Instant now = clock.instant();
        Plan.Limits limits = owner.plan().limits();
        if (rooms.countActiveByOwner(owner.ownerKey(), now) >= limits.activeRooms()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_ROOM_LIMIT",
                    "Close an active room before creating another one"
            );
        }
        if (command.lifetimeMinutes() < 5
                || command.lifetimeMinutes() > limits.maximumLifetimeMinutes()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LIFETIME",
                    "Room lifetime is outside the plan limit"
            );
        }
        if (command.accessLimit() < 1 || command.accessLimit() > limits.maximumEntries()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ACCESS_LIMIT",
                    "Room access limit is outside the plan limit"
            );
        }

        String passwordHash = command.visibility() == RoomVisibility.PRIVATE
                && command.password() != null
                && !command.password().isBlank()
                ? passwordEncoder.encode(command.password())
                : null;
        String code = codes.nextAvailable(rooms::isCodeUnavailable);
        Room room = new Room(
                UUID.randomUUID().toString(),
                code,
                owner.ownerKey(),
                owner.accountId(),
                owner.plan(),
                command.visibility(),
                passwordHash,
                command.accessLimit(),
                0,
                "",
                0,
                now,
                now.plus(Duration.ofMinutes(command.lifetimeMinutes())),
                null,
                0
        );
        rooms.insert(room);
        return room;
    }

    @Transactional
    public EnteredRoom enter(String accessCode, String password) {
        Room room = getOpen(accessCode);
        if (room.passwordHash() != null
                && (password == null || !passwordEncoder.matches(password, room.passwordHash()))) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "ROOM_PASSWORD_INCORRECT",
                    "The room password is incorrect"
            );
        }
        if (!rooms.consumeEntry(room.id(), clock.instant())) {
            throw roomClosed();
        }
        Room entered = rooms.findById(room.id()).orElseThrow(RoomService::roomNotFound);
        return new EnteredRoom(entered, roomAccess.issue(entered));
    }

    public Room getAuthorized(
            String accessCode,
            OwnerIdentity owner,
            String roomToken
    ) {
        Room room = getOpen(accessCode);
        if (!room.isOwnedBy(owner.ownerKey()) && !roomAccess.canAccess(room, roomToken)) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "ROOM_ACCESS_REQUIRED",
                    "Enter the access code before opening this room"
            );
        }
        return room;
    }

    public List<Room> listOwned(OwnerIdentity owner) {
        if (owner.accountId() == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "ACCOUNT_REQUIRED",
                    "Create a free account to view My ShareRooms"
            );
        }
        return rooms.findActiveByOwner(owner.ownerKey(), clock.instant());
    }

    public Room getOwned(String accessCode, OwnerIdentity owner) {
        return requireOwner(accessCode, owner);
    }

    @Transactional
    public Room updateClipboard(
            String accessCode,
            OwnerIdentity owner,
            String roomToken,
            String text,
            long version
    ) {
        Room room = getAuthorized(accessCode, owner, roomToken);
        if (text.length() > room.plan().limits().clipboardCharacters()) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "CLIPBOARD_LIMIT",
                    "The clipboard is larger than this room plan allows"
            );
        }
        if (!rooms.updateClipboard(room.id(), version, text)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ROOM_CHANGED",
                    "The room changed in another tab; refresh before saving again"
            );
        }
        return rooms.findById(room.id()).orElseThrow(RoomService::roomNotFound);
    }

    @Transactional
    public Room updateSettings(
            String accessCode,
            OwnerIdentity owner,
            UpdateRoom command
    ) {
        Room room = requireOwner(accessCode, owner);
        Plan.Limits limits = room.plan().limits();
        if (command.accessLimit() < room.accessCount()
                || command.accessLimit() > limits.maximumEntries()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ACCESS_LIMIT",
                    "Access limit cannot be below entries already used or above the plan limit"
            );
        }
        Instant maximumExpiry = room.createdAt()
                .plus(Duration.ofMinutes(limits.maximumLifetimeMinutes()));
        Instant expiresAt = room.createdAt()
                .plus(Duration.ofMinutes(command.lifetimeMinutes()));
        if (command.lifetimeMinutes() < 5 || expiresAt.isAfter(maximumExpiry)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LIFETIME",
                    "Room lifetime is outside the plan limit"
            );
        }
        String passwordHash = command.visibility() == RoomVisibility.PRIVATE
                ? resolvePasswordHash(room, command.password())
                : null;
        if (!rooms.updateSettings(
                room.id(),
                command.version(),
                command.visibility(),
                passwordHash,
                command.accessLimit(),
                expiresAt
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ROOM_CHANGED",
                    "The room changed in another tab; refresh before saving again"
            );
        }
        return rooms.findById(room.id()).orElseThrow(RoomService::roomNotFound);
    }

    @Transactional
    public void close(String accessCode, OwnerIdentity owner) {
        Room room = requireOwner(accessCode, owner);
        if (!rooms.close(room.id(), owner.ownerKey(), clock.instant())) {
            throw roomClosed();
        }
    }

    private Room requireOwner(String accessCode, OwnerIdentity owner) {
        Room room = getOpen(accessCode);
        if (!room.isOwnedBy(owner.ownerKey())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "ROOM_OWNER_REQUIRED",
                    "Only the room owner can change these settings"
            );
        }
        return room;
    }

    private Room getOpen(String accessCode) {
        String normalized = AccessCodeGenerator.normalize(accessCode);
        if (!AccessCodeGenerator.isValid(normalized)) {
            throw roomNotFound();
        }
        Room room = rooms.findByCode(normalized).orElseThrow(RoomService::roomNotFound);
        if (room.isClosedAt(clock.instant())) {
            throw roomClosed();
        }
        return room;
    }

    private String resolvePasswordHash(Room room, String password) {
        if (password == null || password.isBlank()) {
            return room.passwordHash();
        }
        return passwordEncoder.encode(password);
    }

    private static ApiException roomNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "ROOM_NOT_FOUND",
                "No room matches that access code"
        );
    }

    private static ApiException roomClosed() {
        return new ApiException(
                HttpStatus.GONE,
                "ROOM_CLOSED",
                "This room is closed, expired, or out of entries"
        );
    }

    public record CreateRoom(
            RoomVisibility visibility,
            String password,
            int lifetimeMinutes,
            int accessLimit
    ) {
    }

    public record UpdateRoom(
            RoomVisibility visibility,
            String password,
            int lifetimeMinutes,
            int accessLimit,
            long version
    ) {
    }

    public record EnteredRoom(Room room, String token) {
    }
}
