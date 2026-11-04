# changes/

Release note entries for the next release, one file per change. At the release, the release work
(the `/belayer-webapi-release` skill) collects them by kind into the section of the version in
`docs/release.md` and removes the files.

Each pull request adds its own file instead of editing `docs/release.md`, so branches do not
conflict on the release note when they are merged. This directory is not part of the distribution.

## Where to put an entry

The directory is the kind of the change.

| Directory | Heading in the release note |
|:--|:--|
| `breaking/` | Breaking changes |
| `feature/` | Feature improvements |
| `bugfix/` | Bug fixes |
| `misc/` | Misc changes |

- Name the file after the branch with `/` replaced by `_`, plus `.md` (e.g. branch `fix/forwarded_headers` →
  `bugfix/fix_forwarded_headers.md`). Turning the first `_` back into `/` gives the branch name, so keep a single `/`
  in branch names (after the prefix such as `fix/`). An entry committed to master without a branch gets a
  name that says what it is about.
- A change of two kinds puts a file of the same name in each directory.
- Commit it together with the code, the tests and the documents of the change.

## How to write it

- No heading. Only the `* ` lines that go into the release note as they are; details as `* ` lines
  indented by four spaces.
- Follow the entries already in `docs/release.md`: English, starting with a verb in the present
  tense, saying what changes for the user.

```markdown
* fix the rollback of a stateful transaction, which always returned 400.
```

The `.gitkeep` in each directory keeps the directory when it has no entry. Do not remove it.
