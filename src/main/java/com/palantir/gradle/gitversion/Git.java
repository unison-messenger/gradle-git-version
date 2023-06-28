/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Git {
    private static final String DOT_GIT_DIR_PATH = "/.git";

    private static final Logger log = LoggerFactory.getLogger(Git.class);

    private final ExecOperations execOperations;
    private final File worktreeDirectory;

    static Git fromGitDirectory(ExecOperations execOperations, File gitDirectory) {
        String gitDirStr = gitDirectory.toString();
        String projectDir = gitDirStr.substring(0, gitDirStr.length() - DOT_GIT_DIR_PATH.length());

        return new Git(execOperations, new File(projectDir));
    }

    Git(ExecOperations execOperations, File worktreeDirectory) {
        this(execOperations, worktreeDirectory, false);
    }

    @VisibleForTesting
    Git(ExecOperations execOperations, File worktreeDirectory, boolean testing) {
        this.execOperations = execOperations;
        if (!gitCommandExists()) {
            throw new RuntimeException("Git not found in project");
        }
        this.worktreeDirectory = worktreeDirectory;
        if (testing && !checkIfUserIsSet()) {
            setGitUser();
        }
    }

    private String runGitCmd(String... commands) throws IOException, InterruptedException {
        return runGitCmd(new HashMap<>(), commands);
    }

    private String runGitCmd(Map<String, String> envvars, String... commands) throws IOException, InterruptedException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ExecResult execResult = execOperations.exec(execSpec -> {
            execSpec.setWorkingDir(worktreeDirectory);
            execSpec.setExecutable("git");
            execSpec.setArgs(Arrays.asList(commands));
            execSpec.setEnvironment(envvars);
            execSpec.setStandardOutput(stdout);
        });

        int exitCode = execResult.getExitValue();
        if (exitCode != 0) {
            return "";
        } else {
            String output = new String(stdout.toByteArray(), Charset.defaultCharset());
            return output.trim();
        }
    }

    public String runGitCommand(Map<String, String> envvar, String... command) {
        try {
            return runGitCmd(envvar, command);
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git command {} failed.\n", command, e);
            return null;
        }
    }

    public String runGitCommand(String... command) {
        return runGitCommand(new HashMap<>(), command);
    }

    private boolean checkIfUserIsSet() {
        try {
            String userEmail = runGitCmd("config", "user.email");
            if (userEmail.isEmpty()) {
                return false;
            }
            return true;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git config user.email failed", e);
            return false;
        }
    }

    private void setGitUser() {
        try {
            runGitCommand("config", "--global", "user.email", "email@example.com");
            runGitCommand("config", "--global", "user.name", "name");
        } catch (RuntimeException e) {
            log.debug("Native git set user failed", e);
        }
    }

    public String getCurrentBranch() {
        try {
            String branch = runGitCmd("branch", "--show-current");
            if (branch.isEmpty()) {
                return null;
            }
            return branch;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git branch --show-current failed", e);
            return null;
        }
    }

    public String getCurrentHeadFullHash() {
        try {
            return runGitCmd("rev-parse", "HEAD");
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git rev-parse HEAD failed", e);
            return null;
        }
    }

    public Boolean isClean() {
        try {
            String result = runGitCmd("status", "--porcelain");
            if (result.isEmpty()) {
                return true;
            }
            return false;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git status --porcelain failed", e);
            return null;
        }
    }

    public String describe(String prefix) {
        try {
            String result = runGitCmd(
                    "describe",
                    "--tags",
                    "--always",
                    "--first-parent",
                    "--abbrev=7",
                    "--match=" + prefix + "*",
                    "HEAD");
            if (result.isEmpty()) {
                return null;
            }
            return result;
        } catch (IOException | InterruptedException | RuntimeException e) {
            log.debug("Native git describe failed", e);
            return null;
        }
    }

    private boolean gitCommandExists() {
        try {
            // verify that "git" command exists (throws exception if it does not)
            ExecResult execResult = execOperations.exec(execSpec -> {
                execSpec.commandLine("git", "version");
                execSpec.setStandardOutput(new ByteArrayOutputStream());
            });
            if (execResult.getExitValue() != 0) {
                throw new IllegalStateException("error invoking git command");
            }
            return true;
        } catch (RuntimeException e) {
            log.debug("Native git command not found", e);
            return false;
        }
    }
}
