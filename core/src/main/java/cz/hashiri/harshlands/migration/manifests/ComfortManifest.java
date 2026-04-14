package cz.hashiri.harshlands.migration.manifests;

import cz.hashiri.harshlands.migration.ModuleManifest;

import java.util.Set;

public class ComfortManifest extends ModuleManifest {

    @Override
    public String moduleId() {
        return "comfort";
    }

    @Override
    protected Set<String> translationRoots() {
        return Set.of(
                "Messages",
                "CabinFever"
        );
    }
}
