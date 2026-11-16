/*
 * Totals one invoice. Reads the invoice JSON from `stdin`; overrides come from `args`
 * (--discount=<percent>, --approval=<who>). Prints the audited figures as JSON on stdout,
 * or the rejection reason on stderr with exit code 2.
 *
 * Deliberately written against the parts of ECMAScript 2026 that a JS engine has to
 * implement properly: Math.sumPrecise for an exact float sum, Iterator.concat,
 * Error.isError, Map.prototype.getOrInsertComputed, the Uint8Array base64/hex
 * conversions, and JSON.rawJSON so a number too large for a double survives.
 */

const VAT_BY_CATEGORY = new Map([["goods", 0], ["freight", 21], ["services", 21]]);

const cents = (value) => Math.round((value + Number.EPSILON) * 100) / 100;

function parseArgs(argv) {
  const parsed = { discount: 0, approval: null };
  for (const flag of argv) {
    const match = /^--(discount|approval)=(.*)$/.exec(flag);
    if (!match) {
      throw new Error(`unrecognised argument '${flag}'; expected --discount=<percent> or --approval=<who>`);
    }
    if (match[1] === "discount") {
      parsed.discount = Number(match[2]);
      if (!Number.isFinite(parsed.discount) || parsed.discount < 0 || parsed.discount > 100) {
        throw new Error(`--discount must be a percentage between 0 and 100, got '${match[2]}'`);
      }
    } else {
      parsed.approval = match[2];
    }
  }
  return parsed;
}

function reject(reason) {
  console.error(`REJECTED: ${reason}`);
  process.exit(2);
}

const options = parseArgs(args);

let invoice;
try {
  invoice = JSON.parse(stdin);
} catch (cause) {
  // Error.isError: the only reliable cross-realm test that the catch gave us a real Error.
  if (!Error.isError(cause)) {
    reject("stdin threw a non-Error value");
  }
  reject(`stdin is not valid JSON: ${cause.message}`);
}

if (!invoice || !Array.isArray(invoice.lines) || invoice.lines.length === 0) {
  reject("invoice has no lines");
}
if (options.discount > 15 && !options.approval) {
  reject(`a ${options.discount}% discount needs an --approval=<who> argument`);
}

// One pass per category, keyed through getOrInsertComputed so the accumulator is
// created by the same operation that reads it.
const byCategory = new Map();
for (const line of invoice.lines) {
  const rate = VAT_BY_CATEGORY.get(line.category);
  if (rate === undefined) {
    reject(`unknown category '${line.category}' on line '${line.description}'`);
  }
  const bucket = byCategory.getOrInsertComputed(line.category, () => ({ net: 0, lines: 0 }));
  bucket.net = cents(bucket.net + cents(line.qty * line.unitPrice));
  bucket.lines += 1;
}

const discounted = (category, net) =>
  (options.discount > 0 && category !== "freight") ? cents(net * (1 - options.discount / 100)) : net;

const netByCategory = new Map(
  [...byCategory.entries()].map(([category, bucket]) => [category, discounted(category, bucket.net)]),
);

// sumPrecise keeps the total exact where a plain reduce() drifts on long invoices.
const netTotal = cents(Math.sumPrecise([...netByCategory.values()]));
const vatTotal = cents(Math.sumPrecise(
  [...netByCategory.entries()].map(([category, net]) => cents(net * VAT_BY_CATEGORY.get(category) / 100)),
));

// The checksum covers the priced nets *and* the line counts behind them, so a reader
// can tell "same figures" from "same figures, different lines". Iterator.concat walks
// both maps as a single stream instead of two loops.
const checksumSource = [...Iterator.concat(
  [...netByCategory.entries()].map(([category, net]) => `${category}=${net}`),
  [...byCategory.entries()].map(([category, bucket]) => `${category}:n=${bucket.lines}`),
)].join("|");
const checksum = Uint8Array.from(checksumSource, (character) => character.charCodeAt(0)).toBase64();

const lineCount = invoice.lines.reduce((total, line) => total + line.qty, 0);

const figures = {
  number: invoice.number ?? "(unnumbered)",
  currency: invoice.currency ?? "EUR",
  lineCount,
  discountPercent: options.discount,
  discountApproval: options.approval,
  netByCategory: Object.fromEntries(netByCategory),
  netTotal,
  vatByCategory: Object.fromEntries(
    [...netByCategory.entries()].map(([category, net]) => [
      category,
      cents(net * VAT_BY_CATEGORY.get(category) / 100),
    ]),
  ),
  vatTotal,
  payable: cents(netTotal + vatTotal),
  checksum,
};

// JSON.rawJSON keeps an invoice reference that is longer than a double can hold.
if (invoice.ledgerAccount) {
  figures.ledgerAccount = JSON.rawJSON(String(invoice.ledgerAccount));
}

console.log(JSON.stringify(figures, null, 2));
