// SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
// Author: Ivan Rodriguez

/*
 * validate_submission.js — fail if a rendered skill card still carries unresolved
 * VERIFY or SELECT markers.
 *
 * This is the engineering substitute for a review UI. A rendered card leaves the
 * generator with:
 *   - Red <span style="color:#d73a49"> wrappers + VERIFY comments
 *     around inferred or defaulted fields (owner, license).
 *   - Blue <span style="color:#0366d6"> intro lines + SELECT / /SELECT wrappers
 *     around canned catalog entries.
 *
 * The human reviewer is expected to:
 *   1. Confirm or edit each VERIFY field, then delete the red span and its comment.
 *   2. Inside each SELECT block, delete the canned entries that do not apply, add any
 *      skill-specific custom entries, then delete both the SELECT comments.
 *
 * This is a single-pass sweep over the rendered markdown that exits non-zero if any
 * marker, visual or machine-readable, remains. Run it as the pre-submission gate.
 *
 * Input: the card on stdin, or one file name as an argument (a file staged for the run
 * or inside this skill's own folder), e.g. --file <card>.md.
 *
 * Exit codes: 0 clean; 1 markers present, reviewer not done; 2 usage error.
 */

var CHECKS = [
  {
    pattern: /<!--\s*VERIFY\b/g,
    kind: "verify-comment",
    help: "Confirm or edit each red-highlighted field value, then delete the " +
      "`<!-- VERIFY: ... -->` comment and the surrounding " +
      '`<span style="color:#d73a49">...</span>` wrapper.',
  },
  {
    pattern: /<!--\s*SELECT:/g,
    kind: "select-open",
    help: "Open `<!-- SELECT: ... -->` marker remains: prune the canned entries " +
      "inside the block (delete the lines that don't apply, add any " +
      "skill-specific custom entries), then delete both the `<!-- SELECT: -->` " +
      "and the matching `<!-- /SELECT -->` comments.",
  },
  {
    pattern: /<!--\s*\/SELECT\s*-->/g,
    kind: "select-close",
    help: "Closing `<!-- /SELECT -->` marker remains: see the SELECT block guidance above.",
  },
  {
    pattern: /color:\s*#d73a49/gi,
    kind: "verify-style",
    help: 'Red verify styling is still present: remove the `<span style="color:#d73a49">...</span>` ' +
      "wrappers after you have confirmed the inferred field values.",
  },
  {
    pattern: /color:\s*#0366d6/gi,
    kind: "select-style",
    help: "Blue select styling is still present: remove the blue intro line " +
      '(`<span style="color:#0366d6">...</span>`) after you have pruned each SELECT block.',
  },
  {
    pattern: /^>\s*\*\*Red lines need your verification/gm,
    kind: "legend-red",
    help: "The red-marker legend line at the top of the card is still present; " +
      "remove the two legend blockquote lines before submission.",
  },
  {
    pattern: /^>\s*\*\*Blue lines are selectable/gm,
    kind: "legend-blue",
    help: "The blue-marker legend line at the top of the card is still present; " +
      "remove the two legend blockquote lines before submission.",
  },
];

function usage() {
  console.error("Usage: pipe the rendered card on stdin, or pass --file <rendered-card.md>");
  process.exit(2);
}

function cardText() {
  if (truthy(stdin)) return stdin;
  for (var i = 0; i < args.length; i++) {
    if (args[i] === "--file" && i + 1 < args.length) return readCard(args[i + 1]);
    if (args[i].indexOf("--file=") === 0) return readCard(args[i].slice(7));
    if (args[i].indexOf("--") !== 0) return readCard(args[i]);
  }
  usage();
  return "";
}

function readCard(name) {
  try {
    return readFile(name);
  } catch (ex) {
    console.error("ERROR: file not found: " + name);
    process.exit(2);
    return "";
  }
}

function truthy(value) {
  if (value === null || value === undefined) return false;
  if (typeof value === "string") return value.length > 0;
  return Boolean(value);
}

function validate(text) {
  var failures = [];
  for (var i = 0; i < CHECKS.length; i++) {
    var check = CHECKS[i];
    var hits = text.match(check.pattern);
    if (hits && hits.length) failures.push([check.kind, hits.length, check.help]);
  }
  return failures;
}

function main() {
  var text = cardText();
  var failures = validate(text);
  if (!failures.length) {
    console.log("OK: the card has no unresolved verify/select markers.");
    process.exit(0);
  }
  console.error("FAIL: the card has unresolved verify/select markers:");
  for (var i = 0; i < failures.length; i++) {
    console.error("");
    console.error("  [" + failures[i][0] + "] " + failures[i][1] + " occurrence(s)");
    console.error("    " + failures[i][2]);
  }
  process.exit(1);
}

main();
