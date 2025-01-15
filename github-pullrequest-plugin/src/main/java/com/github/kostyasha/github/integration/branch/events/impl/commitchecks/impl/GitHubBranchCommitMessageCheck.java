package com.github.kostyasha.github.integration.branch.events.impl.commitchecks.impl;

import com.github.kostyasha.github.integration.branch.GitHubBranchCause;
import com.github.kostyasha.github.integration.branch.GitHubBranchRepository;
import com.github.kostyasha.github.integration.branch.events.impl.commitchecks.GitHubBranchCommitCheck;
import com.github.kostyasha.github.integration.branch.events.impl.commitchecks.GitHubBranchCommitCheckDescriptor;
import hudson.Extension;
import hudson.ExtensionPoint;
import org.jenkinsci.Symbol;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCompare.Commit;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GitHubBranchCommitMessageCheck extends GitHubBranchCommitCheck implements ExtensionPoint {
    private static final String DISPLAY_NAME = "Commit Message Pattern";

    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final Logger LOG = LoggerFactory.getLogger(GitHubBranchCommitMessageCheck.class);

    private boolean exclude;

    private Set<String> matchCriteria;

    @DataBoundConstructor
    public GitHubBranchCommitMessageCheck() {
        this.matchCriteria = Collections.emptySet();
    }

    @Override
    public GitHubBranchCause doCheck(GHBranch remoteBranch, GitHubBranchRepository localRepo, GHCommit commit) throws IOException {
        List<String> messages = Arrays.asList(commit.getCommitShortInfo().getMessage());
        return check(remoteBranch, localRepo, () -> messages);
    }

    @Override
    public GitHubBranchCause check(GHBranch remoteBranch, GitHubBranchRepository localRepo, Commit[] commits) {
        return check(remoteBranch, localRepo, () -> Stream.of(commits)
                .map(commit -> commit.getCommit().getMessage())
                .collect(Collectors.toList()));
    }

    public String getMatchCriteria() {
        return String.join(LINE_SEPARATOR, matchCriteria);
    }

    public boolean isExclude() {
        return exclude;
    }

    @DataBoundSetter
    public void setExclude(boolean exclude) {
        this.exclude = exclude;
    }

    @DataBoundSetter
    public void setMatchCriteria(String matchCriteria) {
        this.matchCriteria = Stream.of(matchCriteria
                .split(LINE_SEPARATOR))
                .collect(Collectors.toSet());
    }

    private <T> GitHubBranchCause check(GHBranch remoteBranch, GitHubBranchRepository localRepo, Supplier<List<String>> supplier) {
        if (matchCriteria.isEmpty()) {
            LOG.warn("Commit message event added but no match criteria set, all commits are allowed.");
            return null;
        }

        String name = remoteBranch.getName();
        List<String> messages = supplier.get();
        if (commitsAreAllowed(messages)) {
            LOG.debug("Commit messages {} for branch [{}] allowed, commit ignored.", messages, name);
            return toCause(remoteBranch, localRepo, false, "Commit messages %s for branch [%s] allowed by check.", messages, name);
        }

        return toCause(remoteBranch, localRepo, true, "Commit messages %s for branch [%s] not allowed by check.", messages, name);
    }

    private boolean commitsAreAllowed(List<String> messages) {
        boolean allowed = false;
        for (String message : messages) {
            if (commitIsAllowed(message)) {
                allowed = true;
                break;
            }
        }
        return allowed;
    }

    private boolean commitIsAllowed(String message) {
        for (String pattern : matchCriteria) {
            LOG.debug("Checking commit [{}] against pattern [{}] - exclude [{}]", message, pattern, exclude);
            if (matches(message, pattern)) {
                if (exclude) {
                    LOG.debug("Commit [{}] matches pattern [{}], will be excluded", message, pattern);
                    return false;
                }
                LOG.debug("Commit [{}] matched pattern [{}] and is marked for inclusion", message, pattern);
                return true;
            }
        }

        LOG.debug("Commit [{}] matched no patterns, included [{}]", message, exclude);
        return exclude;
    }

    private boolean matches(String message, String pattern) {
        try {
            return Pattern.compile(pattern).matcher(message).matches();
        } catch (PatternSyntaxException e) {
            LOG.error("Invalid pattern [{}] detected checking commit [{}]", pattern, message, e);
            return false;
        }
    }

    private GitHubBranchCause toCause(GHBranch remoteBranch, GitHubBranchRepository localRepo, boolean skip, String message,
                                      Object... args) {
        return new GitHubBranchCause(remoteBranch, localRepo, String.format(message, args), skip);
    }

    @Symbol("commitMessagePattern")
    @Extension
    public static class DescriptorImpl extends GitHubBranchCommitCheckDescriptor {
        @NonNull
        @Override
        public final String getDisplayName() {
            return DISPLAY_NAME;
        }
    }
}
