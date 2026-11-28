# Severity labels

Four labels. A bullet carries exactly one of these words, spelled this way. Nothing else
counts as a severity, including words that sound like one.

| Label | A file earns it when |
| --- | --- |
| breaking | The change removes or renames something a caller outside this folder could have been calling: an exported name, a public argument, a file another file opens, a stored field. A change that only adds is never breaking. |
| fix | The change makes something already present do what it always claimed to: a correction to a condition, a boundary, an error path, a default that was being ignored. |
| feature | The change adds a capability a reader could ask for and get, where nothing existed before. Adding a field nobody can request yet is not a feature; it is internal. |
| internal | Everything else: renames inside one file, comments, formatting, build plumbing, dependency bumps with no visible effect, moved files whose contents did not change. |

## Tie-breaks

Two labels fit one file: take the more severe, in the order breaking, feature, fix,
internal. A group's label is the most severe label among its files; the group is not
labelled by whichever file was opened first.

A group whose files are all internal stays out of the notes unless the release is otherwise
empty, in which case it gets one line saying the release was internal work.

## Evidence, not intention

Judge the file, not the commit message, branch name, or ticket title. A file described as a
fix that only adds a parameter to an exported function is breaking, and a file described as
breaking whose diff is a comment is internal. Where the file alone cannot tell you - a
generated file, a binary, something the script reported as unreadable - do not guess a
label: put the file in the unresolved line at the end of the notes instead.
