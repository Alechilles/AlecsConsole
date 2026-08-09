package com.alechilles.alecsconsole;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Inventory of the explicitly allowed local server and client log roots. */
public final class LogFileCatalog {
    private static final String APPDATA = "APPDATA";

    public enum Source {
        CURRENT_SERVER,
        PREVIOUS_SERVER,
        CLIENT
    }

    public record Option(String id, Source source, String displayName, Path path) {
    }

    private final Path serverRoot;
    private final List<Root> clientRoots;
    private final Map<String, Option> options = new LinkedHashMap<>();

    public LogFileCatalog() {
        this(null);
    }

    public LogFileCatalog(String configuredClientDirectory) {
        this(Path.of("logs").toAbsolutePath().normalize(), configuredClientDirectory);
    }

    LogFileCatalog(Path serverRoot, String configuredClientDirectory) {
        this.serverRoot = Objects.requireNonNull(serverRoot, "serverRoot").toAbsolutePath().normalize();
        this.clientRoots = clientRoots(configuredClientDirectory);
    }

    public synchronized List<Option> refresh() {
        options.clear();
        List<Candidate> serverFiles = enumerate(serverRoot).stream()
                .filter(candidate -> isServerLog(candidate.path))
                .toList();
        serverFiles.sort(Candidate.NEWEST_FIRST);

        boolean currentAssigned = false;
        for (Candidate candidate : serverFiles) {
            Source source = currentAssigned ? Source.PREVIOUS_SERVER : Source.CURRENT_SERVER;
            currentAssigned = true;
            addOption(candidate, source, "server");
        }

        for (Root root : clientRoots) {
            List<Candidate> clientFiles = enumerate(root.path);
            clientFiles.sort(Candidate.NEWEST_FIRST);
            for (Candidate candidate : clientFiles) {
                addOption(candidate, Source.CLIENT, root.id);
            }
        }
        return List.copyOf(options.values());
    }

    public synchronized Optional<Option> resolve(String id) {
        if (id == null) {
            return Optional.empty();
        }
        Option option = options.get(id);
        if (option == null) {
            return Optional.empty();
        }
        Path root = rootFor(option);
        if (root == null) {
            options.remove(id);
            return Optional.empty();
        }
        Path candidate = safeRealFile(option.path(), root);
        if (candidate == null) {
            options.remove(id);
            return Optional.empty();
        }
        Option verified = new Option(option.id(), option.source(), option.displayName(), candidate);
        options.put(id, verified);
        return Optional.of(verified);
    }

    private void addOption(Candidate candidate, Source source, String rootId) {
        String relativeName = candidate.relative.toString().replace('\\', '/');
        String id = source.name() + ":" + rootId + ":" + relativeName;
        String displayName = sourceLabel(source) + " · " + relativeName;
        options.put(id, new Option(id, source, displayName, candidate.path));
    }

    private Path rootFor(Option option) {
        if (option.source() == Source.CURRENT_SERVER || option.source() == Source.PREVIOUS_SERVER) {
            return serverRoot;
        }
        for (Root root : clientRoots) {
            if (option.id().startsWith(Source.CLIENT.name() + ":" + root.id + ":")) {
                return root.path;
            }
        }
        return null;
    }

    private static String sourceLabel(Source source) {
        return switch (source) {
            case CURRENT_SERVER -> "Current server";
            case PREVIOUS_SERVER -> "Previous server";
            case CLIENT -> "Client";
        };
    }

    private static List<Candidate> enumerate(Path root) {
        Path rootReal;
        try {
            rootReal = root.toRealPath();
        } catch (IOException exception) {
            return List.of();
        }

        try (var stream = Files.list(rootReal)) {
            return stream
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".log"))
                    .map(path -> candidate(path, rootReal))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static boolean isServerLog(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("_server.log");
    }

    private static Candidate candidate(Path path, Path root) {
        Path verified = safeRealFile(path, root);
        if (verified == null) {
            return null;
        }
        try {
            return new Candidate(verified, root.relativize(verified), Files.getLastModifiedTime(verified).toMillis());
        } catch (IOException exception) {
            return null;
        }
    }

    private static Path safeRealFile(Path path, Path root) {
        try {
            Path rootReal = root.toRealPath();
            Path candidateReal = path.toRealPath();
            return Files.isRegularFile(candidateReal, LinkOption.NOFOLLOW_LINKS)
                    && candidateReal.startsWith(rootReal) ? candidateReal : null;
        } catch (IOException exception) {
            return null;
        }
    }

    private static List<Root> clientRoots(String configuredClientDirectory) {
        ArrayList<Root> roots = new ArrayList<>();
        String appData = System.getenv(APPDATA);
        if (appData != null && !appData.isBlank()) {
            Path appDataRoot = Path.of(appData);
            roots.add(new Root("release", appDataRoot.resolve("Hytale/data/release/Logs").normalize()));
            roots.add(new Root("pre-release", appDataRoot.resolve("Hytale/data/pre-release/Logs").normalize()));
        }
        if (configuredClientDirectory != null && !configuredClientDirectory.isBlank()) {
            roots.add(new Root("configured", Path.of(configuredClientDirectory).toAbsolutePath().normalize()));
        }
        return List.copyOf(roots);
    }

    private record Root(String id, Path path) {
    }

    private record Candidate(Path path, Path relative, long lastModified) {
        private static final Comparator<Candidate> NEWEST_FIRST = Comparator
                .comparingLong(Candidate::lastModified).reversed()
                .thenComparing(candidate -> candidate.path.getFileName().toString());
    }
}
