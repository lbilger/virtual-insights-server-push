package com.lhind.virtualinsights.serverpushmongo.repository

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import java.util.*

interface TodoRepository : ReactiveMongoRepository<Todo, UUID>

@Document(collection = "todos")
data class Todo(
    @Id
    val id: UUID = UUID.randomUUID(),
    val text: String,
    val assignee: String,
    val completed: Boolean = false,
    val deleted: Boolean = false
)
