package com.mysend.file;

import com.mysend.common.ApiException;
import com.mysend.room.Room;
import com.mysend.room.RoomRepository;
import com.mysend.room.RoomService;
import com.mysend.security.OwnerIdentity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileBoardService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "txt", "md", "java", "py", "c", "h", "cpp", "hpp",
            "doc", "docx", "jpg", "jpeg", "png", "gif", "webp", "zip", "json"
    );

    private final RoomService rooms;
    private final RoomRepository roomRepository;
    private final RoomFileRepository files;
    private final FileStore store;
    private final StorageDeletionService storageDeletions;
    private final Clock clock;

    public FileBoardService(
            RoomService rooms,
            RoomRepository roomRepository,
            RoomFileRepository files,
            FileStore store,
            StorageDeletionService storageDeletions,
            Clock clock
    ) {
        this.rooms = rooms;
        this.roomRepository = roomRepository;
        this.files = files;
        this.store = store;
        this.storageDeletions = storageDeletions;
        this.clock = clock;
    }

    public List<RoomFile> list(
            String accessCode,
            OwnerIdentity owner,
            String roomToken
    ) {
        Room room = rooms.getAuthorized(accessCode, owner, roomToken);
        return files.findByRoomId(room.id());
    }

    @Transactional
    public RoomFile upload(
            String accessCode,
            OwnerIdentity owner,
            String roomToken,
            MultipartFile upload
    ) {
        Room room = rooms.getAuthorized(accessCode, owner, roomToken);
        String originalName = sanitizeName(upload.getOriginalFilename());
        validateUpload(room, upload, originalName);

        if (!roomRepository.adjustFileBytes(
                room.id(),
                upload.getSize(),
                room.plan().limits().roomFileBytes()
        )) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "ROOM_FILE_LIMIT",
                    "This upload would exceed the room file limit"
            );
        }

        String id = UUID.randomUUID().toString();
        String storageKey = id + "." + extension(originalName);
        RoomFile file = new RoomFile(
                id,
                room.id(),
                storageKey,
                originalName,
                safeContentType(upload.getContentType()),
                upload.getSize(),
                clock.instant()
        );
        boolean stored = false;
        try {
            store.put(
                    storageKey,
                    upload.getInputStream(),
                    upload.getSize(),
                    file.contentType()
            );
            stored = true;
            files.insert(file);
            return file;
        } catch (IOException exception) {
            compensateFailedUpload(file);
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "FILE_STORE_FAILED",
                    "The file could not be stored"
            );
        } catch (RuntimeException exception) {
            if (stored) {
                compensateFailedUpload(file);
            }
            throw exception;
        }
    }

    public Download download(
            String accessCode,
            String fileId,
            OwnerIdentity owner,
            String roomToken
    ) {
        Room room = rooms.getAuthorized(accessCode, owner, roomToken);
        RoomFile file = files.findByIdAndRoomId(fileId, room.id())
                .orElseThrow(FileBoardService::fileNotFound);
        try {
            return new Download(file, new InputStreamResource(store.open(file.storageKey())));
        } catch (IOException exception) {
            throw fileNotFound();
        }
    }

    public void delete(
            String accessCode,
            String fileId,
            OwnerIdentity owner
    ) {
        Room room = rooms.getOwned(accessCode, owner);
        RoomFile file = files.findByIdAndRoomId(fileId, room.id())
                .orElseThrow(FileBoardService::fileNotFound);
        storageDeletions.deleteFile(room, file, clock.instant());
    }

    private void compensateFailedUpload(RoomFile file) {
        try {
            store.delete(file.storageKey());
        } catch (IOException exception) {
            storageDeletions.enqueueCompensation(
                    file.storageKey(),
                    file.sizeBytes(),
                    clock.instant()
            );
        }
    }

    private void validateUpload(Room room, MultipartFile upload, String originalName) {
        if (upload.isEmpty() || upload.getSize() <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_FILE",
                    "Choose a non-empty file"
            );
        }
        if (!ALLOWED_EXTENSIONS.contains(extension(originalName))) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "FILE_TYPE_NOT_ALLOWED",
                    "This file type is not allowed"
            );
        }
        if (upload.getSize() > room.plan().limits().singleFileBytes()) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "SINGLE_FILE_LIMIT",
                    "This file is larger than the room plan allows"
            );
        }
    }

    static String sanitizeName(String value) {
        String raw = value == null ? "upload.bin" : value;
        String normalized = raw.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String name = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        String sanitized = name.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").strip();
        if (sanitized.isBlank()) {
            return "upload.bin";
        }
        return sanitized.length() > 180 ? sanitized.substring(sanitized.length() - 180) : sanitized;
    }

    private static String extension(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? "" : name.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private static String safeContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
    }

    private static ApiException fileNotFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                "FILE_NOT_FOUND",
                "The requested file is no longer available"
        );
    }

    public record Download(RoomFile file, InputStreamResource resource) {
    }
}
