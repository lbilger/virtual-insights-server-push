package com.lhind.virtualinsights.serverpushmongo.repository

import com.mongodb.MongoCommandException
import com.mongodb.client.model.changestream.OperationType
import mu.KLogging
import org.bson.BsonValue
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.SignalType
import reactor.util.retry.Retry
import java.time.Duration
import java.util.logging.Level
import javax.annotation.PreDestroy

@Service
class TodoChangeTracker(
    private val todoRepository: TodoRepository
) {
    companion object : KLogging()

    private var lastSeenResumeToken: BsonValue? = null

    final val changeStream: Flux<Todo> = Flux.defer { todoRepository.getTodoUpdates(lastSeenResumeToken) }
        // When there are no more subscribers to the change stream, the flux is cancelled. When a new subscriber appears, they should not get any past updates.
        .doOnCancel { lastSeenResumeToken = null }
        // Remember the last seen resume token if one is present
        .doOnNext { event -> lastSeenResumeToken = event.resumeToken ?: lastSeenResumeToken }
        // When the resume token is out of date, Mongo will throw an error 'resume of change stream was not possible, as the resume token was not found.'
        // Unfortunately, there is no way to identify exactly this error because error codes and messages vary by Mongo server version.
        // The closest we can get is reacting on any MongoCommandException and resetting the token so that upon the next retry, we start without a token.
        .doOnError(MongoCommandException::class.java) { lastSeenResumeToken = null }
        .filter { event ->
            when (event.operationType) {
                OperationType.INSERT, OperationType.UPDATE, OperationType.REPLACE -> {
                    logger.debug { "Got ${event.operationType?.value} event: $event" }
                    true
                }
                else -> {
                    logger.trace { "Ignoring ${event.operationType?.value} event: $event" }
                    false
                }
            }
        }
        .log(TodoChangeTracker::class.qualifiedName, Level.WARNING, SignalType.ON_ERROR)
        .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofMillis(100)).maxBackoff(Duration.ofSeconds(10)))
        .concatMap { event -> Mono.justOrEmpty(event.body) }
        .share()

    // Truly delete documents that have been marked deleted
    val trulyDeleteChangeStreamSubscription = changeStream
        .filter { it.deleted }
        .concatMap { todo ->
            todoRepository.deleteById(todo.id)
                .doOnSuccess { logger.trace { "Deleted todo ${todo.id} from database." } }
        }
        .subscribe()

    @PreDestroy
    fun clearSubscription() {
        trulyDeleteChangeStreamSubscription.dispose()
    }
}
