---
name: invoice-totalling
description: Totals an invoice, applies per-category VAT and discount rules, and produces the audited figures. Use when asked to total, check or reconcile an invoice, or to produce its VAT breakdown and payable amount.
license: Apache-2.0
compatibility: Requires a JavaScript runtime (ECMAScript 2026). Executed in-process by the GraalJS skill-script engine.
metadata:
  author: embabel-smith
  version: "1.0"
---

# Invoice totalling

Totaling an invoice is arithmetic, not judgement. Never compute the figures yourself: run the
script and quote what it printed. A model that adds a line-item list by hand will drift, and an
invoice is audited.

## Workflow

1. Put the invoice into the JSON shape below and pipe it to `scripts/compute_totals.js` as
   `stdin`. Pass any per-invoice overrides as `args`.
2. Quote the figures the script printed verbatim. Do not re-add, round, or sanity-check them
   yourself — if you disagree with the script, say so and re-run it instead.
3. If the script exits non-zero, report its error text as the reason the invoice is rejected.
   Do not invent figures for a rejected invoice.

## Invoice shape

```json
{
  "number": "INV-2026-014",
  "currency": "EUR",
  "lines": [
    { "description": "Cold-chain pallet", "category": "goods", "qty": 12, "unitPrice": 84.99 },
    { "description": "Refrigerated haulage", "category": "freight", "qty": 1, "unitPrice": 240.00 }
  ]
}
```

## Rules the script enforces

These are the rules that make the answer non-obvious; the script, not you, applies them.

- VAT is per category, not per invoice: `goods` 0%, `freight` 21%, `services` 21%, and any
  unknown category is rejected rather than defaulted.
- A line is priced as `qty * unitPrice`, rounded to cents **per line**, before any subtotal —
  rounding the total instead is off by a cent on most real invoices.
- A discount above 15% requires an `approval` argument, otherwise the invoice is rejected.
- Freight is never discounted, even when a discount applies to the rest.
- The output carries a `checksum` over the figures so a reader can tell two runs apart; it is
  derived, never typed.
