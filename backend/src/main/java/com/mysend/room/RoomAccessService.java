package com.mysend.room;

import com.mysend.common.Hashing;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class RoomAccessService {

    private final RoomAccessTokenRepository repository;
    private final SecureRandom random;
    private final Clock clock;

    public RoomAccessService(
            RoomAccessTokenRepository repository,
            SecureRandom random,
            Clock clock
    ) {
        this.repository = repository;
        this.random = random;
        this.clock = clock;
    }

    public String issue(Room room) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        repository.insert(
                UUID.randomUUID().toString(),
                room.id(),
                Hashing.sha256(token),
                room.expiresAt(),
                now
        );
        return token;
    }

    public boolean canAccess(Room room, String token) {
        return token != null
                && repository.isValid(room.id(), Hashing.sha256(token), clock.instant());
    }
}
