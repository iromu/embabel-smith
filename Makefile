# Author: Ivan Rodriguez
# Stable entry points for building and checking this repo.
# Agents: use these targets instead of inventing mvn invocations.

# Prefer the Maven wrapper if present, fall back to the system Maven.
MVN ?= $(shell test -x ./mvnw && echo ./mvnw || echo mvn)
MODULE ?=
GOAL ?= compile

# The POC reactor sits behind the `poc` Maven profile and is OFF by default.
# Set POC=1 to include it (`make test POC=1`); a poc/ MODULE implies it.
POC_FLAG := $(if $(filter poc/%,$(MODULE)),-Ppoc,$(if $(POC),-Ppoc,))

.PHONY: help build test test-live test-module checkstyle checkstyle-module module clean install setup

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-18s %s\n", $$1, $$2}'

build: ## Compile the default reactor (POCs: POC=1), skip tests
	$(MVN) $(POC_FLAG) -q compile -DskipTests

test: ## Run all tests, live group excluded (POCs: POC=1)
	@[ -f "$$HOME/.config/env/llm.env" ] && . "$$HOME/.config/env/llm.env" && export OPENAI_CUSTOM_BASE_URL OPENAI_CUSTOM_API_KEY OPENAI_CUSTOM_MODELS OPENAI_API_KEY ANTHROPIC_API_KEY EMBABEL_MODELS_DEFAULT_LLM; \
	$(MVN) $(POC_FLAG) test

# Tests that need a real model endpoint carry @Tag("live"), and the root pom excludes that group
# through the overridable `test.excludedGroups` property (default `live`), so `make test` is green
# with no endpoint configured.
#
# TRAP: `mvn test -Dgroups=live` alone selects NOTHING. Surefire applies the selection and the
# exclusion together, and the default exclusion still says "exclude live". Opting in therefore has
# to clear the exclusion as well as ask for the group — the two flags below, in that pair, are the
# only supported invocation. `make test-live` is the documented entry point; the identical pair on a
# bare `./mvnw test` works the same way.
test-live: ## Run ONLY the live, endpoint-driven tests (needs ~/.config/env/llm.env)
	@[ -f "$$HOME/.config/env/llm.env" ] && . "$$HOME/.config/env/llm.env" && export OPENAI_CUSTOM_BASE_URL OPENAI_CUSTOM_API_KEY OPENAI_CUSTOM_MODELS OPENAI_API_KEY ANTHROPIC_API_KEY EMBABEL_MODELS_DEFAULT_LLM; \
	$(MVN) $(POC_FLAG) test -Dtest.excludedGroups= -Dgroups=live

test-module: ## Test one module + its upstream deps (MODULE=prod/embabel-smith-studio)
	@[ -f "$$HOME/.config/env/llm.env" ] && . "$$HOME/.config/env/llm.env" && export OPENAI_CUSTOM_BASE_URL OPENAI_CUSTOM_API_KEY OPENAI_CUSTOM_MODELS OPENAI_API_KEY ANTHROPIC_API_KEY EMBABEL_MODELS_DEFAULT_LLM; \
	$(MVN) $(POC_FLAG) test -pl $(MODULE) -am

lint: ## Project SpringChecks on the default reactor (0 violations expected)
	$(MVN) $(POC_FLAG) checkstyle:check

checkstyle: ## Project SpringChecks on the default reactor (0 violations expected)
	$(MVN) $(POC_FLAG) checkstyle:check

checkstyle-module: ## Project SpringChecks on one module (MODULE=poc/poc-1-no-code-runtime)
	$(MVN) $(POC_FLAG) checkstyle:check -pl $(MODULE)

module: ## Run a goal on one module + upstream deps (MODULE=... GOAL=compile|test|install)
	$(MVN) $(POC_FLAG) $(GOAL) -pl $(MODULE) -am -DskipTests

clean: ## Remove build output
	$(MVN) $(POC_FLAG) clean

install: ## Install all modules to the local repo, skip tests
	$(MVN) $(POC_FLAG) install -DskipTests

setup: ## Developer setup: pre-commit hooks, project skills, OpenSpec CLI
	./setup.sh
