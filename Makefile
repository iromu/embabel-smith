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

.PHONY: help build test test-module checkstyle checkstyle-module module clean install setup

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-18s %s\n", $$1, $$2}'

build: ## Compile the default reactor (POCs: POC=1), skip tests
	$(MVN) $(POC_FLAG) -q compile -DskipTests

test: ## Run all tests (Live* tests need the model-endpoint env; POCs: POC=1)
	@[ -f "$$HOME/.config/env/llm.env" ] && . "$$HOME/.config/env/llm.env" && export OPENAI_CUSTOM_BASE_URL OPENAI_CUSTOM_API_KEY OPENAI_CUSTOM_MODELS OPENAI_API_KEY ANTHROPIC_API_KEY EMBABEL_MODELS_DEFAULT_LLM; \
	$(MVN) $(POC_FLAG) test

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
