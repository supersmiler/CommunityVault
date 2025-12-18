package org.niels.communityVault.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

public class BackupManager {

    private static final String BACKUP_DIR = "plugins/CommunityVault/backups";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int RETENTION_DAYS = 14;
    private static Instant lastBackupTime;

    // Create a backup of vault.yml and categories.yml
    public static void backupVaultAndCategories(Logger logger) {
        try {
            Path backupDir = Path.of(BACKUP_DIR);
            Files.createDirectories(backupDir);

            String timestamp = LocalDateTime.now().format(FORMATTER);

            copyIfExists(Path.of("plugins/CommunityVault/vault.yml"), backupDir.resolve("vault-" + timestamp + ".yml"), logger);
            copyIfExists(Path.of("plugins/CommunityVault/categories.yml"), backupDir.resolve("categories-" + timestamp + ".yml"), logger);
            lastBackupTime = Instant.now();
        } catch (IOException e) {
            logger.warning("[CommunityVault] Failed to create backup directory: " + e.getMessage());
        }
    }

    // Remove backups older than retention window
    public static void pruneOldBackups(Logger logger) {
        File dir = new File(BACKUP_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }

        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            try {
                Instant lastModified = Instant.ofEpochMilli(file.lastModified());
                if (lastModified.isBefore(cutoff)) {
                    Files.deleteIfExists(file.toPath());
                }
            } catch (IOException e) {
                logger.warning("[CommunityVault] Failed to delete old backup " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private static void copyIfExists(Path source, Path target, Logger logger) {
        try {
            if (Files.exists(source)) {
                Path finalTarget = target;
                int counter = 1;
                while (Files.exists(finalTarget)) {
                    String baseName = stripExtension(target.getFileName().toString());
                    String extension = getExtension(target.getFileName().toString());
                    finalTarget = target.getParent().resolve(baseName + "-" + counter + extension);
                    counter++;
                }
                Files.copy(source, finalTarget, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            logger.warning("[CommunityVault] Failed to back up " + source.getFileName() + ": " + e.getMessage());
        }
    }

    private static String stripExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx == -1 ? filename : filename.substring(0, idx);
    }

    private static String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx == -1 ? "" : filename.substring(idx);
    }

    public static Instant getLastBackupTime() {
        return lastBackupTime;
    }
}
