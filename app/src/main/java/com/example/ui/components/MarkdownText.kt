package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormattedAiText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val context = LocalContext.current
    val segments = parseTextWithCodeBlocks(text)

    SelectionContainer {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (segment in segments) {
                when (segment) {
                    is TextSegment.Plain -> {
                        val annotatedText = buildMarkdownAnnotatedString(segment.content, textColor)
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        )
                    }
                    is TextSegment.Code -> {
                        CodeBlockView(
                            language = segment.language,
                            code = segment.code,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Code", segment.code)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

sealed class TextSegment {
    data class Plain(val content: String) : TextSegment()
    data class Code(val language: String, val code: String) : TextSegment()
}

private fun parseTextWithCodeBlocks(text: String): List<TextSegment> {
    val result = mutableListOf<TextSegment>()
    val codeRegex = Regex("```(\\w*)\\n?([\\s\\S]*?)```")
    var lastIndex = 0

    for (match in codeRegex.findAll(text)) {
        if (match.range.first > lastIndex) {
            val plainPart = text.substring(lastIndex, match.range.first)
            if (plainPart.isNotBlank()) {
                result.add(TextSegment.Plain(cleanLatexSymbols(plainPart)))
            }
        }
        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trim()
        result.add(TextSegment.Code(language = lang, code = code))
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex)
        if (remaining.isNotBlank()) {
            result.add(TextSegment.Plain(cleanLatexSymbols(remaining)))
        }
    }

    if (result.isEmpty()) {
        result.add(TextSegment.Plain(cleanLatexSymbols(text)))
    }

    return result
}

private fun cleanLatexSymbols(input: String): String {
    var text = input

    // 1. Unescape block math \[ ... \] and $$ ... $$ and inline math \( ... \) and $ ... $
    text = text.replace(Regex("""\\\[\s*([\s\S]*?)\s*\\\]""")) { "\n" + it.groupValues[1] + "\n" }
    text = text.replace(Regex("""\$\$\s*([\s\S]*?)\s*\$\$""")) { "\n" + it.groupValues[1] + "\n" }
    text = text.replace(Regex("""\\\(\s*([\s\S]*?)\s*\\\)""")) { " " + it.groupValues[1] + " " }
    text = text.replace(Regex("""\$([^\$\n]+)\$""")) { it.groupValues[1] }

    // 2. Remove text/math styling wrappers \text{...}, \mathrm{...}, \mathbf{...}, \mathit{...}, \operatorname{...}
    text = text.replace(Regex("""\\(?:text|mathrm|mathbf|mathit|mathsf|mathtt|operatorname|textbf|textit)\{([^}]*)\}""")) { it.groupValues[1] }

    // 3. Convert \frac{a}{b}, \dfrac{a}{b}, \tfrac{a}{b} -> a / b
    for (pass in 0..3) {
        text = text.replace(Regex("""\\(?:frac|dfrac|tfrac)\{([^}]*)\}\{([^}]*)\}""")) {
            val num = it.groupValues[1].trim()
            val den = it.groupValues[2].trim()
            if (num.contains(" ") || num.contains("+") || num.contains("-")) "($num / $den)" else "$num / $den"
        }
    }

    // 4. Roots \sqrt[n]{x} and \sqrt{x}
    text = text.replace(Regex("""\\sqrt\[([^\]]+)\]\{([^}]*)\}""")) { "(${it.groupValues[1]})√(${it.groupValues[2]})" }
    text = text.replace(Regex("""\\sqrt\{([^}]*)\}""")) { "√(${it.groupValues[1]})" }

    // 5. Greek Letters
    val greekMap = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\Gamma" to "Γ",
        "\\delta" to "δ", "\\Delta" to "Δ", "\\epsilon" to "ε", "\\varepsilon" to "ε",
        "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ", "\\Theta" to "Θ",
        "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\Lambda" to "Λ",
        "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\Xi" to "Ξ",
        "\\pi" to "π", "\\Pi" to "Π", "\\rho" to "ρ", "\\sigma" to "σ",
        "\\Sigma" to "Σ", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ",
        "\\varphi" to "φ", "\\Phi" to "Φ", "\\chi" to "χ", "\\psi" to "ψ",
        "\\Psi" to "Ψ", "\\omega" to "ω", "\\Omega" to "Ω"
    )
    for ((latex, unicode) in greekMap) {
        text = text.replace(latex, unicode)
    }

    // 6. Mathematical Operators & Symbols
    val opMap = mapOf(
        "\\cdot" to " · ",
        "\\times" to " × ",
        "\\div" to " ÷ ",
        "\\pm" to " ± ",
        "\\mp" to " ∓ ",
        "\\approx" to " ≈ ",
        "\\sim" to " ~ ",
        "\\cong" to " ≅ ",
        "\\equiv" to " ≡ ",
        "\\neq" to " ≠ ",
        "\\ne" to " ≠ ",
        "\\leq" to " ≤ ",
        "\\le" to " ≤ ",
        "\\geq" to " ≥ ",
        "\\ge" to " ≥ ",
        "\\ll" to " ≪ ",
        "\\gg" to " ≫ ",
        "\\infty" to "∞",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\int" to "∫",
        "\\iint" to "∬",
        "\\iiint" to "∭",
        "\\oint" to "∮",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\in" to " ∈ ",
        "\\notin" to " ∉ ",
        "\\subset" to " ⊂ ",
        "\\subseteq" to " ⊆ ",
        "\\supset" to " ⊃ ",
        "\\supseteq" to " ⊇ ",
        "\\cup" to " ∪ ",
        "\\cap" to " ∩ ",
        "\\land" to " ∧ ",
        "\\lor" to " ∨ ",
        "\\neg" to "¬",
        "\\oplus" to " ⊕ ",
        "\\otimes" to " ⊗ ",
        "\\to" to " → ",
        "\\rightarrow" to " → ",
        "\\leftarrow" to " ← ",
        "\\Rightarrow" to " ⇒ ",
        "\\Leftarrow" to " ⇐ ",
        "\\leftrightarrow" to " ↔ ",
        "\\Leftrightarrow" to " ⇔ ",
        "\\iff" to " ⟺ ",
        "\\implies" to " ⟹ ",
        "\\circ" to "°",
        "\\degree" to "°",
        "\\dots" to "...",
        "\\ldots" to "...",
        "\\cdots" to "..."
    )
    for ((latex, unicode) in opMap) {
        text = text.replace(latex, unicode)
    }

    // 7. Delimiters and spacing
    text = text.replace("\\left(", "(")
        .replace("\\right)", ")")
        .replace("\\left[", "[")
        .replace("\\right]", "]")
        .replace("\\left\\{", "{")
        .replace("\\right\\}", "}")
        .replace("\\{", "{")
        .replace("\\}", "}")
        .replace("\\left|", "|")
        .replace("\\right|", "|")
        .replace("\\quad", " ")
        .replace("\\qquad", "  ")
        .replace("\\,", " ")
        .replace("\\;", " ")
        .replace("\\:", " ")
        .replace("\\!", "")
        .replace("\\\\", "\n")

    // 8. Superscripts (e.g. ^2, ^3, ^{0-9}, ^+)
    val superscriptMap = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ'
    )
    text = text.replace(Regex("""\^\{([0-9+\-nixy=()]+)\}""")) { match ->
        match.groupValues[1].map { ch -> superscriptMap[ch] ?: ch }.joinToString("")
    }
    text = text.replace(Regex("""\^([0-9+\-nixy])""")) { match ->
        val ch = match.groupValues[1][0]
        superscriptMap[ch]?.toString() ?: match.value
    }

    // 9. Subscripts (e.g. _{0-9}, _0, _i, _{in}, _{out})
    val subscriptMap = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )
    text = text.replace(Regex("""_\{([0-9a-z+\-=()]+)\}""")) { match ->
        val content = match.groupValues[1]
        val allSubscriptable = content.all { it in subscriptMap }
        if (allSubscriptable) {
            content.map { subscriptMap[it] ?: it }.joinToString("")
        } else {
            "_" + content
        }
    }
    text = text.replace(Regex("""_([0-9a-z])""")) { match ->
        val ch = match.groupValues[1][0]
        subscriptMap[ch]?.toString() ?: match.value
    }

    // 10. Clean remaining dangling backslashes before plain words e.g. \vec, \bar, \hat, \overline
    text = text.replace(Regex("""\\(?:vec|bar|hat|tilde|over|underline|overline)\{([^}]*)\}""")) { it.groupValues[1] }
    text = text.replace(Regex("""\\([a-zA-Z]+)""")) { match ->
        match.groupValues[1]
    }

    return text
}

private fun buildMarkdownAnnotatedString(text: String, baseColor: Color) = buildAnnotatedString {
    val lines = text.split("\n")
    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("### ")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = baseColor)) {
                append(trimmed.removePrefix("### "))
            }
        } else if (trimmed.startsWith("## ")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = baseColor)) {
                append(trimmed.removePrefix("## "))
            }
        } else if (trimmed.startsWith("# ")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = baseColor)) {
                append(trimmed.removePrefix("# "))
            }
        } else if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
            append("• ")
            appendInlineFormatting(trimmed.substring(2), baseColor)
        } else {
            appendInlineFormatting(line, baseColor)
        }

        if (i < lines.size - 1) append("\n")
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineFormatting(text: String, baseColor: Color) {
    val boldRegex = Regex("\\*\\*([^*]+)\\*\\*")
    var currentIndex = 0

    for (match in boldRegex.findAll(text)) {
        if (match.range.first > currentIndex) {
            withStyle(SpanStyle(color = baseColor)) {
                append(text.substring(currentIndex, match.range.first))
            }
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
            append(match.groupValues[1])
        }
        currentIndex = match.range.last + 1
    }

    if (currentIndex < text.length) {
        withStyle(SpanStyle(color = baseColor)) {
            append(text.substring(currentIndex))
        }
    }
}

@Composable
fun CodeBlockView(
    language: String,
    code: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Code content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFF38BDF8),
                lineHeight = 18.sp
            )
        }
    }
}
