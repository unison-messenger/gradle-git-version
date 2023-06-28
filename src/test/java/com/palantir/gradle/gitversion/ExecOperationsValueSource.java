package com.palantir.gradle.gitversion;

import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;

public abstract class ExecOperationsValueSource implements ValueSource<ExecOperations, ValueSourceParameters.None> {
    @Inject
    abstract ExecOperations getExecOperations();

    @Nullable
    @Override
    public ExecOperations obtain() {
        return getExecOperations();
    }
}
