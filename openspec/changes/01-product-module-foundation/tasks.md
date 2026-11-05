# Tasks

## 1. Aggregator

- [x] 1.1 Create `prod/pom.xml` — aggregator `embabel-smith-prod` (packaging `pom`, parent `embabel-smith-parent`), listing the four child modules
- [x] 1.2 Register `prod` in the root `pom.xml` `<modules>`

## 2. Module skeletons

- [x] 2.1 Create `prod/embabel-smith-skillsource/` skeleton (POM with parent + `smith-common` dep, empty `src/main/java` + `src/test/java`, README: "filled by change 02")
- [x] 2.2 Create `prod/embabel-smith-runtime/` skeleton (POM + `smith-common`, `embabel-smith-skillsource`, Embabel starters; README: "filled by change 03")
- [x] 2.3 Create `prod/embabel-smith-builder/` skeleton (POM + `smith-common`, `embabel-smith-skillsource`, Embabel starters; README: "filled by change 04")
- [x] 2.4 Create `prod/embabel-smith-studio/` skeleton (POM + `smith-common`, `embabel-smith-skillsource`, `embabel-smith-runtime`, `embabel-smith-builder`, Spring Boot web; README: "filled by changes 05–06")

## 3. Dependency rules

- [x] 3.1 Add `dependencyManagement` entries for the four `com.embabel.smith:embabel-smith-*` artifacts in the root POM
- [x] 3.2 Add `maven-enforcer-plugin` (version in root `pluginManagement`) and configure `bannedDependencies` in `prod/pom.xml` banning `com.embabel.smith:poc-*` for all child modules
- [x] 3.3 Verify the ban works: temporarily add a `poc-1-no-code-runtime` dependency to one skeleton, confirm the build fails naming it, then remove it

## 4. Verify

- [x] 4.1 `mvn -q verify` from the root passes with the new modules in the reactor
- [x] 4.2 Confirm no file under `poc/` was touched (`git status`)
