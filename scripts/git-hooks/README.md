# Git hooks

## pre-push: protect `docs/` from being pushed

This hook **blocks** any push that would send commits touching the **docs/** folder . You can still commit `docs/` locally; the push is only blocked when those commits would be sent to the remote.

### Install (run from repo root)

```bash
cp scripts/git-hooks/pre-push .git/hooks/pre-push && chmod +x .git/hooks/pre-push
```

### Uninstall

```bash
rm .git/hooks/pre-push
```

### Behavior

- **Push that does not include changes under `docs/`** → allowed.
- **Push that includes at least one commit that modifies any file under `docs/`** → blocked with an error message.

The hook is local to your clone (`.git/hooks` is not pushed to GitHub).
