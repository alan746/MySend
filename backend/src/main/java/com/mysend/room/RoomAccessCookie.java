package com.mysend.room;

import com.mysend.security.CookieSupport;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.Optional;

public final class RoomAccessCookie {

    private static final String PREFIX = "mysend_room_access_";

    private RoomAccessCookie() {
    }

    public static String name(String accessCode) {
        String normalized = AccessCodeGenerator.normalize(accessCode);
        if (!AccessCodeGenerator.isValid(normalized)) {
            return PREFIX + "invalid";
        }
        return PREFIX + normalized.toLowerCase(Locale.ROOT);
    }

    public static Optional<String> read(
            HttpServletRequest request,
            String accessCode
    ) {
        return CookieSupport.read(request, name(accessCode));
    }
}
