package com.github.kostyasha.github.integration.branch.events.impl.commitchecks.impl;

import com.github.kostyasha.github.integration.branch.GitHubBranch;
import com.github.kostyasha.github.integration.branch.GitHubBranchCause;
import com.github.kostyasha.github.integration.branch.GitHubBranchRepository;

import org.junit.Before;
import org.junit.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit.ShortInfo;
import org.kohsuke.github.GHCompare.Commit;
import org.kohsuke.github.GHCompare.InnerCommit;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

public class GitHubBranchCommitMessageCheckTest {

    private static final String PATTERN = ".*\\[maven-release-plugin\\].*";

    private GitHubBranchCommitMessageCheck event;

    @Mock
    private Commit mockCommit;

    private Commit[] mockCommits;

    @Mock
    private InnerCommit mockInnerCommit;

    @Mock
    private GitHubBranch mockLocalBranch;

    @Mock
    private GHBranch mockRemoteBranch;

    @Mock
    private GitHubBranchRepository mockRepo;

    private GitHubBranchCause result;

    @Mock
    private ShortInfo mockShortCommit;

    @Before
    public void begin() {
        MockitoAnnotations.initMocks(this);
        event = new GitHubBranchCommitMessageCheck();
        when(mockRemoteBranch.getName()).thenReturn("remoteBranch");
    }

    @Test
    public void testComparedCommitsSkipBuild() {
        givenCommitMessages();
        givenAMessagePattern();
        givenMessageShouldBeExcluded();
        whenComparedCommitsAreChecked();
        thenBuildIsSkipped();
    }

    @Test
    public void testComparedCommitsReturnNoCause() {
        givenCommitMessages();
        givenAMessagePattern();
        whenComparedCommitsAreChecked();
        thenBuildIsNotSkipped();
    }

    @Test
    public void testLastCommitReturnNoCause() throws Exception {
        givenACommitMessage();
        givenAMessagePattern();
        whenLastCommitIsChecked();
        thenBuildIsNotSkipped();
    }

    @Test
    public void testLastCommitSkipBuild() throws Exception {
        givenACommitMessage();
        givenAMessagePattern();
        givenMessageShouldBeExcluded();
        whenLastCommitIsChecked();
        thenBuildIsSkipped();
    }

    @Test
    public void testNoPatternsSpecified() {
        givenCommitMessages();
        whenComparedCommitsAreChecked();
        thenCheckHasNoEffect();
    }

    private void givenACommitMessage() throws IOException {
        when(mockShortCommit.getMessage()).thenReturn("[maven-release-plugin] commit message");
        when(mockCommit.getCommitShortInfo()).thenReturn(mockShortCommit);
    }

    private void givenCommitMessages() {
        when(mockInnerCommit.getMessage()).thenReturn("commit message");
        when(mockInnerCommit.getMessage()).thenReturn("[maven-release-plugin] commit message");

        when(mockCommit.getCommit()).thenReturn(mockInnerCommit);

        mockCommits = new Commit[] { mockCommit, mockCommit };
    }

    private void givenAMessagePattern() {
        event.setMatchCriteria(PATTERN);
    }

    private void givenMessageShouldBeExcluded() {
        event.setExclude(true);
    }

    private void thenBuildIsSkipped() {
        assertThat("build not triggered", result.isSkip(), equalTo(true));
    }

    private void thenBuildIsNotSkipped() {
        assertThat("build triggered", result.isSkip(), equalTo(false));
    }

    private void thenCheckHasNoEffect() {
        assertThat("build triggered", result, nullValue());
    }

    private void whenComparedCommitsAreChecked() {
        result = event.check(mockRemoteBranch, mockRepo, mockCommits);
    }

    private void whenLastCommitIsChecked() {
        result = event.check(mockRemoteBranch, mockRepo, mockCommit);
    }
}
