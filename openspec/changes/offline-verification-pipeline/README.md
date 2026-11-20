# offline-verification-pipeline

Run `make build`, `make test` and `make lint` on every push and pull request, with no model endpoint
configured, so a reactor regression stops at the pull request instead of at the next person who
happens to build.
