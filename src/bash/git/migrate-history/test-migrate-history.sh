#!/usr/bin/env bash

# This is a test script for migrate-history.sh.  It sets up a couple
# of test repositories, then runs migrate-history.sh and checks that
# the results are as-expected.

die () {
    echo "$*"
    exit 1
}

test_start() {
    echo
    echo "$1"
    echo "================"
}

test_done() {
     # Prepare for next test
     # git fsck doesn't have a sensible exit stauts
    git fsck --no-progress 2>&1 | grep error && die "git fsck reported errors"
    git reset --hard "$clean_recipient"
}

#Test script for migrate-history.sh

cd $(dirname $0)

[[ -e immigrant ]] && die "Directory named immigrant already exists.  Please remove it."
[[ -e recpient ]] && die "Directory named recipient already exists. Please remove it."

#Create the immigrant repository
(
mkdir immigrant
cd immigrant
git init
git remote add origin https://example.com/immigrant.git
git remote add aremote https://example.org/immigrant.git
echo "This is the immigrant repo" > README
git add README

#We need a subdirectory
mkdir subdir
echo "subdir readme" > subdir/README
mkdir subdir/dir
echo "subdir/dir readme" > subdir/dir/README
git add subdir

git commit  -m 'commit 1'

#Now let's create some branching history
git checkout -b my/branch-b
git checkout master
echo "Branch the first" > README
git add README
git commit -m 'branch a'
git checkout master

#Contents of branch b
git checkout my/branch-b
echo "Branch the second" > README
git add README
echo "subdir/dir readme bis" > subdir/dir/README
git add subdir/dir/README
git commit -m 'branch b'
git checkout master

git merge my/branch-b #there are conflicts
echo "Merged" > README
git add README
git commit -m 'merged'
)
immigrant_revisions=$(
    cd immigrant
    git rev-list HEAD|wc -l
)

immigrant_subdir_revisions=$(
    cd immigrant/subdir
    git log --oneline .|wc -l
)

branch_b_revisions=$(
    cd immigrant
    git rev-list my/branch-b|wc -l
)

#Create the recipient repository
mkdir recipient
cd recipient
git init
echo -n "I am a recipient repo" > README
mkdir dir
echo "I am in a subdir" > dir/README
git add README dir/README
git commit -m 'I am a commit on recipient'
mkdir post
echo "I am in a different subdir" > post/README
git add post/README
git commit -m 'I am another commit on recipient'
master_revisions=$(git rev-list HEAD|wc -l)
clean_recipient=$(git rev-parse HEAD)

#migrate in the repo

#Tests
test_start "Testing migrate-history.sh with no options"

output=$(../migrate-history.sh ../immigrant) || die "Failed to migrate $output"
[[ $output == $(git rev-parse HEAD) ]] || die "Output consists of something other than sha of new HEAD $output"

#these strange echos are because OS X echo outputs some whitespace before the number of lines.
revisions=$(echo $(git rev-list HEAD|wc -l))
expected_revisions=$(echo $(expr $master_revisions + $immigrant_revisions))
[[ $revisions == $expected_revisions ]] || die "Wrong number of revisions (got $revisions expected $expected_revisions)"
[[ "I am a recipient repo" == $(cat README) ]] || die "We overwrote README in recipient"
[[ -e dir/README ]] || die "We deleted dir in recipient"
[[ -e post/README ]] || die "We deleted post in recipient"

[[ "Merged" == $(cat immigrant/README) ]] || die "Wrong content imported for immigrant/README"
migrated_revisions=$(git log --format=oneline immigrant | wc -l)
[[ $migrated_revisions == $immigrant_revisions ]] || die "Wrong number of revisions for immigrant"
last_immigrant_commit=$(git log --format=oneline --parents -n 1 immigrant)
echo "$last_immigrant_commit" | egrep '[a-f0-9]{40} [a-f0-9]{40} merged' > /dev/null || die "commit graph not imported (bad last commit)"
first_immigrant_commit=$(git log --format=oneline --parents immigrant | tail -1)
echo "$first_immigrant_commit" | egrep '[a-f0-9]{40} commit 1' > /dev/null || die "commit graph not imported (bad first commit)"

test_done || exit 1

test_start "Testing migrate-history.sh with -s"
../migrate-history.sh -s the_subdir ../immigrant || die "Failed to migrate"
[[ "I am a recipient repo" == $(cat README) ]] || die "We overwrote README in recipient"
[[ -e dir/README ]] || die "We deleted dir in recipient"
[[ -e post/README ]] || die "We deleted post in recipient"

[[ ! -e immigrant ]] || die "We migrated to the wrong place"

[[ "Merged" == $(cat the_subdir/README) ]] || die "Wrong content imported for the_subdir/README"
migrated_revisions=$(git log --format=oneline the_subdir | wc -l)
[[ $migrated_revisions == $immigrant_revisions ]] || die "Wrong number of revisions for immigrant"
last_immigrant_commit=$(git log --format=oneline --parents -n 1 the_subdir)
echo "$last_immigrant_commit" | egrep '[a-f0-9]{40} [a-f0-9]{40} merged' > /dev/null || die "commit graph not imported (bad last commit)"

test_done || exit 1

test_start "Testing migrate-history.sh with -b"
../migrate-history.sh -b my/branch-b ../immigrant || die "Failed to migrate"
[[ "I am a recipient repo" == $(cat README) ]] || die "We overwrote README in recipient"
[[ -e dir/README ]] || die "We deleted dir in recipient"
[[ -e post/README ]] || die "We deleted post in recipient"

[[ "Branch the second" == $(cat immigrant/README) ]] || die "Wrong content imported for immigrant/README"
migrated_revisions=$(git log --format=oneline immigrant | wc -l)
[[ $migrated_revisions == $branch_b_revisions ]] || die "Wrong number of revisions for immigrant"
last_immigrant_commit=$(git log --format=oneline --parents -n 1 immigrant)
echo "$last_immigrant_commit" | egrep '[a-f0-9]{40} branch b' > /dev/null || die "commit graph not imported (bad last commit)"

test_done || exit 1

test_start "Testing migrate-history.sh with -o"
../migrate-history.sh -o subdir -s imported ../immigrant || die "Failed to migrate"
[[ "I am a recipient repo" == $(cat README) ]] || die "We overwrote README in recipient"
[[ -e dir/README ]] || die "We deleted dir in recipient"
[[ -e post/README ]] || die "We deleted post in recipient"

[[ ! -e immigrant ]] || die "We migrated to the wrong place"

[[ "subdir readme" == $(cat imported/README) ]] || die "Wrong content imported for imported/README"
[[ "subdir/dir readme bis" == $(cat imported/dir/README) ]] || die "Wrong content imported for imported/dir/README"
migrated_revisions=$(git log --format=oneline imported | wc -l)
[[ $migrated_revisions == $immigrant_subdir_revisions ]] || die "Wrong number of revisions for immigrant"
last_immigrant_commit=$(git log --format=oneline --parents -n 1 imported)
echo "$last_immigrant_commit" | egrep 'branch b' > /dev/null || die "commit graph not imported (bad last commit)"

test_done || exit 1

#Tests
test_start "Testing migrate-history.sh with a remote branch"
../migrate-history.sh  -s imported -b origin/fleem ../immigrant 2>&1 | grep -q "Use a local branch" ||
die "Should have failed with remote branch origin/fleem"

test_start "Testing migrate-history.sh with a different remote branch"
../migrate-history.sh  -s imported -b aremote/fleem ../immigrant 2>&1 | grep -q "Use a local branch" ||
die "Should have failed with remote branch aremote/fleem"

test_done || exit 1

test_start "Testing migrate-history.sh with failed merge"
../migrate-history.sh -s dir ../immigrant 2>&1 | grep -q "^Conflict:" ||
die "Should have failed with confict for README"

test_done || exit 1

test_start "Testing migrate-history.sh with -c existing"
../migrate-history.sh -c existing -s dir ../immigrant 2>&1 ||
die "Should have resolved confict for README"
[[ "I am in a subdir" == $(cat dir/README) ]] || die "We chose the wrong README"

test_done || exit 1

test_start "Testing migrate-history.sh with -c incoming"
../migrate-history.sh -c incoming -s dir ../immigrant 2>&1 ||
die "Should have resolved confict for README"
[[ "Merged" == $(cat dir/README) ]] || die "We chose the wrong README"

test_done || exit 1

#Cleanup
cd ..
rm -rf immigrant
rm -rf recipient

echo "All tests passed"
