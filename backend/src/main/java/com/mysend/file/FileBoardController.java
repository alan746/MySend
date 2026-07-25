package com.mysend.file;

import com.mysend.security.CookieSupport;
import com.mysend.security.DeviceIdentityService;
import com.mysend.security.OwnerIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/rooms/{accessCode}/files")
public class FileBoardController {

    private static final String ROOM_ACCESS_COOKIE = "mysend_room_access";

    private final FileBoardService service;
    private final DeviceIdentityService identities;

    public FileBoardController(
            FileBoardService service,
            DeviceIdentityService identities
    ) {
        this.service = service;
        this.identities = identities;
    }

    @GetMapping
    List<FileView> list(
            @PathVariable String accessCode,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        String roomToken = CookieSupport.read(request, ROOM_ACCESS_COOKIE).orElse(null);
        return service.list(accessCode, owner, roomToken).stream()
                .map(FileView::from)
                .toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<FileView> upload(
            @PathVariable String accessCode,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        String roomToken = CookieSupport.read(request, ROOM_ACCESS_COOKIE).orElse(null);
        RoomFile stored = service.upload(accessCode, owner, roomToken, file);
        return ResponseEntity.status(201).body(FileView.from(stored));
    }

    @GetMapping("/{fileId}")
    ResponseEntity<?> download(
            @PathVariable String accessCode,
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        String roomToken = CookieSupport.read(request, ROOM_ACCESS_COOKIE).orElse(null);
        FileBoardService.Download download = service.download(
                accessCode,
                fileId,
                owner,
                roomToken
        );
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.file().originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.file().contentType()))
                .contentLength(download.file().sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    @DeleteMapping("/{fileId}")
    ResponseEntity<Void> delete(
            @PathVariable String accessCode,
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OwnerIdentity owner = identities.resolve(request, response);
        service.delete(accessCode, fileId, owner);
        return ResponseEntity.noContent().build();
    }

    public record FileView(
            String id,
            String name,
            String contentType,
            long sizeBytes,
            java.time.Instant uploadedAt
    ) {
        static FileView from(RoomFile file) {
            return new FileView(
                    file.id(),
                    file.originalName(),
                    file.contentType(),
                    file.sizeBytes(),
                    file.uploadedAt()
            );
        }
    }
}
