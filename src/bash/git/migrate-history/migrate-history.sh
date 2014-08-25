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
    test -n "$subtree_remote_name" && git remote rm "$subtree_remote_name"
    echo "$*"
    exit 1
}

make_subtreed_tree () {
    subtree_tree="$1"
    test -n "$subtree_tree" || die "Empty subtree_tree parameter"
    parent_tree="$2"
    subtree_dir="$3" || die "missing subtree directory"
    tree_contents=$(git ls-tree "$parent_tree") || die "Failed to ls-tree $parent_tree"
    new_tree_line=$(echo -e "\n040000 tree $subtree_tree\t$subtree_dir")
    tree_contents+="$new_tree_line"
    tree_contents=$(echo "$tree_contents" | sort -k 4) || die "sort"
    echo "$tree_contents" | git mktree || die "Failed to mktree subtreed tree"
}

usage() {
    cat <<EOF
Usage: migrate-history.sh [-b branch] [-s subdir] [-o origin-subdir] <path-to-immigrant>

Migrate the repository at <path-to-immigrant> into this repository at
subdir.  If -s is omitted, it will be the basename of
<path-to-immigrant>.  If origin-subdir is supplied, only the subtree
at <path-to-immigrant>/origin-subdir will be migrated.

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

while getopts ":s:b:o:" OPTION
do
    case "$OPTION" in
	b) branch="$OPTARG"  ;;
	s) subdir="$OPTARG" ;;
	o) origin_subdir="$OPTARG" ;;
	--) shift ; break ;;
	*) usage
    esac
done

origin_subdir=$(echo -n $origin_subdir|sed 's,/$,,')

shift $(($OPTIND - 1))

test "$#" = 1 || usage

orig_path_to_project="$*"

#get real path to project
path_to_project=$(
  cd $orig_path_to_project &&
  pwd
) || die "Can't find $orig_path_to_project"

[[ -n $subdir ]] || subdir=$(basename "$path_to_project")

if [[ -n $branch ]]
then
    if echo "$branch" | grep -q /
    then
	remote_regex=$(
	    cd $orig_path_to_project &&
	    git remote | xargs echo | sed 's/ /\\|/g'
	) || die "can't get remotes to check if branch is remote."
	if [[ -n "$remote_regex" ]]
	then
	    expr "$branch" : "^\($remote_regex\)/" > /dev/null &&
	    die "Use a local branch for -b (try git checkout branchname)"
	fi
    fi
else
    branch=master
fi

log "Creating a remote for the imported project so I can easily get commits from it."

timestamp=$(date +"%Y%m%d%H%M%S")
subtree_remote_name="remote-$timestamp-$$-$subdir"

git remote add "$subtree_remote_name" "$path_to_project" || die "Can't add remote $subtree_remote_name for $path_to_project"

log "Fetching from imported project"
git fetch "$subtree_remote_name" || die "Can't fetch $subtree_remote_name"

localbranch="migrate-$timestamp-$$"

log "Creating a branch for the migration"
git checkout -q -b "$localbranch" "$subtree_remote_name/$branch" || die "Can't branch"

if [[ -n "$origin_subdir" ]]
then
    #Find the first commit which introduces this subdir.
    first=$(git log --reverse --format="%H" "$origin_subdir/" | head -1) || die "Can't find first commit for $origin_subdir/"
    if git rev-parse --verify --quiet "$first^"
    then
	parents=$(git log --pretty="%P" "$first" -n 1) || die "Expected $first to exist"
	for parent in $parents
	do
	    map_insert commit_map "$parent" master
	done
	#in git rev-list, ^some_commit *excludes* some_commit; we want
	#to *include it so we add a trailing ^.
	first_commit="^$first^"
    fi
fi

commits=$(git rev-list --reverse --topo-order "$localbranch" $first_commit) || die "can't rev-list"

count=0
total=$(echo "$commits" | wc -l)

log "Migrating commits"
changed="assumedTrue"
for commit in $commits
do
    count=$(expr $count + 1)
    log "Examining $commit ($count/$total)"
    parents=$(git log --pretty="%P" "$commit" -n 1) || die "Expected $commit to exist"

    rebased_parents=""
    for orig_parent in $parents
    do
	rebased_parent_commit=$(map_find commit_map $orig_parent)
	if [[ -z "$rebased_parent_commit" ]]
	then
	    log "Unknown parent $orig_parent"
	    continue
	fi
        rebased_parents="$rebased_parents -p $rebased_parent_commit"
    done
    #if there are no parents, this is the first (relevant) commit; assume the parent
    #is this repo's master
    [[ -n $rebased_parents ]] || rebased_parents="-p master"

    #we want to create a new tree that represents the contents of
    #this commit as-rebased.

    commit_tree=$(git rev-parse "$commit^{tree}") || die "Can't get tree for $commit"
    if [[ -n "$origin_subdir" ]]
    then
	changed=$(git diff-tree --root -m -s "$commit" "$origin_subdir")
	if [[ -n "$changed" ]]
	then
	    #get only the tree for $origin_subdir
	    obj=$(git ls-tree "$commit^{tree}" "$origin_subdir" | grep "$origin_subdir")
	    obj_type=$(echo "$obj" | awk '{print $2}')
	    if [[ "$obj_type" == "tree" ]]
	    then
		commit_tree=$(echo "$obj" | awk '{print $3}')
	    else
		log "Warning: in commit $commit, $orig_subdir was $obj_type instead of tree"
		commit_tree=""
	    fi
	fi
    fi
    if [[ -n "$commit_tree" ]]
    then
	new_tree=$(make_subtreed_tree "$commit_tree" master "$subdir") || die "Failed to make subtreed tree: $new_tree"
    fi

    if [[ -z "$commit_tree" ]] || [[ -z "$changed" ]]
    then
	#this commit doesn't affect $origin_subdir
	parents=$(git log --pretty="%P" "$commit" -n 1) || die "Expected $commit to exist"
	if [[ -z "$parents" ]]
	then
	    map_insert commit_map "$commit" master
	else
	    for parent in $parents
	    do
		rebased_parent=$(map_find commit_map "$parent")
		if [[ -n "$rebased_parent" ]] && [[ "$rebased_parent" != "master" ]]
		then
		    map_insert commit_map "$commit" "$rebased_parent"
		    break
		fi
	    done
	    test -n "$(map_find commit_map "$commit")" || log "Failed to find appropriate parents for $commit"
	fi
	continue
    fi

    merge_commit_message=$(git log --format=%B -n 1 "$commit") || die "can't get merge commit message for $commit"
    committed=$(echo "$merge_commit_message" | \
	GIT_AUTHOR_NAME=$(git log --format=%an -n 1 $commit) \
	GIT_AUTHOR_EMAIL=$(git log --format=%ae -n 1 $commit) \
	GIT_AUTHOR_DATE=$(git log --format=%aD -n 1 $commit) \
	GIT_COMMITTER_NAME=$(git log --format=%cn -n 1 $commit) \
	GIT_COMMITTER_EMAIL=$(git log --format=%ce -n 1 $commit) \
	GIT_COMMITTER_DATE=$(git log --format=%aD -n 1 $commit) \
	EMAIL=$GIT_COMMITTER_EMAIL \
	git commit-tree $new_tree $rebased_parents) || die "Failed to commit tree $new_tree $rebased_parents"
    map_insert commit_map $commit $committed
    log "Committed new revision $committed"
done

log "Switching to new commit history"
git reset -q --hard $committed

log "Cleaning up remote"
git remote rm "$subtree_remote_name"

echo $committed
