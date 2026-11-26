// SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
// Author: Ivan Rodriguez

/*
 * render_card.js — render a skill card from a validated context.
 *
 * The context JSON arrives on stdin. The card goes to stdout, and is also written
 * with writeFile so a host that surfaces script artifacts can copy the rendered
 * bytes verbatim instead of retyping them.
 *
 * The card layout lives in renderCard() below. references/skill-card.md.j2 is the
 * human-readable form of that same layout: change one, change the other. Rendering
 * is deterministic, so two identical contexts always produce identical cards.
 *
 * Args: --out <name>  the artifact name to write (default: "<skill_name>-card.md")
 *
 * Exit codes: 0 rendered; 2 bad arguments or unparseable context; 3 context
 * validation failed (the reasons are listed on stderr).
 */

var SCHEMA = [
  ["skill_name", "str", true],
  ["skill_kind", "str", true],
  ["description_sentence", "str", true],
  ["usage_posture", "str", true],
  ["owner", "dict", true],
  ["license_identifier", "strOrNone", false],
  ["license_verify", "bool", false],
  ["license_verify_reason", "str", false],
  ["use_case", "str", true],
  ["deployment_geography", "str", true],
  ["credential_requirements", "dict", false],
  ["references", "list", true],
  ["output", "dict", true],
  ["skill_version", "str", true],
  ["evaluation", "dict", false],
];

var VALID_USAGE = ["commercial", "demonstration", "research_dev"];
var VALID_OWNER_KINDS = ["nvidia", "third_party"];
var VALID_CREDENTIAL_STATUSES = ["no", "not specified", "optional", "yes"];
var VALID_CREDENTIAL_TYPES = ["API key", "Cloud Credentials", "None", "OAuth Token", "Service Account"];

var SENSITIVE_CREDENTIAL_VALUE_PATTERNS = [
  /\b[A-Z][A-Z0-9_]*(?:API_KEY|TOKEN|SECRET|PASSWORD|PRIVATE_KEY|CLIENT_SECRET|ACCESS_KEY)[A-Z0-9_]*\s*[:=]\s*(?:[^\s"'`]+|"[^"]*"|'[^']*')/i,
  /\b(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|client[_-]?secret)\b\s*[:=]\s*(?:[^\s"'`]+|"[^"]*"|'[^']*')/i,
  /\bauthorization\s*:\s*bearer\s+[A-Za-z0-9._~+/=-]+/i,
  /[?&](?:token|api_key|key|secret|password|access_token)=[^&\s)>"'`]+/i,
  /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b|\b(?:sk|hf|ghp|glpat|nvapi)-?[A-Za-z0-9_=-]{20,}\b|\bgithub_pat_[A-Za-z0-9_]{20,}\b/,
  /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
];

var EVALUATION_STRING_FIELDS = ["agent", "tasks", "results_markdown"];
var EVALUATION_METRIC_GROUPS = ["dimensions", "signals"];
var TESTING_COMPLETED_FIELDS = ["agent_red_teaming", "network_security", "product_security"];

/* ─── Context schema ─────────────────────────────────────────────────────── */

function typeName(value) {
  if (value === null) return "NoneType";
  if (value === undefined) return "NoneType";
  if (typeof value === "string") return "str";
  if (typeof value === "boolean") return "bool";
  if (Array.isArray(value)) return "list";
  if (typeof value === "number") return Number.isInteger(value) ? "int" : "float";
  if (typeof value === "object") return "dict";
  return typeof value;
}

function isDict(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function matchesType(value, expected) {
  if (expected === "strOrNone") return typeof value === "string" || value === null;
  if (expected === "str") return typeof value === "string";
  if (expected === "bool") return typeof value === "boolean";
  if (expected === "list") return Array.isArray(value);
  if (expected === "dict") return isDict(value);
  return false;
}

function describeType(expected) {
  return expected === "strOrNone" ? "str or NoneType" : expected;
}

function has(obj, key) {
  return Object.prototype.hasOwnProperty.call(obj, key);
}

function validate(ctx) {
  var errors = [];
  validateSchema(ctx, errors);
  validateUsage(ctx, errors);
  validateOwner(ctx, errors);
  validateCredentialRequirements(ctx, errors);
  validateOutput(ctx, errors);
  validateEvaluation(ctx, errors);
  validateReferences(ctx, errors);
  return errors;
}

function validateSchema(ctx, errors) {
  for (var i = 0; i < SCHEMA.length; i++) {
    var key = SCHEMA[i][0];
    var expected = SCHEMA[i][1];
    var required = SCHEMA[i][2];
    if (!has(ctx, key)) {
      if (required) errors.push("missing required key: '" + key + "'");
      continue;
    }
    if (!matchesType(ctx[key], expected)) {
      errors.push(
        "'" + key + "' should be " + describeType(expected) + ", got " + typeName(ctx[key])
      );
    }
  }
}

function validateUsage(ctx, errors) {
  if (has(ctx, "usage_posture") && VALID_USAGE.indexOf(ctx.usage_posture) < 0) {
    errors.push(
      "'usage_posture' must be one of [" + VALID_USAGE.join(", ") + "], got " + quote(ctx.usage_posture)
    );
  }
}

function validateOwner(ctx, errors) {
  if (has(ctx, "owner") && isDict(ctx.owner)) {
    var kind = ctx.owner.kind;
    if (VALID_OWNER_KINDS.indexOf(kind) < 0) {
      errors.push(
        "'owner.kind' must be one of [" + VALID_OWNER_KINDS.join(", ") + "], got " + quote(kind)
      );
    }
    if (kind === "third_party") {
      var needed = ["name", "card_link"];
      for (var i = 0; i < needed.length; i++) {
        if (!ctx.owner[needed[i]]) {
          errors.push(
            "'owner." + needed[i] + "' required when owner.kind == 'third_party'"
          );
        }
      }
    }
  }
}

function validateCredentialRequirements(ctx, errors) {
  var credentials = ctx.credential_requirements;
  if (credentials === undefined || credentials === null || !isDict(credentials)) return;

  var required = ["requires_api_key_or_credential", "credential_types"];
  for (var i = 0; i < required.length; i++) {
    if (!has(credentials, required[i])) {
      errors.push("'credential_requirements." + required[i] + "' missing");
    }
  }

  var status = credentials.requires_api_key_or_credential;
  if (typeof status === "string") {
    if (VALID_CREDENTIAL_STATUSES.indexOf(status) < 0) {
      errors.push(
        "'credential_requirements.requires_api_key_or_credential' must be one of [" +
          VALID_CREDENTIAL_STATUSES.join(", ") + "], got " + quote(status)
      );
    }
  } else if (status !== undefined && status !== null) {
    errors.push(
      "'credential_requirements.requires_api_key_or_credential' should be str, got " + typeName(status)
    );
  }

  validateStringList(
    "credential_requirements.credential_types", credentials.credential_types, errors
  );

  var types = credentials.credential_types;
  if (Array.isArray(types)) {
    for (var idx = 0; idx < types.length; idx++) {
      if (typeof types[idx] === "string") {
        var ok =
          VALID_CREDENTIAL_TYPES.indexOf(types[idx]) >= 0 ||
          (/^Other \[.*\]$/.test(types[idx]) && types[idx].indexOf("Other [") === 0);
        if (!ok) {
          errors.push(
            "'credential_requirements.credential_types[" + idx + "]' must be one of [" +
              VALID_CREDENTIAL_TYPES.join(", ") + "] or 'Other [description]', got " + quote(types[idx])
          );
        }
      }
    }
  }
}

function validateStringList(path, value, errors) {
  if (value === undefined || value === null) return;
  if (!Array.isArray(value)) {
    errors.push("'" + path + "' should be list, got " + typeName(value));
    return;
  }
  for (var i = 0; i < value.length; i++) {
    if (typeof value[i] !== "string") {
      errors.push("'" + path + "[" + i + "]' should be str, got " + typeName(value[i]));
    }
  }
}

function validateNoSensitiveCredentialValue(path, value, errors) {
  for (var i = 0; i < SENSITIVE_CREDENTIAL_VALUE_PATTERNS.length; i++) {
    if (SENSITIVE_CREDENTIAL_VALUE_PATTERNS[i].test(value)) {
      errors.push(
        "'" + path + "' must describe credential requirements without containing " +
          "credential values, assignments, bearer tokens, private keys, or secret-like tokens"
      );
      return;
    }
  }
}

function validateOutput(ctx, errors) {
  if (has(ctx, "output") && isDict(ctx.output)) {
    var needed = ["types", "format", "parameters", "other_properties"];
    for (var i = 0; i < needed.length; i++) {
      if (!has(ctx.output, needed[i])) {
        errors.push("'output." + needed[i] + "' missing");
      }
    }
  }
}

function validateEvaluation(ctx, errors) {
  var evaluation = ctx.evaluation;
  if (!isDict(evaluation)) return;

  var i;
  for (i = 0; i < EVALUATION_STRING_FIELDS.length; i++) {
    var key = EVALUATION_STRING_FIELDS[i];
    if (has(evaluation, key) && typeof evaluation[key] !== "string") {
      errors.push("'evaluation." + key + "' should be str, got " + typeName(evaluation[key]));
    }
  }

  if (has(evaluation, "agents")) {
    if (!Array.isArray(evaluation.agents)) {
      errors.push("'evaluation.agents' should be list, got " + typeName(evaluation.agents));
    } else {
      for (i = 0; i < evaluation.agents.length; i++) {
        if (typeof evaluation.agents[i] !== "string") {
          errors.push(
            "'evaluation.agents[" + i + "]' should be str, got " + typeName(evaluation.agents[i])
          );
        }
      }
    }
  }

  if (has(evaluation, "metrics") && typeof evaluation.metrics !== "string") {
    if (!isDict(evaluation.metrics)) {
      errors.push("'evaluation.metrics' should be str or dict, got " + typeName(evaluation.metrics));
    } else {
      for (var g = 0; g < EVALUATION_METRIC_GROUPS.length; g++) {
        var group = EVALUATION_METRIC_GROUPS[g];
        if (has(evaluation.metrics, group)) {
          var entries = evaluation.metrics[group];
          if (!Array.isArray(entries)) {
            errors.push(
              "'evaluation.metrics." + group + "' should be list, got " + typeName(entries)
            );
          } else {
            for (i = 0; i < entries.length; i++) {
              var entry = entries[i];
              if (!isDict(entry)) {
                errors.push(
                  "'evaluation.metrics." + group + "[" + i + "]' should be dict, got " +
                    typeName(entry)
                );
              } else {
                var fields = ["name", "description"];
                for (var f = 0; f < fields.length; f++) {
                  if (!has(entry, fields[f])) {
                    errors.push(
                      "'evaluation.metrics." + group + "[" + i + "]." + fields[f] + "' missing"
                    );
                  } else if (typeof entry[fields[f]] !== "string") {
                    errors.push(
                      "'evaluation.metrics." + group + "[" + i + "]." + fields[f] +
                        "' should be str, got " + typeName(entry[fields[f]])
                    );
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (has(evaluation, "testing_completed")) {
    if (!isDict(evaluation.testing_completed)) {
      errors.push(
        "'evaluation.testing_completed' should be dict, got " + typeName(evaluation.testing_completed)
      );
    } else {
      for (i = 0; i < TESTING_COMPLETED_FIELDS.length; i++) {
        var field = TESTING_COMPLETED_FIELDS[i];
        if (!has(evaluation.testing_completed, field)) {
          errors.push("'evaluation.testing_completed." + field + "' missing");
        } else if (typeof evaluation.testing_completed[field] !== "boolean") {
          errors.push(
            "'evaluation.testing_completed." + field + "' should be bool, got " +
              typeName(evaluation.testing_completed[field])
          );
        }
      }
    }
  }
}

function validateReferences(ctx, errors) {
  var references = Array.isArray(ctx.references) ? ctx.references : [];
  for (var i = 0; i < references.length; i++) {
    var item = references[i];
    if (!isDict(item) || !has(item, "label") || !has(item, "url")) {
      errors.push("each 'references' item needs 'label' and 'url'");
      break;
    }
  }
}

function quote(value) {
  if (value === undefined) return "None";
  if (value === null) return "None";
  if (typeof value === "boolean") return value ? "True" : "False";
  if (typeof value === "number") return String(value);
  if (Array.isArray(value)) return "[" + value.map(quote).join(", ") + "]";
  if (isDict(value)) return "{" + Object.keys(value).map(function (k) { return quote(k) + ": " + quote(value[k]); }).join(", ") + "}";
  return "'" + String(value).replace(/\\/g, "\\\\").replace(/'/g, "\\'") + "'";
}

function applyMarkerDefaults(ctx) {
  if (!has(ctx, "license_verify")) ctx.license_verify = false;
  if (!has(ctx, "license_verify_reason")) ctx.license_verify_reason = "";
  if (isDict(ctx.owner)) {
    if (!has(ctx.owner, "verify")) ctx.owner.verify = false;
    if (!has(ctx.owner, "verify_reason")) ctx.owner.verify_reason = "";
  }
  if (!isDict(ctx.credential_requirements)) ctx.credential_requirements = {};
  if (!has(ctx.credential_requirements, "requires_api_key_or_credential")) {
    ctx.credential_requirements.requires_api_key_or_credential = "not specified";
  }
  if (!has(ctx.credential_requirements, "credential_types")) {
    ctx.credential_requirements.credential_types = [];
  }
}

/* ─── Filters ────────────────────────────────────────────────────────────── */

function titleCase(text) {
  var out = "";
  var previous = "";
  for (var i = 0; i < text.length; i++) {
    var ch = text[i];
    out += /[A-Za-z]/.test(ch) && !/[A-Za-z0-9]/.test(previous) ? ch.toUpperCase() : ch.toLowerCase();
    previous = ch;
  }
  return out;
}

function truthy(value) {
  if (Array.isArray(value)) return value.length > 0;
  if (value === null || value === undefined) return false;
  if (typeof value === "object") return Object.keys(value).length > 0;
  return Boolean(value);
}

function joinList(values, separator) {
  var parts = [];
  for (var i = 0; i < values.length; i++) parts.push(String(values[i]));
  return parts.join(separator);
}

/* ─── The card layout ────────────────────────────────────────────────────── */

function renderCard(ctx) {
  var out = [];
  function w(text) {
    out.push(text);
  }

  var evaluation = isDict(ctx.evaluation) ? ctx.evaluation : {};
  var credentials = isDict(ctx.credential_requirements) ? ctx.credential_requirements : {};
  var credentialStatus = has(credentials, "requires_api_key_or_credential")
    ? credentials.requires_api_key_or_credential
    : "not specified";
  var credentialTypes = has(credentials, "credential_types") ? credentials.credential_types : [];

  w("## Description: <br>\n");
  w(ctx.description_sentence + " <br>\n\n");

  if (ctx.usage_posture === "commercial") {
    w("This skill is ready for commercial/non-commercial use. <br>");
  } else if (ctx.usage_posture === "research_dev") {
    w("This skill is for research and development only. <br>");
  } else if (ctx.usage_posture === "demonstration") {
    w("This skill is for demonstration purposes and not for production usage. <br>");
  }
  w("\n\n");

  if (ctx.owner.kind === "nvidia") {
    w("## Owner\n");
    if (truthy(ctx.owner.verify)) {
      w('<span style="color:#d73a49">NVIDIA</span> <!-- VERIFY: ' +
        (truthy(ctx.owner.verify_reason)
          ? ctx.owner.verify_reason
          : "inferred or defaulted; confirm or correct before submission") +
        " --> <br>");
    } else {
      w("NVIDIA <br>");
    }
  } else {
    w("## Third-Party Community Consideration\n");
    var thirdParty =
      "This skill is not owned or developed by NVIDIA. This skill has been developed and " +
      "built to a third-party's requirements for this application and use case; see link to " +
      "Non-NVIDIA [" + ctx.owner.name + " Agent Card](" + ctx.owner.card_link + ").";
    if (truthy(ctx.owner.verify)) {
      w('<span style="color:#d73a49">' + thirdParty + "</span> <!-- VERIFY: " +
        (truthy(ctx.owner.verify_reason)
          ? ctx.owner.verify_reason
          : "third-party ownership inferred; confirm vendor name and card link") +
        " --> <br>");
    } else {
      w(thirdParty + " <br>");
    }
  }
  w("\n\n");

  w("### License/Terms of Use: <br>\n");
  if (truthy(ctx.license_identifier)) {
    if (truthy(ctx.license_verify)) {
      w('<span style="color:#d73a49">' + ctx.license_identifier + "</span> <!-- VERIFY: " +
        (truthy(ctx.license_verify_reason)
          ? ctx.license_verify_reason
          : "license not extracted verbatim from a documentation file; confirm terms before submission") +
        " --> <br>\n");
    } else {
      w(ctx.license_identifier + " <br>\n");
    }
  }

  w("## Use Case: <br>\n");
  w(ctx.use_case + " <br>\n\n");
  w("### Deployment Geography for Use: <br>\n");
  w(ctx.deployment_geography + " <br>\n\n");

  w("## Requirements / Dependencies: <br>\n");
  w("**Requires API Key or External Credential:** [" + titleCase(String(credentialStatus)) + "] <br>\n");
  w("**Credential Type(s):** [" +
    (truthy(credentialTypes) ? joinList(credentialTypes, ", ") : "None identified") + "] <br>  \n\n");
  w("Do not include secrets in prompts/logs/output; use least-privilege credentials; rotate keys as appropriate. See skill body for more details. <br>\n\n");

  w("## Known Risks and Mitigations: <br>\n");
  w("Risk: Review before execution as proposals could introduce incorrect or misleading guidance into skills. <br>\n");
  w("Mitigation: Review and scan skill before deployment. <br>\n\n");

  w("## Reference(s): <br>\n");
  var references = Array.isArray(ctx.references) ? ctx.references : [];
  for (var r = 0; r < references.length; r++) {
    w("- [" + references[r].label + "](" + references[r].url + ") <br>\n");
  }
  w("\n\n");

  w("## Skill Output: <br>\n");
  w("**Output Type(s):** [" + joinList(ctx.output.types, ", ") + "] <br>\n");
  w("**Output Format:** [" + ctx.output.format + "] <br>\n");
  w("**Output Parameters:** [" + ctx.output.parameters + "] <br>\n");
  w("**Other Properties Related to Output:** [" + ctx.output.other_properties + "] <br>\n\n");

  if (truthy(evaluation.agents) || truthy(evaluation.agent)) {
    w("## Evaluation Agents Used: <br>\n");
    if (truthy(evaluation.agents)) {
      for (var a = 0; a < evaluation.agents.length; a++) {
        w("- " + evaluation.agents[a] + " <br>\n");
      }
      w("\n\n\n");
    } else {
      w("- " + evaluation.agent + " <br>\n\n\n");
    }
  }

  if (truthy(evaluation.tasks)) {
    w("## Evaluation Tasks: <br>\n");
    w(evaluation.tasks + " <br>\n\n");
  }

  if (truthy(evaluation.metrics)) {
    w("## Evaluation Metrics Used: <br>\n");
    var metrics = evaluation.metrics;
    if (isDict(metrics)) {
      if (truthy(metrics.dimensions)) {
        w("Reported benchmark dimensions: <br>\n");
        for (var d = 0; d < metrics.dimensions.length; d++) {
          w("- " + metrics.dimensions[d].name + ": " + metrics.dimensions[d].description + " <br>\n");
        }
        w("\n");
      }
      if (truthy(metrics.signals)) {
        w("Underlying evaluation signals used in this run: <br>\n");
        for (var s = 0; s < metrics.signals.length; s++) {
          w("- `" + metrics.signals[s].name + "`: " + metrics.signals[s].description + " <br>\n");
        }
        w("\n");
      }
    } else {
      w(metrics + " <br>\n");
    }
    w("\n\n");
  }

  if (truthy(evaluation.results_markdown)) {
    w("## Evaluation Results: <br>\n");
    w(evaluation.results_markdown + "\n\n");
  }

  if (truthy(evaluation.testing_completed)) {
    w("## Testing Completed: <br>\n");
    var testing = evaluation.testing_completed;
    var boxes = [
      ["agent_red_teaming", "Agent Red-Teaming"],
      ["network_security", "Network Security"],
      ["product_security", "Product Security"],
    ];
    for (var t = 0; t < boxes.length; t++) {
      w("**[" + (truthy(testing[boxes[t][0]]) ? "x" : " ") + "] " + boxes[t][1] + "** <br>\n");
    }
    w("\n");
  }

  w("## Skill Version(s): <br>\n");
  w(ctx.skill_version + " <br>\n\n");

  if (ctx.owner.kind === "nvidia") {
    w("## Ethical Considerations: <br>\n");
    w("NVIDIA believes Trustworthy AI is a shared responsibility and we have established policies and practices to enable development for a wide array of AI applications. When downloaded or used in accordance with our terms of service, developers should work with their internal team to ensure this skill meets requirements for the relevant industry and use case and addresses unforeseen product misuse. <br>\n\n");
    w("(For Release on NVIDIA Platforms Only) <br>\n");
    w("Please report quality, risk, security vulnerabilities or NVIDIA AI Concerns [here](https://app.intigriti.com/programs/nvidia/nvidiavdp/detail). <br>");
  }
  w("\n");

  return out.join("");
}

/* ─── Entry point ────────────────────────────────────────────────────────── */

function parseOutName(argv) {
  for (var i = 0; i < argv.length; i++) {
    if (argv[i] === "--out" && i + 1 < argv.length) return argv[i + 1];
    if (argv[i].indexOf("--out=") === 0) return argv[i].slice(6);
  }
  return null;
}

function main() {
  if (!truthy(stdin)) {
    console.error("ERROR: no context on stdin. Pipe the context JSON in.");
    process.exit(2);
  }
  var ctx;
  try {
    ctx = JSON.parse(stdin);
  } catch (ex) {
    console.error("ERROR: context is not valid JSON: " + ex.message);
    process.exit(2);
  }
  if (!isDict(ctx) || Array.isArray(ctx)) {
    console.error("ERROR: context must be a JSON object.");
    process.exit(2);
  }

  var errors = validate(ctx);
  if (errors.length) {
    console.error("Context validation failed:");
    for (var i = 0; i < errors.length; i++) console.error("  - " + errors[i]);
    process.exit(3);
  }

  applyMarkerDefaults(ctx);

  var rendered = renderCard(ctx);
  var outName = parseOutName(args) || String(ctx.skill_name || "skill") + "-card.md";
  writeFile(outName, rendered);
  console.log(rendered);
  console.log("Rendered card: " + outName);
}

main();
