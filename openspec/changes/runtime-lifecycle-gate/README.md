# runtime-lifecycle-gate

Make the runtime honour the lifecycle state a pack was published with. Today `retired` — documented as
"terminal, out of the library" — stops nothing at run time: the runtime module contains no reference to
lifecycle at all, and the studio loop is the only place that excludes a retired pack. A pack can be
retired and still run. This change excludes retired packs from ingest and names the block when a run's
goal belonged to one, following the trust-tier pattern already in the module.
