package br.balladesh;

import org.apache.commons.cli.*;

import java.io.Console;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 7‑Zip per‑file encryption utility.
 * <p>
 * Implements a minimal "ACID" style:
 * <ul>
 *   <li><b>Atomicity</b> – each file is first written to a temporary <code>.tmp</code> file; only after a successful
 *   verification is it atomically moved to the final <code>.7z</code> name.</li>
 *   <li><b>Consistency</b> – destination tree mirrors the source tree; verification guarantees file integrity.</li>
 *   <li><b>Isolation</b> – files are processed independently so an interruption leaves previously completed
 *   archives intact.</li>
 *   <li><b>Durability</b> – completed archives are final OS‑level files.</li>
 * </ul>
 */
public class App {
    private static final int OK = 0;
    private static final int WARN = 1;

    public static void main(String[] args) {
        Options opts = buildOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        try {
            cmd = parser.parse(opts, args, false);
        } catch (ParseException e) {
            showHelpAndExit(opts);
            return;
        }

        if (cmd.hasOption("help") || cmd.getArgList().isEmpty()) {
            showHelpAndExit(opts);
            return;
        }

        String command = cmd.getArgList().getFirst().toLowerCase();
        Path sevenZip = detect7Zip(cmd.getOptionValue("7zip"));
        Path inputDir = Paths.get(cmd.getOptionValue("input", ".")).toAbsolutePath();
        Path outputDir = Paths.get(cmd.getOptionValue("output", "encrypted")).toAbsolutePath();

        // Ask for password once, masked
        System.out.println("Master Password: ");
        Scanner scanner = new Scanner(System.in);
        String password = scanner.nextLine();

        SevenZipService svc = new SevenZipService(sevenZip, password);

        try {
            switch (command) {
                case "encrypt":
                    encryptRecursive(inputDir, outputDir, svc);
                    break;
                case "verify":
                    verifyRecursive(outputDir, svc);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    help(opts);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void encryptRecursive(Path input, Path output, SevenZipService svc) throws IOException {
        if (!Files.isDirectory(input)) {
            throw new IllegalArgumentException("Input directory " + input + " does not exist");
        }

        Files.createDirectories(output);

        AtomicInteger done = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();

        Files.walkFileTree(input, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    Path relative = input.relativize(file);
                    Path dest = output.resolve(relative.toString() + ".7z");
                    Files.createDirectories(dest.getParent());

                    if (Files.exists(dest)) {
                        int v = svc.verify(dest);
                        if (v == App.OK || v == App.WARN) {
                            System.out.println("OK  -> " + relative);
                            skipped.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }
                        System.out.println("Corrupt, recreating: " + relative);
                    }

                    Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
                    svc.encrypt(file, tmp);
                    int after = svc.verify(tmp);
                    if (after == App.OK || after == App.WARN) {
                        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        System.out.println("+ Encrypted -> " + relative);
                        done.incrementAndGet();
                    } else {
                        Files.deleteIfExists(tmp);
                        System.err.println("FAILED verify after encrypt: " + relative);
                    }
                } catch (InterruptedException ex) {
                    throw new IOException(ex);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.printf("Completed: %d new, %d already OK%n", done.get(), skipped.get());
    }

    private static void verifyRecursive(Path dir, SevenZipService svc) throws IOException, InterruptedException {
        if (!Files.isDirectory(dir))
            throw new IllegalArgumentException("Directory does not exist: " + dir);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger bad = new AtomicInteger();

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".7z")) return FileVisitResult.CONTINUE;
                try {
                    int code = svc.verify(file);
                    if (code == App.OK || code == App.WARN) {
                        ok.incrementAndGet();
                    } else {
                        System.out.println("Corrupt -> " + dir.relativize(file));
                        bad.incrementAndGet();
                    }
                } catch (InterruptedException ex) {
                    throw new IOException(ex);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        System.out.printf("Verification summary: %d OK, %d corrupt%n", ok.get(), bad.get());
    }

    private static void help(Options o) {
        new HelpFormatter().printHelp("java -jar encryptor.jar <encrypt|verify> [options]", o);
    }

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(
            Option.builder("7zip")
                .longOpt("7zip")
                .hasArg()
                .argName("path")
                .desc("Path to 7z executable (default: 7z on PATH)")
                .build()
        );
        options.addOption(
            Option.builder("i")
                .longOpt("input")
                .hasArg()
                .argName("dir")
                .desc("Input folder containing plain files")
                .required()
                .build()
        );
        options.addOption(
            Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("dir")
                .desc("Destination folder for .7z archives")
                .required()
                .build()
        );
        options.addOption(
            Option.builder("h")
                .longOpt("help")
                .desc("Show help")
                .build()
        );
        return options;
    }

    /**
     * Determine the 7‑Zip executable path.
     * Priority:
     *   1) user‑supplied via --7zip
     *   2) common defaults based on OS
     *   3) rely on executable name "7z" / "7z.exe" present in PATH
     */
    private static Path detect7Zip(String userProvided) {
        List<Path> candidates = new ArrayList<>();

        if (userProvided != null) {
            candidates.add(Paths.get(userProvided));
        }

        String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (os.contains("win")) {
            candidates.add(Paths.get("C:/Program Files/7-Zip/7z.exe"));
            candidates.add(Paths.get("C:/Program Files (x86)/7-Zip/7z.exe"));
            candidates.add(Paths.get("7z.exe"));
        } else if (os.contains("mac")) { // macOS
            candidates.add(Paths.get("/opt/homebrew/bin/7z"));
            candidates.add(Paths.get("/usr/local/bin/7z"));
            candidates.add(Paths.get("/usr/bin/7z"));
            candidates.add(Paths.get("7z"));
        } else { // assume Linux/Unix
            candidates.add(Paths.get("/usr/bin/7z"));
            candidates.add(Paths.get("/usr/local/bin/7z"));
            candidates.add(Paths.get("7z"));
        }

        for (Path p : candidates) {
            if (Files.exists(p) && Files.isExecutable(p)) {
                return p;
            }
            // If just a command like "7z" and PATH contains it, accept.
            if (!p.toString().contains("/") && !p.toString().contains("\\")) {
                return p; // rely on PATH resolution
            }
        }

        System.err.println("7-Zip executable not found. Use --7zip to specify the path.");
        System.exit(1);

        return null; // unreachable
    }

    private static void showHelpAndExit(Options opts) {
        new HelpFormatter().printHelp(
            "java -jar encryptor.jar <encrypt|verify> [options]",
            opts);
        System.exit(0);
    }
}
