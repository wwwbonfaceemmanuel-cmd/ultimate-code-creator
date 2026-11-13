package com.ultimate.codecreator.engine

import com.ultimate.codecreator.model.GenerationResult
import com.ultimate.codecreator.model.RecipeInfo
import com.ultimate.codecreator.model.RecipeParameter
import com.ultimate.codecreator.model.SupportedLanguage

/**
 * Small, deterministic, on-device recipe library. This is not an LLM, compiler,
 * or general-purpose natural-language-to-code system. Unmatched requests get a
 * clearly labelled starter in the selected language, never code for another one.
 */
object OfflineCodeSynthesizer {
    const val MAX_PROMPT_LENGTH = 4_000

    private data class Recipe(
        val id: String,
        val intent: String,
        val explanation: String,
        val pattern: Regex,
        val templates: Map<SupportedLanguage, String>,
        val keywords: List<String>,
        val dependencies: Map<SupportedLanguage, List<String>>,
        val safetyNotes: List<String>,
        val parameters: List<RecipeParameter>
    )

    val catalog: List<RecipeInfo> by lazy {
        recipes.map { recipe ->
            RecipeInfo(
                id = recipe.id,
                title = recipe.intent,
                explanation = recipe.explanation,
                keywords = recipe.keywords,
                languages = recipe.templates.keys.toList(),
                dependencies = recipe.dependencies,
                safetyNotes = recipe.safetyNotes,
                parameters = recipe.parameters
            )
        }
    }

    fun catalogFor(language: SupportedLanguage): List<RecipeInfo> = catalog.filter { language in it.languages }

    fun synthesize(rawPrompt: String, language: SupportedLanguage): GenerationResult {
        val prompt = rawPrompt.trim()
        require(prompt.isNotEmpty()) { "Enter an instruction or choose a keyword first." }
        require(prompt.length <= MAX_PROMPT_LENGTH) { "Keep instructions under $MAX_PROMPT_LENGTH characters." }
        val normalized = prompt.lowercase()
        val matches = recipes.filter { it.pattern.containsMatchIn(normalized) }
        val recipe = matches.firstOrNull()
        val template = recipe?.templates?.get(language)
        if (template != null && recipe != null) {
            val (code, appliedParameters) = applyParameters(template, recipe, prompt)
            return GenerationResult(
                code = code,
                intentDetected = recipe.intent,
                explanation = recipe.explanation,
                language = language,
                recipeId = recipe.id,
                confidence = if (matches.size == 1) 0.95f else 0.75f,
                alternatives = matches.drop(1).map { it.intent },
                appliedParameters = appliedParameters
            )
        }
        return GenerationResult(
            code = starter(language, safeComment(prompt)),
            intentDetected = "${language.displayName} starter",
            explanation = if (recipe == null) {
                "No bundled recipe matched. This is a language-specific starter, not an implementation of your instruction."
            } else {
                "The ${recipe.intent} recipe is not bundled for ${language.displayName}. This is a starter, not the requested implementation."
            },
            language = language,
            isTemplateFallback = true,
            alternatives = matches.map { it.intent }
        )
    }

    private fun applyParameters(template: String, recipe: Recipe, prompt: String): Pair<String, Map<String, String>> {
        var code = template
        val applied = mutableMapOf<String, String>()
        recipe.parameters.forEach { parameter ->
            val match = Regex("\\b${Regex.escape(parameter.key)}\\s*=\\s*([^\\s,;]+)", RegexOption.IGNORE_CASE).find(prompt)
            val value = match?.groupValues?.get(1) ?: parameter.defaultValue
            require(Regex(parameter.validationPattern).matches(value)) {
                "Invalid ${parameter.label}. ${parameter.helpText}"
            }
            code = code.replace("{{${parameter.key}}}", value)
            applied[parameter.key] = value
        }
        return code to applied
    }

    private fun safeComment(prompt: String): String = prompt
        .replace(Regex("\\s+"), " ")
        .replace("*/", "* /")
        .replace("-->", "-- >")
        .take(160)

    private fun recipe(
        intent: String,
        explanation: String,
        pattern: String,
        vararg templates: Pair<SupportedLanguage, String>
    ): Recipe {
        val id = intent.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val keywords = pattern.split('|')
            .map { it.replace(Regex("[^a-zA-Z0-9 /_+.-]"), " ").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
        val dependencies = when (intent) {
            "REST API example" -> mapOf(
                SupportedLanguage.PYTHON to listOf("fastapi", "uvicorn"),
                SupportedLanguage.JAVASCRIPT to listOf("express")
            )
            "Concurrent / async tasks" -> mapOf(SupportedLanguage.KOTLIN to listOf("kotlinx-coroutines-core"))
            else -> emptyMap()
        }
        val safetyNotes = when {
            intent.contains("login", ignoreCase = true) -> listOf("UI demo only; not a real authentication system.")
            intent.contains("HTTP", ignoreCase = true) || intent.contains("REST", ignoreCase = true) -> listOf("Generated client/service may require network access when run outside this app.")
            intent.contains("File", ignoreCase = true) -> listOf("Replace example paths and review file permissions before running.")
            intent.contains("SQL", ignoreCase = true) -> listOf("Review schema, types, and constraints for your database.")
            else -> emptyList()
        }
        val parameters = if (intent == "LRU cache") listOf(
            RecipeParameter("capacity", "Cache capacity", "2", "^[1-9][0-9]{0,4}$", "Use an integer from 1 to 99999, for example capacity=10.")
        ) else emptyList()
        return Recipe(id, intent, explanation, Regex(pattern), templates.toMap(), keywords, dependencies, safetyNotes, parameters)
    }

    private val sqlRecipes = listOf(
        recipe("SQL CRUD transaction", "Portable table, insert, query, update and delete examples. Review types and constraints for your database.", "crud|database|query",
            SupportedLanguage.SQL to """
                BEGIN;

                CREATE TABLE tasks (
                    id INTEGER PRIMARY KEY,
                    title VARCHAR(200) NOT NULL,
                    completed BOOLEAN NOT NULL DEFAULT FALSE
                );

                INSERT INTO tasks (id, title, completed) VALUES (1, 'Write offline code', FALSE);
                SELECT id, title, completed FROM tasks ORDER BY id;
                UPDATE tasks SET completed = TRUE WHERE id = 1;
                DELETE FROM tasks WHERE completed = TRUE;

                COMMIT;
            """.trimIndent()),
        recipe("SQL table schema", "Example schema with primary key and uniqueness constraints.", "create table|schema|table",
            SupportedLanguage.SQL to """
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY,
                    email VARCHAR(254) NOT NULL UNIQUE,
                    display_name VARCHAR(100) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    active BOOLEAN NOT NULL DEFAULT TRUE
                );

                CREATE INDEX users_display_name_idx ON users (display_name);
            """.trimIndent()),
        recipe("SQL inner join", "Join existing users and orders tables and sort by total.", "inner join|join",
            SupportedLanguage.SQL to """
                SELECT
                    users.id,
                    users.display_name,
                    orders.id AS order_id,
                    orders.total
                FROM users
                INNER JOIN orders ON orders.user_id = users.id
                WHERE orders.total > 0
                ORDER BY orders.total DESC;
            """.trimIndent()),
        recipe("SQL window ranking", "Rank employees within each department. Requires a database with window-function support.", "window|rank|dense_rank",
            SupportedLanguage.SQL to """
                WITH ranked_salaries AS (
                    SELECT
                        emp_id,
                        full_name,
                        department_id,
                        salary,
                        DENSE_RANK() OVER (
                            PARTITION BY department_id ORDER BY salary DESC
                        ) AS salary_rank
                    FROM employees
                )
                SELECT emp_id, full_name, department_id, salary
                FROM ranked_salaries
                WHERE salary_rank <= 3
                ORDER BY department_id, salary_rank;
            """.trimIndent())
    )

    private val recipes = listOf(
        recipe("Offline web calculator", "Responsive calculator using bounded arithmetic logic, not eval(). Everything runs locally.", "calculator",
            SupportedLanguage.HTML_CSS to """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Offline Calculator</title>
                  <style>
                    * { box-sizing: border-box; }
                    body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #0f172a; color: #f8fafc; font-family: system-ui, sans-serif; }
                    .calculator { width: min(320px, 92vw); padding: 20px; background: #1e293b; border-radius: 16px; }
                    output { display: block; padding: 18px 8px; text-align: right; font-size: 30px; overflow-wrap: anywhere; }
                    .keys { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
                    button { min-height: 52px; border: 0; border-radius: 8px; background: #334155; color: white; font-size: 20px; }
                    button.operator { background: #0369a1; }
                    button:focus-visible { outline: 3px solid #38bdf8; }
                  </style>
                </head>
                <body>
                  <main class="calculator" aria-label="Calculator">
                    <output id="display" aria-live="polite">0</output>
                    <div class="keys">
                      <button data-key="clear">C</button><button data-key="/">÷</button><button data-key="*">×</button><button data-key="-">−</button>
                      <button data-key="7">7</button><button data-key="8">8</button><button data-key="9">9</button><button class="operator" data-key="+">+</button>
                      <button data-key="4">4</button><button data-key="5">5</button><button data-key="6">6</button><button class="operator" data-key="=">=</button>
                      <button data-key="1">1</button><button data-key="2">2</button><button data-key="3">3</button><button data-key=".">.</button>
                      <button data-key="0">0</button>
                    </div>
                  </main>
                  <script>
                    const display = document.getElementById('display');
                    let current = '0', stored = null, operator = null, fresh = true;
                    function calculate() {
                      const a = stored, b = Number(current);
                      if (a === null || !operator) return b;
                      if (operator === '/' && b === 0) throw new Error('Cannot divide by zero');
                      const result = operator === '+' ? a + b : operator === '-' ? a - b : operator === '*' ? a * b : a / b;
                      if (!Number.isFinite(result)) throw new Error('Result is too large');
                      return Number(result.toPrecision(12));
                    }
                    document.querySelector('.keys').addEventListener('click', event => {
                      const key = event.target.closest('button')?.dataset.key;
                      if (!key) return;
                      try {
                        if (key === 'clear') { current = '0'; stored = null; operator = null; fresh = true; }
                        else if (/^[0-9.]$/.test(key)) {
                          if (fresh) { current = key === '.' ? '0.' : key; fresh = false; }
                          else if (key !== '.' || !current.includes('.')) { current = current === '0' && key !== '.' ? key : current + key; }
                        } else {
                          const value = calculate();
                          current = String(value);
                          stored = key === '=' ? null : value;
                          operator = key === '=' ? null : key;
                          fresh = true;
                        }
                        display.textContent = current;
                      } catch (error) {
                        display.textContent = error.message;
                        current = '0'; stored = null; operator = null; fresh = true;
                      }
                    });
                  </script>
                </body>
                </html>
            """.trimIndent()),
        recipe("Accessible login form (demo)", "Local form with browser validation. It does not authenticate, transmit or store credentials; connect a reviewed backend for real authentication.", "\\blogin\\b|sign in|signup|sign up|\\bform\\b",
            SupportedLanguage.HTML_CSS to """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Login Form</title>
                  <style>
                    * { box-sizing: border-box; }
                    body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 20px; background: #0f172a; color: #f8fafc; font-family: system-ui, sans-serif; }
                    .card { width: min(380px, 100%); padding: 28px; border: 1px solid #334155; border-radius: 16px; background: #1e293b; }
                    h1 { margin-top: 0; color: #38bdf8; }
                    label { display: block; margin: 16px 0 6px; }
                    input, button { width: 100%; min-height: 46px; padding: 10px 12px; border-radius: 8px; font: inherit; }
                    input { border: 1px solid #64748b; background: #0f172a; color: white; }
                    input:focus-visible, button:focus-visible { outline: 3px solid #38bdf8; outline-offset: 2px; }
                    button { margin-top: 20px; border: 0; background: #38bdf8; color: #0f172a; font-weight: 700; }
                    .note { color: #cbd5e1; font-size: 14px; line-height: 1.5; }
                  </style>
                </head>
                <body>
                  <main class="card">
                    <h1>Sign in</h1>
                    <p class="note">Offline UI demo. No credentials are sent or saved.</p>
                    <form id="login-form">
                      <label for="email">Email address</label>
                      <input id="email" name="email" type="email" autocomplete="username" required>
                      <label for="password">Password</label>
                      <input id="password" name="password" type="password" autocomplete="current-password" minlength="8" required>
                      <button type="submit">Check form</button>
                    </form>
                    <p id="status" role="status"></p>
                  </main>
                  <script>
                    document.getElementById('login-form').addEventListener('submit', event => {
                      event.preventDefault();
                      document.getElementById('status').textContent = 'Form is valid. This demo does not sign you in.';
                      document.getElementById('password').value = '';
                    });
                  </script>
                </body>
                </html>
            """.trimIndent()),
        recipe("Glassmorphism profile card", "Responsive profile card with local CSS and no external fonts or images.", "card|glass|profile|portfolio",
            SupportedLanguage.HTML_CSS to """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Profile Card</title>
                  <style>
                    * { box-sizing: border-box; }
                    body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 24px; background: linear-gradient(135deg, #0284c7, #7c3aed); color: white; font-family: system-ui, sans-serif; }
                    .card { width: min(340px, 100%); padding: 32px; text-align: center; border: 1px solid #ffffff55; border-radius: 20px; background: #ffffff20; backdrop-filter: blur(14px); box-shadow: 0 16px 40px #00000030; }
                    .avatar { width: 76px; height: 76px; margin: auto; display: grid; place-items: center; border-radius: 50%; background: #0f172a; color: #38bdf8; font-size: 26px; font-weight: 700; }
                    p { line-height: 1.6; }
                    button { min-height: 44px; padding: 10px 24px; border: 0; border-radius: 24px; background: white; color: #0369a1; font-weight: 700; }
                  </style>
                </head>
                <body>
                  <main class="card">
                    <div class="avatar" aria-hidden="true">AR</div>
                    <h1>Alex Rivers</h1>
                    <p>Full-stack engineer who enjoys building useful, accessible tools.</p>
                    <button id="connect">Say hello</button>
                    <p id="message" role="status"></p>
                  </main>
                  <script>
                    document.getElementById('connect').addEventListener('click', () => {
                      document.getElementById('message').textContent = 'Hello! This interaction runs entirely offline.';
                    });
                  </script>
                </body>
                </html>
            """.trimIndent()),
        recipe("Binary search", "Search a sorted collection in O(log n) time. The caller must provide sorted input.", "binary search|binary_search|binarysearch",
            SupportedLanguage.PYTHON to """
                def binary_search(values: list[int], target: int) -> int | None:
                    left, right = 0, len(values) - 1
                    while left <= right:
                        mid = left + (right - left) // 2
                        if values[mid] == target:
                            return mid
                        if values[mid] < target:
                            left = mid + 1
                        else:
                            right = mid - 1
                    return None

                if __name__ == "__main__":
                    numbers = [2, 5, 8, 12, 16, 23, 38]
                    print(binary_search(numbers, 23))
            """.trimIndent(),
            SupportedLanguage.JAVA to """
                public class Main {
                    public static int binarySearch(int[] values, int target) {
                        int left = 0, right = values.length - 1;
                        while (left <= right) {
                            int mid = left + (right - left) / 2;
                            if (values[mid] == target) return mid;
                            if (values[mid] < target) left = mid + 1;
                            else right = mid - 1;
                        }
                        return -1;
                    }

                    public static void main(String[] args) {
                        int[] values = {2, 5, 8, 12, 16, 23, 38};
                        System.out.println(binarySearch(values, 23));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.KOTLIN to """
                fun binarySearch(values: IntArray, target: Int): Int {
                    var left = 0
                    var right = values.lastIndex
                    while (left <= right) {
                        val mid = left + (right - left) / 2
                        if (values[mid] == target) return mid
                        if (values[mid] < target) left = mid + 1 else right = mid - 1
                    }
                    return -1
                }

                fun main() {
                    println(binarySearch(intArrayOf(2, 5, 8, 12, 16, 23, 38), 23))
                }
            """.trimIndent(),
            SupportedLanguage.JAVASCRIPT to """
                function binarySearch(values, target) {
                    let left = 0, right = values.length - 1;
                    while (left <= right) {
                        const mid = left + Math.floor((right - left) / 2);
                        if (values[mid] === target) return mid;
                        if (values[mid] < target) left = mid + 1;
                        else right = mid - 1;
                    }
                    return -1;
                }

                console.log(binarySearch([2, 5, 8, 12, 16, 23, 38], 23));
            """.trimIndent(),
            SupportedLanguage.CPP to """
                #include <cstddef>
                #include <iostream>
                #include <vector>

                std::size_t binarySearch(const std::vector<int>& values, int target) {
                    std::size_t left = 0, right = values.size();
                    while (left < right) {
                        const std::size_t mid = left + (right - left) / 2;
                        if (values[mid] == target) return mid;
                        if (values[mid] < target) left = mid + 1;
                        else right = mid;
                    }
                    return values.size();
                }

                int main() {
                    const std::vector<int> values{2, 5, 8, 12, 16, 23, 38};
                    std::cout << binarySearch(values, 23) << '\n';
                }
            """.trimIndent(),
            SupportedLanguage.GO to """
                package main

                import "fmt"

                func binarySearch(values []int, target int) int {
                    left, right := 0, len(values)-1
                    for left <= right {
                        mid := left + (right-left)/2
                        if values[mid] == target {
                            return mid
                        }
                        if values[mid] < target {
                            left = mid + 1
                        } else {
                            right = mid - 1
                        }
                    }
                    return -1
                }

                func main() {
                    fmt.Println(binarySearch([]int{2, 5, 8, 12, 16, 23, 38}, 23))
                }
            """.trimIndent(),
            SupportedLanguage.RUST to """
                fn binary_search(values: &[i32], target: i32) -> Option<usize> {
                    let (mut left, mut right) = (0usize, values.len());
                    while left < right {
                        let mid = left + (right - left) / 2;
                        if values[mid] == target {
                            return Some(mid);
                        }
                        if values[mid] < target {
                            left = mid + 1;
                        } else {
                            right = mid;
                        }
                    }
                    None
                }

                fn main() {
                    let values = [2, 5, 8, 12, 16, 23, 38];
                    println!("{:?}", binary_search(&values, 23));
                }
            """.trimIndent(),
            SupportedLanguage.C_SHARP to """
                using System;

                public static class Program {
                    public static int BinarySearch(int[] values, int target) {
                        int left = 0, right = values.Length - 1;
                        while (left <= right) {
                            int mid = left + (right - left) / 2;
                            if (values[mid] == target) return mid;
                            if (values[mid] < target) left = mid + 1;
                            else right = mid - 1;
                        }
                        return -1;
                    }

                    public static void Main() {
                        Console.WriteLine(BinarySearch(new[] {2, 5, 8, 12, 16, 23, 38}, 23));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.TYPESCRIPT to """
                function binarySearch(values: readonly number[], target: number): number {
                    let left = 0, right = values.length - 1;
                    while (left <= right) {
                        const mid = left + Math.floor((right - left) / 2);
                        if (values[mid] === target) return mid;
                        if (values[mid] < target) left = mid + 1;
                        else right = mid - 1;
                    }
                    return -1;
                }

                console.log(binarySearch([2, 5, 8, 12, 16, 23, 38], 23));
            """.trimIndent()),
        recipe("Iterative Fibonacci", "O(n) time and O(1) space. Check numeric limits before using large indices.", "fibonacci",
            SupportedLanguage.PYTHON to """
                def fibonacci(n: int) -> int:
                    if n < 0:
                        raise ValueError("n must be non-negative")
                    a, b = 0, 1
                    for _ in range(n):
                        a, b = b, a + b
                    return a

                if __name__ == "__main__":
                    print([fibonacci(i) for i in range(12)])
            """.trimIndent(),
            SupportedLanguage.JAVA to """
                public class Main {
                    public static long fibonacci(int n) {
                        if (n < 0 || n > 92) throw new IllegalArgumentException("Use 0 through 92 for long");
                        long a = 0, b = 1;
                        for (int i = 0; i < n; i++) {
                            long next = a + b;
                            a = b;
                            b = next;
                        }
                        return a;
                    }

                    public static void main(String[] args) {
                        for (int i = 0; i < 12; i++) System.out.println(fibonacci(i));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.KOTLIN to """
                fun fibonacci(n: Int): Long {
                    require(n in 0..92) { "Use 0 through 92 for Long" }
                    var a = 0L
                    var b = 1L
                    repeat(n) {
                        val next = a + b
                        a = b
                        b = next
                    }
                    return a
                }

                fun main() {
                    repeat(12) { println(fibonacci(it)) }
                }
            """.trimIndent(),
            SupportedLanguage.JAVASCRIPT to """
                function fibonacci(n) {
                    if (!Number.isInteger(n) || n < 0 || n > 78) throw new RangeError('Use an integer from 0 through 78');
                    let a = 0, b = 1;
                    for (let i = 0; i < n; i++) [a, b] = [b, a + b];
                    return a;
                }

                console.log(Array.from({length: 12}, (_, i) => fibonacci(i)));
            """.trimIndent(),
            SupportedLanguage.CPP to """
                #include <cstdint>
                #include <iostream>
                #include <stdexcept>

                std::uint64_t fibonacci(unsigned int n) {
                    if (n > 93) throw std::invalid_argument("n exceeds the uint64_t result range");
                    std::uint64_t a = 0, b = 1;
                    for (unsigned int i = 0; i < n; ++i) {
                        const auto next = a + b;
                        a = b;
                        b = next;
                    }
                    return a;
                }

                int main() {
                    for (unsigned int i = 0; i < 12; ++i) std::cout << fibonacci(i) << '\n';
                }
            """.trimIndent(),
            SupportedLanguage.GO to """
                package main

                import "fmt"

                func fibonacci(n int) uint64 {
                    var a, b uint64 = 0, 1
                    for i := 0; i < n; i++ {
                        a, b = b, a+b
                    }
                    return a
                }

                func main() {
                    for i := 0; i < 12; i++ {
                        fmt.Println(fibonacci(i))
                    }
                }
            """.trimIndent(),
            SupportedLanguage.RUST to """
                fn fibonacci(n: u32) -> u64 {
                    let (mut a, mut b) = (0u64, 1u64);
                    for _ in 0..n {
                        let next = a + b;
                        a = b;
                        b = next;
                    }
                    a
                }

                fn main() {
                    for i in 0..12 {
                        println!("{}", fibonacci(i));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.C_SHARP to """
                using System;

                public static class Program {
                    public static ulong Fibonacci(int n) {
                        if (n < 0 || n > 93) throw new ArgumentOutOfRangeException(nameof(n));
                        ulong a = 0, b = 1;
                        for (int i = 0; i < n; i++) {
                            ulong next = a + b;
                            a = b;
                            b = next;
                        }
                        return a;
                    }

                    public static void Main() {
                        for (int i = 0; i < 12; i++) Console.WriteLine(Fibonacci(i));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.TYPESCRIPT to """
                function fibonacci(n: number): number {
                    if (!Number.isInteger(n) || n < 0 || n > 78) throw new RangeError('Use an integer from 0 through 78');
                    let a = 0, b = 1;
                    for (let i = 0; i < n; i++) [a, b] = [b, a + b];
                    return a;
                }

                console.log(Array.from({length: 12}, (_, i) => fibonacci(i)));
            """.trimIndent()),
        recipe("Quicksort", "Readable divide-and-conquer example. Average O(n log n), worst-case O(n²); not an in-place sort.", "quicksort|quick sort|mergesort|merge sort|\\bsort\\b",
            SupportedLanguage.PYTHON to """
                def quicksort(values: list[int]) -> list[int]:
                    if len(values) <= 1:
                        return values.copy()
                    pivot = values[len(values) // 2]
                    smaller = [value for value in values if value < pivot]
                    equal = [value for value in values if value == pivot]
                    larger = [value for value in values if value > pivot]
                    return quicksort(smaller) + equal + quicksort(larger)

                if __name__ == "__main__":
                    print(quicksort([38, 27, 43, 3, 9, 82, 10]))
            """.trimIndent()),
        recipe("LRU cache", "Evicts the least recently used entry. This implementation is not thread-safe.", "\\blru\\b|cache",
            SupportedLanguage.PYTHON to """
                from collections import OrderedDict

                class LRUCache:
                    def __init__(self, capacity: int):
                        if capacity <= 0:
                            raise ValueError("capacity must be positive")
                        self.capacity = capacity
                        self.items = OrderedDict()

                    def get(self, key):
                        if key not in self.items:
                            return None
                        self.items.move_to_end(key)
                        return self.items[key]

                    def put(self, key, value) -> None:
                        self.items[key] = value
                        self.items.move_to_end(key)
                        if len(self.items) > self.capacity:
                            self.items.popitem(last=False)

                if __name__ == "__main__":
                    cache = LRUCache({{capacity}})
                    cache.put("a", 1)
                    cache.put("b", 2)
                    cache.get("a")
                    cache.put("c", 3)
                    print(cache.get("b"), cache.get("a"))
            """.trimIndent(),
            SupportedLanguage.JAVA to """
                import java.util.LinkedHashMap;
                import java.util.Map;

                public class Main {
                    public static void main(String[] args) {
                        final int capacity = {{capacity}};
                        Map<String, Integer> cache = new LinkedHashMap<>(16, 0.75f, true) {
                            @Override
                            protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                                return size() > capacity;
                            }
                        };
                        cache.put("a", 1);
                        cache.put("b", 2);
                        cache.get("a");
                        cache.put("c", 3);
                        System.out.println(cache);
                    }
                }
            """.trimIndent(),
            SupportedLanguage.KOTLIN to """
                class LRUCache<K, V>(private val capacity: Int) {
                    init { require(capacity > 0) }
                    private val items = object : LinkedHashMap<K, V>(16, 0.75f, true) {
                        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean = size > capacity
                    }

                    fun get(key: K): V? = items[key]
                    fun put(key: K, value: V) { items[key] = value }
                }

                fun main() {
                    val cache = LRUCache<String, Int>({{capacity}})
                    cache.put("a", 1)
                    cache.put("b", 2)
                    cache.get("a")
                    cache.put("c", 3)
                    println(cache.get("b"))
                }
            """.trimIndent(),
            SupportedLanguage.JAVASCRIPT to """
                class LRUCache {
                    constructor(capacity) {
                        if (!Number.isInteger(capacity) || capacity <= 0) throw new RangeError('Positive capacity required');
                        this.capacity = capacity;
                        this.items = new Map();
                    }
                    get(key) {
                        if (!this.items.has(key)) return undefined;
                        const value = this.items.get(key);
                        this.items.delete(key);
                        this.items.set(key, value);
                        return value;
                    }
                    put(key, value) {
                        this.items.delete(key);
                        this.items.set(key, value);
                        if (this.items.size > this.capacity) this.items.delete(this.items.keys().next().value);
                    }
                }

                const cache = new LRUCache({{capacity}});
                cache.put('a', 1); cache.put('b', 2); cache.get('a'); cache.put('c', 3);
                console.log(cache.get('b'), cache.get('a'));
            """.trimIndent(),
            SupportedLanguage.C_SHARP to """
                using System;
                using System.Collections.Generic;

                public class LRUCache<K, V> where K : notnull {
                    private readonly int capacity;
                    private readonly Dictionary<K, LinkedListNode<(K Key, V Value)>> items = new();
                    private readonly LinkedList<(K Key, V Value)> order = new();

                    public LRUCache(int capacity) {
                        if (capacity <= 0) throw new ArgumentOutOfRangeException(nameof(capacity));
                        this.capacity = capacity;
                    }

                    public bool TryGet(K key, out V value) {
                        if (!items.TryGetValue(key, out var node)) { value = default!; return false; }
                        order.Remove(node);
                        order.AddFirst(node);
                        value = node.Value.Value;
                        return true;
                    }

                    public void Put(K key, V value) {
                        if (items.TryGetValue(key, out var node)) { order.Remove(node); items.Remove(key); }
                        var added = order.AddFirst((key, value));
                        items[key] = added;
                        if (items.Count <= capacity) return;
                        var last = order.Last!;
                        items.Remove(last.Value.Key);
                        order.RemoveLast();
                    }
                }

                public static class Program {
                    public static void Main() {
                        var cache = new LRUCache<string, int>({{capacity}});
                        cache.Put("a", 1); cache.Put("b", 2); cache.TryGet("a", out _); cache.Put("c", 3);
                        Console.WriteLine(cache.TryGet("b", out _));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.TYPESCRIPT to """
                class LRUCache<K, V> {
                    private readonly items = new Map<K, V>();
                    constructor(private readonly capacity: number) {
                        if (!Number.isInteger(capacity) || capacity <= 0) throw new RangeError('Positive capacity required');
                    }
                    get(key: K): V | undefined {
                        if (!this.items.has(key)) return undefined;
                        const value = this.items.get(key)!;
                        this.items.delete(key);
                        this.items.set(key, value);
                        return value;
                    }
                    put(key: K, value: V): void {
                        this.items.delete(key);
                        this.items.set(key, value);
                        if (this.items.size > this.capacity) {
                            const oldest = this.items.keys().next().value as K;
                            this.items.delete(oldest);
                        }
                    }
                }

                const cache = new LRUCache<string, number>({{capacity}});
                cache.put('a', 1); cache.put('b', 2); cache.get('a'); cache.put('c', 3);
                console.log(cache.get('b'), cache.get('a'));
            """.trimIndent()),
        recipe("Binary search tree", "Insert and inorder traversal. Unbalanced worst-case operations are O(n).", "\\btree\\b|\\bbst\\b",
            SupportedLanguage.PYTHON to """
                from dataclasses import dataclass

                @dataclass
                class Node:
                    value: int
                    left: "Node | None" = None
                    right: "Node | None" = None

                class BinarySearchTree:
                    def __init__(self):
                        self.root: Node | None = None

                    def insert(self, value: int) -> None:
                        node = self.root
                        if node is None:
                            self.root = Node(value)
                            return
                        while True:
                            if value < node.value:
                                if node.left is None:
                                    node.left = Node(value)
                                    return
                                node = node.left
                            else:
                                if node.right is None:
                                    node.right = Node(value)
                                    return
                                node = node.right

                    def inorder(self) -> list[int]:
                        result, stack, node = [], [], self.root
                        while stack or node is not None:
                            while node is not None:
                                stack.append(node)
                                node = node.left
                            node = stack.pop()
                            result.append(node.value)
                            node = node.right
                        return result

                if __name__ == "__main__":
                    tree = BinarySearchTree()
                    for value in [8, 3, 10, 1, 6]:
                        tree.insert(value)
                    print(tree.inorder())
            """.trimIndent()),
        recipe("Stack", "Checked stack operations with clear empty-stack behavior.", "\\bstack\\b",
            SupportedLanguage.PYTHON to """
                class MinStack:
                    def __init__(self):
                        self.values: list[int] = []
                        self.minimums: list[int] = []

                    def push(self, value: int) -> None:
                        self.values.append(value)
                        self.minimums.append(min(value, self.minimums[-1]) if self.minimums else value)

                    def pop(self) -> int:
                        if not self.values:
                            raise IndexError("Stack is empty")
                        self.minimums.pop()
                        return self.values.pop()

                    def minimum(self) -> int:
                        if not self.minimums:
                            raise IndexError("Stack is empty")
                        return self.minimums[-1]

                if __name__ == "__main__":
                    stack = MinStack()
                    stack.push(4)
                    stack.push(2)
                    print(stack.minimum())
                    print(stack.pop())
            """.trimIndent(),
            SupportedLanguage.CPP to """
                #include <iostream>
                #include <stdexcept>
                #include <vector>

                template<typename T>
                class Stack {
                    std::vector<T> values;
                public:
                    void push(T value) { values.push_back(value); }
                    T pop() {
                        if (values.empty()) throw std::underflow_error("Stack is empty");
                        const T value = values.back();
                        values.pop_back();
                        return value;
                    }
                    bool empty() const { return values.empty(); }
                };

                int main() {
                    Stack<int> stack;
                    stack.push(4);
                    stack.push(2);
                    std::cout << stack.pop() << '\n';
                }
            """.trimIndent(),
            SupportedLanguage.RUST to """
                fn main() {
                    let mut stack: Vec<i32> = Vec::new();
                    stack.push(4);
                    stack.push(2);
                    match stack.pop() {
                        Some(value) => println!("{}", value),
                        None => println!("Stack is empty"),
                    }
                    println!("Remaining: {}", stack.len());
                }
            """.trimIndent()),
        recipe("REST API example", "Runnable service example with validation and status codes. Install noted dependencies; generated network services are not themselves offline.", "rest|endpoint|controller|fastapi|spring|(?<!fetch )\\bapi\\b",
            SupportedLanguage.PYTHON to """
                # Install: python -m pip install fastapi uvicorn
                # Run: uvicorn main:app --host 127.0.0.1 --port 8000
                from fastapi import FastAPI, HTTPException, Response
                from pydantic import BaseModel, Field

                app = FastAPI(title="Task API")

                class TaskInput(BaseModel):
                    title: str = Field(min_length=1, max_length=200)

                class Task(TaskInput):
                    id: int

                tasks: dict[int, Task] = {}
                next_id = 1

                @app.get("/tasks")
                def list_tasks() -> list[Task]:
                    return list(tasks.values())

                @app.post("/tasks", status_code=201)
                def create_task(payload: TaskInput, response: Response) -> Task:
                    global next_id
                    task = Task(id=next_id, title=payload.title)
                    tasks[next_id] = task
                    next_id += 1
                    response.headers["Location"] = "/tasks/" + str(task.id)
                    return task

                @app.delete("/tasks/{task_id}", status_code=204)
                def delete_task(task_id: int) -> Response:
                    if task_id not in tasks:
                        raise HTTPException(status_code=404, detail="Task not found")
                    del tasks[task_id]
                    return Response(status_code=204)
            """.trimIndent(),
            SupportedLanguage.JAVASCRIPT to """
                // Requires Node.js 18+ and: npm install express
                // Express 4 validates payloads explicitly below.
                const express = require('express');
                const app = express();
                app.use(express.json({limit: '16kb'}));
                const tasks = new Map();
                let nextId = 1;

                app.get('/tasks', (req, res) => res.json([...tasks.values()]));
                app.post('/tasks', (req, res) => {
                    const title = req.body?.title;
                    if (typeof title !== 'string' || !title.trim() || title.length > 200) {
                        return res.status(400).json({error: 'A title of 1 to 200 characters is required'});
                    }
                    const task = {id: nextId++, title: title.trim()};
                    tasks.set(task.id, task);
                    res.status(201).location('/tasks/' + task.id).json(task);
                });
                app.use((error, req, res, next) => res.status(400).json({error: 'Invalid JSON request'}));
                app.listen(3000, '127.0.0.1', () => console.log('Task API on port 3000'));
            """.trimIndent(),
            SupportedLanguage.GO to """
                package main

                import (
                    "encoding/json"
                    "log"
                    "net/http"
                    "sync"
                )

                type Task struct {
                    ID    int    `json:"id"`
                    Title string `json:"title"`
                }

                func main() {
                    var mu sync.Mutex
                    tasks := []Task{}
                    nextID := 1
                    http.HandleFunc("/tasks", func(w http.ResponseWriter, r *http.Request) {
                        w.Header().Set("Content-Type", "application/json")
                        mu.Lock()
                        defer mu.Unlock()
                        switch r.Method {
                        case http.MethodGet:
                            if err := json.NewEncoder(w).Encode(tasks); err != nil {
                                log.Println(err)
                            }
                        case http.MethodPost:
                            var input struct { Title string `json:"title"` }
                            decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, 16384))
                            decoder.DisallowUnknownFields()
                            if err := decoder.Decode(&input); err != nil || input.Title == "" || len(input.Title) > 200 {
                                http.Error(w, "Invalid task", http.StatusBadRequest)
                                return
                            }
                            task := Task{ID: nextID, Title: input.Title}
                            nextID++
                            tasks = append(tasks, task)
                            w.WriteHeader(http.StatusCreated)
                            if err := json.NewEncoder(w).Encode(task); err != nil {
                                log.Println(err)
                            }
                        default:
                            w.Header().Set("Allow", "GET, POST")
                            http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
                        }
                    })
                    log.Fatal(http.ListenAndServe("127.0.0.1:8080", nil))
                }
            """.trimIndent()),
        recipe("HTTP client", "HTTP example with a timeout. This generated code needs network access when you run it.", "\\bfetch\\b|http client|http request|\\brequest\\b",
            SupportedLanguage.PYTHON to """
                import json
                from urllib.request import Request, urlopen

                def get_json(url: str) -> dict:
                    request = Request(url, headers={"Accept": "application/json"})
                    with urlopen(request, timeout=10) as response:
                        return json.load(response)

                if __name__ == "__main__":
                    print(get_json("https://example.com/api/data"))
            """.trimIndent(),
            SupportedLanguage.JAVA to """
                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.time.Duration;

                public class Main {
                    public static void main(String[] args) throws Exception {
                        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com/api/data"))
                            .timeout(Duration.ofSeconds(10)).GET().build();
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            throw new IllegalStateException("HTTP " + response.statusCode());
                        }
                        System.out.println(response.body());
                    }
                }
            """.trimIndent(),
            SupportedLanguage.KOTLIN to """
                import java.net.URI
                import java.net.http.HttpClient
                import java.net.http.HttpRequest
                import java.net.http.HttpResponse
                import java.time.Duration

                fun main() {
                    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    val request = HttpRequest.newBuilder(URI.create("https://example.com/api/data"))
                        .timeout(Duration.ofSeconds(10)).GET().build()
                    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                    check(response.statusCode() in 200..299) { "HTTP error" }
                    println(response.body())
                }
            """.trimIndent(),
            SupportedLanguage.JAVASCRIPT to """
                async function getJSON(url) {
                    const response = await fetch(url, {signal: AbortSignal.timeout(10000)});
                    if (!response.ok) throw new Error('HTTP ' + response.status);
                    return response.json();
                }

                getJSON('https://example.com/api/data').then(console.log).catch(console.error);
            """.trimIndent(),
            SupportedLanguage.GO to """
                package main

                import (
                    "fmt"
                    "io"
                    "net/http"
                    "time"
                )

                func main() {
                    client := &http.Client{Timeout: 10 * time.Second}
                    response, err := client.Get("https://example.com/api/data")
                    if err != nil {
                        panic(err)
                    }
                    defer response.Body.Close()
                    if response.StatusCode < 200 || response.StatusCode >= 300 {
                        panic(fmt.Sprintf("HTTP %d", response.StatusCode))
                    }
                    body, err := io.ReadAll(io.LimitReader(response.Body, 1<<20))
                    if err != nil {
                        panic(err)
                    }
                    fmt.Println(string(body))
                }
            """.trimIndent(),
            SupportedLanguage.C_SHARP to """
                using System;
                using System.Net.Http;
                using System.Threading.Tasks;

                public static class Program {
                    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(10) };

                    public static async Task Main() {
                        using var response = await Client.GetAsync("https://example.com/api/data");
                        response.EnsureSuccessStatusCode();
                        Console.WriteLine(await response.Content.ReadAsStringAsync());
                    }
                }
            """.trimIndent(),
            SupportedLanguage.TYPESCRIPT to """
                async function getJSON(url: string): Promise<unknown> {
                    const response = await fetch(url, {signal: AbortSignal.timeout(10000)});
                    if (!response.ok) throw new Error('HTTP ' + response.status);
                    return response.json();
                }

                getJSON('https://example.com/api/data').then(console.log).catch(console.error);
            """.trimIndent()),
        recipe("File I/O", "Explicit encoding and scoped file access. Replace the example path before running.", "\\bfile\\b|i/o|\\bread\\b|\\bwrite\\b|\\bjson\\b",
            SupportedLanguage.PYTHON to """
                from pathlib import Path

                def read_text(path: str) -> str:
                    return Path(path).read_text(encoding="utf-8")

                def write_text(path: str, content: str) -> None:
                    Path(path).write_text(content, encoding="utf-8")

                if __name__ == "__main__":
                    write_text("example.txt", "Created offline")
                    print(read_text("example.txt"))
            """.trimIndent(),
            SupportedLanguage.JAVA to """
                import java.nio.charset.StandardCharsets;
                import java.nio.file.Files;
                import java.nio.file.Path;

                public class Main {
                    public static void main(String[] args) throws Exception {
                        Path path = Path.of("example.txt");
                        Files.writeString(path, "Created offline", StandardCharsets.UTF_8);
                        System.out.println(Files.readString(path, StandardCharsets.UTF_8));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.KOTLIN to """
                import java.nio.charset.StandardCharsets
                import java.nio.file.Files
                import java.nio.file.Path

                fun main() {
                    val path = Path.of("example.txt")
                    Files.writeString(path, "Created offline", StandardCharsets.UTF_8)
                    println(Files.readString(path, StandardCharsets.UTF_8))
                }
            """.trimIndent(),
            SupportedLanguage.JAVASCRIPT to """
                import {readFile, writeFile} from 'node:fs/promises';

                await writeFile('example.txt', 'Created offline', 'utf8');
                console.log(await readFile('example.txt', 'utf8'));
            """.trimIndent(),
            SupportedLanguage.CPP to """
                #include <fstream>
                #include <iostream>
                #include <stdexcept>
                #include <string>

                int main() {
                    {
                        std::ofstream output("example.txt");
                        if (!output) throw std::runtime_error("Cannot open output file");
                        output << "Created offline";
                        if (!output) throw std::runtime_error("Cannot write file");
                    }
                    std::ifstream input("example.txt");
                    if (!input) throw std::runtime_error("Cannot open input file");
                    std::string line;
                    while (std::getline(input, line)) std::cout << line << '\n';
                }
            """.trimIndent(),
            SupportedLanguage.GO to """
                package main

                import (
                    "fmt"
                    "os"
                )

                func main() {
                    if err := os.WriteFile("example.txt", []byte("Created offline"), 0600); err != nil {
                        panic(err)
                    }
                    content, err := os.ReadFile("example.txt")
                    if err != nil {
                        panic(err)
                    }
                    fmt.Println(string(content))
                }
            """.trimIndent(),
            SupportedLanguage.RUST to """
                use std::fs;
                use std::io;

                fn main() -> io::Result<()> {
                    fs::write("example.txt", "Created offline")?;
                    let content = fs::read_to_string("example.txt")?;
                    println!("{}", content);
                    Ok(())
                }
            """.trimIndent(),
            SupportedLanguage.C_SHARP to """
                using System;
                using System.IO;

                public static class Program {
                    public static void Main() {
                        File.WriteAllText("example.txt", "Created offline");
                        Console.WriteLine(File.ReadAllText("example.txt"));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.TYPESCRIPT to """
                import {readFile, writeFile} from 'node:fs/promises';

                async function main(): Promise<void> {
                    await writeFile('example.txt', 'Created offline', 'utf8');
                    console.log(await readFile('example.txt', 'utf8'));
                }

                main().catch(console.error);
            """.trimIndent()),
        recipe("Concurrent / async tasks", "Bounded workers or asynchronous tasks with shared-state protection where needed. JavaScript and C# examples use async tasks, not native thread pools.", "thread|coroutine|async|pool|worker|mutex",
            SupportedLanguage.PYTHON to """
                from concurrent.futures import ThreadPoolExecutor

                def square(value: int) -> int:
                    return value * value

                if __name__ == "__main__":
                    with ThreadPoolExecutor(max_workers=4) as executor:
                        results = list(executor.map(square, range(10)))
                    print(results)
            """.trimIndent(),
            SupportedLanguage.JAVA to """
                import java.util.List;
                import java.util.concurrent.Callable;
                import java.util.concurrent.ExecutorService;
                import java.util.concurrent.Executors;
                import java.util.concurrent.Future;
                import java.util.stream.IntStream;

                public class Main {
                    public static void main(String[] args) throws Exception {
                        ExecutorService executor = Executors.newFixedThreadPool(4);
                        try {
                            List<Callable<Integer>> tasks = IntStream.range(0, 10)
                                .mapToObj(value -> (Callable<Integer>) () -> value * value).toList();
                            for (Future<Integer> result : executor.invokeAll(tasks)) {
                                System.out.println(result.get());
                            }
                        } finally {
                            executor.shutdown();
                        }
                    }
                }
            """.trimIndent(),
            SupportedLanguage.KOTLIN to """
                import kotlinx.coroutines.async
                import kotlinx.coroutines.awaitAll
                import kotlinx.coroutines.coroutineScope

                suspend fun main() = coroutineScope {
                    val results = (0 until 10).map { value ->
                        async { value * value }
                    }.awaitAll()
                    println(results)
                }
            """.trimIndent(),
            SupportedLanguage.JAVASCRIPT to """
                async function runTasks(values, concurrency = 4) {
                    if (!Number.isInteger(concurrency) || concurrency < 1) throw new RangeError('Invalid concurrency');
                    const results = new Array(values.length);
                    let next = 0;
                    async function worker() {
                        while (next < values.length) {
                            const index = next++;
                            results[index] = await Promise.resolve(values[index] * values[index]);
                        }
                    }
                    await Promise.all(Array.from({length: Math.min(concurrency, values.length)}, worker));
                    return results;
                }

                runTasks(Array.from({length: 10}, (_, i) => i)).then(console.log).catch(console.error);
            """.trimIndent(),
            SupportedLanguage.CPP to """
                #include <iostream>
                #include <mutex>
                #include <thread>
                #include <vector>

                int main() {
                    int counter = 0;
                    std::mutex mutex;
                    std::vector<std::thread> workers;
                    for (int i = 0; i < 4; ++i) {
                        workers.emplace_back([&] {
                            for (int j = 0; j < 1000; ++j) {
                                std::lock_guard<std::mutex> guard(mutex);
                                ++counter;
                            }
                        });
                    }
                    for (auto& worker : workers) worker.join();
                    std::cout << counter << '\n';
                }
            """.trimIndent(),
            SupportedLanguage.GO to """
                package main

                import (
                    "fmt"
                    "sync"
                )

                func main() {
                    var group sync.WaitGroup
                    var mutex sync.Mutex
                    counter := 0
                    for worker := 0; worker < 4; worker++ {
                        group.Add(1)
                        go func() {
                            defer group.Done()
                            for i := 0; i < 1000; i++ {
                                mutex.Lock()
                                counter++
                                mutex.Unlock()
                            }
                        }()
                    }
                    group.Wait()
                    fmt.Println(counter)
                }
            """.trimIndent(),
            SupportedLanguage.RUST to """
                use std::sync::{Arc, Mutex};
                use std::thread;

                fn main() {
                    let counter = Arc::new(Mutex::new(0));
                    let mut workers = Vec::new();
                    for _ in 0..4 {
                        let counter = Arc::clone(&counter);
                        workers.push(thread::spawn(move || {
                            for _ in 0..1000 {
                                *counter.lock().unwrap() += 1;
                            }
                        }));
                    }
                    for worker in workers {
                        worker.join().unwrap();
                    }
                    println!("{}", *counter.lock().unwrap());
                }
            """.trimIndent(),
            SupportedLanguage.C_SHARP to """
                using System;
                using System.Linq;
                using System.Threading.Tasks;

                public static class Program {
                    public static async Task Main() {
                        var tasks = Enumerable.Range(0, 10).Select(async value => {
                            await Task.Yield();
                            return value * value;
                        });
                        var results = await Task.WhenAll(tasks);
                        Console.WriteLine(string.Join(", ", results));
                    }
                }
            """.trimIndent(),
            SupportedLanguage.TYPESCRIPT to """
                async function square(value: number): Promise<number> {
                    await Promise.resolve();
                    return value * value;
                }

                async function main(): Promise<void> {
                    const results = await Promise.all(Array.from({length: 10}, (_, i) => square(i)));
                    console.log(results);
                }

                main().catch(console.error);
            """.trimIndent())
    ) + sqlRecipes

    private fun starter(language: SupportedLanguage, prompt: String): String = when (language) {
        SupportedLanguage.PYTHON -> """
            # Requested: $prompt
            # No bundled recipe matched. Add your application logic here.
            def main() -> None:
                print("Hello from your Python starter")

            if __name__ == "__main__":
                main()
        """.trimIndent()
        SupportedLanguage.HTML_CSS -> """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Offline Starter</title>
              <style>body { padding: 24px; background: #0f172a; color: #f8fafc; font-family: system-ui, sans-serif; }</style>
            </head>
            <body>
              <!-- Requested: $prompt -->
              <h1>Your offline starter</h1>
              <p>No bundled recipe matched. Replace this page with your application logic.</p>
            </body>
            </html>
        """.trimIndent()
        SupportedLanguage.JAVA -> """
            // Requested: $prompt
            // No bundled recipe matched. Add your application logic here.
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello from your Java starter");
                }
            }
        """.trimIndent()
        SupportedLanguage.KOTLIN -> """
            // Requested: $prompt
            // No bundled recipe matched. Add your application logic here.
            fun main() {
                println("Hello from your Kotlin starter")
            }
        """.trimIndent()
        SupportedLanguage.JAVASCRIPT -> """
            // Requested: $prompt
            // No bundled recipe matched. Add your application logic here.
            function main() {
                console.log('Hello from your JavaScript starter');
            }
            main();
        """.trimIndent()
        SupportedLanguage.CPP -> """
            // Requested: $prompt
            // No bundled recipe matched. Add your application logic here.
            #include <iostream>

            int main() {
                std::cout << "Hello from your C++ starter" << '\n';
                return 0;
            }
        """.trimIndent()
        SupportedLanguage.SQL -> """
            -- Requested: $prompt
            -- No bundled recipe matched. Add and review your database logic here.
            BEGIN;
            CREATE TABLE example (id INTEGER PRIMARY KEY, label VARCHAR(100) NOT NULL);
            INSERT INTO example (id, label) VALUES (1, 'Created offline');
            SELECT id, label FROM example;
            COMMIT;
        """.trimIndent()
        SupportedLanguage.GO -> """
            // Requested: $prompt
            // No bundled recipe matched. Add your application logic here.
            package main

            import "fmt"

            func main() {
                fmt.Println("Hello from your Go starter")
            }
        """.trimIndent()
        SupportedLanguage.RUST -> """
            // Requested: $prompt
            // No bundled recipe matched. Add your application logic here.
            fn main() {
                println!("Hello from your Rust starter");
            }
        """.trimIndent()
        SupportedLanguage.C_SHARP -> """
            // Requested: $prompt
            // No bundled recipe matched. Add your application logic here.
            using System;

            public static class Program {
                public static void Main() {
                    Console.WriteLine("Hello from your C# starter");
                }
            }
        """.trimIndent()
        SupportedLanguage.TYPESCRIPT -> """
            // Requested: $prompt
            // No bundled recipe matched. Add your application logic here.
            function main(): void {
                console.log('Hello from your TypeScript starter');
            }
            main();
        """.trimIndent()
    }
}
