package com.embabel.smith.studio.rest;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.SkillContentHash;
import com.embabel.smith.common.io.SkillPackReader;
import com.embabel.smith.common.io.Yaml;
import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.skillsource.GitSkillSource;
import com.embabel.smith.skillsource.ResolvedPack;
import com.embabel.smith.skillsource.ResolvedSkillSource;
import com.embabel.smith.skillsource.SkillSource;
import com.embabel.smith.studio.rest.dto.PackInfo;
import com.embabel.smith.studio.rest.dto.SourceInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The studio's view over its skill sources (change 06): lists the configured sources, lists the
 * packs of a source with their lifecycle state and content-hash drift status, and reads a named
 * pack for the Security / Trust / Eval panes. Offline — reads from disk, no model calls.
 */
@Service
public class SourceService {

    private final List<String> configuredSources;
    private final Path cacheRoot;
    private final SkillPackReader reader = new SkillPackReader();

    public SourceService(@Value("${smith.studio.sources:}") String sources,
                         @Value("${smith.studio.source-cache-dir:}") String cacheDir) {
        this.configuredSources = split(sources);
        this.cacheRoot = cacheRoot(cacheDir);
    }

    /** The configured sources the studio offers, with their kind. */
    public List<SourceInfo> listSources() {
        return configuredSources.stream().map(uri -> new SourceInfo(uri, Sources.kind(uri))).toList();
    }

    /** The packs of a source, each with its lifecycle state, baseline, and drift status. */
    public List<PackInfo> listPacks(String sourceUri) {
        ResolvedSkillSource resolved = resolve(sourceUri);
        List<PackInfo> packs = new ArrayList<>();
        for (ResolvedPack pack : resolved.packs()) {
            LifecycleMetadata sidecar = readSidecar(pack.root());
            // The sidecar records the pack's semantic content hash (SkillContentHash) — the same
            // scheme the builder stamps and the loop compares — so both the displayed hash and the
            // drift flag are assessed on that, not on ResolvedPack's raw file-bytes hash, which
            // never matches the sidecar.
            String hash = contentHashOf(pack.root());
            boolean drift = sidecar != null && sidecar.contentHash() != null
                    && hash != null && !hash.equals(sidecar.contentHash());
            packs.add(new PackInfo(
                    pack.name(),
                    hash != null ? hash : pack.contentHash(),
                    sidecar == null ? null : sidecar.state().name(),
                    sidecar == null ? null : sidecar.baselinePassRate(),
                    drift,
                    sidecar == null ? null : sidecar.updatedAt()));
        }
        return packs;
    }

    /**
     * The goal names a source's packs export (the Run pane's goal picker): each pack exports the
     * goal named in its goal spec, and the run is addressed by goal, not pack name. Packs that
     * cannot be read, or carry no goal, contribute none.
     */
    public List<String> listGoals(String sourceUri) {
        ResolvedSkillSource resolved = resolve(sourceUri);
        List<String> goals = new ArrayList<>();
        for (ResolvedPack pack : resolved.packs()) {
            try {
                SkillPack read = reader.read(pack.root());
                if (read.goal() != null) {
                    goals.add(read.goal().name());
                }
            } catch (IOException e) {
                // an unreadable pack exports no goal to offer
            }
        }
        return goals;
    }

    /** Reads the named pack from a source (for the Security / Trust / Eval panes). */
    public SkillPack readPack(String sourceUri, String skillName) {
        ResolvedSkillSource resolved = resolve(sourceUri);
        for (ResolvedPack pack : resolved.packs()) {
            if (pack.name().equals(skillName)) {
                try {
                    return reader.read(pack.root());
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot read pack '" + skillName + "'", e);
                }
            }
        }
        throw new IllegalArgumentException("skill '" + skillName + "' not found in source " + sourceUri);
    }

    private ResolvedSkillSource resolve(String sourceUri) {
        if (sourceUri == null || sourceUri.isBlank()) {
            throw new IllegalArgumentException("no source selected");
        }
        SkillSource source = Sources.parse(sourceUri, cacheRoot);
        return source.resolve();
    }

    /** The pack's semantic content hash, or {@code null} when the pack cannot be read. */
    private String contentHashOf(Path root) {
        try {
            return SkillContentHash.of(reader.read(root));
        } catch (IOException e) {
            return null;
        }
    }

    /** The pack's lifecycle sidecar, or {@code null} when it has none. */
    private LifecycleMetadata readSidecar(Path root) {
        Path sidecar = root.resolve("lifecycle.yml");
        if (!Files.isRegularFile(sidecar)) {
            return null;
        }
        try {
            return Yaml.mapper().readValue(Files.readString(sidecar), LifecycleMetadata.class);
        } catch (IOException e) {
            return null; // a corrupt sidecar is treated as absent (drift is then not reported)
        }
    }

    private static List<String> split(String sources) {
        List<String> out = new ArrayList<>();
        if (sources != null) {
            for (String s : sources.split(",")) {
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return List.copyOf(out);
    }

    private static Path cacheRoot(String configured) {
        if (configured == null || configured.isBlank()) {
            return GitSkillSource.defaultCacheRoot();
        }
        return expandHome(configured.trim());
    }

    private static Path expandHome(String p) {
        if (p.equals("~")) {
            return Path.of(System.getProperty("user.home"));
        }
        if (p.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), p.substring(2));
        }
        return Path.of(p);
    }
}
