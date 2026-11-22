// A hand-written detection policy: the human baseline the generated ones are scored against.
//
// It reproduces the three triggers of
// prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/improve/Detector.java on the
// trace window handed in on stdin, and nothing else -- no latency heuristics, no keyword matching,
// no reading of a label (the input carries none; see ./README.md for the contract).
//
// It runs inside POC 26's GraalJS engine on the actor side and never inside the referee.

// Verbatim from Detector.java:41 and Detector.java:48. The product matches these with startsWith,
// so a failure whose message merely mentions the trust tier is a defect. The corpus carries two of
// them on purpose.
const TIER_BLOCK_PREFIX = "Run blocked by trust tier";
const LIFECYCLE_BLOCK_PREFIX = "Run blocked by lifecycle state";

// Trigger one: a run that failed for a reason a prompt refinement could act on. A trust-tier block
// and a lifecycle block are policy outcomes, so they are excluded by prefix -- Detector.failedNonPolicyBlock.
function failedNonPolicyBlock(c) {
  if (c.success !== false) {
    return false;
  }
  if (typeof c.error !== "string") {
    return true;
  }
  return !c.error.startsWith(TIER_BLOCK_PREFIX) && !c.error.startsWith(LIFECYCLE_BLOCK_PREFIX);
}

// Trigger two: the pack's content no longer matches the hash its sidecar recorded --
// Detector.hasDrift. spec_context is where the corpus puts the sidecar, because drift is a
// property of a pack and not of a run.
function contentDrift(c) {
  if (!c.pack || !c.spec_context) {
    return false;
  }
  return c.pack.contentHash !== c.spec_context.sidecarContentHash;
}

// Trigger three: a re-evaluation below the recorded baseline -- Detector.belowBaseline. A missing
// re-eval disables the trigger, which is what a null ReevalHook means to detect().
function belowBaseline(c) {
  if (!c.spec_context) {
    return false;
  }
  const reeval = c.spec_context.reevalPassRate;
  if (typeof reeval !== "number") {
    return false;
  }
  return reeval < c.spec_context.baselinePassRate;
}

function reasonsFor(c) {
  const reasons = [];
  if (failedNonPolicyBlock(c)) {
    reasons.push("failed_step");
  }
  if (contentDrift(c)) {
    reasons.push("content_drift");
  }
  if (belowBaseline(c)) {
    reasons.push("below_baseline");
  }
  return reasons;
}

const cases = JSON.parse(stdin).cases;
for (const c of cases) {
  const reasons = reasonsFor(c);
  console.log(JSON.stringify({ runId: c.runId, defect: reasons.length > 0, reasons: reasons }));
}
