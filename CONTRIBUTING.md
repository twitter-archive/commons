# Twitter Commons Contributors Guide

This page documents how to make contributions to Twitter Commons. If you've developed a change to
Twitter Commons, it passes all tests (including the ones written for your change), and you'd like to
"send it upstream", here's what to do:

## Join the Conversation

Join the [`twitter-commons` Google Group][group]
to keep in touch with other Twitter Commons users and Developers.

Join the [`pants-reviews` Google Group](https://groups.google.com/forum/#!forum/pants-reviews) to
see code reviews, including for the [Pants Build Tool](http://pantsbuild.github.io) which Twitter
Commons uses.

Watch the [`twitter/commons` Github project](https://github.com/twitter/commons) for notifications
of new issues and updates.

## Life of a Change

Let's walk through the process of making a change to Twitter Commons. At a high level, the steps
are:

1. Identify the change you'd like to make (e.g.: fix a bug, add a feature).
2. Get the code.
3. Make your change on a branch.
4. Write test(s) to validate your change works as expected
5. Get a code review.
6. Commit your change to master.

Please note--despite being hosted on GitHub--we *do not use pull requests to merge to master*; we
prefer to maintain a linear commit history and to *review code with [RBCommons][rbcommons]*. You
will, however, need to create a Github pull request in order to kick off CI.

### Identify the change

It's a good idea to make sure the work you'll be embarking on is generally agreed to be in a useful
direction for the project before getting too far along.

If there is a pre-existing Github issue filed and un-assigned, feel free to grab it and ask any
clarifying questions needed on the Github issue. If there is an issue you'd like to work on that's
assigned and stagnant, please ping the assignee on the Github issue before taking over ownership for
 the issue.

If you have an idea for new work that's not yet been discussed on a Github issue or
[`twitter-commons@`][group], then file an issue on Github to vet the proposal.


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

Now that your change is complete, post it for review. We use [rbcommons.com][rbcommons]
to host code reviews:

#### Posting the First Draft

Before posting your first review, you must create an account on
[rbcommons.com][rbcommons]. To create one, visit
[the login page](https://rbcommons.com/account/login/) and click "Create one now."

To set up local tools, run `./rbt help`. `./rbt` is a wrapper around the usual RBTools `rbt` script.
The first time this runs it will bootstrap and you'll see a lot of building info.

Before you post your review to [RBCommons][rbcommons] you should create a Github pull request in
order to kick off a Travis-CI run against your change.

#### Post your change for review:

    $ ./rbt post -o -g

The first time you post, `rbt` asks you to log in using your [RBCommons][rbcommons] credentials.
Subsequent runs use your cached login credentials.

This post creates a new review, but *does not yet publish it*.

At the provided URL, there's a web form. To get your change reviewed, you must fill in the change
description, reviewers, testing done, etc. To make sure it gets seen by the appropriate people and
that they have the appropriate context, add:

* `pants-reviews` to the Groups field
* Any specific reviewers to the People field
* The pull request number from your _Github pull request in the Bug field_
* Your git branch name in the Branch field.

When the review looks good, publish it by clicking the Publish button on the green bar at the top of
the screen. An email will be sent to the `pants-reviews@` mailing list and the reviewers will take a
look. (For your first review, double-check that the mail got sent; [RBCommons][rbcommons] tries to
"spoof" mail from you and it doesn't work for everybody's email address. If your address doesn't
work, you might want to use another one.)

#### Iterating

If reviewers have feedback, there might be a few iterations before finally getting a `Ship It!`. As
reviewers enter feedback, the [RBCommons][rbcommons] page updates; it should also send you mail (but
sometimes its "spoof" fails).

If those reviews inspire you to change some code, great. Change some code, commit locally. To update
the code review with the new diff where `<RB_ID>` is a review number like 123:

    $ ./rbt post -o -r <RB_ID>

Look over the fields in the web form; perhaps some could use updating. Press the web form's Publish
button.

If need a reminder of your review number, you can get a quick list with:

    $ ./rbt status
    r/1234 - Make pants go even faster

### Commit Your Change
.
At this point you've made a change, had it reviewed and are ready to complete things by getting your
 change in master. (If you're not a committer, please ask one to do this section for you.)

    $ cd /path/to/commons/repo
    $ git checkout master
    $ git pull
    $ ./rbt patch -c <RB_ID>
    $ ./build-support/bin/ci.sh

Here, ensure that the commit message generated from the review summary is accurate, and that the
resulting commit contains the changes you expect. If `rbt` gives mysterious errors, pass `--debug`
for more info. If that doesn't clarify the problem, mail `pants-devel@` (and include that `--debug`
output).

Lastly,

    $ git push origin master

The very last step is closing the review as "Submitted"

    $ ./rbt close <RB_ID>

And the change is complete.

[group]: https://groups.google.com/forum/#!forum/twitter-commons "Twitter Commons Google Group"
[rbcommons]: http://rbcommons.com "RBCommons"
