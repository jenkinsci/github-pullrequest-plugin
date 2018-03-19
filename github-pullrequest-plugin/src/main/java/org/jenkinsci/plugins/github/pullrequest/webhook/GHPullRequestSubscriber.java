package org.jenkinsci.plugins.github.pullrequest.webhook;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import hudson.Extension;
import hudson.model.Job;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.github.extension.GHEventsSubscriber;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRTrigger;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRTriggerMode;
import org.jenkinsci.plugins.github.util.FluentIterableWrapper;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GHEventPayload.IssueComment;
import org.kohsuke.github.GHEventPayload.PullRequest;
import org.kohsuke.github.GHEventPayload.PullRequestReview;
import org.kohsuke.github.GitHub;
import org.jenkinsci.plugins.github.pullrequest.utils.PRApprovalState;
import org.jenkinsci.plugins.github.pullrequest.utils.ReviewState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.File;


import static com.google.common.collect.Sets.immutableEnumSet;
import static java.lang.String.format;
import static org.jenkinsci.plugins.github.pullrequest.utils.JobHelper.ghPRTriggerFromJob;
import static org.jenkinsci.plugins.github.pullrequest.webhook.WebhookInfoPredicates.withPRTrigger;
import static org.jenkinsci.plugins.github.pullrequest.webhook.WebhookInfoPredicates.withRepo;
import static org.jenkinsci.plugins.github.util.JobInfoHelpers.isBuildable;

/**
 * Uses extension point from github-plugin to get events form standard github-webhook endpoint.
 * Subscribes on pull_request and issue_comment events.
 *
 * @author lanwen (Merkushev Kirill)
 */
@SuppressWarnings("unused")
@Extension
public class GHPullRequestSubscriber extends GHEventsSubscriber {
    private static final Logger LOGGER = LoggerFactory.getLogger(GHPullRequestSubscriber.class);

    @Override
    protected boolean isApplicable(Job<?, ?> job) {
        return withPRTrigger().apply(job);
    }

    @Override
    protected Set<GHEvent> events() {
        return immutableEnumSet(GHEvent.PULL_REQUEST, GHEvent.ISSUE_COMMENT);
    }

    @Override
    protected void onEvent(GHEvent event, String payload) {
        try {
            GitHub gh = GitHub.connectAnonymously();

            PullRequestInfo info = extractPullRequestInfo(event, payload, gh);


            LOGGER.info("TEST GHPullRequestSubscriber.onEvent()  \n\n ");

            LOGGER.info("Payload: {} \n\n ", payload);
            
            // JsonNode jsonNode = objectMapper.readTree(payload);
            // String text = jsonNode.get("requested_reviewers").asText(); 
            //ObjectMapper objectMapper = new ObjectMapper();
 


            for (Job job : getPRTriggerJobs(info.getRepo())) {
                GitHubPRTrigger trigger = ghPRTriggerFromJob(job);
                GitHubPRTriggerMode triggerMode = trigger.getTriggerMode();

                switch (triggerMode) {
                    case HEAVY_HOOKS_CRON:
                    case HEAVY_HOOKS: {
                        LOGGER.debug("Queued check for {} (PR #{}) after heavy hook", job.getName(), info.getNum());
                        trigger.queueRun(job, info.getNum());
                        break;
                    }
                    case LIGHT_HOOKS: {
                        LOGGER.warn("Unsupported LIGHT_HOOKS trigger mode");
//                        LOGGER.info("Begin processing hooks for {}", trigger.getRepoFullName(job));
//                        for (GitHubPREvent prEvent : trigger.getEvents()) {
//                            GitHubPRCause cause = prEvent.checkHook(trigger, parsedPayload, null);
//                            if (cause != null) {
//                                trigger.build(cause);
//                            }
//                        }
                        break;
                    }
                    default:
                        break;
                }
            }

        } catch (Exception e) {
            LOGGER.error("Can't process {} hook", event, e);
        }
    }

    private PullRequestInfo extractPullRequestInfo(GHEvent event, String payload, GitHub gh) throws IOException {
        switch (event) {
            case ISSUE_COMMENT: {
                IssueComment commentPayload = gh.parseEventPayload(new StringReader(payload), IssueComment.class);
                int prNumber = commentPayload.getIssue().getNumber();

                return new PullRequestInfo(commentPayload.getRepository().getFullName(), prNumber);
            }

            case PULL_REQUEST: {
                LOGGER.warn("\nParsing the pull request\n");
                PullRequest pr = gh.parseEventPayload(new StringReader(payload), PullRequest.class);

                List<GHUser> u = pr.getPullRequest().getRequestedReviewers();

                if(u.size() == 0){
                    LOGGER.warn("\nNo requested reviewers\n");
                }
                PRApprovalState pras = new PRApprovalState();
                for(int i = 0; i < u.size() ; i++){
                    LOGGER.warn("reviewer {}: {} ", i, u.get(i).getLogin());
                    pras.addReviewState(new ReviewState(u.get(i)));
                }
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.writeValue(new File("~/.jenkins/PR_TEST.json"),pras);
              
                return new PullRequestInfo(pr.getPullRequest().getRepository().getFullName(), pr.getNumber());
            }

            case PULL_REQUEST_REVIEW: {
                LOGGER.info("\n\n PullRequestReview received \n\n", payload);
            }
            default:
                throw new IllegalStateException(format("Did you add event %s in events() method?", event));
        }
    }

    static Set<Job> getPRTriggerJobs(final String repo) {
        final Set<Job> ret = new HashSet<>();

        ACL.impersonate(ACL.SYSTEM, () -> {
            List<Job> jobs = Jenkins.getActiveInstance().getAllItems(Job.class);
            ret.addAll(FluentIterableWrapper.from(jobs)
                    .filter(isBuildable())
                    .filter(withPRTrigger())
                    .filter(withRepo(repo))
                    .toSet()
            );
        });

        return ret;
    }
}
