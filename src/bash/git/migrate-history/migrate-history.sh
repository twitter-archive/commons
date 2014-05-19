#!/usr/bin/env bash

# This is a script to migrate a git repository to a subdirectory of
# another, while preserving history.

# I would like to use git subtree, but git subtree doesn't rewrite
# imported history, so files appear to move.  That means that git log
# on the subdirectory won't work.  You have to do git log --follow to
# see the history of a file, and you can only do that on an individual
# file basis.

# Also, I'm not sure what git subtree does with non-linear history.
# Git rebase fails on fake conflicts as it attempts cross the streams
# (that is, rewrite history as if it were all one branch).  And many
# projects have non-linear history on master.

# So instead I use the git plumbing commands to build a new commit for
# every commit on the imported tree.  The new commit contains the
# original commit's tree within its tree object.  So, it's basically a
# multibranch, cross-repo rebase.

# This stuff is for bash 3 compatibility; bash 4 has associative
# arrays.
map_insert () {
    local map_name=$1 key=$2 val=$3
    eval __map_${map_name}_${key}=$val
}

map_find () {
    local map_name=$1 key=$2
    local var=__map_${map_name}_${key}
    echo ${!var}
}

die () {
    test -n $subtree_remote_name && git remote rm "$subtree_remote_name"
    echo "$*"
    exit 1
}

make_subtreed_tree () {
    subtree_tree="$1"
    parent_tree="$2"
    subtree_dir="$3" || die "missing subtree directory"
    tree_contents=$(git ls-tree "$parent_tree") || die "ls-tree"
    new_tree_line=$(echo -e "\n040000 tree $subtree_tree\t$subtree_dir")
    tree_contents+="$new_tree_line"
    tree_contents=$(echo "$tree_contents" | sort -k 4) || die "sort"
    echo "$tree_contents" | git mktree || die "mktree"
}

usage() {
    cat <<EOF
Usage: migrate-history.sh [-b branch] [-s subdir] <path-to-immigrant>

Migrate the repository at <path-to-repo> into this repository at
subdir.  If -s is omitted, it will be the basename of
<path-to-repo>.

You can use -b to get a branch other than master from the immigrant.
This repository's master will always be used.

Run this script from inside your repository, at the root directory.  The
result will be a branch named migrated-\$subdir

EOF

    exit 1
}

log() {
    echo "$@" 1>&2
}

#check for clean index
git diff-index --quiet --cached HEAD || die "This command should be run on a clean index."
#and clean working tree
git diff-files --quiet || die "This command should be run on a clean working tree"

while getopts ":s:b:" OPTION
do
    case "$OPTION" in
	b) branch="$OPTARG" ;;
	s) subdir="$OPTARG" ;;
	--) shift ; break ;;
	*) usage
    esac
done

shift $(($OPTIND - 1))

test "$#" = 1 || usage

path_to_project="$*"

#get real path to project
path_to_project=$(
  cd $path_to_project
  pwd
)

[[ -n $subdir ]] || subdir=$(basename "$path_to_project")
[[ -n $branch ]] || branch=master

log "Creating a remote for the imported project so I can easily get commits from it."

timestamp=$(date +"%Y%m%d%H%M%S")
subtree_remote_name="remote-$timestamp-$$-$subdir"

git remote add "$subtree_remote_name" "$path_to_project" || die "Can't add remote $subtree_remote_name for $path_to_project"

log "Fetching from imported project"
git fetch "$subtree_remote_name"

localbranch="migrate-$timestamp-$$"

log "Creating a branch for the migration"
git checkout -q -b "$localbranch" "$subtree_remote_name/$branch" || die "Can't branch"

commits=$(git rev-list --reverse --topo-order "$localbranch")

count=0
total=$(echo "$commits" | wc -l)

log "Migrating commits"
for commit in $commits
do
    parents=$(git log --pretty="%P" $commit -n 1) || die "Expected $commit to exist"

    rebased_parents=""
    for orig_parent in $parents
    do
	rebased_parent_commit=$(map_find commit_map $orig_parent)
	[[ -n $rebased_parent_commit ]] || die "I lost track of $orig_parent"
        rebased_parents="$rebased_parents -p $rebased_parent_commit"
    done
    #if there are no parents, this is the first commit; assume the parent
    #is this repo's master
    [[ -n $rebased_parents ]] || rebased_parents="-p master"

    #we want to create a new tree that represents the contents of
    #this commit as-rebased.

    commit_tree=$(git rev-parse $commit^{tree})
    new_tree=$(make_subtreed_tree $commit_tree master $subdir)
    merge_commit_message=$(git log --format=%B -n 1 $commit)
    committed=$(echo "$merge_commit_message" | \
	GIT_AUTHOR_NAME=$(git log --format=%an -n 1 $commit) \
	GIT_AUTHOR_EMAIL=$(git log --format=%ae -n 1 $commit) \
	GIT_AUTHOR_DATE=$(git log --format=%aD -n 1 $commit) \
	GIT_COMMITTER_NAME=$(git log --format=%cn -n 1 $commit) \
	GIT_COMMITTER_EMAIL=$(git log --format=%ce -n 1 $commit) \
	GIT_COMMITTER_DATE=$(git log --format=%aD -n 1 $commit) \
	EMAIL=$GIT_COMMITTER_EMAIL \
	git commit-tree $new_tree $rebased_parents)
    map_insert commit_map $commit $committed
    last_commit=$committed
    count=$(expr $count + 1)
    log "committed new revision $committed ($count/$total)"
done

log "Switching to new commit history"
git reset -q --hard $committed

log "Cleaning up remote"
git remote rm "$subtree_remote_name"

echo $committed