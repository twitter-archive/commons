#!/usr/bin/env bash

# This is a test script for migrate-history.sh.  It sets up a couple
# of test repositories, then runs migrate-history.sh and checks that
# the results are as-expected.

die () {
    echo "$*"
    exit 1
}


#Test script for test.sh

[[ -e immigrant ]] && die "Directory named immigrant already exists.  Please remove it."
[[ -e recpient ]] && die "Directory named recipient already exists. Please remove it."

#Create the immigrant repository
(
mkdir immigrant
cd immigrant
git init
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
git checkout -b branch-b
git checkout master
echo "Branch the first" > README
git add README
git commit -m 'branch a'
git checkout master

#Contents of branch b
git checkout branch-b
echo "Branch the second" > README
git add README
echo "subdir/dir readme bis" > subdir/dir/README
git add subdir/dir/README
git commit -m 'branch b'
git checkout master

git merge branch-b #there are conflicts
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
    git rev-list branch-b|wc -l
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
echo "Testing migrate-history.sh with no options"

output=$(../migrate-history.sh ../immigrant) || die "Failed to migrate"
[[ $output == $(git rev-parse HEAD) ]] || die "Output consists of something other than sha of new HEAD $output"

revisions=$(git rev-list HEAD|wc -l)
expected_revisions=$(expr "$master_revisions" + "$immigrant_revisions")
[[ $revisions == $expected_revisions ]] || die "Wrong number of revisions"
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

#Prepare for next test
git reset --hard "$clean_recipient"

echo "Testing migrate-history.sh with -s"
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

#Prepare for next test
git reset --hard "$clean_recipient"

echo "Testing migrate-history.sh with -b"
../migrate-history.sh -b branch-b ../immigrant || die "Failed to migrate"
[[ "I am a recipient repo" == $(cat README) ]] || die "We overwrote README in recipient"
[[ -e dir/README ]] || die "We deleted dir in recipient"
[[ -e post/README ]] || die "We deleted post in recipient"

[[ "Branch the second" == $(cat immigrant/README) ]] || die "Wrong content imported for immigrant/README"
migrated_revisions=$(git log --format=oneline immigrant | wc -l)
[[ $migrated_revisions == $branch_b_revisions ]] || die "Wrong number of revisions for immigrant"
last_immigrant_commit=$(git log --format=oneline --parents -n 1 immigrant)
echo "$last_immigrant_commit" | egrep '[a-f0-9]{40} branch b' > /dev/null || die "commit graph not imported (bad last commit)"

#Prepare for next test
git reset --hard "$clean_recipient"

echo "Testing migrate-history.sh with -o"
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

#Cleanup
cd ..
rm -rf immigrant
rm -rf recipient

echo "All tests passed"
