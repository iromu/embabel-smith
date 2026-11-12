package com.embabel.smith.specsource;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.validation.AgentSpecValidator;
import com.embabel.smith.common.validation.ValidationIssue;
import com.embabel.smith.common.validation.ValidationResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A spec source rooted at a local directory. The root is a *library* of packs: each immediate
 * subdirectory holding a {@code manifest.yml} is a pack candidate and must pass
 * {@link AgentSpecValidator} structure validation to be included.
 *
 * <p>Deeper nesting is not scanned. Candidates that fail validation are excluded and named in
 * the result's warnings; a root with no valid packs resolves to an empty pack set plus a warning
 * (not an error).
 */
public final class FolderSpecSource implements SpecSource {

    private final String uri;
    private final Path root;
    private final AgentSpecValidator validator = new AgentSpecValidator();
    private final AgentSpecReader reader = new AgentSpecReader();

    public FolderSpecSource(String uri, Path root) {
        this.uri = uri;
        this.root = root;
    }

    /** The directory this source scans. */
    public Path root() {
        return root;
    }

    @Override
    public ResolvedSpecSource resolve() {
        if (!Files.isDirectory(root)) {
            throw new SpecSourceException(
                    "folder source root does not exist or is not a directory: " + root + " (uri: " + uri + ")");
        }
        List<ResolvedSpec> packs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Path candidate : subdirectories(root)) {
            if (!Files.isRegularFile(candidate.resolve("manifest.yml"))) {
                continue;
            }
            ValidationResult result = validator.validate(candidate);
            if (result.isValid()) {
                packs.add(new ResolvedSpec(candidate, readName(candidate), PackContentHasher.hash(candidate), true));
            } else {
                warnings.add("pack '" + candidate.getFileName() + "' failed validation: "
                        + result.issues().stream().map(ValidationIssue::message).toList());
            }
        }
        if (packs.isEmpty()) {
            warnings.add("no valid agent specs found in folder source " + root);
        }
        return new ResolvedSpecSource(new ResolvedSpecSource.Origin(uri, root), packs, warnings);
    }

    private List<Path> subdirectories(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + dir, e);
        }
    }

    private String readName(Path candidate) {
        try {
            return reader.read(candidate).manifest().name();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read manifest of validated pack " + candidate, e);
        }
    }
}
