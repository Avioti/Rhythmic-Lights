package com.rhythm.audio.executable;

import com.rhythm.util.RhythmConstants;
import org.apache.commons.lang3.SystemUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages external executables (yt-dlp, ffmpeg) for downloading and processing audio.
 * Handles automatic downloading from GitHub releases and process lifecycle management.
 */
public enum RhythmExecutable {

    YT_DLP("yt-dlp", "yt-dlp/yt-dlp", resolveYtDlpFileName()),
    FFMPEG("ffmpeg", "eugeneware/ffmpeg-static", resolveFfmpegFileName());

    // Platform-specific constants
    private static final String WINDOWS_EXTENSION = ".exe";
    private static final String ZIP_EXTENSION = ".zip";
    private static final String GITHUB_DOWNLOAD_URL_FORMAT = "https://github.com/%s/releases/latest/download/%s";
    private static final long PROCESS_POLL_INTERVAL_MS = 10L;
    private static final int SUCCESS_EXIT_CODE = 0;

    // Instance fields
    private final Path directory = RhythmConstants.getExecutablesPath();
    private final String fileName;
    private final String repositoryName;
    private final String repositoryFile;
    private final Path filePath;
    private final ConcurrentHashMap<String, ProcessStream> activeProcesses = new ConcurrentHashMap<>();

    RhythmExecutable(String fileName, String repositoryName, String repositoryFile) {
        this.fileName = fileName;
        this.repositoryName = repositoryName;
        this.repositoryFile = repositoryFile;
        this.filePath = directory.resolve(fileName + getPlatformExtension());
    }

    // ==================== Static Helper Methods ====================

    private static String resolveYtDlpFileName() {
        if (SystemUtils.IS_OS_LINUX) return "yt-dlp_linux";
        if (SystemUtils.IS_OS_MAC) return "yt-dlp_macos";
        return "yt-dlp.exe";
    }

    private static String resolveFfmpegFileName() {
        String platform = SystemUtils.IS_OS_LINUX ? "linux" : SystemUtils.IS_OS_MAC ? "darwin" : "win32";
        return String.format("ffmpeg-%s-x64", platform);
    }

    private static String getPlatformExtension() {
        return SystemUtils.IS_OS_WINDOWS ? WINDOWS_EXTENSION : "";
    }

    // ==================== Public API ====================

    /**
     * Returns the file path of this executable.
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Checks if a process with the given ID is currently running.
     */
    public boolean isProcessRunning(String id) {
        return activeProcesses.containsKey(id);
    }

    /**
     * Gets the ProcessStream for the given ID, or null if not found.
     */
    public ProcessStream getProcessStream(String id) {
        return activeProcesses.get(id);
    }

    /**
     * Ensures the executable exists, downloading it if necessary.
     *
     * @return true if executable is available, false otherwise
     */
    public boolean checkForExecutable() {
        try {
            Files.createDirectories(directory);
            return Files.exists(filePath) || downloadExecutable();
        } catch (IOException e) {
            RhythmConstants.LOGGER.error("Failed to create executable directory", e);
            return false;
        }
    }

    /**
     * Creates and starts a new process with the given arguments.
     *
     * @param id        unique identifier for this process
     * @param arguments command line arguments
     * @return the ProcessStream for monitoring output
     */
    public ProcessStream executeCommand(String id, String... arguments) {
        return new ProcessStream(id, arguments);
    }

    /**
     * Terminates a specific process by ID.
     */
    public void killProcess(String id) {
        ProcessStream stream = activeProcesses.remove(id);
        if (stream == null || stream.process == null) {
            return;
        }
        terminateProcess(stream.process, id);
    }

    /**
     * Terminates all active processes.
     */
    public void killAllProcesses() {
        Set.copyOf(activeProcesses.keySet()).forEach(this::killProcess);
    }

    // ==================== Process Management ====================

    boolean registerProcess(String id, ProcessStream processStream) {
        return activeProcesses.computeIfAbsent(id, k -> {
            processStream.onExit(() -> activeProcesses.remove(id));
            return processStream;
        }) == processStream;
    }

    private void terminateProcess(Process process, String id) {
        try {
            process.descendants().forEach(this::destroyProcessHandle);
            destroyAndWait(process);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Failed to kill process with ID: {}", id, e);
        }
    }

    private void destroyProcessHandle(ProcessHandle handle) {
        handle.destroyForcibly();
        handle.onExit().join();
    }

    private void destroyAndWait(Process process) {
        process.destroyForcibly();
        process.onExit().join();
    }

    // ==================== Download Logic ====================

    private boolean downloadExecutable() {
        RhythmConstants.LOGGER.info("Downloading {}...", fileName);
        try (InputStream inputStream = openDownloadStream()) {
            extractExecutable(inputStream);
            makeExecutableOnUnix();
            return Files.exists(filePath);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Failed to download {}: {}", fileName, e.getMessage());
            return false;
        }
    }

    private InputStream openDownloadStream() throws IOException, URISyntaxException {
        String url = String.format(GITHUB_DOWNLOAD_URL_FORMAT, repositoryName, repositoryFile);
        if (RhythmConstants.DEBUG_DOWNLOADS) {
            RhythmConstants.LOGGER.debug("Downloading from: {}", url);
        }
        return new URI(url).toURL().openStream();
    }

    private void extractExecutable(InputStream inputStream) throws IOException {
        if (repositoryFile.endsWith(ZIP_EXTENSION)) {
            extractFromZip(inputStream);
        } else {
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            logDownloadSuccess();
        }
    }

    private void extractFromZip(InputStream inputStream) throws IOException {
        String targetName = fileName + getPlatformExtension();
        try (ZipInputStream zipInput = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (isTargetEntry(entry.getName(), targetName)) {
                    Files.copy(zipInput, filePath, StandardCopyOption.REPLACE_EXISTING);
                    logDownloadSuccess();
                    break;
                }
            }
        }
    }

    private boolean isTargetEntry(String entryName, String targetName) {
        return entryName.endsWith(targetName) || entryName.endsWith("/" + targetName);
    }

    private void logDownloadSuccess() {
        RhythmConstants.LOGGER.info("✓ {} downloaded successfully", fileName);
    }

    private void makeExecutableOnUnix() {
        if (!SystemUtils.IS_OS_UNIX) {
            return;
        }
        try {
            new ProcessBuilder("chmod", "+x", filePath.toString()).start().waitFor();
        } catch (IOException | InterruptedException e) {
            RhythmConstants.LOGGER.warn("Failed to set executable permission for {}", fileName);
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Manages an external process and provides reactive stream output.
     */
    public class ProcessStream {
        private final String id;
        private final String[] arguments;
        private final SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
        private final ConcurrentHashMap<String, Flow.Subscription> subscriptions = new ConcurrentHashMap<>();
        private volatile Process process;

        ProcessStream(String id, String... arguments) {
            this.id = id;
            this.arguments = arguments;
            if (registerProcess(id, this)) {
                CompletableFuture.runAsync(this::startProcess);
            }
        }

        public String getId() {
            return id;
        }

        public SubscriberBuilder subscribe(String subscriberId) {
            return new SubscriberBuilder(subscriberId);
        }

        public void unsubscribe(String subscriberId) {
            Flow.Subscription subscription = subscriptions.remove(subscriberId);
            if (subscription != null) {
                subscription.cancel();
            }
        }

        public int subscriberCount() {
            return subscriptions.size();
        }

        public void onExit(Runnable callback) {
            CompletableFuture.runAsync(() -> {
                awaitProcessStart();
                process.onExit().thenRun(() -> {
                    subscriptions.keySet().forEach(this::unsubscribe);
                    callback.run();
                });
            });
        }

        private void awaitProcessStart() {
            while (process == null) {
                try {
                    TimeUnit.MILLISECONDS.sleep(PROCESS_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        private void startProcess() {
            try {
                process = createProcess();
                readProcessOutput();
                handleExitCode(process.waitFor());
            } catch (IOException | InterruptedException e) {
                publisher.closeExceptionally(e);
            } finally {
                killProcess(id);
            }
        }

        private Process createProcess() throws IOException {
            String[] command = Stream.concat(Stream.of(filePath.toString()), Stream.of(arguments))
                    .toArray(String[]::new);
            return new ProcessBuilder()
                    .command(command)
                    .redirectErrorStream(true)
                    .start();
        }

        private void readProcessOutput() throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && !publisher.isClosed()) {
                    publisher.submit(line);
                }
            }
        }

        private void handleExitCode(int exitCode) {
            if (exitCode == SUCCESS_EXIT_CODE) {
                publisher.close();
            } else {
                publisher.closeExceptionally(new IOException("Process failed with code: " + exitCode));
            }
        }

        /**
         * Builder for subscribing to process output with custom handlers.
         */
        public class SubscriberBuilder {
            private final String subscriberId;
            private Consumer<String> onOutputHandler = s -> {};
            private Consumer<Throwable> onErrorHandler = t -> {};
            private Runnable onCompleteHandler = () -> {};

            SubscriberBuilder(String subscriberId) {
                this.subscriberId = subscriberId;
            }

            public SubscriberBuilder onOutput(Consumer<String> consumer) {
                this.onOutputHandler = consumer;
                return this;
            }

            public SubscriberBuilder onError(Consumer<Throwable> consumer) {
                this.onErrorHandler = consumer;
                return this;
            }

            public SubscriberBuilder onComplete(Runnable runnable) {
                this.onCompleteHandler = runnable;
                return this;
            }

            public void start() {
                publisher.subscribe(new OutputSubscriber());
            }

            private class OutputSubscriber implements Flow.Subscriber<String> {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscriptions.put(subscriberId, subscription);
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String item) {
                    onOutputHandler.accept(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    subscriptions.remove(subscriberId);
                    onErrorHandler.accept(throwable);
                }

                @Override
                public void onComplete() {
                    subscriptions.remove(subscriberId);
                    onCompleteHandler.run();
                }
            }
        }
    }
}

