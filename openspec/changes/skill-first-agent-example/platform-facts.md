# Platform facts

Facts the implementation agents code against, each read off a jar or off a real call site in this repo —
never off a plausible class name. Where a member does not exist, the section says **absent** and shows
the search that establishes it (type-checked; matching a class name for a word is not evidence).

**Version inspected:** `embabel-agent.version` = **1.5.4** (root `pom.xml:21`);
`embabel-agent-experimental.version` = **1.5.4-SNAPSHOT** (`pom.xml:24`), which is what resolves
`embabel-agent-spec`.

| Artifact | Path |
| --- | --- |
| `embabel-agent-api-1.5.4.jar` (+ `-sources.jar`) | `~/.m2/repository/com/embabel/agent/embabel-agent-api/1.5.4/` |
| `embabel-agent-skills-1.5.4.jar` (+ `-sources.jar`) | `~/.m2/repository/com/embabel/agent/embabel-agent-skills/1.5.4/` |
| `embabel-agent-platform-autoconfigure-1.5.4.jar` (+ `-sources.jar`) | `~/.m2/repository/com/embabel/agent/embabel-agent-platform-autoconfigure/1.5.4/` |
| `embabel-agent-spec-1.5.4-SNAPSHOT.jar` (no sources jar) | `~/.m2/repository/com/embabel/agent/embabel-agent-spec/1.5.4-SNAPSHOT/` — sha256 `cb2bc3f3…` is identical to `…1.5.4-20261116.002600-80.jar`, so the resolved snapshot is build 80 (2026-11-16) |

Method: `unzip -l` / `unzip -p`, `javap -p`, `javap -c` where control flow matters, `javap -v` for
annotations, `grep -rn` over `prod/` and `smith-common/` as cross-check. No prod build was run.

Kotlin-to-Java rules that recur below: a companion `operator fun invoke` is **name-mangled**, so `javac`
cannot write the name; `@JvmOverloads` emits the shorter public constructors; a `Duration` (value-class)
parameter makes the deeper synthetic constructors unreachable from Java; `@JvmStatic` / `@JvmField`
decide whether a factory or constant is a Java `static`; `internal` **top-level** functions are not
mangled and so *are* Java-visible.

---

## 1. File tool interfaces

### 1.1 Packages, types, Java construction

| FQN | Kind | Java construction | Evidence |
| --- | --- | --- | --- |
| `com.embabel.agent.tools.DirectoryBased` | interface | not constructible; exactly one member: `String getRoot()` | `javap -p`: `public interface com.embabel.agent.tools.DirectoryBased { public abstract java.lang.String getRoot(); }` — nothing else on the type |
| `com.embabel.agent.tools.file.FileReadTools` | interface `extends DirectoryBased, FileReadLog, FileAccessLog, SelfToolPublisher` | `FileTools.readOnly(root)` or `new LocalDirectory(...)`; impl `DefaultFileReadTools` (public) | `javap -p`: `public interface …FileReadTools extends …DirectoryBased,…FileReadLog,…FileAccessLog,com.embabel.agent.api.common.support.SelfToolPublisher` |
| `com.embabel.agent.tools.file.FileWriteTools` | interface `extends DirectoryBased, FileAccessLog, FileChangeLog, SelfToolPublisher` | `FileTools.readWrite(root)` | `javap -p`: `public interface …FileWriteTools extends …DirectoryBased,…FileAccessLog,…FileChangeLog,…SelfToolPublisher` |
| `com.embabel.agent.tools.file.PatternSearch` | interface `extends DirectoryBased` | `new LocalDirectory(root, description[, notes[, transformers]])` — the only shipped implementor | `javap -p`: `public interface …PatternSearch extends …DirectoryBased`; `LocalDirectory implements …FileReadTools,…PatternSearch,…FileReadLog,…LlmReference` |
| `com.embabel.agent.tools.file.LocalDirectory` | final class (a tool host **and** an `LlmReference`) | `new LocalDirectory(String root, String description)`; `(String,String,String notes)`; `(String,String,String,List<StringTransformer>)` | `javap -p` lists exactly those three public constructors plus the synthetic `(…,int,DefaultConstructorMarker)` (`@JvmOverloads` on the 4-arg primary). `name` = `root.substringAfterLast('/')`; `withUsageNotes(String)` returns `copy(notes=…)` |
| `com.embabel.agent.api.reference.LocalDirectoryReference` | final class — a **reference**, not a tool host; `implements LlmReferenceProvider, LlmReference` | `new LocalDirectoryReference(String root, String description, String notes, String name)` (name defaulted to `root.substringAfterLast('/')`) | `javap -p`: 4-arg public ctor + synthetic `(…,int,DefaultConstructorMarker)` |
| `com.embabel.agent.api.common.support.SelfToolPublisher` | interface `extends ToolPublisher` | not constructible; `default List<Tool> getTools()` = `safelyGetToolsFrom(ToolObject(this))` | `javap -p`: `public interface …SelfToolPublisher extends com.embabel.agent.core.ToolPublisher { public default java.util.List<…Tool> getTools(); }`; body in `api/com/embabel/agent/api/common/support/SelfToolPublisher.kt` |

`LocalDirectory` declares **no `@LlmTool` of its own** — its tool list is `FileReadTools`' plus
`PatternSearch`'s, collected by `SelfToolPublisher` over `ToolObject(this)`. Because it is also an
`LlmReference`, those tools reach the wire prefixed with its sanitized directory name.

`com.embabel.agent.core.DirectoryBased` — **absent**:
`javap -p com.embabel.agent.core.DirectoryBased` → `Error: class not found`. The only `DirectoryBased`
is `com.embabel.agent.tools.DirectoryBased`.

### 1.2 The `@LlmTool` surface and the resolver each method uses

`@LlmTool` registers nothing by itself — the offerable surface is exactly the annotated methods, gathered
through `SelfToolPublisher` / `Tool.fromInstance`.

| Interface | `@LlmTool` method (Java-visible) | Resolver in the body | Evidence (1.5.4 sources jar) |
| --- | --- | --- | --- |
| `FileReadTools` | `int fileCount()` | `resolvePath("")` | `FileReadTools.kt:57-60` |
| `FileReadTools` | `List<String> findFiles(String glob)` | `resolvePath` (delegates to `findFiles(glob,false)`; `Paths.get(root)…normalize()` + glob matcher) | `FileReadTools.kt:105-106` |
| `FileReadTools` | `String fileSize(String path)` | `resolvePath(path)` | `FileReadTools.kt:201-203` |
| `FileReadTools` | `String readFile(String path)` | **`resolveAndValidateFile(path)`** | `FileReadTools.kt:214-216` |
| `FileReadTools` | `List<String> listFiles(String path)` | `resolvePath(path)` | `FileReadTools.kt:232-234` |
| `FileWriteTools` | `String createFile(String path, String content)` | `resolvePath` (via `createFile(path,content,overwrite)`) | `FileWriteTools.kt:41-44` |
| `FileWriteTools` | `String writeFile(String path, String content)` | `resolvePath` (same delegate) | `FileWriteTools.kt:54-57` |
| `FileWriteTools` | `String editFile(String path, String oldContent, String newContent)` | **`resolveAndValidateFile`**; no-change returns a "no changes made" string via `String.replace` (first occurrence) | `FileWriteTools.kt:87-92` |
| `FileWriteTools` | `String createDirectory(String path)` | `resolvePath(root = root, path = path)` | `FileWriteTools.kt:118-120` |
| `FileWriteTools` | `String appendFile(String path, String content)` | **`resolveAndValidateFile`** | `FileWriteTools.kt:134-137` |
| `FileWriteTools` | `String delete(String path)` | **`resolveAndValidateFile`** | `FileWriteTools.kt:167-169` |
| `PatternSearch` | `String findPatternInProject(String pattern, String globPattern)` | neither — `File(root).walkTopDown()` | `PatternSearch.kt:29-35`; the only `@LlmTool` in that file |

Present but **not** offered to a model (no annotation): `FileReadTools.exists()`,
`FileReadTools.safeReadFile(String)`, `FileReadTools.findFiles(String,boolean)`,
`FileWriteTools.createFile(String,String,boolean)`, `FileWriteTools.appendToFile(String,String,boolean)`,
`PatternSearch.findPatternInProject(Regex,String,Asyncer)`, **`PatternSearch.matchesGlob(String,String)`**.
Established by `grep -n -A3 "@LlmTool"` on each file: `FileReadTools.kt` → 5 hits,
`FileWriteTools.kt` → 6, `PatternSearch.kt` → 1. `matchesGlob` is a public
`default boolean matchesGlob(String,String)` but is not in any tool list.

### 1.3 What each resolver enforces (task 3.5's split)

`api/com/embabel/agent/tools/file/fileUtils.kt`:

```kotlin
internal fun resolvePath(root: String, path: String): Path {
    val basePath = Paths.get(root).toAbsolutePath().normalize()
    val resolvedPath = basePath.resolve(path).normalize().toAbsolutePath()
    if (!resolvedPath.startsWith(basePath)) {
        throw SecurityException("Path traversal attempt detected: $path, root=$root, …")
    }
    return resolvedPath
}
internal fun resolveAndValidateFile(root: String, path: String): Path {
    val resolvedPath = resolvePath(root = root, path = path)          // traversal check first
    if (!Files.exists(resolvedPath)) throw IllegalArgumentException("File does not exist: …")
    if (!Files.isRegularFile(resolvedPath)) throw IllegalArgumentException("Path is not a regular file: …")
    return resolvedPath
}
```

**Both enforce containment** — `resolveAndValidateFile` calls `resolvePath`. So the plain-`resolvePath`
methods in §1.2 are traversal-safe and merely skip the exists / is-a-regular-file checks. **The set of
unconfined readers task 3.5 expects is empty**; there is nothing to withhold on traversal grounds.
Design D8's symlink caveat is unaffected: normalization is lexical, so a symlink inside the root that
points outside it is not caught and needs its own case.

Both are declared `internal`, but Kotlin does not mangle internal top-level functions, so they are
Java-visible as statics (only Kotlin callers are blocked):

```
public final class com.embabel.agent.tools.file.FileUtilsKt {
  public static final java.nio.file.Path resolvePath(java.lang.String, java.lang.String);
  public static final java.nio.file.Path resolveAndValidateFile(java.lang.String, java.lang.String);
  public static final java.lang.String formatFileSize(long);
}
```

The interface-side bridges `resolvePath(String)` and `resolveAndValidateFile(String)` are public
defaults on `FileReadTools` (`javap -p` on §1.1's row).

---

## 2. `FileTools` companion call shape

| FQN | Member | Java-visible signature | Evidence |
| --- | --- | --- | --- |
| `com.embabel.agent.tools.file.FileTools` | `interface FileTools extends FileReadTools, FileWriteTools` | statics usable directly: `FileTools.readWrite(root)` | `javap -p`: `public interface …FileTools extends …FileReadTools,…FileWriteTools` |
| ‟ | `readOnly` | `public static FileReadTools readOnly(String root)` and `public static FileReadTools readOnly(String root, List<? extends com.embabel.common.util.StringTransformer> transformers)` | same javap output (`@JvmStatic @JvmOverloads`) |
| ‟ | `readWrite` | `public static FileTools readWrite(String root)` and `public static FileTools readWrite(String, List<? extends StringTransformer>)` | same |
| `FileTools$Companion` | same four | `public final FileReadTools readOnly(String)`, `readOnly(String,List)` + `readOnly$default`, `readWrite(String)`, `readWrite(String,List)` + `readWrite$default` | `javap -p 'com.embabel.agent.tools.file.FileTools$Companion'` |

So the Java shape is `FileTools.readWrite(root)` — no transformer list needed. `readOnly` returns
`FileReadTools` (write methods absent from the static type); `readWrite` returns `FileTools`.
Implementations: `DefaultFileTools` is package-private, `DefaultFileReadTools` public.
`ProcessSkillScriptExecutionEngine.confinedTo` itself calls
`FileTools$Companion.readWrite$default(FileTools.Companion, root, …)` — see §5.

---

## 3. Tool-group factories (and the mangling `ToolGroups.java` already works around)

| FQN | Member | Java-visible signature | Evidence |
| --- | --- | --- | --- |
| `com.embabel.agent.core.ToolGroupDescription` | interface + companion | `public static ToolGroupDescription create(String description, String role)` — `@JvmStatic`, **plainly named** | `javap -p`: `public interface …ToolGroupDescription { … public static com.embabel.agent.core.ToolGroupDescription create(java.lang.String, java.lang.String); }` |
| ‟ | `Companion.invoke` | `ToolGroupDescription$Companion.invoke(String,String,String)` (+ `invoke$default`); third arg is `childToolUsageNotes` | `javap -p '…ToolGroupDescription$Companion'` |
| `com.embabel.agent.core.ToolGroupMetadata` | interface `extends ToolGroupDescription, AssetCoordinates, HasInfoString` | **no `of(...)` statics on the class** — only `Companion` and `getPermissions()` | `javap -p`: `public interface …ToolGroupMetadata extends … { public static final …Companion Companion; public abstract java.util.Set<…ToolGroupPermission> getPermissions(); }` — the reflective `Companion` route is the only Java path |
| `ToolGroupMetadata$Companion` | 6-arg `operator invoke`, String-led | `public final ToolGroupMetadata invoke-oteLqWg(String name, String description, String role, String vendor, Set<? extends ToolGroupPermission>, String version)` + `invoke-oteLqWg$default(…)` | `javap -p 'com.embabel.agent.core.ToolGroupMetadata$Companion'` |
| ‟ | **5-arg `operator invoke`, `ToolGroupDescription`-led** | `public final ToolGroupMetadata invoke-bYD-3ao(ToolGroupDescription description, String role, String vendor, Set<? extends ToolGroupPermission>, String version)` + `invoke-bYD-3ao$default(…)` | verbatim from the same javap: `public final com.embabel.agent.core.ToolGroupMetadata invoke-bYD-3ao(com.embabel.agent.core.ToolGroupDescription, java.lang.String, java.lang.String, java.util.Set<? extends com.embabel.agent.core.ToolGroupPermission>, java.lang.String)` |
| `com.embabel.agent.core.ToolGroup$Companion` | `ofTools` | `public final ToolGroup ofTools(ToolGroupMetadata, List<? extends Tool>)` — **unmangled** | `javap -p 'com.embabel.agent.core.ToolGroup$Companion'` |
| ‟ | `invoke` | `public final ToolGroup invoke(ToolGroupMetadata, List<? extends Tool>)` — unmangled | same |
| `com.embabel.agent.core.ToolGroupPermission` | enum | `HOST_ACCESS`, `INTERNET_ACCESS` (two values; enforces nothing — descriptive metadata only) | javap enum constants |
| `com.embabel.agent.core.ToolGroupRequirement` | data class | `(String role, Set<String> requiredToolNames = emptySet(), TerminationScope terminationScope = null)` | javap; this repo already writes `new ToolGroupRequirement(role, Set.of(), null)` in `prod/embabel-smith-runtime/src/test/java/com/embabel/smith/runtime/core/ResolvedToolGroupsTest.java` |

The recipe to duplicate is
`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/tools/ToolGroups.java`:

- `:88` — `return ToolGroup.Companion.ofTools(metadata(ToolGroupDescription.create(description, role), role), tools);`
- `:100-106` — iterates `companion.getClass().getMethods()` for
  `parameterCount() == 5 && paramTypes[0] == ToolGroupDescription.class && paramTypes[4] == String.class`,
  then `method.invoke(companion, description, role, VENDOR, Set.of(ToolGroupPermission.HOST_ACCESS), VERSION)`
  with `VENDOR = "embabel-smith"` (`:68`), `VERSION = "1.0.0"` (`:71`)
- `:109` — `throw new IllegalStateException("Failed to build the '" + role + "' ToolGroupMetadata", ex)`
  when the signature is absent, i.e. a version bump fails loudly rather than shipping a dead pack
- `:35` — the javadoc names the mangling (`invoke-bYD-3ao`) and points at the frozen
  `poc/poc-7-runtime-adapter/…/ShellToolGroupFactory.java`

The `api/annotation/ToolGroup` **annotation** is referenced by no other class:
`grep -rl "api/annotation/ToolGroup"` over the extracted api jar returns `ToolGroup.class` itself only.

---

## 4. `Skills`: construction, surface, filters

| FQN | Member | Java-visible signature | Evidence |
| --- | --- | --- | --- |
| `com.embabel.agent.skills.Skills` | data class `implements LlmReference` | public ctors down to `Skills(String name, String description, List<LoadedSkill> skills)` and `Skills(String,String)`; the 9-arg primary is public too | `javap -p` constructor list, incl. `…Skills(java.lang.String, java.lang.String);` and `…Skills(String,String,List<LoadedSkill>)` up to `(String,String,List,DirectorySkillDefinitionLoader,GitHubSkillDefinitionLoader,SkillFrontMatterFormatter,SkillFilter,SkillScriptExecutionEngine,String)` |
| ‟ | `withLocalSkills(String parentDirectory)` | `public final Skills withLocalSkills(String)` | javap |
| ‟ | `withScriptExecutionEngine(…)` | `public final Skills withScriptExecutionEngine(SkillScriptExecutionEngine)` | javap |
| ‟ | other withers | `withFilter(SkillFilter)`, `withSkills(LoadedSkill…)`, `withLocalSkill(String)`, `withFrontMatterFormatter`, `withActivatorDescriptionTail(String)`, `withGitHubUrl(String)`, `withGitHubSkills(String,String,String,String)` (+`withGitHubSkills$default`) — all `public final Skills` | javap |
| ‟ | `tools()` | `public java.util.List<Tool> tools()` | javap; body = `Tool.fromInstance(this)` **plus `skills.flatMap { it.getScriptTools(scriptExecutionEngine) }` for every loaded skill, activated or not** (source comment: the prompt runner captures the tool list at start-up) |
| ‟ | `@LlmTool activate(String name)` | `public final String activate(String)` | javap; `findSkill` canonicalizes `_` ↔ `-`, so the argument is the declared (hyphenated) name while tool names are underscored |
| ‟ | `@LlmTool listResources` / `readResource` | `public final String listResources(String skillName, String resourceType)`; `public final String readResource(String,String,String)` | javap |
| ‟ | `asIndividualReferences()` | `public final List<LlmReference> asIndividualReferences()` | javap; body returns one `LlmReference.of(name = skill.name, …)` per skill (activation tool named `sanitizeToolName(skill.name)`: lowercase, `-`→`_`) **plus one shared `LlmReference.of(name = "skill_resources", …)` holding everything except `activate`** |
| `com.embabel.agent.skills.SkillFilter` | `fun interface` | `boolean accept(LoadedSkill)` | `javap -p`: `public interface …SkillFilter { public abstract boolean accept(com.embabel.agent.skills.support.LoadedSkill); }` |
| ‟ | `WITHOUT_SCRIPTS` | **not a Java public static.** `javap -p '…SkillFilter$Companion'`: `private static final com.embabel.agent.skills.SkillFilter WITHOUT_SCRIPTS;` and `public final com.embabel.agent.skills.SkillFilter getWITHOUT_SCRIPTS();` — Java writes `SkillFilter.Companion.getWITHOUT_SCRIPTS()` | same javap output (also `private static final boolean WITHOUT_SCRIPTS$lambda$0(LoadedSkill)`) |
| ‟ | other filter constants | **absent** — the companion exposes only `getWITHOUT_SCRIPTS()` | the javap member list above is the companion in full |
| ‟ | usage sites | **inert in 1.5.4** — a constant-pool scan of `embabel-agent-skills-1.5.4.jar` finds only `Skills`, `SkillFilter` and `SkillFilter$Companion` naming the type; no class calls `SkillFilter.accept`, and neither `tools()` nor `getSkills()` reads the filter field. The surface lever is the engine, not the filter | constant-pool scan of the jar, 2026-11-28 |
| `LlmReference` | `withUnfolding` | `public default LlmReference withUnfolding()` and `withUnfolding(String childToolUsageNotes)` — a **default on `LlmReference`**, so it is callable on a `Skills` value, but it is not declared on `Skills` | `javap -p com.embabel.agent.api.reference.LlmReference`; `javap -p com.embabel.agent.skills.Skills` has no `withUnfolding` |
| `com.embabel.agent.api.reference.UnfoldingReference` | — | **not constructible from Java**: `final class` (package-private) with ctor `(LlmReference, String)` | `javap` prints `final class …UnfoldingReference implements …LlmReference` — no `public` on the class. Its `getNamingStrategy()` is IDENTITY and it exposes one `UnfoldingTool.of(name = delegate.toolPrefix(), …)` |

This repo's construction,
`prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/PublishedSkillHost.java:110-112`:

```java
Skills skills = new Skills(REFERENCE_NAME, "Skills published under " + this.root, List.of())
        .withLocalSkills(this.root.toString());
return (this.engine == null) ? skills : skills.withScriptExecutionEngine(this.engine);
```

`REFERENCE_NAME = "smith-published-skills"` (`:69`); load refusals are surfaced by diffing
`Files.list(root)` children carrying `DefaultDirectorySkillDefinitionLoader.SKILL_FILE_NAME` (`:148`)
against the loaded set. Note the shape: an **empty** `Skills` plus `withLocalSkills`, engine attached only
when supplied — the withers, not the big constructor.

---

## 5. Script execution engines

### 5.1 The SPI

`com.embabel.agent.skills.script.SkillScriptExecutionEngine`:

```
public interface …SkillScriptExecutionEngine {
  public abstract java.util.Set<ScriptLanguage> supportedLanguages();
  public abstract ScriptExecutionResult execute(SkillScript, java.util.List<java.lang.String>,
                                                java.lang.String, java.util.List<? extends java.nio.file.Path>);
  public static ScriptExecutionResult execute$default(…);
  public default ScriptExecutionResult$Denied validate(SkillScript);   // null = allowed
}
```

`validate` is the per-script policy hook; `LoadedSkill.getScriptTools(engine)` first filters by
`ScriptLanguage.fromFileName(fileName) ∩ engine.supportedLanguages()`.

### 5.2 Every implementation on this classpath

| FQN | Java-visible construction | Languages accepted | Evidence |
| --- | --- | --- | --- |
| `…script.NoOpExecutionEngine` | `NoOpExecutionEngine.INSTANCE` (Kotlin `object`, private ctor); denies everything | `emptySet()` | `javap -p`: `public final class …NoOpExecutionEngine implements …SkillScriptExecutionEngine { public static final …INSTANCE; private …(); }`; `SkillScriptExecutionEngine.kt:93` `override fun supportedLanguages(): Set<ScriptLanguage> = emptySet()` |
| `…script.ProcessSkillScriptExecutionEngine` | `new ProcessSkillScriptExecutionEngine()` **or** `ProcessSkillScriptExecutionEngine.confinedTo(String root)` | all four by default | `javap -p`: `public …ProcessSkillScriptExecutionEngine();` + `public static final …confinedTo(java.lang.String);`. The 7-arg primary is **private** — only `(long,Set,Map,Map,boolean,Set,FileTools,int,DefaultConstructorMarker)` and siblings are public, so timeout / env-allowlist / interpreter / `fileTools` are **not reachable from Java** (the leading `Duration` value-class param); the companion's own javadoc says so. `ProcessSkillScriptExecutionEngine.kt:60` `supportedLanguages: Set<ScriptLanguage> = ScriptLanguage.entries.toSet()`; `:115` denies with "Enabled languages: …" |
| `…script.DockerSkillScriptExecutionEngine` | `new DockerSkillScriptExecutionEngine()`, `new DockerSkillScriptExecutionEngine(String image)`, `DockerSkillScriptExecutionEngine.confinedTo(String)`; `public static final String DEFAULT_IMAGE = "embabel/agent-sandbox:latest"` | all four by default; `pythonOnly(...)` → `{PYTHON}` | `javap -p`: those three public ctors + `public static final confinedTo(String)`; deeper overloads are `(String,long,Set,boolean,MemorySize,CpuLimit,Map,String,String,FileTools,…,DefaultConstructorMarker)`. Companion: `confinedTo`, `isDockerAvailable()`, `imageExists(String)`, `ensureDefaultImageExists()`, `pythonOnly-HG0u8IE(String,long)`, `isolated-HG0u8IE(String,long)` (Duration-mangled → Java-invisible). `DockerSkillScriptExecutionEngine.kt:124-140`: `pythonOnly` sets `supportedLanguages = setOf(PYTHON)`; `isolated` sets `networkEnabled = false`, timeout 30s |
| `…script.PodmanSkillScriptExecutionEngine` | same shape (`open class`); `confinedTo(String)`, `isPodmanAvailable()`, `imageExists`, `ensureDefaultImageExists`, `pythonOnly-HG0u8IE`, `getCpuCgroupAvailable()` | all four by default; `pythonOnly` → `{PYTHON}` | `javap -p '…PodmanSkillScriptExecutionEngine$Companion'` |
| `…script.AbstractContainerSkillScriptExecutionEngine` | base class for the two container engines; `protected val supportedLanguages: Set<ScriptLanguage>` (`:55`), `override fun supportedLanguages()` (`:106`); nested `ContainerStartupStatus` = **`STARTED` / `FAILED` / `UNKNOWN`**; nested `ContainerStateInspection` = `Inspected` / `NotCreated` / `Unavailable`; `CpuLimit`, `MemorySize` | — | `javap -p '…$ContainerStartupStatus'` shows exactly `STARTED`, `FAILED`, `UNKNOWN`; the `Unavailable` case belongs to `ContainerStateInspection` (its own nested type), not to `ContainerStartupStatus` |
| `com.embabel.smith.builder.skill.script.GraalJsSkillScriptExecutionEngine` | repo-authored, **builder module only** | — | `prod/embabel-smith-builder/src/main/java/com/embabel/smith/builder/skill/script/GraalJsSkillScriptExecutionEngine.java:77` `public final class … implements SkillScriptExecutionEngine`, `supportedLanguages()` at `:169` |

Search that establishes the set: `for j in $(find ~/.m2/repository/com/embabel -name "*1.5.4.jar"
! -name "*sources*"); do unzip -l "$j" | grep -i SkillScriptExecutionEngine; done` → only
`embabel-agent-skills-1.5.4.jar` contains any (19 entries: the interface, `NoOpExecutionEngine`, Process,
Docker, Podman, `AbstractContainerSkillScriptExecutionEngine`), plus the repo's Graal engine.
**No engine is a Spring bean upstream** (§11).

`confinedTo(root)` confinement is the same containment as §1.3: `javap -c` on
`ProcessSkillScriptExecutionEngine$Companion.confinedTo` shows
`FileTools$Companion.readWrite$default(FileTools.Companion, root, …)` fed into the private constructor,
and `ProcessSkillScriptExecutionEngine` references `ConfinedInputResolver` (whose `stageArtifacts` the
execute path uses for `inputFiles`) — so the root the file tools use is the root script inputs are
checked against.

`ProcessSkillScriptExecutionEngine`'s env handling: `SAFE_ENV_ALLOWLIST` (PATH, HOME, LANG, LC_ALL,
LC_CTYPE, TMPDIR, TERM, TZ, SystemRoot, SystemDrive, TEMP, TMP, USERPROFILE, PATHEXT) and
`defaultInterpreters` (python3, bash, node, kotlin) are **private statics** with only synthetic
`access$get…$cp()` accessors — not settable from Java.

### 5.3 How a script's callable tool name is formed

`…script/SkillScript.kt`:

```kotlin
val toolName get() = "${skillName}_${fileName.substringBeforeLast('.')}"
```

**Unsanitized** — D4's claim holds. A hyphenated file name survives into the tool name, and
`LlmReference.namingStrategy` then prepends the sanitized reference prefix
(`toolPrefix()` = `name.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()`), so
`<agent>_skills_<skill>_<script-basename>` is what reaches the wire while `Skills.tools()` reports the
unprefixed form. `scriptPath = basePath.resolve("scripts").resolve(fileName)`;
`enum ScriptLanguage { PYTHON(py), BASH(sh,bash), JAVASCRIPT(js,mjs), KOTLIN_SCRIPT(kts) }`.
`ScriptTool`'s input schema is fixed at `args` (array of string), `stdin` (string), `inputFiles`
(array of string); it calls `engine.validate(script)` before executing and formats exit code, duration,
stdout, stderr and artifacts back to the model.

---

## 6. `LlmReference` implementations and what `fromYmlFile` expects

| FQN | Java-visible construction | Evidence |
| --- | --- | --- |
| `com.embabel.agent.api.reference.LlmReference` | interface `extends NamedAndDescribed, PromptContributor`; defaults `toolPrefix()`, `getNamingStrategy()`, `contribution()`, `tools()`, `toolInstances()` (deprecated), `withUnfolding[(String)]`; abstract `String notes()`; statics `prefixedTools(LlmReference)`, `of(String,String,List<Tool>,String notes)`, `of(String,String,Object,String)`, `fromToolInstances(String,String,String,Object…)` + the `@JvmOverloads` 3-arg `of` forms | `javap -p com.embabel.agent.api.reference.LlmReference` |
| ‟ | `contribution()` emits exactly Reference / Description / Tool prefix / Notes lines; `of(...)` and `fromToolInstances` are the public factories | same javap output; `api/com/embabel/agent/api/reference/LlmReference.kt` |
| `…reference.LiteralText` | **public** `LiteralText(String name, String description, String notes)`; `implements LlmReferenceProvider, LlmReference` | `javap`: `public final class …LiteralText implements …LlmReferenceProvider,…LlmReference` |
| `…reference.SpringResource` | **public** `(String resourcePath)` and `(String resourcePath, String name, String description)` + synthetic default; `notes()` reads the resource content | `javap`: `public final class …SpringResource implements …` with `(String,String,String)` and `(…,int,DefaultConstructorMarker)` |
| `…reference.WebPage` | **public** `(String url)` and `(String url, String name, String description)` + synthetic default | `javap`: `public final class …WebPage implements …` |
| `…reference.LocalDirectoryReference` | **public** `(String root, String description, String notes, String name)` + synthetic default | `javap` (§1.1) |
| `…reference.SimpleLlmReference` | **absent from Java** — `final class` (package-private), ctor `(String,String,String,List<? extends Tool>)` | `javap` prints `final class …SimpleLlmReference implements …LlmReference` (no `public`); reachable only through `LlmReference.of` / `fromToolInstances` |
| `…reference.UnfoldingReference` | **absent from Java** — package-private | `javap` (§4) |
| `com.embabel.agent.skills.Skills`, `com.embabel.agent.tools.file.LocalDirectory` | public ctors (§4, §1.1) | javap |
| `PromptRunner.withReference` | `fun withReference(reference: LlmReference) = withToolObject(ToolObject(objects = reference.tools(), namingStrategy = reference.namingStrategy)).withPromptContributor(reference)`; `withReferences(List)` folds it | `api/com/embabel/agent/api/common/PromptRunner.kt:248`. Only `withReference` call sites in the 1.5.4 jars are inside `embabel-agent-api` itself (confirms D13) |

`LlmReferenceProvider` carries `@JsonTypeInfo(use = Id.CLASS, include = As.PROPERTY, property = "fqn")` —
that is what a YAML file must carry per entry.

`com.embabel.agent.api.reference.LlmReferenceProviders`:

```
public static final java.util.List<LlmReference> fromYmlFile(java.lang.String resource);
public static final java.util.List<LlmReference> fromYmlFile(java.lang.String,
                                                            org.springframework.core.io.ResourceLoader);
public static java.util.List fromYmlFile$default(java.lang.String, ResourceLoader, int, java.lang.Object);
private static final tools.jackson.dataformat.yaml.YAMLMapper yamlMapper;
```

What the file must contain: a **YAML list**, each item deserialized as an `LlmReferenceProvider`
(Jackson 3 — `tools.jackson.dataformat.yaml.YAMLMapper` + `kotlinModule`), each needing an `fqn:` key
naming a class that implements `LlmReferenceProvider`, then mapped with `.reference()`. The four
provider types usable that way are `LiteralText`, `SpringResource`, `WebPage`,
`LocalDirectoryReference` — all public, all with a Jackson-visible constructor. A missing resource throws
`IllegalArgumentException("Resource not found: …")`.

Classpath fact for the example's placement: `Skills` is **not** on
`prod/embabel-smith-runtime`'s compile classpath.
`grep -rn "embabel-agent-skills" --include=pom.xml .` → declared only by
`prod/embabel-smith-builder/pom.xml:81` and `poc/poc-26-graaljs-skill-scripts/pom.xml:29`, plus
`dependencyManagement` at `pom.xml:188`; `prod/embabel-smith-runtime/pom.xml` has no such entry.
`grep -rl "embabel-agent-skills" ~/.m2/repository/com/embabel/agent/*/1.5.4/*.pom` → only
`embabel-agent-dependencies` (the BOM) and its own pom, so **nothing pulls it transitively** — the
runtime must declare it (matches design.md:238's dependency note).

---

## 7. `StepSpec`, `StepSpecContext`, and where a declared reference is silently dropped

All from `embabel-agent-spec-1.5.4-SNAPSHOT.jar` (build 80).

| FQN | Member | Java-visible signature | Evidence |
| --- | --- | --- | --- |
| `com.embabel.agent.spec.model.StepSpec<T extends AgentSystemStep>` | interface `extends NamedAndDescribed` | `String getStepType(); T emit(StepSpecContext);` — **plain public interface, no `PermittedSubclasses`** | `javap -p`: `public interface …StepSpec<T extends …AgentSystemStep> extends …NamedAndDescribed`; `javap -v` flags `(0x0601) ACC_PUBLIC, ACC_INTERFACE, ACC_ABSTRACT`, no `PermittedSubclasses` attribute |
| `…spec.model.StepSpecContext` | public ctor | `StepSpecContext(String name, DataDictionary dataDictionary, List<? extends ToolGroupDescription> toolGroups, List<? extends Tool> tools, List<? extends LlmReference> availableReferences)` | `javap -p`: that 5-arg public constructor (+ the synthetic `(…,int,DefaultConstructorMarker)`) |
| ‟ | `copy` | `public final StepSpecContext copy(String, DataDictionary, List, List, List)` — **five components, same order as the ctor**; `copy$default` public | `javap -p` |
| ‟ | accessors | `getName()`, `getDataDictionary()`, `getToolGroups()`, `getTools()`, `getAvailableReferences()`, `component1..5` | `javap -p` |
| ‟ | a 16th/maxIterations or callback slot | **absent** — `StepSpecContext` has exactly five components; no iteration-count or callback member | full `javap -p` member list (above) is the class in full |
| `…spec.model.PromptedActionSpec` (`implements ActionSpec`) | `references` | `private final java.util.List<java.lang.String> references;` / `public final List<String> getReferences()` | `javap -p` |
| ‟ | the silent drop | `emit` builds a `LinkedHashMap` from `getAvailableReferences()` keyed by `LlmReference.getName()`, then per declared name: `Map.get` → `checkcast LlmReference` → `ifnull` → **skip** | `javap -c -p`: `781: invokeinterface // InterfaceMethod java/util/Map.get`, `786: checkcast // class com/embabel/agent/api/reference/LlmReference`, `790: ifnull 811`; map built at ~279–350 (`328: checkcast …LlmReference`, `340: …LlmReference.getName()`). Same `Map.get`/`ifnull` shape at `622`/`631` for data-dictionary types. A dangling reference name is dropped with no warning |
| `…spec.model.GoalSpec` | fields | `name, description, outputTypeName, export, stepType` — **no `references` field** | `javap -p …GoalSpec` |
| `…spec.model.ActionSpec` | marker | bare marker interface extending `StepSpec<Action>` | `javap -p` |
| `com.embabel.agent.spec.StepSpecAgentScopeBuilder` | ctor + methods | `(String name, AgentPlatform, StepSpecRepository, DataDictionary, List<? extends ToolGroupDescription>, List<? extends Tool>)` — six params, **no reference channel**; `createAgentScope()`, `deploy()` | `javap -p`; repo call site `prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/RuntimeCore.java:142-143`, and `:185` for the run |

**Reconciling design.md:12.** `deploy()` does construct the context with a trailing `16`:

```
53: aconst_null
54: bipush  16
56: invokespecial // Method com/embabel/agent/spec/model/StepSpecContext."<init>":(Ljava/lang/String;Lcom/embabel/agent/core/DataDictionary;Ljava/util/List;Ljava/util/List;Ljava/util/List;ILkotlin/jvm/internal/DefaultConstructorMarker;)V
```

That is Kotlin's **synthetic** default-args constructor; the `16` bitmask selects the default for
parameter index 4 (`availableReferences`), whose default is `emptyList()`. So design.md is faithful to
the bytecode, but a Java author cannot write that call (`DefaultConstructorMarker`). The Java-visible
form the D1 wrapper must use is the public five-argument constructor / `copy` with
`availableReferences` passed explicitly — which is also the whole point of the wrapper.

---

## 8. Global guardrails

| Fact | Value | Evidence |
| --- | --- | --- |
| FQN | `com.embabel.agent.spi.support.guardrails.GlobalGuardRailsRegistry` | `javap -p`: `public class …GlobalGuardRailsRegistry` |
| `@Component`? | **yes** | `javap -v` class-level `RuntimeVisibleAnnotations: 0: org.springframework.stereotype.Component` |
| Is that package scanned? | **yes** — `AgentPlatformAutoConfiguration` has **no `@Conditional*`** and `@Import`s `ScanConfiguration`, whose `@ComponentScan` (and `@ConfigurationPropertiesScan`) base packages are `com.embabel.agent.api, .core, .experimental, .prompt, .spi, .test, .tools, .web` — `.spi` covers `spi.support.guardrails` | `javap -v …ScanConfiguration`: `@ComponentScan(basePackages=["com.embabel.agent.api",…,"com.embabel.agent.spi",…,"com.embabel.agent.web"])` + `@Configuration`; `javap -v …AgentPlatformAutoConfiguration` constant pool: `@AutoConfiguration`, `@ImportAutoConfiguration(QuiteMcpClientAutoConfiguration)`, `@Import` of `CommonPlatformPropertiesLoader`, `ScanConfiguration`, `spi.config.spring.AgentPlatformConfiguration`, `spi.config.spring.ToolGroupsConfiguration`, `EmbeddingTrackingConfiguration` |
| Constructor | `public GlobalGuardRailsRegistry(String userInputClassNames, String assistantMessageClassNames, boolean failOnError)` | `javap -p` |
| The three property names | `embabel.agent.guardrails.user-input` (default empty), `embabel.agent.guardrails.assistant-message` (default empty), `embabel.agent.guardrails.fail-on-error` (**default `false`**) | `javap -v` ctor `RuntimeVisibleParameterAnnotations`: `parameter 0: @Value(value="${embabel.agent.guardrails.user-input:}")`, then `…assistant-message:}`, then `…fail-on-error:false}` |
| Instantiation | `@PostConstruct init()` → `ClassUtils.forName` + `BeanUtils.instantiateClass` + `ClassUtils.isAssignable`; on failure it logs ("Failed to instantiate guardrail from class …") and yields null **unless** `failOnError`, which throws `GuardRailInstantiationException` | `api/com/embabel/agent/spi/support/guardrails/GlobalGuardRailsRegistry.kt` |
| Static access | `GlobalGuardRailsRegistry.Companion.get()`, `.getUserInputGuardRails()`, `.getAssistantMessageGuardRails()` | `javap -p '…GlobalGuardRailsRegistry$Companion'` |

`GuardRail` shape — and it is **`sealed`**:

```
public interface com.embabel.agent.api.validation.guardrails.GuardRail
        extends com.embabel.agent.api.validation.ContentValidator<java.lang.String> { }
PermittedSubclasses:
  com/embabel/agent/api/validation/guardrails/AssistantMessageGuardRail
  com/embabel/agent/api/validation/guardrails/UserInputGuardRail
```

(`javap -v` shows the `PermittedSubclasses` attribute with exactly those two.) So an implementation
implements one of the two sub-interfaces, never `GuardRail` itself.

- `ContentValidator<T>`: `String getName()`, `String getDescription()`,
  `ValidationResult validate(T, Blackboard)` — all abstract.
- `UserInputGuardRail extends GuardRail`: adds **only defaults** —
  `String combineMessages(List<UserMessage>)`, `ValidationResult validate(List<UserMessage>, Blackboard)`,
  `ValidationResult validate(MultimodalContent, Blackboard)`. So the methods to implement are exactly
  `getName`, `getDescription`, `validate(String, Blackboard)`.
- `AssistantMessageGuardRail extends GuardRail`: **adds an abstract**
  `ValidationResult validate(ThinkingResponse<?>, Blackboard)` alongside a default
  `validate(AssistantMessage, Blackboard)` — a second method a naive implementation misses.
- `com.embabel.common.core.validation.ValidationResult(boolean isValid, List<ValidationError> errors)`,
  `isValid()`, `getErrors()`, `getHighestSeverity()`. The class is in `com.embabel.common…` but is
  packaged inside `embabel-agent-api.jar`.

Working precedent in this repo:
`poc/poc-10-trust-tiers/src/main/java/com/embabel/smith/trust/InputSanitizerGuardRail.java` —
`public class InputSanitizerGuardRail implements UserInputGuardRail`, overriding `getName()`,
`getDescription()`, `validate(String, Blackboard)` and returning
`new ValidationResult(false, List.of(new ValidationError("prompt-injection", …, ValidationSeverity.CRITICAL)))`;
its `src/main/resources/application.yml` sets
`embabel.agent.guardrails.user-input: com.embabel.smith.trust.InputSanitizerGuardRail`.

---

## 9. `ProcessOptions`, budgets, planner choice, tool-loop policy

| Fact | Value | Evidence |
| --- | --- | --- |
| FQN / construction | `com.embabel.agent.core.ProcessOptions` (in `core`, not `spi`). Java: `new ProcessOptions()` or `ProcessOptions.DEFAULT` (`@JvmField`); every longer ctor is synthetic (`…,int,DefaultConstructorMarker`). Change it with withers | `javap -p`: `public static final com.embabel.agent.core.ProcessOptions DEFAULT;`, `public com.embabel.agent.core.ProcessOptions();`, and 12-arg forms ending in `DefaultConstructorMarker` |
| Fields (12) | `contextId` (nullable), `identities`, `blackboard` (null), `verbosity`, `budget = Budget()`, `processControl = ProcessControl(NONE, NONE, budget.earlyTerminationPolicy())`, `prune=false`, `ephemeral=false`, `listeners`, `outputChannel = DevNullOutputChannel`, `plannerType = PlannerType.GOAP`, `toolCallContext = EMPTY` | `api/com/embabel/agent/core/ProcessOptions.kt` (`@JvmOverloads` primary; the 12-arg descriptor in the javap ctor list matches this order) |
| Withers | `withPlannerType(PlannerType)`, `withBudget(Budget)`, `withProcessControl(…)`, `withAdditionalEarlyTerminationPolicy(…)`, … | `javap -p`: `public final …withPlannerType(com.embabel.agent.api.common.PlannerType);`, `public final …withBudget(com.embabel.agent.core.Budget);`. Repo call site `prod/embabel-smith-runtime/src/main/java/com/embabel/smith/runtime/core/RuntimeCore.java:185`: `platform.runAgentFrom(goalAgent, ProcessOptions.DEFAULT.withPlannerType(planner), Map.of("userInput", new UserInput(input)))` |
| `com.embabel.agent.core.Budget` | public ctors `()`, `(double)`, `(double,int)`, `(double,int,int)`; `@JvmField DEFAULT`; `earlyTerminationPolicy()`, `withCost`, `withActions`, `withTokens` | `javap -p com.embabel.agent.core.Budget` |
| Budget defaults | `DEFAULT_COST_LIMIT = 2.0d`, `DEFAULT_ACTION_LIMIT = 50`, `DEFAULT_TOKEN_LIMIT = 1000000` | `javap -constants com.embabel.agent.core.Budget` — the three constants printed verbatim |
| Tool-loop ceiling | **not on `ProcessOptions`** — `com.embabel.agent.core.support.LlmInteraction.maxToolIterations: Int = 20` | `api/com/embabel/agent/core/support/LlmInteraction.kt:128` (`val maxToolIterations: Int = 20`), documented at `:116` "(default 20)" |
| Tool-not-found policy | also on `LlmInteraction`: `val toolNotFoundPolicy: ToolNotFoundPolicy? = null` (`:135`), alongside `guardRails: List<GuardRail> = emptyList()` (`:129`) and `toolLoopInspectors` (`:131`). Implementations: `com.embabel.agent.spi.loop.AutoCorrectionPolicy(int maxRetries = DEFAULT_MAX_RETRIES, int minTokenLength, double minTokenSimilarity)` with `public static final int DEFAULT_MAX_RETRIES = 3` and a public no-arg ctor; `com.embabel.agent.spi.loop.ImmediateThrowPolicy` (`INSTANCE`). Actions are `ToolNotFoundAction.FeedbackToModel` / `.Throw` | `javap -constants …AutoCorrectionPolicy`, `javap -p …ImmediateThrowPolicy` |
| `PlannerType.values()` | `GOAP(needsGoals=true)`, `UTILITY(false)`, `HYBRID(true)`, `SUPERVISOR(true)` | `javap -p com.embabel.agent.api.common.PlannerType`: four `public static final` constants, `private final boolean needsGoals`, `getNeedsGoals()` |
| `DefaultPlannerFactory` mapping | `GOAP -> AStarGoapPlanner`, `UTILITY -> UtilityPlanner`, `HYBRID -> HybridUtilityPlanner`, **`SUPERVISOR -> AStarGoapPlanner`** | `api/com/embabel/agent/spi/support/DefaultPlannerFactory.kt:40-45` — `PlannerType.SUPERVISOR -> AStarGoapPlanner(worldStateDeterminer)`. D17 confirmed: SUPERVISOR is not a distinct planner, and `createPlanner(processOptions, worldStateDeterminer)` is hardcoded, so a custom planner needs a platform subclass |
| Adjacent types | `Verbosity`, `Delay` (`UNSET`/`NONE`/`MEDIUM`/`LONG`), `Identities`, `ProcessControl`, `EarlyTerminationPolicy` | `javap -p` each |

Consequence for task 8.3's wording: budgets and the planner type are reachable through
`ProcessOptions`; the tool-loop iteration ceiling and the tool-not-found behaviour are
`LlmInteraction` settings and are **not** on `ProcessOptions`.

---

## 10. `ToolGroupsConfiguration` beans and `CoreToolGroups` roles

`com.embabel.agent.spi.config.spring.ToolGroupsConfiguration`
(`api/com/embabel/agent/spi/config/spring/ToolGroupsConfiguration.kt`):
`@Configuration` (`:169`), `@ConditionalOnClass(McpSyncClient::class)` (`:170`),
`@EnableConfigurationProperties(ToolGroupsProperties::class)` (`:171`); ctor
`(List<McpSyncClient>, ToolGroupsProperties, ObjectProvider<ToolCallContextMcpMetaConverter>)`.
It is reached because `AgentPlatformAutoConfiguration` `@Import`s it (§8), not via its own
`AutoConfiguration.imports`.

| Bean method | Condition | Role string |
| --- | --- | --- |
| `List<ToolGroup> includedToolGroups()` (`:205`) | only the class condition | from the `embabel.agent.platform.tools.includes` map — `ToolGroupDescription(description = gid.description ?: role, role = role)` (`:221`) |
| `mathToolGroup() = MathTools()` (`:214`) | **unconditional** | `math` |
| `mcpWebToolsGroup()` (`:241-242`) | `@ConditionalOnMcpConnection("brave-search-mcp","fetch-mcp","wikipedia-mcp","docker-mcp")` | `web` (`CoreToolGroups.WEB_DESCRIPTION`) |
| `mapsToolsGroup()` (`:269-270`) | `@ConditionalOnMcpConnection("google-maps-mcp","docker-mcp")` | `maps` |
| `browserAutomationWebToolsGroup()` (`:287-288`) | `@ConditionalOnMcpConnection("puppeteer-mcp","docker-mcp")` | `browser_automation` |
| `githubToolsGroup()` (`:313-314`) | `@ConditionalOnMcpConnection("github-mcp","docker-mcp")` | `github` |

`@ConditionalOnMcpConnection` is `@Conditional(OnMcpConnectionCondition::class)` (`:77`) and `matches`
(`:100`) tests `environment.containsProperty("spring.ai.mcp.client.stdio.connections.<name>.command")`
or `…sse.connections.<name>.url` — **property presence only**: no credential, liveness or handshake
check. `ToolGroupsProperties` prefix is `embabel.agent.platform.tools` (`:139`):
`includes`, `excludes`, `version`, `lazyInit`.

`com.embabel.agent.core.CoreToolGroups` constants (`javap -constants`):

```
public static final java.lang.String WEB = "web";
public static final java.lang.String MATH = "math";
public static final java.lang.String MAPS = "maps";
public static final java.lang.String GITHUB = "github";
public static final java.lang.String BROWSER_AUTOMATION = "browser_automation";
```

There is **no `search` and no `file` constant** here — the two role names `TrustTier.STANDARD` leans on
for file/search exist only as strings this change would introduce.
`tools/math/MathTools.kt` is `class MathTools : ToolGroup, AssetCoordinates` publishing **one**
`UnfoldingTool.of(name = "math", …)` whose inner tools are add / subtract / multiply / divide / mean /
min / max / floor / ceiling / round — the unfolding shape D11 describes.

Is `@ConditionalOnClass(McpSyncClient)` satisfied here? **Yes.** `embabel-agent-api-1.5.4.pom:171-176`
declares `org.springframework.ai:spring-ai-starter-mcp-client:2.0.1` at **compile** scope, non-optional,
so `io.modelcontextprotocol.client.McpSyncClient` is present (locally:
`~/.m2/repository/io/modelcontextprotocol/sdk/mcp*`,
`~/.m2/repository/org/springframework/ai/spring-ai-starter-mcp-client/{1.1.7,2.0.1,2.0.2}`).
That matches the measured behaviour recorded in
`prod/embabel-smith-runtime/src/test/java/com/embabel/smith/runtime/core/ResolvedToolGroupsTest.java`:
`availableToolGroups()` = `[math]` with nothing configured and
`[browser_automation, github, maps, math, web]` once
`spring.ai.mcp.client.stdio.connections.docker-mcp.command=/bin/true` is set; all 14 probed roles
resolve to nothing except `math`. The builder's six roles do not resolve from a runtime boot because the
builder is not a dependency of the runtime, its six `@Configuration`s are unconditional, and it ships no
`AutoConfiguration.imports`.

---

## 11. Does `embabel-agent-skills` ship auto-configuration?

**No — nothing at all.** Three independent checks on
`~/.m2/repository/com/embabel/agent/embabel-agent-skills/1.5.4/embabel-agent-skills-1.5.4.jar`:

1. `unzip -l … | grep META-INF` lists only `MANIFEST.MF`, `build-info.properties`,
   `embabel-agent-skills.kotlin_module`, `maven/com.embabel.agent/embabel-agent-skills/pom.xml` and
   `…/pom.properties` — **no `META-INF/spring/` directory at all**.
2. `unzip -p … META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
   → `caution: filename not matched`.
3. Binary greps of the jar for `AutoConfiguration`, `ComponentScan`, `ConditionalOnProperty`, and for the
   descriptors `Lorg/springframework/stereotype/Component;` and
   `Lorg/springframework/context/annotation/Configuration;` → **zero hits**. So it contains no
   Spring-annotated class either, not merely no imports file.

For contrast, `embabel-agent-platform-autoconfigure-1.5.4.jar`'s imports file lists
`…platform.AgentPlatformAutoConfiguration`, `…platform.cache.AgentCacheProviderAutoConfiguration`,
`…platform.cache.CacheSnapshotStoreAutoConfiguration`, `…LlmDecisionServicesAutoConfiguration`,
`…DecisionServiceRegistryAutoConfiguration`.

Consequence: adding `embabel-agent-skills` to a module activates nothing by itself — no script engine, no
`Skills` bean, no auto-configuration to switch off. A `Skills` reference exists only because this repo
constructs one from a folder (§4), and the engine is chosen in code via `confinedTo(root)` (§5), not by a
property.

---

## Appendix — two claims worth pinning

- **`@SecureAgentTool` is absent from 1.5.4.** The annotation inventory in
  `embabel-agent-api-1.5.4.jar` under `api/annotation/` is `AchievesGoal, Action, Agent, Condition, Cost,
  EmbabelComponent, Export, LlmTool` (+ `LlmTool$Meta`, `LlmTool$Param`), `Provided, RequireNameMatch,
  RunSubagent, SpecialReturnException, State, SubagentExecutionRequest, ToolGroup, UnfoldingTools`;
  a jar-wide grep of `embabel-agent-api-1.5.4.jar` and `embabel-agent-ai-1.5.4.jar` for
  `SecureAgentTool` returns nothing. Matches design.md:434.
- **The only self-registering tool class under `com.embabel.agent.tools`** is
  `com.embabel.agent.tools.osx.AppleScriptTools`: `javap -v` class-level annotations are `@Service`,
  `@Profile(["!test"])`, `@Conditional(MacOSCondition)`. It is picked up because `ScanConfiguration`
  component-scans `com.embabel.agent.tools` (§8). No file, search or shell tool class is
  `@Component`/`@Service`, which is why D-context's "a group has to be published as a bean" holds.
