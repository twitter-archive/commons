A monorepo is one of the popular ways to manage complexity at scale.
The problem with large software projects is that the dependency graph
becomes very complicated.  When these dependencies cross repository
boundaries, it can require multiple commits to update a version.  This
breaks the atomicity of commits (and is a hassle).  The solution is to
store the entire dependency graph (except maybe leaves) in a single
repository: the monorepo.

To explain this, we'll have two example repositories: mono (the
monorepo) and immigrant (the repo that we'll be importing).  Let's
imagine that inside immigrant, there's a file called foo/bar.txt.  We
want to import immigrant into mono, such that mono contains
immigrant/foo/bar.txt.

Projects that have historically been managed outside the monorepo
might need to be imported.  The traditional solution to this is to use
git-subtree.  However, git-subtree does a weird thing to the immigrant
project's history: it treats the import as a move. In other words,
immigrant/foo/bar.txt was previously foo/bar.txt and now it's been
moved.  This means that git log doesn't work across the move boundary.
You can use various tricks to get the old history, but it's a hassle.

The solution is a little shell script that I banged together called
migrate.sh.  The idea are trying to import immigrant into mono as
a subtree.

The simple solution would be to rewrite history using eg
filter-branch, and then rebase on top of master.  But this only works
for a single branch; for complex history, it doesn't.

Instead, we use git plumbing operations to rewrite each commit on
immigrant such that its tree appears inside of the root tree of mono
at the appropriate place.  In order to preserve ancestry, we also need
to change the parent pointer on each migrated commit such that it
points to the migrated version of its parent.

And voila -- we have a history-preserving migration.

    Usage: migrate-history.sh [-b branch] [-s subdir] <path-to-immigrant>"

Migrate the repository at <path-to-repo> into this repository at
subdir.  If -s is omitted, it will be the basename of
<path-to-repo>.

You can use -b to get a branch other than master from the immigrant.
This repository's master will always be used.

Run this script from inside your repository, at the root directory.  The
result will be a branch named migrated-\$subdir
 