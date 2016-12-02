# Twitter Commons Contributors Guide

This page documents how to make contributions to Twitter Commons. If you've developed a change to
Twitter Commons, it passes all tests (including the ones written for your change), and you'd like to
"send it upstream", here's what to do:

## Life of a Change

Let's walk through the process of making a change to Twitter Commons. At a high level, the steps
are:

1. Identify the change you'd like to make (e.g.: fix a bug, add a feature).
2. Get the code.
3. Make your change on a branch.
4. Write test(s) to validate your change works as expected
5. Get a code review via a github pull request.
6. Merge the pull request to master.

### Identify the change

It's a good idea to make sure the work you'll be embarking on is generally agreed to be in a useful
direction for the project before getting too far along.

If there is a pre-existing Github issue filed and un-assigned, feel free to grab it and ask any
clarifying questions needed on the Github issue. If there is an issue you'd like to work on that's
assigned and stagnant, please ping the assignee on the Github issue before taking over ownership for
 the issue.

If you have an idea for new work that's not yet been discussed on a Github issue, please file an issue
on Github to vet the proposal.


### Getting Twitter Commons Source Code

If you just want to compile and look at the source code, the easiest way is to clone the repo.

    $ git clone https://github.com/twitter/commons

If you would like to start developing patches and contributing them back, you will want to create a
fork of the repo using the instructions on github.com. With your fork, you can push branches and
run Travis-CI before your change is committed. Create a new branch off master and make changes.

    $ git checkout -b $FEATURE_BRANCH

### Run the CI Tests

Before posting a review but certainly before the branch ships you should run relevant tests. If
you're not sure what those are, run all the tests.

### Code Review

Now that your change is complete, post it for review. We use github pull requests
to host code reviews:

#### Posting the First Draft

Before requesting review, you should create a Github pull request in order to kick off a
Travis-CI run against your change.

To get your change reviewed, you should fill in the template in the default pull request description.
To make sure it gets seen by the appropriate people and that they have the appropriate context, you
should "@mention" a few people who recently editted the files that your commit changes.

### Commit Your Change

At this point you've made a change, had it reviewed and are ready to complete things by getting your
change in master. (If you're not a committer, please ask one to do this section for you.)

To merge, use the `Squash and Merge` button at the bottom of the pull request form, and copy the
review description (which should match the pull request template) as the commit message.
