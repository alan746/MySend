package com.mysend.file;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileBoardServiceTest {

    @Test
    void removesClientPathsAndUnsafeFilenameCharacters() {
        assertThat(FileBoardService.sanitizeName("C:\\fakepath\\report<final>.pdf"))
                .isEqualTo("report_final_.pdf");
    }

    @Test
    void suppliesANameWhenTheBrowserDoesNot() {
        assertThat(FileBoardService.sanitizeName(null)).isEqualTo("upload.bin");
    }
}
