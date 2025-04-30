package br.balladesh;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Thin wrapper around the 7‑Zip CLI for per‑file operations.
 */
public class SevenZipService {
    public static final int OK_CODE   = 0;
    public static final int WARN_CODE = 1;

    private final Path exe;
    private final String password;

    public SevenZipService(Path exe, String password) {
        this.exe = exe;
        this.password = password;
    }

    /**
     * Encrypt a single file. Returns the exit code from 7‑Zip.
     */
    public int encrypt(Path source, Path dest) throws IOException, InterruptedException {
        return run(
            List.of(
                exe.toString(), "a", "-t7z",
                dest.toString(),
                source.toString(),
                "-mhe=on",
                "-p" + password,
                "-y"
            )
        );
    }

    /**
     * Verify an existing archive. 0 = OK, 1 = OK with warnings, ≥2 error.
     */
    public int verify(Path archive) throws IOException, InterruptedException {
        return run(
            List.of(
                exe.toString(), "t",
                archive.toString(),
                "-p" + password
            )
        );
    }

    /**
     * Executes the given command line and returns the process exit code.
     */
    private int run(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        return pb.start().waitFor();
    }
}
