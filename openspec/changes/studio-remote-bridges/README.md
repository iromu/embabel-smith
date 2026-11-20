# studio-remote-bridges

Wire remote invocation into the product: copy POC 16's three-endpoint sidecar contract into a
`remote/` client in the runtime module, expose it as `/api/remote/bridges` and `/api/remote/execute`,
and replace the `PocOnlyPane` stub at `/remote` with a pane that lists each configured sidecar, shows
the action catalog it advertises, runs one action on request, and says so plainly when the process is
not running. Sidecars are started by hand — the studio never spawns or supervises a process. GOAP
planning over remote actions stays out of scope: the agent spec format has no remote step to name.
