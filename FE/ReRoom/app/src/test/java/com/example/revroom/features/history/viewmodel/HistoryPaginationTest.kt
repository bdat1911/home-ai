package com.example.revroom.features.history.viewmodel

import com.example.revroom.features.history.model.ProjectModel
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPaginationTest {
    @Test
    fun overlappingPagesUpdateStatusWithoutDuplicatingOrReorderingProjects() {
        val newest = project("newest", "completed")
        val pending = project("overlap", "pending")
        val completed = pending.copy(status = "completed", designedImageUrl = "generated.png")
        val older = project("older", "failed")

        val result = mergeProjectPages(listOf(newest, pending), listOf(completed, older))

        assertEquals(listOf(newest, completed, older), result)
        assertEquals("pending", pending.status)
    }

    @Test
    fun repeatedPageDoesNotAddDuplicateCards() {
        val projects = listOf(project("first", "completed"), project("second", "pending"))

        assertEquals(projects, mergeProjectPages(projects, projects))
        assertEquals(projects, mergeProjectPages(emptyList(), projects + projects))
    }

    @Test
    fun missingIdsDoNotCollapseUnrelatedProjects() {
        val first = project(null, "pending")
        val second = project(null, "failed")
        val blank = project("", "processing")

        assertEquals(listOf(first, second, blank), mergeProjectPages(listOf(first), listOf(second, blank)))
    }

    private fun project(id: String?, status: String) = ProjectModel(
        designId = id,
        originalImageUrl = "original.png",
        designedImageUrl = null,
        status = status,
        createdAt = null
    )
}
