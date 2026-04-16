package cz.hashiri.harshlands.migration.manifests;

import cz.hashiri.harshlands.migration.ModuleManifest;

import java.util.Set;

public class IntegrationsManifest extends ModuleManifest {

    @Override
    public String moduleId() {
        return "integrations";
    }

    @Override
    protected Set<String> translationRoots() {
        return Set.of(
                "PlaceholderAPI",
                "AuraSkills"
        );
    }
}
