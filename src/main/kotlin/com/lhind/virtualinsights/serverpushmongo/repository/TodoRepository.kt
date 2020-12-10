package com.lhind.virtualinsights.serverpushmongo.repository

import org.bson.BsonValue
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.ChangeStreamEvent
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.changeStream
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import java.util.*

interface TodoRepository : ReactiveMongoRepository<Todo, UUID>, TodoRepositoryExtension

interface TodoRepositoryExtension {
    fun getTodoUpdates(resumeToken: BsonValue? = null): Flux<ChangeStreamEvent<Todo>>

}

open class TodoRepositoryExtensionImpl(private val mongoTemplate: ReactiveMongoTemplate) : TodoRepositoryExtension {
    override fun getTodoUpdates(resumeToken: BsonValue?): Flux<ChangeStreamEvent<Todo>> {
        return mongoTemplate.changeStream<Todo>()
            .withOptions { if (resumeToken != null) it.resumeToken(resumeToken) }
            .watchCollection(Todo::class.java).listen()
    }
}

@Document(collection = "todos")
data class Todo(
    @Id
    val id: UUID = UUID.randomUUID(),
    val text: String,
    val assignee: String,
    val completed: Boolean = false,
    val deleted: Boolean = false
)
