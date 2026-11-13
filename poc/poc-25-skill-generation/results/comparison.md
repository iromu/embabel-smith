# POC 25 — blueprint → Agent Skill generation + with/without-skill eval

Each blueprint is first DECOMPOSED into the set of skills required to compartmentalize
the process (the extraction step); each skill is generated into its own `SKILL.md`, and its
per-skill `evals.json` suite is then run against a general-purpose agent **with the skill
loaded** and **without it** (the baseline), graded against verifiable expectations. The
skill lift is the with-skill pass rate minus the no-skill pass rate, per skill.

## Blueprint: Aurora

FAILED: Failed to parse the generated evals after 3 attempts: Failed to parse evals.json: Unexpected end-of-input: expected close marker for Array (start marker at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 57])
 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 7173] (through reference chain: com.embabel.smith.skillgen.EvalSuite["evals"])

## Blueprint: Sentinel

FAILED: LLM call failed: HTTP 500 {"error":{"message":"litellm.InternalServerError: InternalServerError: Hosted_vllmException - Cannot connect to host localhost:8000 ssl:<ssl.SSLContext object at 0xf68dc06fd9f0> [Connect call failed ('REDACTED', 8000)]\n\nLiteLLM: model group 'Qwen-instruct' failed with the error above. No fallback was attempted.","type":"internal_server_error","param":null,"code":"500"}}

## Blueprint: TradingAgents

FAILED: LLM call failed: HTTP 500 {"error":{"message":"litellm.InternalServerError: InternalServerError: Hosted_vllmException - Cannot connect to host localhost:8000 ssl:<ssl.SSLContext object at 0xf68dc06fd9f0> [Connect call failed ('REDACTED', 8000)]\n\nLiteLLM: model group 'Qwen-instruct' failed with the error above. No fallback was attempted.","type":"internal_server_error","param":null,"code":"500"}}
