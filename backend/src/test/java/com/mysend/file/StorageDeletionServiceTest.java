package com.mysend.file;

import com.mysend.room.Plan;
import com.mysend.room.Room;
import com.mysend.room.RoomRepository;
import com.mysend.room.RoomVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageDeletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private RoomRepository rooms;
    private RoomFileRepository files;
    private StoredObjectDeletionRepository deletions;
    private StorageDeletionService service;

    @BeforeEach
    void setUp() {
        rooms = mock(RoomRepository.class);
        files = mock(RoomFileRepository.class);
        deletions = mock(StoredObjectDeletionRepository.class);
        service = new StorageDeletionService(rooms, files, deletions);
    }

    @Test
    void fileDeletionCommitsMetadataAndQueuesObjectRemovalTogether() {
        Room room = room();
        RoomFile file = file(room.id());
        when(files.delete(file.id(), room.id())).thenReturn(true);
        when(rooms.adjustFileBytes(
                room.id(),
                -file.sizeBytes(),
                room.plan().limits().roomFileBytes()
        )).thenReturn(true);

        service.deleteFile(room, file, NOW);

        verify(deletions).enqueue(file.storageKey(), file.sizeBytes(), NOW);
    }

    @Test
    void roomPurgeQueuesEveryObjectOnlyAfterTheRoomWasDeleted() {
        Room room = room();
        RoomFile file = file(room.id());
        Instant cutoff = NOW.minusSeconds(60);
        when(files.findByRoomId(room.id())).thenReturn(List.of(file));
        when(rooms.deleteByIdIfClosedBefore(room.id(), cutoff)).thenReturn(true);

        service.purgeRoom(room, cutoff, NOW);

        verify(deletions).enqueue(file.storageKey(), file.sizeBytes(), NOW);
    }

    @Test
    void roomPurgeDoesNotQueueObjectsWhenTheRoomBecameIneligible() {
        Room room = room();
        RoomFile file = file(room.id());
        Instant cutoff = NOW.minusSeconds(60);
        when(files.findByRoomId(room.id())).thenReturn(List.of(file));
        when(rooms.deleteByIdIfClosedBefore(room.id(), cutoff)).thenReturn(false);

        service.purgeRoom(room, cutoff, NOW);

        verify(deletions, never()).enqueue(file.storageKey(), file.sizeBytes(), NOW);
    }

    private Room room() {
        return new Room(
                "room-1",
                "12ABC",
                "owner-1",
                null,
                Plan.GUEST,
                RoomVisibility.PUBLIC,
                null,
                5,
                0,
                "",
                2048,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(120),
                null,
                0
        );
    }

    private RoomFile file(String roomId) {
        return new RoomFile(
                "file-1",
                roomId,
                "file-1.pdf",
                "brief.pdf",
                "application/pdf",
                2048,
                NOW.minusSeconds(300)
        );
    }
}
