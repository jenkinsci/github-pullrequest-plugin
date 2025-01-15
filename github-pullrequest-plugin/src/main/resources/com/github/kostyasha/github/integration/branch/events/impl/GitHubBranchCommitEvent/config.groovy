package com.github.kostyasha.github.integration.branch.events.impl.GitHubBranchCommitEvent

import com.github.kostyasha.github.integration.branch.events.impl.GitHubBranchCommitEvent
import lib.FormTagLib

def f = namespace(FormTagLib)

if (instance == null) {
    instance = new GitHubBranchCommitEvent()
}

f.block {
    f.entry() {
        f.hetero_list(name: "checks",
                items: instance.checks,
                descriptors: descriptor.getEventDescriptors(),
                hasHeader: true
        )
    }
}
