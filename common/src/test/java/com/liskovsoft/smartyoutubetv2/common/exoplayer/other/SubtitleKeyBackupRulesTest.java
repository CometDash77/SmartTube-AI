package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** T08 slice: the stored key is provably outside every backed-up location. */
public class SubtitleKeyBackupRulesTest {
    private static final String NO_BACKUP = "/data/user/0/com.example.app/no_backup";

    @Test
    public void aFileInsideTheNoBackupDirectoryIsExcluded() {
        assertTrue(SubtitleKeyBackupRules.isExcludedFromBackup(NO_BACKUP + "/ai_subtitle_key", NO_BACKUP));
        assertTrue(SubtitleKeyBackupRules.isExcludedFromBackup(NO_BACKUP + "/nested/ai_subtitle_key", NO_BACKUP));
    }

    @Test
    public void aTrailingSlashOrDifferentSeparatorDoesNotChangeTheVerdict() {
        assertTrue(SubtitleKeyBackupRules.isExcludedFromBackup(NO_BACKUP + "/ai_subtitle_key", NO_BACKUP + "/"));
        assertTrue(SubtitleKeyBackupRules.isExcludedFromBackup("C:\\app\\no_backup\\ai_subtitle_key", "C:\\app\\no_backup"));
    }

    @Test
    public void aLookalikeDirectoryIsNotAcceptedByPrefixConfusion() {
        assertFalse("a sibling directory with a longer name is a different location",
                SubtitleKeyBackupRules.isExcludedFromBackup("/data/user/0/com.example.app/no_backup_other/ai_subtitle_key", NO_BACKUP));
        assertFalse(SubtitleKeyBackupRules.isExcludedFromBackup("/data/user/0/com.example.app/no_backupX", NO_BACKUP));
    }

    @Test
    public void backedUpLocationsAreRejected() {
        assertFalse(SubtitleKeyBackupRules.isExcludedFromBackup("/data/user/0/com.example.app/shared_prefs/ai_subtitle_key", NO_BACKUP));
        assertFalse(SubtitleKeyBackupRules.isExcludedFromBackup("/data/user/0/com.example.app/files/ai_subtitle_key", NO_BACKUP));
        assertFalse(SubtitleKeyBackupRules.isExcludedFromBackup("/sdcard/ai_subtitle_key", NO_BACKUP));
    }

    @Test
    public void unknownLocationsAreNeverTreatedAsExcluded() {
        assertFalse(SubtitleKeyBackupRules.isExcludedFromBackup(null, NO_BACKUP));
        assertFalse(SubtitleKeyBackupRules.isExcludedFromBackup(NO_BACKUP + "/ai_subtitle_key", null));
        assertFalse(SubtitleKeyBackupRules.isExcludedFromBackup("   ", NO_BACKUP));
        assertFalse(SubtitleKeyBackupRules.isExcludedFromBackup(NO_BACKUP, NO_BACKUP));
    }
}
