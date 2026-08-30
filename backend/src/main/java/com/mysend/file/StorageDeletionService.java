package com.mysend.file;

import com.mysend.room.Room;
import com.mysend.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class StorageDeletionService {

    private final RoomRepository rooms;
    private final RoomFileRepository files;
    private final StoredObjectDeletionRepository deletions;

    public StorageDeletionService(
            RoomRepository rooms,
            RoomFileRepository files,
            StoredObjectDeletionRepository deletions
    ) {
        this.rooms = rooms;
        this.files = files;
        this.deletions = deletions;
    }

    @Transactional
    public void deleteFile(Room room, RoomFile file, Instant now) {
        if (!files.delete(file.id(), room.id())) {
            return;
        }
        if (!rooms.adjustFileBytes(
                room.id(),
                -file.sizeBytes(),
                room.plan().limits().roomFileBytes()
        )) {
            throw new IllegalStateException("Could not release room file capacity");
        }
        deletions.enqueue(file.storageKey(), file.sizeBytes(), now);
    }

    @Transactional
    public boolean purgeRoom(Room room, Instant cutoff, Instant now) {
        List<RoomFile> storedFiles = files.findByRoomId(room.id());
        if (!rooms.deleteByIdIfClosedBefore(room.id(), cutoff)) {
            return false;
        }
        storedFiles.forEach(file -> deletions.enqueue(
                file.storageKey(),
                file.sizeBytes(),
                now
        ));
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueCompensation(String storageKey, long sizeBytes, Instant now) {
        deletions.enqueue(storageKey, sizeBytes, now);
    }
}
