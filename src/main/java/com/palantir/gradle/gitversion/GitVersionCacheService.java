/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.gitversion;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.process.ExecOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class GitVersionCacheService implements BuildService<BuildServiceParameters.None> {
    public static final String NAME = "GitVersionCacheService";
    private final ExecOperations execOperations;

    @Inject
    public GitVersionCacheService(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    private static final Logger log = LoggerFactory.getLogger(GitVersionCacheService.class);

    private final Timer timer = new Timer();
    private final ConcurrentMap<String, VersionDetails> versionDetailsMap = new ConcurrentHashMap<>();

    public final String getGitVersion(File project, Object args) {
        return getVersionDetails(project, args).getVersion();
    }

    public final VersionDetails getVersionDetails(File project, Object args) {
        File gitDir = getRootGitDir(project);
        GitVersionArgs gitVersionArgs = GitVersionArgs.fromGroovyClosure(args);
        String key = gitDir.toPath() + "|" + gitVersionArgs.getPrefix();
        VersionDetails versionDetails =
                versionDetailsMap.computeIfAbsent(key, _k -> createVersionDetails(gitDir, gitVersionArgs));
        return versionDetails;
    }

    public static VersionDetails getVersionDetails(ExecOperations execOperations, File projectDir) {
        File gitDir = getRootGitDir(projectDir);
        GitVersionArgs gitVersionArgs = new GitVersionArgs();
        Timer timer = new Timer();
        return TimingVersionDetails.wrap(timer, new VersionDetailsImpl(Git.fromGitDirectory(execOperations, gitDir), gitVersionArgs));
    }

    private VersionDetails createVersionDetails(File gitDir, GitVersionArgs args) {
        return TimingVersionDetails.wrap(timer, new VersionDetailsImpl(Git.fromGitDirectory(execOperations, gitDir), args));
    }

    public final Timer timer() {
        return timer;
    }

    private static File getRootGitDir(File currentRoot) {
        File gitDir = scanForRootGitDir(currentRoot);
        if (!gitDir.exists()) {
            throw new IllegalArgumentException("Cannot find '.git' directory");
        }
        return gitDir;
    }

    private static File scanForRootGitDir(File currentRoot) {
        File gitDir = new File(currentRoot, ".git");

        if (gitDir.exists()) {
            return gitDir;
        }

        // stop at the root directory, return non-existing File object;
        if (currentRoot.getParentFile() == null) {
            return gitDir;
        }

        // look in parent directory;
        return scanForRootGitDir(currentRoot.getParentFile());
    }

    public static Provider<GitVersionCacheService> getSharedGitVersionCacheService(Project project) {
        return getSharedGitVersionCacheService(project.getGradle().getSharedServices());
    }

    public static Provider<GitVersionCacheService> getSharedGitVersionCacheService(BuildServiceRegistry registry) {
        return registry.registerIfAbsent(NAME, GitVersionCacheService.class, _spec -> {});
    }
}
