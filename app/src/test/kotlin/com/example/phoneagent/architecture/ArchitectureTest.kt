package com.example.phoneagent.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureTest {
    private val productionFiles =
        Konsist
            .scopeFromProject()
            .files
            .filter { "/src/main/" in it.path }

    @Test
    fun `production code does not use blocking or unstructured concurrency`() {
        val violations =
            productionFiles.filter { file ->
                file.imports.any {
                    it.name == "kotlinx.coroutines.GlobalScope" ||
                        it.name == "kotlinx.coroutines.runBlocking" ||
                        it.name == "java.lang.Thread.sleep"
                }
            }

        assertTrue("Forbidden concurrency in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `production code uses AppLogger instead of Android Log`() {
        val violations = productionFiles.filter { file -> file.imports.any { it.name == "android.util.Log" } }

        assertTrue("Direct Android Log in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `pure JVM modules do not import platform or dependency injection libraries`() {
        val forbiddenPrefixes = listOf("android.", "androidx.", "okhttp3.", "dagger.", "com.example.phoneagent.data.")
        val violations =
            productionFiles
                .filter { "/core/domain/" in it.path || "/data/llm/" in it.path }
                .filter { file -> file.imports.any { import -> forbiddenPrefixes.any(import.name::startsWith) } }

        assertTrue("Forbidden pure JVM imports in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `ui does not import adapter implementations`() {
        val violations =
            productionFiles
                .filter { "/app/" in it.path && "/ui/" in it.path }
                .filter { file ->
                    file.imports.any {
                        it.name.startsWith("com.example.phoneagent.data.") ||
                            it.name.startsWith("com.example.phoneagent.tools.")
                    }
                }

        assertTrue("UI imports adapters in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `design system does not import screens`() {
        val violations =
            productionFiles
                .filter { "/app/" in it.path && "/ui/designsystem/" in it.path }
                .filter { file -> file.imports.any { ".ui.chat." in it.name || ".ui.navigation." in it.name } }

        assertTrue("Design system imports screens in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `ui color literals live only in theme and Text has no literals`() {
        val colorViolations =
            productionFiles
                .filter { "/app/" in it.path && "/ui/" in it.path && "/designsystem/theme/" !in it.path }
                .filter { "Color(0x" in it.text }
        val textViolations =
            productionFiles
                .filter { "/app/" in it.path && "/ui/" in it.path }
                .filter { Regex("Text\\s*\\(\\s*\"").containsMatchIn(it.text) }

        assertTrue("Color literals outside theme: ${colorViolations.map { it.path }}", colorViolations.isEmpty())
        assertTrue("Text literals in UI: ${textViolations.map { it.path }}", textViolations.isEmpty())
    }

    @Test
    fun `view models do not retain Android framework types`() {
        val forbiddenImports = listOf("android.app.Activity", "android.content.Context", "android.view.View")
        val violations =
            productionFiles
                .filter { it.name.endsWith("ViewModel.kt") }
                .filter { file -> file.imports.any { it.name in forbiddenImports } }

        assertTrue("Android types in ViewModels: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `tool packages do not import sibling tools`() {
        val toolsRoot = "/tools/src/main/kotlin/com/example/phoneagent/tools/"
        val violations =
            productionFiles
                .filter { toolsRoot in it.path }
                .filter { file ->
                    val ownPackage = file.path.substringAfter(toolsRoot).substringBefore('/')
                    file.imports.any { import ->
                        val importedPackage =
                            import.name
                                .removePrefix("com.example.phoneagent.tools.")
                                .substringBefore('.')
                        import.name.startsWith("com.example.phoneagent.tools.") && importedPackage != ownPackage
                    }
                }

        assertTrue("Cross-tool imports in: ${violations.map { it.path }}", violations.isEmpty())
    }

    @Test
    fun `tool implementations live in tools packages and use Tool suffix`() {
        val implementationPattern = Regex("class\\s+(\\w+)\\s*[^\\n]*:\\s*[^\\n]*\\bTool\\b")
        val violations =
            productionFiles.flatMap { file ->
                implementationPattern.findAll(file.text).mapNotNull { match ->
                    val className = match.groupValues[1]
                    if ("/tools/src/main/" !in file.path || !className.endsWith("Tool")) file.path else null
                }
            }

        assertTrue("Invalid Tool implementations in: $violations", violations.isEmpty())
    }
}
