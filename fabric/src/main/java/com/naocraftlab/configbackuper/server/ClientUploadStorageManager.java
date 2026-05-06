package com.naocraftlab.configbackuper.server;

import com.naocraftlab.configbackuper.FabricModInitializer;
import com.naocraftlab.configbackuper.core.ModConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 管理客户端上传到服务端的分片文件写入与落盘。
 */
public class ClientUploadStorageManager {
    private static final Map<UUID, UploadSession> SESSIONS = new ConcurrentHashMap<>();

    public static void begin(UUID playerId, String playerName, String fileName, long expectedSize, ModConfig cfg) throws IOException {
        closeSession(playerId);
        Path playerDir = resolvePlayerDir(cfg, playerName);
        Files.createDirectories(playerDir);
        String safeFileName = sanitizeFileName(fileName);
        Path tempPath = playerDir.resolve(safeFileName + ".uploading");
        OutputStream out = Files.newOutputStream(tempPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        SESSIONS.put(playerId, new UploadSession(playerName, safeFileName, expectedSize, 0L, tempPath, out));
    }

    public static void append(UUID playerId, byte[] chunk) throws IOException {
        UploadSession s = requireSession(playerId);
        s.out.write(chunk);
        s.written += chunk.length;
        if (s.written > s.expectedSize) {
            throw new IOException("Uploaded size exceeded expected size");
        }
    }

    public static Path finish(UUID playerId, ModConfig cfg) throws IOException {
        UploadSession s = requireSession(playerId);
        s.out.flush();
        s.out.close();
        if (s.written != s.expectedSize) {
            Files.deleteIfExists(s.tempPath);
            SESSIONS.remove(playerId);
            throw new IOException("Uploaded size mismatch");
        }
        Path finalPath = s.tempPath.getParent().resolve(s.fileName);
        Files.move(s.tempPath, finalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        SESSIONS.remove(playerId);
        enforcePlayerLimit(finalPath.getParent(), cfg);
        return finalPath;
    }

    public static void closeSession(UUID playerId) {
        UploadSession s = SESSIONS.remove(playerId);
        if (s == null) {
            return;
        }
        try {
            s.out.close();
        } catch (IOException ignored) {
        }
        try {
            Files.deleteIfExists(s.tempPath);
        } catch (IOException ignored) {
        }
    }

    public static Path resolvePlayerDir(ModConfig cfg, String playerName) {
        Path root = cfg.getClientUploadFolder();
        if (!root.isAbsolute()) {
            root = Path.of(System.getProperty("user.dir")).resolve(root).normalize();
        }
        return root.resolve(playerName);
    }

    public static void enforcePlayerLimit(Path playerDir, ModConfig cfg) {
        int max = cfg.getClientUploadMaxBackupsPerPlayer();
        if (max < 0) {
            return;
        }
        try (Stream<Path> files = Files.list(playerDir)) {
            var sorted = files
                    .filter(Files::isRegularFile)
                    .filter(f -> !f.getFileName().toString().endsWith(".uploading"))
                    .sorted(Comparator.comparingLong(f -> {
                        try {
                            return Files.readAttributes(f, BasicFileAttributes.class).lastModifiedTime().toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .toList();
            int remove = sorted.size() - max;
            for (int i = 0; i < remove; i++) {
                Files.deleteIfExists(sorted.get(i));
            }
        } catch (IOException e) {
            FabricModInitializer.getLogger().error("Failed to enforce upload backup limit for " + playerDir, e);
        }
    }

    private static String sanitizeFileName(String name) {
        return name.replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_");
    }

    private static UploadSession requireSession(UUID playerId) throws IOException {
        UploadSession s = SESSIONS.get(playerId);
        if (s == null) {
            throw new IOException("No upload session started");
        }
        return s;
    }

    private static class UploadSession {
        private final String playerName;
        private final String fileName;
        private final long expectedSize;
        private long written;
        private final Path tempPath;
        private final OutputStream out;

        private UploadSession(String playerName, String fileName, long expectedSize, long written, Path tempPath, OutputStream out) {
            this.playerName = playerName;
            this.fileName = fileName;
            this.expectedSize = expectedSize;
            this.written = written;
            this.tempPath = tempPath;
            this.out = out;
        }
    }
}
