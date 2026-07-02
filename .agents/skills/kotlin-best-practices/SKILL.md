---
name: kotlin-best-practices
description: >-
  Core patterns for robust Kotlin code — official coding conventions, scope
  functions, backing properties, collection exposure, visibility, and
  top-level APIs. Use when writing or reviewing Kotlin under backend/, or when the
  user mentions Kotlin style, scope functions, backing properties, or clean
  Kotlin code.
---

# Kotlin Best Practices

## **Priority: P1 (HIGH)**

Engineering standards for clean, maintainable Kotlin systems.

**Authoritative source:** [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) — apply via IDE Kotlin style guide; sections below highlight rules most often missed in reviews.

## Implementation Guidelines

- **Scope Functions**:
  - `apply`: Object configuration (returns object).
  - `also`: Side effects validation/logging (returns object).
  - `let`: Null checks (`?.let`) or mapping (returns result).
  - `run`: Object configuration and mapping (returns result).
  - `with`: Grouping multiple method calls on an object (returns result).
- **Backing Properties**: Use `_prop` (private mutable) and `prop` (public immutable) pattern for encapsulation.
- **Collections**: Expose `List`/`Map` (read-only interfaces) publicly, keep `MutableList` internal. Prefer `listOf()` / `setOf()` / `mapOf()` over mutable factories when not mutating.
- **Single Expression**: Use `runCatching` for simple error handling over `try/catch` blocks.
- **Visibility**: Default to `private` or `internal`. Minimize `public` surface area.
- **Top-Level**: Prefer top-level functions/constants over implementation-less `object` singletons.

## Naming (kotlinlang.org)

- **Packages** — lowercase, no underscores (`net.jonasmf.auctionengine.service`).
- **Types** — UpperCamelCase; **functions/properties** — lowerCamelCase.
- **Constants** — `const val` / immutable top-level `val`: `SCREAMING_SNAKE_CASE` (`MAX_PAGE_SIZE`).
- **Acronyms** — two letters all caps (`IOStream`); longer acronyms capitalize first letter only (`HttpClient`, `XmlParser`).
- **Names tell intent** — nouns for types, verbs for functions; avoid meaningless `Util`, `Manager`, `Wrapper` (especially in file names).
- **Files** — one primary class/interface per file, file name = class name (`RecipeService.kt`). Group only closely related declarations.
- **Factory functions** — prefer a distinct name (`createRecipe`) unless factory semantics match the return type name per conventions.

## Idiomatic Kotlin (kotlinlang.org)

- **`val` over `var`** — locals and properties immutable unless reassigned.
- **Default parameters** — prefer over overloads (`fun foo(a: String = "a")`).
- **Expression bodies** — single-expression functions: `fun id() = uuid` not block + `return`.
- **`if` / `when` / `try`** — use expression form (`return if (x) a else b`); binary condition → `if`; three or more branches → `when`.
- **Nullable `Boolean`** — `if (value == true)` / `if (value == false)`, not `if (value)`.
- **Lambdas** — use `it` in short, non-nested lambdas; name parameters in nested/multiline lambdas.
- **Named arguments** — when several parameters share a primitive type or meaning is unclear (`drawSquare(x = 10, width = 100, fill = true)`).
- **Loops** — prefer `filter` / `map` / etc. over manual loops when clear; open range `0..<n` not `0..n - 1`.
- **Strings** — templates over `+` concatenation; multiline strings + `trimIndent` / `trimMargin` when needed.
- **Property vs function** — read-only property when result is cheap, stable, and does not throw; otherwise function.
- **Extension functions** — keep visibility as narrow as possible (`private` top-level in `mapper/`).
- **Platform types** (Java interop) — explicit Kotlin type on **public** API return values and properties initialized from Java.
- **Trailing commas** — at declaration sites (parameters, enums, properties) for cleaner diffs.
- **Redundant syntax** — omit `: Unit`, semicolons, and `{}` in simple string templates (`"$name"` not `"${name}"`).

## Class layout

Order: properties → `init` → secondary constructors → methods → companion object (last resort). Keep interface implementations in interface member order. Place overloads adjacent.

## Anti-Patterns

- **Nesting Scope Functions**: Avoid nesting `let`/`apply` more than 2 levels deep. It destroys readability.
- **Mutable Public Props**: Avoid `public var`. Use `private set` or backing properties.
- **Global Mutable State**: Avoid top-level mutable variables.
- **Mutable collection types in APIs** — `fun f(items: ArrayList<T>)` when callers must not mutate.
- **Labeled returns in lambdas** — restructure instead; no labeled return on last lambda statement.

## Code

```kotlin
// Backing Property Pattern
class ViewModel {
    private val _uiState = MutableStateFlow(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow() // Read-only

    fun load() {
        // apply for config
        val user = User().apply {
            name = "John"
            age = 30
        }

        // runCatching
        runCatching { api.fetch() }
            .onSuccess { _uiState.value = UiState.Success(it) }
            .onFailure { logger.error(it) }
    }
}
```

## wow-auction-engine context

For Spring layering, companion-object rules, and issue-work standards, also read
[github-issue-work/focus/kotlin-backend.md](../github-issue-work/focus/kotlin-backend.md).

## Related Topics

language | coroutines
