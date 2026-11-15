package com.litelisten.player.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FolderPathsTest {
    @Test
    fun mediaStoreAbsolutePathKeepsMeaningfulFolder() {
        assertThat(FolderPaths.folderOf("/storage/emulated/0/Music/周杰伦/七里香.flac"))
            .isEqualTo("Music/周杰伦")
    }

    @Test
    fun sdcardPrefixIsRemoved() {
        assertThat(FolderPaths.folderOf("/sdcard/Download/a.mp3")).isEqualTo("Download")
        assertThat(FolderPaths.folderOf("/mnt/sdcard/Music/b.flac")).isEqualTo("Music")
    }

    @Test
    fun externalVolumeRootIsRemoved() {
        assertThat(FolderPaths.folderOf("/storage/1A2B-3C4D/Music/c.wav")).isEqualTo("Music")
    }

    @Test
    fun relativePathDirectoryKeepsTrailingSlashSemantics() {
        assertThat(FolderPaths.folderOf("Music/周杰伦/")).isEqualTo("Music/周杰伦")
        assertThat(FolderPaths.folderOf("Music/")).isEqualTo("Music")
    }

    @Test
    fun safRelativePathWithoutExtensionIsTreatedAsFolder() {
        assertThat(FolderPaths.folderOf("Music")).isEqualTo("Music")
    }

    @Test
    fun relativeFilePathDropsFileName() {
        assertThat(FolderPaths.folderOf("Music/七里香.flac")).isEqualTo("Music")
        assertThat(FolderPaths.folderOf("sub/deep/a.flac")).isEqualTo("sub/deep")
    }

    @Test
    fun fileNameOnlyFallsBackToRoot() {
        assertThat(FolderPaths.folderOf("七里香.flac")).isEmpty()
        assertThat(FolderPaths.folderOf("")).isEmpty()
    }

    @Test
    fun backSlashIsNormalized() {
        assertThat(FolderPaths.folderOf("Music\\周杰伦\\七里香.flac")).isEqualTo("Music/周杰伦")
    }

    @Test
    fun isUnderIncludesSubFolders() {
        assertThat(FolderPaths.isUnder("Music", "Music/周杰伦/a.flac")).isTrue()
        assertThat(FolderPaths.isUnder("Music", "Other/a.flac")).isFalse()
        assertThat(FolderPaths.isUnder("Music", "MusicVideo/a.flac")).isFalse()
    }

    @Test
    fun rootFolderMatchesOnlyRootTracks() {
        assertThat(FolderPaths.isUnder("", "a.flac")).isTrue()
        assertThat(FolderPaths.isUnder("", "Music/a.flac")).isFalse()
    }
}
