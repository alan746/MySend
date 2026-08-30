package com.mysend.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigratingFileStoreTest {

    private FileStore primary;
    private FileStore legacy;
    private MigratingFileStore store;

    @BeforeEach
    void setUp() {
        primary = mock(FileStore.class);
        legacy = mock(FileStore.class);
        store = new MigratingFileStore(primary, legacy);
    }

    @Test
    void writesNewObjectsOnlyToThePrimaryStore() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[]{1, 2});

        store.put("file.pdf", input, 2, "application/pdf");

        verify(primary).put("file.pdf", input, 2, "application/pdf");
        verify(legacy, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void readsLegacyObjectsWhenTheyAreNotInThePrimaryStore() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[]{3, 4});
        when(primary.open("old.pdf")).thenThrow(new FileNotFoundException("old.pdf"));
        when(legacy.open("old.pdf")).thenReturn(input);

        assertThat(store.open("old.pdf")).isSameAs(input);
    }

    @Test
    void deletesObjectsFromBothStores() throws Exception {
        store.delete("file.pdf");

        verify(primary).delete("file.pdf");
        verify(legacy).delete("file.pdf");
    }

    @Test
    void mergesObjectListingsWithoutDuplicatingKeys() throws Exception {
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        when(primary.list()).thenReturn(List.of(
                new FileStore.StoredObject("new.pdf", 10, now),
                new FileStore.StoredObject("both.pdf", 20, now)
        ));
        when(legacy.list()).thenReturn(List.of(
                new FileStore.StoredObject("old.pdf", 30, now),
                new FileStore.StoredObject("both.pdf", 20, now.minusSeconds(1))
        ));

        assertThat(store.list())
                .extracting(FileStore.StoredObject::storageKey)
                .containsExactlyInAnyOrder("new.pdf", "old.pdf", "both.pdf");
    }
}
