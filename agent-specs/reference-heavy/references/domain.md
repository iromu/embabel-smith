# Coffee Brewing Domain Guide

This reference is the domain knowledge the `reference-heavy` skill answers from. It is deliberately
long so the progressive-disclosure POC can measure the context cost of loading it on demand versus
inlining it into every prompt.

## Extraction methods

- **Pour over (drip)**: hot water is poured over ground coffee in a filter; gravity draws the brew
  through. Typical ratio is 1:16 to 1:17 (coffee to water by weight). A 2–4 minute brew time is
  normal; faster usually means the grind is too coarse or the dose too light.
- **Espresso**: a small volume of water (about 18–36 g) is forced through a fine, tamped dose at
  roughly 9 bar for 25–30 seconds, producing a concentrated shot with crema.
- **French press**: coarsely ground coffee steeped in water for about four minutes, then the plunger
  presses the grounds to the bottom. It keeps more oils and fine sediment, giving a fuller body.
- **AeroPress**: a hybrid of immersion and pressure; a plunger creates gentle pressure that pushes
  the brew through a paper or metal filter. Versatile — recipes range from espresso-like to
  drip-like.

## Key variables

- **Grind size**: the single biggest lever. Fine for espresso, medium for pour over, coarse for
  French press. A finer grind increases extraction (more soluble material dissolves); a coarser
  grind decreases it.
- **Water temperature**: 90–96 °C (195–205 °F) is the usual range. Too hot over-extracts and
  bitterness rises; too cold under-extracts and the cup tastes sour and thin.
- **Dose and ratio**: the coffee-to-water ratio sets strength. More coffee per gram of water makes
  a stronger, more intense cup; less makes a lighter, more delicate one.
- **Brew time**: the window in which the target solubles extract. Each method has a target window;
  drifting outside it shifts the balance toward under- or over-extraction.

## Extraction science

Coffee is roughly 80% water after brewing. Soluble material — acids, sugars, oils, aromatics —
dissolves into the water. Extraction is the transfer of those solubles from the grounds to the
liquid. Under-extraction leaves the cup sour, weak, and hollow; over-extraction pulls out harsh,
bitter, astringent compounds. The goal is to land in the middle, where sweetness and clarity are
highest.

## Troubleshooting

- **Sour, weak, fast brew**: under-extracted. Finer grind, hotter water, or a slightly higher dose.
- **Bitter, astringent, slow brew**: over-extracted. Coarser grind, cooler water, or a lower dose.
- **Channeling** (water punches through one spot): often a tamping or distribution problem in
  espresso, or a clogged filter in pour over.
- **Stale, flat cup**: old beans or poor storage. Coffee is best within a few weeks of roasting,
  kept airtight and away from light and heat.

## Ratios at a glance

| Method      | Coffee : Water | Water temp     | Time       |
|-------------|----------------|----------------|------------|
| Pour over   | 1 : 16         | 93–96 °C       | 2:30–4:00  |
| Espresso    | 1 : 2          | 90–93 °C       | 25–30 s    |
| French press| 1 : 15         | 93–95 °C       | 4:00       |
| AeroPress   | 1 : 15 (varies)| 85–95 °C       | 1:30–2:30  |
