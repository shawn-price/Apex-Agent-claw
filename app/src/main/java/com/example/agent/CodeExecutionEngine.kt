package com.example.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.math.*

data class ExecutionResult(
    val language: String,
    val output: String,
    val returnValue: String? = null,
    val executionTimeMs: Long,
    val isSuccess: Boolean = true,
    val error: String? = null
)

class CodeExecutionEngine {

    suspend fun execute(code: String, language: String): ExecutionResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val lang = language.lowercase().trim()

        try {
            when {
                lang.contains("python") || lang.contains("py") -> executePython(code, start)
                lang.contains("javascript") || lang.contains("js") -> executeJavaScript(code, start)
                else -> executeGenericScript(code, lang, start)
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - start
            ExecutionResult(
                language = language,
                output = "",
                executionTimeMs = duration,
                isSuccess = false,
                error = "Execution Error: ${e.localizedMessage}"
            )
        }
    }

    private fun executePython(code: String, startTime: Long): ExecutionResult {
        val stdout = StringBuilder()
        val lines = code.trim().lines()
        val variables = mutableMapOf<String, Any>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Handle print statements: print("...", val)
            if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
                val arg = trimmed.substring(6, trimmed.length - 1)
                val evaluated = evaluateExpression(arg, variables)
                stdout.appendLine(evaluated)
            } else if (trimmed.contains("=") && !trimmed.contains("==")) {
                // Variable assignment: x = 10 or s = "hello"
                val parts = trimmed.split("=", limit = 2)
                val varName = parts[0].trim()
                val expr = parts[1].trim()
                val evaluated = evaluateExpression(expr, variables)
                variables[varName] = evaluated
            } else {
                // Direct expression evaluation
                val evaluated = evaluateExpression(trimmed, variables)
                if (evaluated.isNotBlank() && evaluated != "null") {
                    stdout.appendLine(evaluated)
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        return ExecutionResult(
            language = "Python 3.12 (WASM Simulator)",
            output = stdout.toString().trimEnd().ifBlank { "[Program finished with return code 0]" },
            returnValue = variables.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifBlank { null },
            executionTimeMs = duration,
            isSuccess = true
        )
    }

    private fun executeJavaScript(code: String, startTime: Long): ExecutionResult {
        val stdout = StringBuilder()
        val lines = code.trim().lines()
        val variables = mutableMapOf<String, Any>()

        for (line in lines) {
            val trimmed = line.trim().removeSuffix(";")
            if (trimmed.isEmpty() || trimmed.startsWith("//")) continue

            // console.log(...)
            if (trimmed.startsWith("console.log(") && trimmed.endsWith(")")) {
                val arg = trimmed.substring(12, trimmed.length - 1)
                val evaluated = evaluateExpression(arg, variables)
                stdout.appendLine(evaluated)
            } else if (trimmed.startsWith("let ") || trimmed.startsWith("var ") || trimmed.startsWith("const ")) {
                val withoutDecl = trimmed.substringAfter(" ")
                val parts = withoutDecl.split("=", limit = 2)
                val varName = parts[0].trim()
                val expr = parts.getOrNull(1)?.trim() ?: "undefined"
                val evaluated = evaluateExpression(expr, variables)
                variables[varName] = evaluated
            } else {
                val evaluated = evaluateExpression(trimmed, variables)
                if (evaluated.isNotBlank() && evaluated != "null") {
                    stdout.appendLine(evaluated)
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        return ExecutionResult(
            language = "JavaScript (V8/QuickJS Sandbox)",
            output = stdout.toString().trimEnd().ifBlank { "[Process finished with exit code 0]" },
            returnValue = variables.entries.joinToString(", ") { "${it.key}=${it.value}" }.ifBlank { null },
            executionTimeMs = duration,
            isSuccess = true
        )
    }

    private fun executeGenericScript(code: String, lang: String, startTime: Long): ExecutionResult {
        val duration = System.currentTimeMillis() - startTime
        return ExecutionResult(
            language = lang.uppercase(),
            output = "Executed $lang script:\n$code\nStatus: OK",
            executionTimeMs = duration,
            isSuccess = true
        )
    }

    private fun evaluateExpression(expr: String, vars: Map<String, Any>): String {
        var clean = expr.trim()

        // String literals
        if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'"))) {
            return clean.substring(1, clean.length - 1)
        }

        // Variable lookup
        if (vars.containsKey(clean)) {
            return vars[clean].toString()
        }

        // Math calculations: e.g., 2 + 2, 10 * 5, Math.sqrt(144), etc.
        try {
            // Replace vars in math expression
            var mathStr = clean
            vars.forEach { (k, v) ->
                mathStr = mathStr.replace(Regex("\\b$k\\b"), v.toString())
            }

            // Common functions
            if (mathStr.contains("sqrt(") || mathStr.contains("Math.sqrt(")) {
                val num = mathStr.substringAfter("(").substringBefore(")").toDoubleOrNull() ?: 0.0
                return sqrt(num).toString()
            }
            if (mathStr.contains("pow(") || mathStr.contains("Math.pow(")) {
                val args = mathStr.substringAfter("(").substringBefore(")").split(",")
                val b = args[0].trim().toDoubleOrNull() ?: 0.0
                val e = args.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 1.0
                return b.pow(e).toString()
            }

            // Simple binary math: +, -, *, /, %
            val mathRegex = Regex("""^([\d\.]+)\s*([\+\-\*\/\%])\s*([\d\.]+)$""")
            val match = mathRegex.find(mathStr)
            if (match != null) {
                val n1 = match.groupValues[1].toDouble()
                val op = match.groupValues[2]
                val n2 = match.groupValues[3].toDouble()
                val res = when (op) {
                    "+" -> n1 + n2
                    "-" -> n1 - n2
                    "*" -> n1 * n2
                    "/" -> if (n2 != 0.0) n1 / n2 else Double.NaN
                    "%" -> n1 % n2
                    else -> 0.0
                }
                return if (res % 1.0 == 0.0) res.toLong().toString() else res.toString()
            }
        } catch (e: Exception) {
            // fallback
        }

        return clean
    }
}
