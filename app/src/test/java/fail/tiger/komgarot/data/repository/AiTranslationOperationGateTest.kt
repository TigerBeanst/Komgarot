package fail.tiger.komgarot.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTranslationOperationGateTest {
    @Test
    fun activeJobStateTracksRunningBookOperation() = runBlocking {
        val gate = AiTranslationOperationGate()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val operation = launch {
            gate.runBookOperation("book") {
                started.complete(Unit)
                finish.await()
            }
        }

        started.await()
        assertTrue(gate.hasActiveJobs("book"))

        finish.complete(Unit)
        operation.join()
        assertFalse(gate.hasActiveJobs("book"))
    }

    @Test
    fun clearCancelsActiveOperationBeforeDeleting() = runBlocking {
        val gate = AiTranslationOperationGate()
        val started = CompletableDeferred<Long>()
        var operationFinished = false
        var deletedAfterFinish = false
        val operation = launch {
            gate.runBookOperation("book") { generation ->
                started.complete(generation)
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    operationFinished = true
                }
            }
        }
        val oldGeneration = started.await()

        gate.clearBook("book") {
            deletedAfterFinish = operationFinished
        }

        operation.join()
        assertTrue(operation.isCancelled)
        assertTrue(deletedAfterFinish)
        assertFalse(gate.isCurrent("book", oldGeneration))
    }

    @Test
    fun operationStartedDuringDeletionRunsAfterDeleteCompletes() = runBlocking {
        val gate = AiTranslationOperationGate()
        val allowDelete = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val deletion = async(Dispatchers.Default) {
            gate.clearBook("book") {
                events += "delete-start"
                runBlocking { allowDelete.await() }
                events += "delete-end"
            }
        }
        yield()
        val operation = async {
            gate.runBookOperation("book") {
                events += "operation"
            }
        }

        allowDelete.complete(Unit)
        deletion.await()
        operation.await()

        assertEquals(listOf("delete-start", "delete-end", "operation"), events)
    }

    @Test
    fun clearCancelsTrackedJobBeforeItStartsWriting() = runBlocking {
        val gate = AiTranslationOperationGate()
        var ran = false
        val job = launch(start = CoroutineStart.LAZY) { ran = true }
        gate.trackBookJob("book", job)

        gate.clearBook("book") {}
        job.start()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(ran)
    }
}
