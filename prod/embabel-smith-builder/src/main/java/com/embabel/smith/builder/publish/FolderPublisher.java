package com.embabel.smith.builder.publish;

import com.embabel.smith.builder.lifecycle.LifecycleMetadata;
import com.embabel.smith.builder.lifecycle.LifecycleStamper;
import com.embabel.smith.common.io.AgentSpecWriter;
import com.embabel.smith.common.model.AgentSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Publishes a agent spec as a subdirectory named after the pack under the destination folder
 * (design D3): the pack files plus the {@code lifecycle.yml} sidecar. A same-named subdirectory
 * that already exists is a collision unless {@code --overwrite} replaces it.
 */
public class FolderPublisher implements Publisher {

    private final PublishTarget target;
    private final AgentSpecWriter writer = new AgentSpecWriter();
    private final LifecycleStamper stamper = new LifecycleStamper();

    public FolderPublisher(PublishTarget target) {
        this.target = target;
    }

    @Override
    public PublishResult publish(AgentSpec pack, LifecycleMetadata lifecycle, boolean overwrite, boolean push)
            throws IOException {
        Path packDir = Path.of(target.location()).resolve(pack.name());
        checkCollision(packDir, pack.name(), overwrite);
        if (Files.isDirectory(packDir)) {
            deleteTree(packDir);
        }
        writer.write(pack, packDir);
        stamper.write(packDir, lifecycle);
        return new PublishResult(packDir, false);
    }

    private static void checkCollision(Path packDir, String name, boolean overwrite) {
        if (Files.isDirectory(packDir) && !overwrite) {
            throw new CollisionException("A agent spec named '" + name
                    + "' already exists at " + packDir + "; pass --overwrite to replace it");
        }
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot delete " + path, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + dir, e);
        }
    }
}
