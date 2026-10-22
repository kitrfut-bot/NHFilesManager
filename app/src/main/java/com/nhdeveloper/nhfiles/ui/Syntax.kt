package com.nhdeveloper.nhfiles.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

enum class Lang { NONE, CLIKE, JSON, XML, MARKDOWN, INI, HASH }

fun langFor(ext: String): Lang = when (ext) {
    "js", "java", "kt", "kts", "gradle", "c", "h", "cpp", "cs", "php", "css", "sql" -> Lang.CLIKE
    "json" -> Lang.JSON
    "xml", "html", "htm", "svg" -> Lang.XML
    "md" -> Lang.MARKDOWN
    "ini", "cfg", "conf", "properties", "toml" -> Lang.INI
    "py", "sh", "yml", "yaml" -> Lang.HASH
    else -> Lang.NONE
}

private const val KEYWORDS = "function|var|let|const|if|else|for|while|return|class|new|import|package|fun|val|public|private|" +
    "protected|static|void|int|boolean|true|false|null|this|try|catch|finally|throw|switch|case|break|continue|default|" +
    "extends|implements|interface|object|when|in|is|override|suspend|async|await|export|from|typeof|do|enum|struct|" +
    "SELECT|FROM|WHERE|INSERT|UPDATE|DELETE|CREATE|TABLE"

private const val STR = "\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"
private val standaloneStringRegex = Regex(STR)

/**
 * Ranges of the *content* of every quoted string in [text] (the part between the quote
 * characters, excluding the quotes themselves) — the only parts of a file that "Edit text
 * only" mode in the code editor allows typing into.
 */
fun findStringRanges(text: String): List<IntRange> {
    if (text.length > 200_000) return emptyList()
    return standaloneStringRegex.findAll(text).map { m ->
        (m.range.first + 1)..(m.range.last - 1) // drop the opening/closing quote from the editable span
    }.toList()
}

private val clike = Regex("(//[^\\n]*|/\\*[\\s\\S]*?\\*/)|($STR)|(\\b\\d+(?:\\.\\d+)?\\b)|(\\b(?:$KEYWORDS)\\b)")
private val hash = Regex("(#[^\\n]*)|($STR)|(\\b\\d+(?:\\.\\d+)?\\b)|(\\b(?:true|false|null|if|else|for|while|def|class|import|return|fi|then|do|done|echo)\\b)")
private val json = Regex("(\"(?:\\\\.|[^\"\\\\\\n])*\")(?=\\s*:)|(\"(?:\\\\.|[^\"\\\\\\n])*\")|(-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)|\\b(true|false|null)\\b")
private val xml = Regex("(<!--[\\s\\S]*?-->)|(</?[A-Za-z][\\w:.-]*|/?>)|(\"[^\"]*\"|'[^']*')")
private val md = Regex("(^#{1,6}\\s.*$)|(`[^`\\n]+`)|(\\*\\*[^*\\n]+\\*\\*)", RegexOption.MULTILINE)
private val ini = Regex("(^\\s*[;#].*$)|(^\\s*\\[[^\\]\\n]*\\])|(^[^=\\n#;\\[]+(?==))", RegexOption.MULTILINE)

class SyntaxTransformation(private val lang: Lang, private val dark: Boolean) : VisualTransformation {
    private val cComment = if (dark) Color(0xFF7F8C8D) else Color(0xFF8A8F94)
    private val cString = if (dark) Color(0xFF9CCC65) else Color(0xFF2E7D32)
    private val cNumber = if (dark) Color(0xFF4FC3F7) else Color(0xFF0277BD)
    private val cKeyword = if (dark) Color(0xFFFFA726) else Color(0xFFC25E00)
    private val cKey = if (dark) Color(0xFF64B5F6) else Color(0xFF1565C0)

    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        if (lang == Lang.NONE || src.length > 100_000) return TransformedText(text, OffsetMapping.Identity)
        val out = buildAnnotatedString {
            append(src)
            val (re, colors) = when (lang) {
                Lang.CLIKE -> clike to listOf(cComment, cString, cNumber, cKeyword)
                Lang.HASH -> hash to listOf(cComment, cString, cNumber, cKeyword)
                Lang.JSON -> json to listOf(cKey, cString, cNumber, cKeyword)
                Lang.XML -> xml to listOf(cComment, cKeyword, cString)
                Lang.MARKDOWN -> md to listOf(cKeyword, cString, cNumber)
                else -> ini to listOf(cComment, cKeyword, cKey)
            }
            for (m in re.findAll(src)) {
                for (g in 1..colors.size) {
                    val grp = m.groups[g] ?: continue
                    addStyle(SpanStyle(color = colors[g - 1]), grp.range.first, grp.range.last + 1)
                    break
                }
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}
