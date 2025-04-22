package no.jpro.slack.cv

fun String.markdownQuoteBlock(): String {
    return """```
$this
```"""
}
