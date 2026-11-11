package com.embabel.smith.studio.rest;

import com.embabel.smith.common.model.SkillPack;
import com.embabel.smith.studio.rest.dto.DiscoveryRecordDto;
import com.embabel.smith.studio.rest.dto.SavePackRequest;
import com.embabel.smith.studio.rest.dto.SavePackResponse;
import com.embabel.smith.studio.rest.dto.StoredPackInfo;
import com.embabel.smith.studio.rest.dto.StoredPackResponse;
import com.embabel.smith.studio.store.ProcessStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The workspace's durable store surface: save, list, and reload generated skill-packs, and review
 * the discovery history. Offline — no model call.
 */
@RestController
public class StoreController {

    private final ProcessStore store;

    public StoreController(ProcessStore store) {
        this.store = store;
    }

    @GetMapping("/api/store/packs")
    public List<StoredPackInfo> listPacks() {
        return store.listPacks().stream()
                .map(p -> new StoredPackInfo(p.name(), p.goal(), p.steps(), p.savedAt()))
                .toList();
    }

    @GetMapping("/api/store/packs/{name}")
    public StoredPackResponse loadPack(@PathVariable("name") String name) {
        SkillPack pack = store.loadPack(name);
        if (pack == null) {
            return StoredPackResponse.failed("No saved pack named '" + name + "'.");
        }
        return StoredPackResponse.of(pack);
    }

    @PostMapping("/api/store/packs")
    public SavePackResponse savePack(@RequestBody SavePackRequest request) {
        SkillPack pack = request.pack();
        if (pack == null || pack.name() == null || pack.name().isBlank()) {
            return SavePackResponse.failed("No pack to save.");
        }
        try {
            return SavePackResponse.of(store.savePack(pack).name());
        } catch (RuntimeException e) {
            return SavePackResponse.failed(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @GetMapping("/api/store/discoveries")
    public List<DiscoveryRecordDto> discoveries() {
        return store.discoveries().stream()
                .map(d -> new DiscoveryRecordDto(d.timestamp(), d.sourceType(), d.location(), d.docCount()))
                .toList();
    }
}
