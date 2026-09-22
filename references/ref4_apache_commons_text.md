# [4] Apache Commons Text — Official Documentation

> **Citation:** Apache Commons Text documentation.

**URL:** https://commons.apache.org/proper/commons-text/  
**API Docs:** https://commons.apache.org/proper/commons-text/apidocs/  
**Version Referenced:** 1.12.0 (stable) / 1.15.1-SNAPSHOT (latest)  
**Last Published:** 07 Dec 2025  
**License:** Apache License 2.0  
**Downloaded:** 2026-09-22

---

## What Is Apache Commons Text?

Apache Commons Text is a Java library focused on **algorithms and utility methods for working with Strings**. It extends Java's standard `String` handling with specialized text-processing capabilities.

**Maven Dependency:**
```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-text</artifactId>
    <version>1.12.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'org.apache.commons:commons-text:1.12.0'
```

---

## Key Classes & Packages

### 1. String Similarity & Distance (`org.apache.commons.text.similarity`)

The most commonly referenced package for DLC/name-matching applications:

| Class | Algorithm | Use Case |
|-------|-----------|----------|
| `LevenshteinDistance` | Edit distance | Fuzzy name matching |
| `JaroWinklerSimilarity` | Jaro-Winkler | Name similarity scoring (0–1) |
| `FuzzyScore` | Fuzzy matching | Abbreviation matching |
| `CosineSimilarity` | Cosine | Document/token similarity |
| `HammingDistance` | Hamming | Fixed-length string comparison |
| `JaccardSimilarity` | Jaccard | Set-based token overlap |

### 2. String Escaping (`org.apache.commons.text`)
- `StringEscapeUtils` — escape/unescape Java, JavaScript, HTML, XML, CSV

### 3. String Manipulation
- `StrBuilder` — mutable string builder (more flexible than `StringBuilder`)
- `StrSubstitutor` — variable substitution in strings (template engine)
- `StrTokenizer` — tokenization with quoted strings support

### 4. Word Utilities (`org.apache.commons.text`)
- `WordUtils` — capitalize, abbreviate, wrap, extract initials from words

---

## Relevance to Project

Apache Commons Text is used in the project for:
- **Name similarity matching** — comparing pensioner names across Aadhaar records vs PDA records using `JaroWinklerSimilarity` / `LevenshteinDistance`
- **Fuzzy matching fallback** — when exact string match fails (a common DLC rejection cause), fuzzy matching flags near-matches for human review rather than outright rejection

---

## Official Links

| Resource | URL |
|----------|-----|
| Home Page | https://commons.apache.org/proper/commons-text/ |
| JavaDoc (API) | https://commons.apache.org/proper/commons-text/apidocs/ |
| Developer Guide | https://commons.apache.org/proper/commons-text/developer-guide.html |
| GitHub Source | https://github.com/apache/commons-text |
| Release Notes | https://commons.apache.org/proper/commons-text/changes-report.html |

---

## Example Usage (JaroWinklerSimilarity)

```java
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

JaroWinklerSimilarity jw = new JaroWinklerSimilarity();
double score = jw.apply("RAJESH KUMAR", "RAJESH KUMR");  // returns ~0.97
// Threshold > 0.90 → flag as near-match, prompt human review
```

---

*Reference [4] of 5 — Official Apache Software Foundation documentation, Apache License 2.0, Downloaded 2026-09-22*
