# runtime-a2a-operations

Expose the no-code runtime over the A2A protocol — **five** operations: SendMessage,
SendStreamingMessage, GetTask, CancelTask and the agent card — by reusing Embabel's own A2A server
module (`com.embabel.agent:embabel-agent-starter-a2a:1.5.4`, verified published and built against
Spring Boot 4.1.1) and the `io.github.a2asdk:a2a-java-sdk-spec` wire types Embabel already depends
on, so no wire format is hand-written. A new launcher module hosts the servlet surface, because the
runtime boots with `web-application-type: none` and the A2A auto-configuration activates in any
servlet app that has the library on its classpath — which would silently add an `/a2a` endpoint to
the studio.

Tasks map onto the existing run/trace model: one `AgentSpecRunService` run is one task, and the task
id is the trace `runId`, so GetTask reads back what the run already appended. No other module's
interface changes — `TraceStore.read()` already returns every record, so the new module filters the
list it is given instead of growing the SPI.

Two things this change does **not** do, both settled by evidence rather than preference. It does not
list tasks: the library dispatches a fixed method set and rejects `tasks/list` before any host
handler runs, and routing around that would rebuild the protocol surface the reuse ruling exists to
avoid. And it speaks protocol 0.3, so a client expecting the current 1.0 specification will not find
it. Both are recorded as deviations, not presented as support.

**Phase 1 ran on 2026-11-19 and returned PROCEED** — see `design.md` §"Section 1 spike findings". It
resolved the dependency offline, pinned the SDK at 0.3.2.Final, proved the Jackson 2 / Jackson 3
round-trip, and corrected three premises this plan was written on: the servlet flip needs a system
property rather than the programmatic form, listing cannot be served from behind the facade, and an
omitted primitive-backed field fails inside the library before our handler sees it. Sections 2
onward are implementable now.
