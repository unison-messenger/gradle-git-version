package com.palantir.gradle.gitversion;

import java.io.File;
import javax.inject.Inject;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;

public abstract class GitVersionDetailsProvider implements ValueSource<VersionDetails, GitVersionDetailsProvider.Parameters> {
    private final ExecOperations execOperations;

    @Inject
    public GitVersionDetailsProvider(ExecOperations execOperations) {
        this.execOperations = execOperations;
   }

    @Override
    public VersionDetails obtain() {
        return GitVersionCacheService.getVersionDetails(execOperations, getParameters().getProjectDir().get());
    }

    interface Parameters extends ValueSourceParameters {
        Property<File> getProjectDir();
    }
}
