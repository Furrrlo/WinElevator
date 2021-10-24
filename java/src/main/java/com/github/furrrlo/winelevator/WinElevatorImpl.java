package com.github.furrrlo.winelevator;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.furrrlo.winelevator.WinElevatorConstants.*;

class WinElevatorImpl implements WinElevator {

    private static final String OS = System.getProperty("os.name");
    private static final boolean IS_WINDOWS = OS.toLowerCase(Locale.ROOT).contains("win");

    private static final String ARCHITECTURE = System.getProperty("os.arch");
    private static final boolean IS_X86 = ARCHITECTURE.equals("x86");
    private static final boolean IS_X86_64 = ARCHITECTURE.equals("amd64");

    private static volatile Path EXTRACTED_BINARY;
    private static final Object EXTRACTED_BINARY_LOCK = new Object();

    private static final Logger LOGGER = Logger.getLogger(WinElevatorImpl.class.getName());

    private final Path launcherPath;

    public WinElevatorImpl() {
        this(Paths.get("bin"));
    }

    public WinElevatorImpl(Path binFolder) {
        if (!IS_WINDOWS)
            throw new UnsupportedOperationException("Unsupported operating system " + OS);
        if (!IS_X86 && !IS_X86_64)
            throw new UnsupportedOperationException("Unsupported architecture " + ARCHITECTURE);

        this.launcherPath = getLauncherBinPath(binFolder);
    }

    private static Path getLauncherBinPath(Path binFolder) {
        final List<Throwable> exceptions = new ArrayList<>();
        for (Supplier<Path> binDir : Arrays.<Supplier<Path>>asList(
                () -> binFolder,
                WinElevatorImpl::extractBinaries
        )) {
            try {
                final Path exeFolder = binDir.get().resolve(getNativeLibraryResourcePrefix());
                for (String binary : BINARIES) {
                    final Path exe = exeFolder.resolve(binary);
                    if (!Files.exists(exe))
                        throw new FileNotFoundException(exe.toAbsolutePath().toString());
                    if (!Files.isRegularFile(exe))
                        throw new FileNotFoundException(exe.toAbsolutePath() + " is not a file");
                }

                return exeFolder.resolve(LAUNCHER_BINARY);
            } catch (Throwable ex) {
                exceptions.add(ex);
            }
        }

        final UncheckedIOException ex = new UncheckedIOException(new IOException("Couldn't find launcher.exe binary"));
        exceptions.forEach(ex::addSuppressed);
        throw ex;
    }

    private static Path extractBinaries() {
        if(EXTRACTED_BINARY == null)
            synchronized (EXTRACTED_BINARY_LOCK) {
                if(EXTRACTED_BINARY == null)
                    try {
                        EXTRACTED_BINARY = extractBinaries0();
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
            }
        return EXTRACTED_BINARY;
    }

    private static Path extractBinaries0() throws IOException {
        final Path tmpDir;
        try {
            tmpDir = Files.createTempDirectory(TEMP_BINARY_PREFIX + "-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.walkFileTree(tmpDir, new DeleteDirectoryOnExit());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Failed to delete tmp binaries directory", e);
                }
            }, "WinElevator-delete-tmp-binaries-shutdown-hook"));
        } catch (IOException ex) {
            throw new IOException("Failed to create tmp directory", ex);
        }

        final Path dstFolder = tmpDir.resolve(getNativeLibraryResourcePrefix());
        Files.createDirectories(dstFolder);
        if (!dstFolder.toFile().canWrite())
            throw new IOException("Cannot write to tmp directory " + dstFolder);

        final String srcLocation = BINARIES_LOCATION + getNativeLibraryResourcePrefix() + '/';

        for (String binary : BINARIES) {
            final String sourcePath = srcLocation + binary;
            final Path destinationPath = dstFolder.resolve(binary);

            try (InputStream is = WinElevatorImpl.class.getResourceAsStream(sourcePath)) {
                if (is == null)
                    throw new FileNotFoundException("Couldn't find '" + sourcePath + "' inside the jar");
                Files.copy(is, destinationPath.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return tmpDir;
    }

    private static String getNativeLibraryResourcePrefix() {
        return "win32-" + ARCHITECTURE;
    }

    @Override
    public ProcessBuilder newProcessBuilder(List<String> command) {
        final List<String> cmd = new ArrayList<>(command.size() + 1);
        cmd.add(launcherPath.toAbsolutePath().toString());
        cmd.addAll(command);
        return new ProcessBuilder(cmd);
    }

    @Override
    public ProcessBuilder newProcessBuilder(String... command) {
        final List<String> cmd = new ArrayList<>(command.length + 1);
        cmd.add(launcherPath.toAbsolutePath().toString());
        Collections.addAll(cmd, command);
        return new ProcessBuilder(cmd);
    }

    private static class DeleteDirectoryOnExit implements FileVisitor<Path> {

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (file != null)
                Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (dir != null)
                Files.deleteIfExists(dir);
            return FileVisitResult.CONTINUE;
        }
    }
}
