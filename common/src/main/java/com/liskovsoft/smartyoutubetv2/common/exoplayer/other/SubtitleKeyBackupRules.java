package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

/**
 * Decides whether the stored key is excluded from app export and system backup (plan section 7).
 *
 * <p>This is the acceptance helper of that rule: production code never needs to ask the question
 * (the key is simply written there), but the plan requires the exclusion to be checked, so the rule
 * lives here with unit tests and can be used by the instrumented verification.
 *
 * <p>The key file must live inside the application's no-backup directory, which Android excludes from
 * Auto Backup and device transfer. The check is a proper path-segment comparison rather than a string
 * prefix, because {@code /data/user/0/app/no_backup_other} starts with {@code .../no_backup} but is a
 * different directory.
 */
public final class SubtitleKeyBackupRules {
    private SubtitleKeyBackupRules() {
    }

    /**
     * @param keyFilePath     absolute path of the stored key file
     * @param noBackupDirPath absolute path of the platform's no-backup directory
     * @return true only when the key file provably lies inside that directory
     */
    public static boolean isExcludedFromBackup(String keyFilePath, String noBackupDirPath) {
        if (keyFilePath == null || noBackupDirPath == null) {
            return false; // unknown location is never treated as excluded
        }

        String keyPath = normalize(keyFilePath);
        String directoryPath = normalize(noBackupDirPath);

        if (keyPath.isEmpty() || directoryPath.isEmpty() || keyPath.equals(directoryPath)) {
            return false; // the directory itself is not the key file
        }

        return keyPath.startsWith(directoryPath + "/");
    }

    private static String normalize(String path) {
        String normalized = path.trim().replace('\\', '/');

        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
