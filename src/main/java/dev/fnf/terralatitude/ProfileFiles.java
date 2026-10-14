package dev.fnf.terralatitude;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Files are completed before a generation thread can publish a new profile. */
public final class ProfileFiles {
    private ProfileFiles() {}
    public static ClimateProfile read(Path file) throws IOException { return ClimateProfile.fromJson(Files.readString(file)); }
    public static void create(Path file, ClimateProfile profile) throws IOException {
        if (Files.exists(file)) throw new IOException("Refusing to overwrite existing world climate profile: " + file);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".terralatitude-", ".tmp");
        try {
            Files.writeString(temporary, profile.toJson());
            if (Files.exists(file)) throw new IOException("Profile appeared during creation: " + file);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
