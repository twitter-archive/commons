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

function finish {
    log "Cleaning up remote"
    test -n "$subtree_remote_name" && git remote rm "$subtree_remote_name"
}

trap finish EXIT

die () {
    echo "$*" >&2
    exit 1
}

get_sha_from_tree () {
    awk -F $'\t' -F ' ' '{print $3}'
}

get_line_number_for () {
    filename="$1"
    filenames="$2"

    echo "$filenames" | awk -v filename="$filename" '{ if ($1 == filename) print NR }'
}

get_line_by_number () {
    lineno="$1"
    lines="$2"
    echo "$lines" | awk -v n="$lineno" '{if (NR == n) print $1}'
}

make_union_tree () {
# Create a new tree that contains the contents of both arguments, recursively
(
    tree1_sha="$1"
    tree2_sha="$2"
    path="$3"
    if [[ "$tree1_sha" == "$tree2_sha" ]]
    then
        echo "$tree1_sha"
        return
    fi

    # It is possible that one or both of these shas are not trees.  If
    # so, there is no way to preserve both, so either one side will win,
    # or we'll report a conflict and fail, depending on the conflict
    # rule.
    tree1_type=$(git cat-file -t "$tree1_sha") || die "No such sha $tree1_sha"
    tree2_type=$(git cat-file -t "$tree2_sha") || die "No such sha $tree2_sha"
    if [[ "$tree1_type" != "$tree2_type" ]]
    then
        case "$conflict_rule" in
            fail)
                die "Conflict: $tree1_sha ($tree1_type) vs $tree2_sha ($tree2_type) at $path" ;;
            incoming)
                echo "$tree1_sha"
                return ;;
            existing)
                echo "$tree2_sha"
                return ;;
        esac
    elif [[ "$tree1_type" != "tree" ]]
    then
        case "$conflict_rule" in
            fail)
                die "Conflict: $tree1_sha ($tree1_type) vs $tree2_sha at $path" ;;
            incoming)
                echo "$tree1_sha"
                return ;;
            existing)
                echo "$tree2_sha"
                return ;;
        esac
    fi

    tree1=$(git ls-tree "$tree1_sha")
    tree2=$(git ls-tree "$tree2_sha")
    tree1_filenames=$(echo "$tree1" | cut -f 2)
    tree2_filenames=$(echo "$tree2" | cut -f 2)
    tree2_shas=$(echo "$tree2" | get_sha_from_tree)
    new_tree_file=$(mktemp temp_tree.XXXXXXXXX)

    echo "$tree1" | while read child
    do
        filename=$(echo "$child" | cut -f 2)
        tree2_lineno=$(get_line_number_for "$filename" "$tree2_filenames")
        if [[ -n "$tree2_lineno" ]]
        then
            #find the object in tree2 that has the same filename
            conflicting_sha=$(get_line_by_number "$tree2_lineno" "$tree2_shas")
            child_sha=$(echo "$child" | get_sha_from_tree)
            new_child_sha=$(make_union_tree "$child_sha" "$conflicting_sha" "$path/$filename") || exit 1

            mode_and_type=$(echo "$child"|cut -d ' ' -f 1-2)
            echo "$mode_and_type $new_child_sha"$'\t'"$filename" >> "$new_tree_file"
        else
            echo "$child" >> "$new_tree_file"
        fi
    done || exit 1

    echo "$tree2" | while read child
    do
        filename=$(echo "$child" | cut -f 2)
        #If this filename exists in tree1, then we have already handled
        #it above; else we output the tree2 entry here.
        echo "$tree1_filenames" | grep -Fx "$filename" > /dev/null ||
          echo "$child" >> "$new_tree_file"
    done || exit 1

    new_tree=$(cat $new_tree_file | sort -k 4)
    rm "$new_tree_file"
    echo "$new_tree" | git mktree || die "Failed to mktree union tree from $tree1_sha $tree2_sha"
) || exit 1
}

exclude_tree_entry() {
    #This will fail for filenames containing tabs.  Don't be a menace.
    awk -F $'\t' -v filename="$1" '{ if ($2 != filename) print $0 }' || die "exclude tree entry failed"
}

make_subtreed_tree () {
    subtree_tree="$1"
    test -n "$subtree_tree" || die "Empty subtree_tree parameter"
    parent_tree="$2"
    subtree_dir="$3" || die "missing subtree directory"
    tree_contents=$(git ls-tree "$parent_tree") || die "Failed to ls-tree $parent_tree"
    tree_contents=$(echo "$tree_contents" | exclude_tree_entry "$subtree_dir")

    existing_tree=$(git ls-tree "$parent_tree" "$subtree_dir" | get_sha_from_tree)
    if [[ -n "$existing_tree" ]]
    then
        subtree_tree=$(make_union_tree "$subtree_tree" "$existing_tree" "$subtree_dir") || exit 1
    fi
    new_tree_line=$(echo -e "\n040000 tree $subtree_tree\t$subtree_dir")
    tree_contents+="$new_tree_line"
    tree_contents=$(echo "$tree_contents" | sort -k 4) || die "sort"
    echo "$tree_contents" | git mktree || die "Failed to mktree subtreed tree"
}

usage() {
    cat <<EOF
Usage: migrate-history.sh [-b branch]
                          [-s subdir]
                          [-o origin-subdir]
                          [-c conflict_rule]
                          <path-to-immigrant>

Migrate the repository at <path-to-immigrant> into this repository at
subdir.  If -s is omitted, it will be the basename of
<path-to-immigrant>.  If origin-subdir is supplied, only the subtree
at <path-to-immigrant>/origin-subdir will be migrated.

You can use -b to get a branch other than master from the immigrant.
This repository's master will always be used.

If this repository already contains a directory called subdir, a
recursive tree merge will be attempted.  If one side contains a file
where the other contains a directory, or if both sides contain the
same file with different contents, then there is a conflict. The
conflict rule determines what happens in this case.  The options are:

  fail: The import will fail (this is the default)
  incoming: The version from the incoming tree will be used
  existing: The version from the existing tree will be used

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

conflict_rule="fail"

while getopts ":s:b:c:o:" OPTION
do
    case "$OPTION" in
	b) branch="$OPTARG"  ;;
	s) subdir="$OPTARG" ;;
        o) origin_subdir="$OPTARG" ;;
        c) conflict_rule="$OPTARG" ;;
	--) shift ; break ;;
	*) usage
    esac
done

if [[ "$conflict_rule" != "fail" &&
      "$conflict_rule" != "incoming" &&
      "$conflict_rule" != "existing" ]]
then
    die "Bad conflict rule $conflict_rule -- expected fail, incoming, or existing"
fi

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
	    echo "$branch" | grep "^\($remote_regex\)/" > /dev/null &&
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
git checkout -q -b "$localbranch" "$subtree_remote_name/$branch" || die "Can't branch.  Make sure your immigrant branch is pushed to origin."

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

echo $committed
