// SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
// Author: Ivan Rodriguez

/*
 * discover_assets.js — Skill Card Asset Discoverer
 *
 * JavaScript port of discover_assets.py for the builder's in-JVM skill-script
 * sandbox. It emits the same bounded, redacted signal summary the Python
 * version prints: skill frontmatter, repo-root signals, URL / agent /
 * credential / MCP / constraint scans, the docs index and capped excerpts.
 *
 * How to call: put a JSON manifest on stdin and stage every file it names as
 * an input file for this run. The manifest shape is
 *   { "target": "/abs/path/to/target-skill",
 *     "repo_root": "/abs/path/to/repo",
 *     "helper_dir": "/abs/path/to/skill-card-generator",   // optional
 *     "git": { "describe": "...", "last_commit_sha": "...",
 *              "last_commit_date": "...", "remote_url": "..." },
 *     "files": [ { "path": "SKILL.md", "scope": "skill" },
 *                { "path": "docs/index.md", "scope": "repo" } ] }
 * where "path" is relative to target for scope skill and to repo_root for
 * scope repo. File contents are never in the manifest; each one is fetched by
 * basename from what the caller staged. helper_dir names the skill this script
 * came from — its references/ holds the style guide and the card template —
 * and defaults to target.
 *
 * Listing the target tree, walking up to the repo root and running git are the
 * CALLER's job: a sandboxed script sees no directory it was not handed and runs
 * no commands. Everything else, including the redaction, the budgeting and the
 * printed layout, is the Python's, unchanged.
 */

// ─── Constants ────────────────────────────────────────────────────────────

const FILE_CHAR_LIMIT = 1800;
const TOTAL_CHAR_LIMIT = 14000;
const README_CHAR_LIMIT = 1200;
const EVAL_DOC_CHAR_LIMIT = 1500;
const EVAL_DOC_LIMIT = 2;
const CHANGELOG_BODY_CHAR_LIMIT = 1800;
const LICENSE_SCAN_LINE_LIMIT = 5;
const LICENSE_IDENTIFIER_CHAR_LIMIT = 120;
const FRONTMATTER_DELIMITER = "---";
const FRONTMATTER_MARKER_OFFSET = FRONTMATTER_DELIMITER.length;
const CONSTRAINT_SENTENCE_CHAR_LIMIT = 300;
const MCP_REF_LIMIT = 10;
const CONSTRAINT_LIMIT = 25;
const DOC_H1_SCAN_LINE_LIMIT = 40;
const CHANGELOG_BODY_OUTPUT_LINE_LIMIT = 40;
const URL_PLATFORM_OUTPUT_LIMIT = 10;
const SOURCE_OUTPUT_LIMIT = 3;
const DOCS_INDEX_LIMIT = 30;
const REFERENCE_APPENDIX_CHAR_LIMIT = 1800;
const USAGE_ERROR_EXIT_CODE = 2;
const NOT_FOUND_INDEX = -1;
const FIRST_ITEM_INDEX = 0;
const INITIAL_CHAR_COUNT = 0;
const SECTION_RULE_WIDTH = 70;
const SINGULAR_COUNT = 1;
const SKILL_DEF_FULL = true; // Skill definition always extracted in full

const ROLE_ORDER = [
  "Skill definition",
  "Documentation",
  "Reference material",
  "Scripts",
  "Config",
  "Other",
];

// The roles the extraction pass walks, in priority order. Other is excluded.
const EXTRACT_PRIORITY = [
  "Skill definition",
  "Documentation",
  "Reference material",
  "Scripts",
  "Config",
];

const LICENSE_CANDIDATES = ["LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING"];
const README_CANDIDATES = ["README.md", "README.rst", "README.txt"];
const THIRD_PARTY_CANDIDATES = [
  "third_party_oss_license.txt",
  "third_party_licenses.txt",
  "NOTICE",
];

const KNOWN_AGENTS = [
  "Amp",
  "Astra",
  "Blackbox",
  "Claude Code",
  "Codex",
  "Cursor",
  "Gemini Command Line Interface",
  "Gemini CLI",
  "GitHub Copilot",
  "Goose",
  "Junie",
  "OpenCode",
  "OpenClaw",
  "Hermes",
  "Kiro",
  "Roo Code",
];

// [platform, domains] pairs; the order is the printed order.
const PLATFORM_DOMAINS = [
  ["Build.Nvidia.com", ["build.nvidia.com", "nvcr.io"]],
  ["GitHub", ["github.com"]],
  ["Hugging Face", ["huggingface.co", "hf.co"]],
  ["NGC", ["ngc.nvidia.com", "catalog.ngc.nvidia.com"]],
];

const API_KEY_PATTERNS = [
  /\b[A-Z][A-Z0-9_]{1,}_(?:API_KEY|ACCESS_TOKEN|TOKEN|CLIENT_SECRET|SECRET|PRIVATE_KEY)\b/g,
  /\bHF_TOKEN\b/g,
  /\bNGC_API_KEY\b/g,
  /\bOPENAI_API_KEY\b/g,
  /\bANTHROPIC_API_KEY\b/g,
  /\bGITHUB_TOKEN\b/g,
  /\bGOOGLE_APPLICATION_CREDENTIALS\b/g,
  /\bAZURE_(?:CLIENT_ID|CLIENT_SECRET|TENANT_ID|STORAGE_KEY)\b/g,
  /\bAWS_(?:ACCESS_KEY_ID|SECRET_ACCESS_KEY|SESSION_TOKEN)\b/g,
  /\bAWS_[A-Z_]+_KEY\b/g,
  /\bPRIVATE_TOKEN\b/g,
  /\bNVAPIKEY\b/g,
  /\bTOKEN\b/g,
];

const PLACEHOLDER_RE = /^(?:YOUR|MY|EXAMPLE|SAMPLE|REPLACE|PLACEHOLDER)_|^<[^>]+>$/i;

const MCP_PATTERNS = [/\bmcp__[a-z0-9_\-]+/gi, /MCP\s+server/gi];

const CONSTRAINT_KEYWORDS = [
  "not supported",
  "not yet available",
  "must be disabled",
  "only supported",
  "cannot",
  "unsupported",
  "requires",
  "limited to",
];

const EVAL_KEYWORDS = [
  "eval",
  "evaluation",
  "benchmark",
  "performance",
  "accuracy",
  "testing",
  "metric",
  "metrics",
  "validation",
  "red-team",
  "red team",
  "red_teaming",
  "redteam",
  "network security",
  "product security",
];

// Legal/process links that should NOT be emitted as release channels.
const LEGAL_URL_FRAGMENTS = [
  "sharepoint.com",
  "confluence.nvidia.com",
  "nvbugspro.nvidia.com",
  "forms.office.com",
  "app.intigriti.com",
  "nvidia.com/object/submit",
  "psirt",
];

const SENSITIVE_REDACTION = "[REDACTED]";

const IGNORED_DIRECTORY_PARTS = [
  "__pycache__",
  ".aws",
  ".azure",
  ".config",
  ".git",
  ".gnupg",
  ".gcloud",
  ".kube",
  ".ssh",
  ".venv",
  "node_modules",
];

const SENSITIVE_FILENAMES = [
  ".dockerconfigjson",
  ".env",
  ".env.local",
  ".envrc",
  ".netrc",
  ".npmrc",
  ".pypirc",
  "credentials",
  "credentials.json",
  "id_dsa",
  "id_ecdsa",
  "id_ed25519",
  "id_rsa",
  "secrets.json",
  "secrets.yaml",
  "secrets.yml",
];

const SENSITIVE_NAME_PREFIXES = [
  ".env.",
  ".env-",
  "credentials.",
  "credentials-",
  "secret.",
  "secret-",
  "secrets.",
  "secrets-",
];

const SENSITIVE_NAME_SUFFIXES = [".key", ".pem", ".p12", ".pfx"];

const SENSITIVE_VALUE_PATTERNS = [
  [
    /\b(["']?(?:password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|client[_-]?secret)["']?\s*[:=]\s*)([^\s"'`]+|"[^"]*"|'[^']*')/gi,
    "$1" + SENSITIVE_REDACTION,
  ],
  [
    /\b(authorization\s*:\s*bearer\s+)([A-Za-z0-9._~+\/=-]+)/gi,
    "$1" + SENSITIVE_REDACTION,
  ],
  [
    /([?&](?:token|api_key|key|secret|password|access_token)=)[^&\s)>\]"'`]+/gi,
    "$1" + SENSITIVE_REDACTION,
  ],
  [
    /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b|\b(?:sk|hf|ghp|glpat|nvapi)-?[A-Za-z0-9_=-]{20,}\b|\bgithub_pat_[A-Za-z0-9_]{20,}\b/g,
    SENSITIVE_REDACTION,
  ],
];

const CREDENTIAL_FILE_RE = /\b(?:token|credential|auth|secret)[s]?\.(?:yaml|yml|json)\b/i;

const GIT_KEYS = ["describe", "last_commit_sha", "last_commit_date", "remote_url"];

// Extensions that make a file a script wherever it sits, plus the suffix sets
// the role test walks. Mirrors the Python's inline sets.
const SCRIPT_SUFFIXES = [".py", ".sh", ".js", ".ts", ".bash"];
const FRONTMATTER_SUFFIXES = [".md", ".yaml", ".yml"];
const DOCUMENTATION_SUFFIXES = [".md", ".rst", ".txt"];
const CONFIG_SUFFIXES = [".yaml", ".yml", ".toml", ".json", ".ini", ".env", ".cfg"];

const LICENSE_IDENTIFIER_PATTERNS = [
  [/BSD[- ]?2[- ]?Clause/i, "BSD 2-Clause"],
  [/BSD[- ]?3[- ]?Clause/i, "BSD 3-Clause"],
  [/Apache\s+License.*2\.0/i, "Apache 2.0"],
  [/MIT License/i, "MIT"],
  [/GNU GENERAL PUBLIC LICENSE.*Version 3/i, "GPL-3.0"],
  [/GNU GENERAL PUBLIC LICENSE.*Version 2/i, "GPL-2.0"],
  [/Mozilla Public License/i, "MPL-2.0"],
  // CC licences are titled "Attribution 4.0 International" /
  // "Creative Commons Attribution...", which matched no pattern
  // and fell through to the verbatim-first-line fallback.
  [/Creative Commons Attribution|Attribution 4\.0 International/i, "CC BY 4.0"],
  [
    /NVIDIA AI Foundation Models Community License/i,
    "NVIDIA AI Foundation Models Community License",
  ],
];

// The two references this skill's own folder supplies, as
// [section label, path inside the skill folder, name in the not-found line].
const HELPER_APPENDICES = [
  ["STYLE GUIDE EXCERPT", "references/style-guide.md", "style-guide.md", "Style guide: "],
  ["JINJA TEMPLATE EXCERPT", "references/skill-card.md.j2", "skill-card.md.j2", "Card template: "],
];

// ─── Output and path helpers ──────────────────────────────────────────────

/** One stdout line, the way print() does it: the text, then a newline. */
function print(line) {
  console.log(line === undefined ? "" : String(line));
}

/** Python's str() for the values that can be missing. */
function text(value) {
  return value === null || value === undefined ? "None" : String(value);
}

function joinAbs(root, rel) {
  const base = root.length > 1 && root.endsWith("/") ? root.slice(0, -1) : root;
  return base === "/" ? "/" + rel : base + "/" + rel;
}

function basename(path) {
  const i = path.lastIndexOf("/");
  return i < 0 ? path : path.slice(i + 1);
}

/** The path components, the way pathlib splits them. */
function partsOf(path) {
  return path.split("/").filter((p) => p.length > 0);
}

/** The final component's suffix, lowercased, as Path.suffix reports it. */
function suffixOf(name) {
  const i = name.lastIndexOf(".");
  return i <= 0 ? "" : name.slice(i).toLowerCase();
}

/** The final component without its suffix, as Path.stem reports it. */
function stemOf(name) {
  const i = name.lastIndexOf(".");
  return i <= 0 ? name : name.slice(0, i);
}

/** str.splitlines() — the line breaks Python's version recognises. */
function splitLines(body) {
  return body.split(/\r\n|\r|\n|\u000b|\f|\u001c|\u001d|\u001e|\u0085|\u2028|\u2029/);
}

function sortStrings(values) {
  return values.slice().sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
}

/**
 * Order two paths the way sorted(Path) does: component by component, never as
 * one string. The difference is visible only where a directory name is a
 * prefix of its sibling — "a/notes.md" and "a-b/notes.md" — because "/" sorts
 * below "-" and ".", which a string compare gets backwards.
 */
function comparePaths(a, b) {
  const left = partsOf(a);
  const right = partsOf(b);
  for (let i = 0; i < Math.min(left.length, right.length); i++) {
    if (left[i] !== right[i]) return left[i] < right[i] ? -1 : 1;
  }
  return left.length - right.length;
}

/** Escape a literal string for use inside a regular expression. */
function escapeRegExp(literal) {
  return literal.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Path relative to a root, or the path itself when it is not below it. */
function relativeTo(root, abs) {
  if (abs.startsWith(root + "/")) return abs.slice(root.length + 1);
  return abs;
}

// ─── Manifest ─────────────────────────────────────────────────────────────

function usageError() {
  console.error(
    "Usage: put a JSON manifest on stdin — {target, repo_root, git, files:" +
      " [{path, scope}]} — with those files staged for this run."
  );
  process.exit(USAGE_ERROR_EXIT_CODE);
}

function readManifest() {
  const raw = typeof stdin === "string" ? stdin.trim() : "";
  if (!raw) usageError();
  let parsed = null;
  try {
    parsed = JSON.parse(raw);
  } catch (ex) {
    usageError();
  }
  const valid =
    parsed &&
    typeof parsed === "object" &&
    !Array.isArray(parsed) &&
    Array.isArray(parsed.files) &&
    typeof parsed.target === "string" &&
    parsed.target.length > 0;
  if (!valid) usageError();
  return parsed;
}

/** Trailing separators off, so a root compares and joins predictably. */
function dirOf(p) {
  if (typeof p !== "string" || p.length === 0) return p;
  return p.length > 1 && p.endsWith("/") ? p.slice(0, -1) : p;
}

const manifest = readManifest();
const target = dirOf(manifest.target);
const repoRoot = dirOf(manifest.repo_root || manifest.target);
const helperDir = dirOf(manifest.helper_dir || manifest.target);
const repoRootNested = repoRoot !== target;

/** Absolute path of a manifest entry, resolved against its scope root. */
function absoluteOf(entry) {
  return entry.scope === "skill"
    ? joinAbs(target, entry.path)
    : joinAbs(repoRoot, entry.path);
}

const skillEntries = manifest.files.filter((f) => f && typeof f.path === "string" && f.scope === "skill");

// Every manifest file seen from the repo root. The governance files live above
// the skill, but when the target IS the repo root they are the skill files, so
// the repo lookups span both scopes and are keyed by resolved path, not by the
// scope label the caller happened to write down.
const byRepoPath = new Map();
manifest.files.forEach((f) => {
  if (!f || typeof f.path !== "string") return;
  const rel = relativeTo(repoRoot, absoluteOf(f));
  if (!byRepoPath.has(rel)) byRepoPath.set(rel, f);
});

// Staged files are addressed by basename, so two paths sharing one name get
// the same bytes. That is a staging limitation, not a data error: read once,
// use for both, and say which names collided.
(function warnOnCollisions() {
  const seen = new Map();
  manifest.files.forEach((f) => {
    if (!f || typeof f.path !== "string") return;
    const name = basename(f.path);
    if (!seen.has(name)) seen.set(name, []);
    if (seen.get(name).indexOf(f.path) < 0) seen.get(name).push(f.path);
  });
  const collided = sortStrings(Array.from(seen.keys()).filter((b) => seen.get(b).length > 1));
  if (collided.length) {
    console.error(
      "warning: staged basenames collide, one read serves every path that names them: " +
        collided.join(", ")
    );
  }
})();

// ─── File access ──────────────────────────────────────────────────────────

const contentCache = new Map(); // read name -> text, or null when unreadable

/**
 * The bytes of a file, fetched once per read name. `readName` is what the
 * sandbox is asked for: the basename for a file the caller staged, or the
 * folder-relative name for one of this skill's own references — the staged
 * lookup strips a path either way, the own-folder lookup needs it kept.
 */
function fetchRaw(absPath, readName) {
  const name = readName === undefined ? basename(absPath) : readName;
  if (contentCache.has(name)) return contentCache.get(name);
  let body = null;
  try {
    body = readFile(name);
  } catch (ex) {
    body = null;
  }
  if (typeof body !== "string") body = null;
  contentCache.set(name, body);
  return body;
}

/** True for credential files and ignored implementation folders. */
function shouldSkipPath(path) {
  const parts = partsOf(path).map((p) => p.toLowerCase());
  for (const part of parts) {
    if (IGNORED_DIRECTORY_PARTS.indexOf(part) >= 0) return true;
  }
  const name = basename(path).toLowerCase();
  if (SENSITIVE_FILENAMES.indexOf(name) >= 0) return true;
  for (const prefix of SENSITIVE_NAME_PREFIXES) {
    if (name.startsWith(prefix)) return true;
  }
  for (const suffix of SENSITIVE_NAME_SUFFIXES) {
    if (name.endsWith(suffix)) return true;
  }
  return false;
}

/** Mask credential-like values before emitting text to stdout. */
function redactSensitiveText(body) {
  let redacted = body;
  for (const pair of SENSITIVE_VALUE_PATTERNS) {
    redacted = redacted.replace(pair[0], pair[1]);
  }
  return redacted;
}

/**
 * Readable, redacted, capped content — read_content's three outcomes: the
 * text, a sentinel for a file the policy skips, and one for a file that could
 * not be read.
 */
function readContent(absPath, limit, readName) {
  if (shouldSkipPath(absPath)) return "[sensitive file skipped]";
  const body = fetchRaw(absPath, readName);
  if (body === null) return "[unreadable]";
  const redacted = redactSensitiveText(body);
  if (limit === null || limit === undefined || redacted.length <= limit) return redacted;
  return redacted.slice(0, limit) + "\n... [truncated at " + limit + " chars]";
}

/** The YAML frontmatter block, or null when there is no parseable one. */
function frontmatterHeader(absPath) {
  const body = fetchRaw(absPath);
  if (body === null) return null;
  if (!body.startsWith(FRONTMATTER_DELIMITER)) return null;
  const end = body.indexOf("\n" + FRONTMATTER_DELIMITER, FRONTMATTER_MARKER_OFFSET);
  if (end === NOT_FOUND_INDEX) return null;
  return body.slice(FRONTMATTER_MARKER_OFFSET, end);
}

function hasYamlFrontmatter(absPath) {
  const header = frontmatterHeader(absPath);
  return header !== null && header.indexOf("name:") >= 0 && header.indexOf("description:") >= 0;
}

function parseFrontmatter(absPath) {
  const out = {};
  const header = frontmatterHeader(absPath);
  if (header === null) return out;
  for (const line of splitLines(header)) {
    const m = /^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$/.exec(line);
    if (!m) continue;
    const val = redactSensitiveText(m[2].trim().replace(/^"+|"+$/g, "").replace(/^'+|'+$/g, ""));
    if (val) out[m[1]] = val;
  }
  return out;
}

// ─── Parsers ──────────────────────────────────────────────────────────────

/** Identify the license from the first non-empty line of a LICENSE file. */
function parseLicenseIdentifier(body) {
  if (body === null) return null;
  const lines = splitLines(body).slice(0, LICENSE_SCAN_LINE_LIMIT);
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line) continue;
    for (const pair of LICENSE_IDENTIFIER_PATTERNS) {
      if (pair[0].test(line)) return pair[1];
    }
    // If no pattern hits, return the first line verbatim (capped)
    return line.slice(0, LICENSE_IDENTIFIER_CHAR_LIMIT);
  }
  return null;
}

function parsePyprojectVersion(body) {
  if (body === null) return null;
  const m = /^\s*version\s*=\s*["'](.+?)["']/m.exec(body);
  return m ? m[1] : null;
}

function parsePackageJsonVersion(body) {
  if (body === null) return null;
  try {
    const data = JSON.parse(body);
    if (data && typeof data === "object") {
      return data.version === undefined ? null : data.version;
    }
  } catch (ex) {
    return null;
  }
  return null;
}

/** {version, date, body} from the top entry of a Keep-a-Changelog file. */
function parseChangelogTopEntry(body) {
  const out = {};
  if (body === null) return out;
  const redacted = redactSensitiveText(body);
  // Match first version header: ## [1.2.3] - 2026-03-03  (or similar)
  const m = /^##\s*\[?([0-9][^\]\s]*)\]?\s*[-–]\s*(\d{4}-\d{2}-\d{2})/m.exec(redacted);
  if (!m) return out;
  out.version = m[1];
  out.date = m[2];
  // Body: from end of header line until next ## or EOF.
  const start = m.index + m[0].length;
  const nextHeading = /\n##\s/.exec(redacted.slice(start));
  const bodyEnd = nextHeading ? start + nextHeading.index : redacted.length;
  out.body = redacted.slice(start, bodyEnd).trim().slice(0, CHANGELOG_BODY_CHAR_LIMIT);
  return out;
}

/** The first `# ` heading within the scan window, or null. */
function firstH1(absPath) {
  const body = fetchRaw(absPath);
  if (body === null) return null;
  const lines = splitLines(body).slice(0, DOC_H1_SCAN_LINE_LIMIT);
  for (const line of lines) {
    const m = /^#\s+(.+?)\s*$/.exec(line);
    if (m) return m[1];
  }
  return null;
}

// ─── Pattern scans ────────────────────────────────────────────────────────

/**
 * Every match of a pattern, in order of occurrence, as findall returns them.
 * A capturing group (not a non-capturing, lookahead or lookbehind group) is
 * what findall would hand back; a group-free pattern returns the whole match.
 * String.match on a /g pattern yields whole matches only, so this goes through
 * matchAll, the call that can see a group at all.
 */
function findAll(body, pattern) {
  const hits = [];
  if (typeof body !== "string" || body.length === 0) return hits;
  const flags = pattern.flags.indexOf("g") >= 0 ? pattern.flags : pattern.flags + "g";
  const capturing = /\((?!\?)/.test(pattern.source);
  for (const m of body.matchAll(new RegExp(pattern.source, flags))) {
    hits.push(capturing && m.length > 1 ? m[1] : m[0]);
  }
  return hits;
}

function findUrls(body) {
  return findAll(body, /https?:\/\/[^\s)>\]"'`]+/g);
}

function groupUrlsByPlatform(urls) {
  const groups = new Map();
  for (const pair of PLATFORM_DOMAINS) groups.set(pair[0], []);
  groups.set("Other", []);
  for (const url of urls) {
    let legal = false;
    for (const frag of LEGAL_URL_FRAGMENTS) {
      if (url.indexOf(frag) >= 0) legal = true;
    }
    if (legal) continue; // Legal boilerplate URLs are not release channels
    let matched = false;
    for (const pair of PLATFORM_DOMAINS) {
      let hit = false;
      for (const domain of pair[1]) {
        if (url.indexOf(domain) >= 0) hit = true;
      }
      if (!hit) continue;
      const bucket = groups.get(pair[0]);
      if (bucket.indexOf(url) < 0) bucket.push(url);
      matched = true;
      break;
    }
    if (!matched && groups.get("Other").indexOf(url) < 0) groups.get("Other").push(url);
  }
  return groups;
}

function findAgents(body) {
  const found = [];
  for (const agent of KNOWN_AGENTS) {
    if (new RegExp("\\b" + escapeRegExp(agent) + "\\b", "i").test(body)) {
      if (found.indexOf(agent) < 0) found.push(agent);
    }
  }
  return found;
}

function findApiKeys(body) {
  const keys = [];
  for (const pattern of API_KEY_PATTERNS) {
    for (const hit of findAll(body, pattern)) {
      if (keys.indexOf(hit) < 0 && !PLACEHOLDER_RE.test(hit)) keys.push(hit);
    }
  }
  return keys;
}

/** Where each detected key was seen, relative to the repo root where possible. */
function findApiKeySources(extracted) {
  const sources = new Map();
  for (const entry of extracted) {
    for (const key of findApiKeys(entry.content)) {
      if (!sources.has(key)) sources.set(key, []);
      const bucket = sources.get(key);
      if (bucket.indexOf(entry.source) < 0) bucket.push(entry.source);
    }
  }
  return sources;
}

function findMcpRefs(body) {
  const refs = [];
  for (const pattern of MCP_PATTERNS) {
    for (const hit of findAll(body, pattern)) {
      if (refs.indexOf(hit) < 0) refs.push(hit);
    }
  }
  return refs.slice(0, MCP_REF_LIMIT);
}

/**
 * re.split(r"(?<=[.!?])\s+|\n", text) without a lookbehind, so the sandbox's
 * regex engine is never a dependency: at each position the sentence-end
 * alternative wins wherever it applies, else a newline breaks the sentence.
 */
function splitSentences(body) {
  const pieces = [];
  let last = 0;
  let i = 0;
  while (i < body.length) {
    if (i > 0 && /[.!?]/.test(body[i - 1]) && /\s/.test(body[i])) {
      let j = i;
      while (j < body.length && /\s/.test(body[j])) j += 1;
      pieces.push(body.slice(last, i));
      last = j;
      i = j;
      continue;
    }
    if (body[i] === "\n") {
      pieces.push(body.slice(last, i));
      last = i + 1;
      i += 1;
      continue;
    }
    i += 1;
  }
  pieces.push(body.slice(last));
  return pieces;
}

function findConstraints(body) {
  const hits = [];
  for (const rawSentence of splitSentences(body)) {
    const clean = rawSentence.trim();
    if (!clean || clean.length > CONSTRAINT_SENTENCE_CHAR_LIMIT) continue;
    const lower = clean.toLowerCase();
    let hit = false;
    for (const kw of CONSTRAINT_KEYWORDS) {
      if (lower.indexOf(kw) >= 0) hit = true;
    }
    if (hit && hits.indexOf(clean) < 0) hits.push(clean);
  }
  return hits.slice(0, CONSTRAINT_LIMIT);
}

function countArgumentsUsage(body) {
  const hits = typeof body === "string" ? body.match(/\$ARGUMENTS/g) : null;
  return hits ? hits.length : 0;
}

// ─── Skill-dir categorization (unchanged role logic, now from paths) ──────

/**
 * Roles from path plus extension, as categorize_skill_dir derives them. The
 * walk order is the Python's: paths sorted, roles in declaration order.
 */
function categorizeSkillDir() {
  const roles = {};
  for (const role of ROLE_ORDER) roles[role] = [];
  const ordered = skillEntries.slice().sort((a, b) => comparePaths(a.path, b.path));
  for (const entry of ordered) {
    const rel = entry.path;
    if (shouldSkipPath(rel)) continue;
    const dirParts = partsOf(rel).slice(0, -1);
    const suffix = suffixOf(basename(rel));
    if (dirParts.indexOf("references") >= 0) {
      roles["Reference material"].push(entry);
      continue;
    }
    if (dirParts.indexOf("scripts") >= 0 || SCRIPT_SUFFIXES.indexOf(suffix) >= 0) {
      roles["Scripts"].push(entry);
      continue;
    }
    if (FRONTMATTER_SUFFIXES.indexOf(suffix) >= 0 && hasYamlFrontmatter(absoluteOf(entry))) {
      roles["Skill definition"].push(entry);
      continue;
    }
    if (DOCUMENTATION_SUFFIXES.indexOf(suffix) >= 0) {
      roles["Documentation"].push(entry);
      continue;
    }
    if (CONFIG_SUFFIXES.indexOf(suffix) >= 0) {
      roles["Config"].push(entry);
      continue;
    }
    roles["Other"].push(entry);
  }
  return roles;
}

// ─── Repo-root signals, from the manifest's repo-scope files ──────────────

/** First candidate present among the repo-root files, in candidate order. */
function repoFileNamed(candidates) {
  for (const name of candidates) {
    if (byRepoPath.has(name)) return { name: name, entry: byRepoPath.get(name) };
  }
  return null;
}

function collectRepoSignals() {
  const out = { repo_root: repoRoot, is_nested: repoRootNested };

  const lic = repoFileNamed(LICENSE_CANDIDATES);
  if (lic) {
    out.license_file = lic.name;
    out.license_identifier = parseLicenseIdentifier(fetchRaw(absoluteOf(lic.entry)));
  }

  // Version signals — try multiple sources, report all
  const versions = {};
  const py = repoFileNamed(["pyproject.toml"]);
  if (py) {
    const v = parsePyprojectVersion(fetchRaw(absoluteOf(py.entry)));
    if (v) versions.pyproject = v;
  }
  const pkg = repoFileNamed(["package.json"]);
  if (pkg) {
    const v = parsePackageJsonVersion(fetchRaw(absoluteOf(pkg.entry)));
    if (v) versions.package_json = v;
  }
  const cl = repoFileNamed(["CHANGELOG.md"]);
  if (cl) {
    const entry = parseChangelogTopEntry(fetchRaw(absoluteOf(cl.entry)));
    if (entry.version) {
      versions.changelog = entry.version;
      out.changelog_top_entry = entry;
    }
  }
  if (Object.keys(versions).length) out.versions = versions;

  // Git — the caller resolved it. A missing or empty value is absent, exactly
  // as a failed git call is in the Python.
  const git = {};
  if (manifest.git && typeof manifest.git === "object") {
    for (const key of GIT_KEYS) {
      const value = manifest.git[key];
      if (typeof value === "string" && value.trim()) git[key] = value.trim();
    }
  }
  if (Object.keys(git).length) out.git = git;

  // Known-issue / docs scan
  const docFiles = [];
  const evalDocs = [];
  const docs = Array.from(byRepoPath.keys())
    .filter((p) => p.startsWith("docs/") && p.endsWith(".md"))
    .sort(comparePaths);
  for (const rel of docs) {
    const name = basename(rel);
    const title = firstH1(joinAbs(repoRoot, rel)) || stemOf(name);
    const entry = { path: rel, title: title };
    docFiles.push(entry);
    // Flag as evaluation-relevant by name or title
    const nameLower = stemOf(name).toLowerCase();
    const titleLower = title.toLowerCase();
    let flagged = false;
    for (const kw of EVAL_KEYWORDS) {
      if (nameLower.indexOf(kw) >= 0 || titleLower.indexOf(kw) >= 0) flagged = true;
    }
    if (flagged) evalDocs.push(entry);
  }
  if (docFiles.length) out.docs = docFiles;
  if (evalDocs.length) out.evaluation_docs = evalDocs;

  const readme = repoFileNamed(README_CANDIDATES);
  if (readme) out.readme = readme.name;

  const sec = repoFileNamed(["SECURITY.md"]);
  if (sec) out.security_md = sec.name;

  // Third-party license file presence (useful for Database Type context)
  for (const name of THIRD_PARTY_CANDIDATES) {
    if (repoFileNamed([name])) {
      if (!out.third_party_license_files) out.third_party_license_files = [];
      out.third_party_license_files.push(name);
    }
  }
  return out;
}

// ─── Content extraction for the agent ─────────────────────────────────────

/** Skill-local file contents, prioritized and budgeted. */
function extractSkillContents(roles) {
  const extracted = [];
  let total = INITIAL_CHAR_COUNT;
  for (const role of EXTRACT_PRIORITY) {
    for (const entry of roles[role] || []) {
      const abs = absoluteOf(entry);
      if (role === "Skill definition" && SKILL_DEF_FULL) {
        const full = readContent(abs, null);
        extracted.push({ role: role, path: entry.path, abs: abs, content: full });
        total += full.length;
        continue;
      }
      if (total >= TOTAL_CHAR_LIMIT) break;
      const remaining = TOTAL_CHAR_LIMIT - total;
      const content = readContent(abs, Math.min(FILE_CHAR_LIMIT, remaining));
      extracted.push({ role: role, path: entry.path, abs: abs, content: content });
      total += content.length;
    }
    if (total >= TOTAL_CHAR_LIMIT && role !== "Skill definition") break;
  }
  return extracted;
}

/** A small set of repo-root governance files, capped. */
function extractRepoContents(repoSignals) {
  const extracted = [];
  // CHANGELOG top entry is already parsed; don't re-emit full file.
  // README: enough for description + audience.
  if (repoSignals.readme) {
    const abs = joinAbs(repoRoot, repoSignals.readme);
    extracted.push({
      role: "Repo README",
      path: repoSignals.readme,
      abs: abs,
      content: readContent(abs, README_CHAR_LIMIT),
    });
  }
  // Evaluation docs: small sample with capped content.
  for (const d of (repoSignals.evaluation_docs || []).slice(0, EVAL_DOC_LIMIT)) {
    const abs = joinAbs(repoRoot, d.path);
    extracted.push({
      role: "Repo eval doc",
      path: d.path,
      abs: abs,
      content: readContent(abs, EVAL_DOC_CHAR_LIMIT),
    });
  }
  return extracted;
}

// ─── Output ───────────────────────────────────────────────────────────────

function emitSignalSummary(roles, skillExtracted, repoExtracted, repoSignals) {
  print("\n" + "=".repeat(SECTION_RULE_WIDTH));
  print("\n=== STRUCTURED SIGNAL SUMMARY ===");
  print("# These are the pre-extracted signals for card context assembly.");
  print("# Consult this section before scanning raw file contents.");
  print("=".repeat(SECTION_RULE_WIDTH) + "\n");

  // Skill definition frontmatter
  let fm = {};
  if (roles["Skill definition"].length) {
    fm = parseFrontmatter(absoluteOf(roles["Skill definition"][FIRST_ITEM_INDEX]));
  }
  print("## Skill definition frontmatter");
  const fmKeys = Object.keys(fm);
  if (fmKeys.length) {
    for (const k of fmKeys) print("  " + k + ": " + fm[k]);
  } else {
    print("  [no parseable frontmatter]");
  }
  print();

  // Repo signals
  print("## Repo-root signals");
  if (repoSignals.is_nested) print("  repo_root: " + repoSignals.repo_root);
  else print("  [skill directory IS the repo root — no nesting]");
  if (repoSignals.license_identifier) {
    print(
      "  license_identifier: " + repoSignals.license_identifier +
        "  (from " + text(repoSignals.license_file) + ")"
    );
  }
  if (repoSignals.versions) {
    for (const src of Object.keys(repoSignals.versions)) {
      print("  version." + src + ": " + repoSignals.versions[src]);
    }
  }
  if (repoSignals.git) {
    for (const k of GIT_KEYS) {
      if (repoSignals.git[k] !== undefined) print("  git." + k + ": " + repoSignals.git[k]);
    }
  }
  const changelog = repoSignals.changelog_top_entry;
  if (changelog) {
    print("  changelog.version: " + text(changelog.version));
    print("  changelog.date: " + text(changelog.date));
    if (changelog.body) {
      print("  changelog.body: |");
      for (const line of splitLines(changelog.body).slice(0, CHANGELOG_BODY_OUTPUT_LINE_LIMIT)) {
        print("    " + line);
      }
    }
  }
  if (repoSignals.readme) print("  readme: " + repoSignals.readme);
  if (repoSignals.security_md) print("  security_md: " + repoSignals.security_md);
  if (repoSignals.third_party_license_files) {
    for (const t of repoSignals.third_party_license_files) {
      print("  third_party_license_file: " + t);
    }
  }
  print();

  // Collect full text for pattern scans
  const entries = skillExtracted.concat(repoExtracted);
  let allText = entries.map((e) => e.content).join("\n");
  if (fmKeys.length) allText += "\n" + fmKeys.map((k) => k + ": " + fm[k]).join(" ");
  // CHANGELOG top-entry body is extracted separately; include it in the scan
  if (changelog && changelog.body) allText += "\n" + changelog.body;

  // URLs
  const groups = groupUrlsByPlatform(findUrls(allText));
  print("## Detected URLs by platform  (legal/process URLs excluded)");
  let anyUrls = false;
  for (const pair of Array.from(groups.entries())) {
    if (!pair[1].length) continue;
    anyUrls = true;
    print("  " + pair[0] + ":");
    for (const u of pair[1].slice(0, URL_PLATFORM_OUTPUT_LIMIT)) print("    - " + u);
  }
  if (!anyUrls) print("  [no release-channel URLs detected]");
  print();

  // Agents
  const agents = findAgents(allText);
  print("## Agents mentioned anywhere in sources");
  if (agents.length) {
    for (const a of agents) print("  - " + a);
  } else {
    print("  [none detected]");
  }
  print();

  // Credentials
  const keys = findApiKeys(allText);
  print("## Detected API-key / credential env vars");
  if (keys.length) {
    const keySources = findApiKeySources(
      entries.map((e) => ({ content: e.content, source: relativeTo(repoRoot, e.abs) }))
    );
    for (const k of keys) {
      const sources = keySources.has(k) ? keySources.get(k) : [];
      if (sources.length) {
        print("  - " + k + " (source: " + sources.slice(0, SOURCE_OUTPUT_LIMIT).join(", ") + ")");
      } else {
        print("  - " + k);
      }
    }
  } else {
    print("  [none detected]");
  }
  print();

  // MCP references
  const mcps = findMcpRefs(allText);
  print("## MCP / tool references");
  if (mcps.length) {
    for (const m of mcps) print("  - " + m);
  } else {
    print("  [none detected]");
  }
  print();

  // Auth classification hints
  if (mcps.length && !keys.length) {
    print(
      "  NOTE: MCP auth detected with no env vars — " +
        "credential status is likely 'yes' (auth handled server-side)."
    );
  }
  const skillFilesText = skillExtracted.map((e) => e.abs).join(" ").toLowerCase();
  if (CREDENTIAL_FILE_RE.test(skillFilesText)) {
    print(
      "  NOTE: File-based credential pattern detected in skill file paths — " +
        "set requires_api_key_or_credential to 'yes'."
    );
  }

  // $ARGUMENTS
  print("## $ARGUMENTS usage count: " + countArgumentsUsage(allText));
  print();

  // Constraint sentences
  const constraints = findConstraints(allText);
  print("## Constraint sentences (candidates for Known Technical Limitations)");
  if (constraints.length) {
    for (const c of constraints) print("  - " + c);
  } else {
    print("  [none detected]");
  }
  print();

  // Evaluation docs
  print("## Evaluation artifacts");
  const evalDocs = repoSignals.evaluation_docs || [];
  if (evalDocs.length) {
    for (const d of evalDocs) print("  - " + d.path + "  (" + d.title + ")");
  } else {
    print("  [none detected — omit optional evaluation fields unless user provides details]");
  }
  print();

  // Docs index
  if (repoSignals.docs) {
    print("## Repo docs/ index");
    for (const d of repoSignals.docs.slice(0, DOCS_INDEX_LIMIT)) {
      print("  - " + d.path + "  (" + d.title + ")");
    }
    print();
  }
}

/** Compact guidance for targeted reads after the summary review. */
function emitReadNextGuidance(roles, repoSignals) {
  print("\n" + "=".repeat(SECTION_RULE_WIDTH));
  print("\n=== READ NEXT ONLY IF NEEDED ===");
  print("=".repeat(SECTION_RULE_WIDTH) + "\n");
  print("# Use these paths for targeted follow-up reads instead of reloading this report.");
  if (roles["Skill definition"].length) {
    print("- Target skill definition: " + roles["Skill definition"][FIRST_ITEM_INDEX].path);
  }
  if (repoSignals.readme) {
    print("- Repo README excerpt source: " + joinAbs(repoRoot, repoSignals.readme));
  }
  for (const d of (repoSignals.evaluation_docs || []).slice(0, EVAL_DOC_LIMIT)) {
    print("- Evaluation source: " + joinAbs(repoRoot, d.path));
  }
  for (const a of HELPER_APPENDICES) {
    print("- " + a[3] + joinAbs(helperDir, a[1]));
  }
  print();
}

function emitExcerpts(title, entries) {
  print("\n" + "=".repeat(SECTION_RULE_WIDTH));
  print("\n=== " + title + " ===");
  print("=".repeat(SECTION_RULE_WIDTH) + "\n");
  for (const e of entries) {
    print("### [" + e.role + "] " + e.path);
    print("```");
    print(e.content);
    print("```\n");
  }
}

/** The two appendices this skill's own references/ supplies, capped. */
function emitHelperAppendices() {
  for (const a of HELPER_APPENDICES) {
    print("\n" + "=".repeat(SECTION_RULE_WIDTH));
    print("\n=== " + a[0] + " ===");
    print("=".repeat(SECTION_RULE_WIDTH) + "\n");
    const abs = joinAbs(helperDir, a[1]);
    // The read keeps the folder-relative name: only the staged lookup strips a
    // path, and these files were never staged — they are this skill's own.
    if (fetchRaw(abs, a[1]) === null) {
      print("[" + a[2] + " not found — check skill installation at " + helperDir + "]");
      continue;
    }
    print("# Source: " + abs);
    print("# Excerpt capped; read the source file directly if more detail is needed.\n");
    print(readContent(abs, REFERENCE_APPENDIX_CHAR_LIMIT, a[1]));
  }
}

// ─── Main ─────────────────────────────────────────────────────────────────

function main() {
  print("# Asset Discovery Report — Skill Card");
  print("# Skill target: " + target);
  print("# Repo root:    " + repoRoot);
  if (!repoRootNested) print("# (Skill directory is the repo root — no parent signals.)");
  print();

  const roles = categorizeSkillDir();
  for (const role of ROLE_ORDER) {
    const files = roles[role];
    if (!files.length) continue;
    print("## " + role + " (" + files.length + " file" + (files.length !== SINGULAR_COUNT ? "s" : "") + ")");
    for (const entry of files) print("  - " + entry.path);
    print();
  }

  if (!roles["Skill definition"].length) {
    print("STOP: No skill definition file found. Cannot proceed.");
    return;
  }

  // Repo-root scope
  const repoSignals = collectRepoSignals();

  // Extract contents
  const skillExtracted = extractSkillContents(roles);
  const repoExtracted = extractRepoContents(repoSignals);

  emitSignalSummary(roles, skillExtracted, repoExtracted, repoSignals);
  emitReadNextGuidance(roles, repoSignals);

  emitExcerpts("CAPPED FILE EXCERPTS (skill scope)", skillExtracted);
  if (repoExtracted.length) emitExcerpts("CAPPED FILE EXCERPTS (repo scope)", repoExtracted);

  emitHelperAppendices();
}

main();
