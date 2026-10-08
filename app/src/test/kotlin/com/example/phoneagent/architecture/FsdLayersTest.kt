package com.example.phoneagent.architecture

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

class FsdLayersTest {
    private val layersTopDown = listOf("screens", "widgets", "features", "entities", "shared")
    private val layerFiles =
        Konsist
            .scopeFromProject()
            .files
            .filter { file ->
                layersTopDown.any { layer -> "/$layer/src/" in file.path } &&
                    listOf("/src/commonMain/", "/src/androidMain/", "/src/main/").any { it in file.path }
            }
    private val commonMainFiles = layerFiles.filter { "/src/commonMain/" in it.path }

    @Test
    fun `FSD source files do not import from higher layers`() {
        val violations =
            layerFiles.flatMap { file ->
                val sourceLayer = layersTopDown.firstOrNull { "/$it/src/" in file.path }
                if (sourceLayer == null) {
                    emptyList()
                } else {
                    val sourceIndex = layersTopDown.indexOf(sourceLayer)
                    file.imports
                        .filter { imported ->
                            layersTopDown.take(sourceIndex).any { layer ->
                                imported.name.isPackageOrChild("com.example.phoneagent.$layer")
                            }
                        }.map { imported -> "${file.path}: ${imported.name}" }
                }
            }

        assertTrue("FSD layers must only depend downward: $violations", violations.isEmpty())
    }

    @Test
    fun `feature slices do not import from sibling slices`() {
        val violations =
            layerFiles
                .filter { "/features/src/" in it.path }
                .flatMap { file ->
                    val ownSlice = file.packagee?.name?.featureSlice()
                    if (ownSlice == null) {
                        emptyList()
                    } else {
                        file.imports
                            .filter { imported ->
                                val importedSlice = imported.name.featureSlice()
                                importedSlice != null && importedSlice != ownSlice
                            }.map { imported -> "${file.path}: ${imported.name}" }
                    }
                }

        assertTrue("Feature slices must not depend on sibling slices: $violations", violations.isEmpty())
    }

    @Test
    fun `commonMain does not import platform-only packages`() {
        val forbiddenRoots = listOf("android", "androidx", "java")
        val violations =
            commonMainFiles.flatMap { file ->
                file.imports
                    .filter { imported -> forbiddenRoots.any { root -> imported.name.isPackageOrChild(root) } }
                    .map { imported -> "${file.path}: ${imported.name}" }
            }

        assertTrue("commonMain must remain platform independent: $violations", violations.isEmpty())
    }

    private fun String.isPackageOrChild(packageName: String): Boolean = this == packageName || startsWith("$packageName.")

    private fun String.featureSlice(): String? {
        val prefix = "com.example.phoneagent.features."
        if (!startsWith(prefix)) return null
        return removePrefix(prefix).substringBefore('.')
    }
}
