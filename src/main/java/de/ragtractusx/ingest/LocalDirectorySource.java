package de.ragtractusx.ingest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads docs from a local directory laid out like the website repo (e.g. a local clone). Used for tests and offline
 * runs. Blob SHAs are computed the same way git does, so switching between sources keeps change detection intact.
 */
public class LocalDirectorySource implements DocsSource {

    private final Path root;
    private final DocPathFilter filter;

    public LocalDirectorySource(Path root, List<String> includePrefixes, String kitsVersion) {
        this.root = root.toAbsolutePath().normalize();
        this.filter = DocPathFilter.needsKitsVersion(includePrefixes)
                ? DocPathFilter.withKitsVersion(includePrefixes, resolveKitsVersion(this.root, kitsVersion))
                : new DocPathFilter(includePrefixes);
    }

    private static String resolveKitsVersion(Path root, String kitsVersion) {
        if (kitsVersion != null && !"latest".equalsIgnoreCase(kitsVersion)) {
            return kitsVersion;
        }
        try {
            return DocPathFilter.latestVersion(Files.readString(root.resolve("docs-kits_versions.json")));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot resolve latest KIT version from docs-kits_versions.json", e);
        }
    }

    @Override
    public String describe() {
        return "local:" + root;
    }

    @Override
    public List<DocRef> list() {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .filter(filter::accept)
                    .sorted()
                    .map(p -> new DocRef(p, gitBlobSha(root.resolve(p))))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String read(DocRef ref) {
        try {
            return Files.readString(root.resolve(ref.path()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String gitBlobSha(Path file) {
        try {
            byte[] content = Files.readAllBytes(file);
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
            sha1.update(content);
            return HexFormat.of().formatHex(sha1.digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
