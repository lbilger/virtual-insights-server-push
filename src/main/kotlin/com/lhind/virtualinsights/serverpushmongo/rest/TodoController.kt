package com.lhind.virtualinsights.serverpushmongo.rest

import com.lhind.virtualinsights.serverpushmongo.repository.Todo
import com.lhind.virtualinsights.serverpushmongo.repository.TodoChangeTracker
import com.lhind.virtualinsights.serverpushmongo.repository.TodoRepository
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.UUID.randomUUID

@RestController
@RequestMapping("todo")
class TodoController(private val repository: TodoRepository, private val todoChangeTracker: TodoChangeTracker) {
    @PostMapping
    fun createTodo(@RequestBody eventualTodo: Mono<Todo>): Mono<Todo> {
        return eventualTodo
            .map { it.copy(id = randomUUID()) }
            .flatMap { repository.save(it) }
    }

    @GetMapping(produces = ["application/json"])
    fun findAllTodos(): Flux<Todo> {
        return repository.findAll()
    }

    @GetMapping(produces = ["text/event-stream"])
    fun findAllTodosStream(): Flux<Todo> {
        return findAllTodos().concatWith(todoChangeTracker.changeStream)
    }

    @PutMapping("{id}")
    fun updateTodo(@PathVariable("id") id: UUID, @RequestBody eventualTodo: Mono<Todo>): Mono<ResponseEntity<Todo>> {
        return repository.findById(id)
            .flatMap { eventualTodo }
            .map { it.copy(id = id) }
            .flatMap { repository.save(it) }
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())
    }

    @DeleteMapping("{id}")
    fun deleteTodo(@PathVariable("id") id: UUID): Mono<ResponseEntity<Void>> {
        return repository.findById(id)
            .map { it.copy(deleted = true) }
            .flatMap { repository.save(it).thenReturn(ResponseEntity.status(NO_CONTENT).build<Void>()) }
            .defaultIfEmpty(ResponseEntity.notFound().build())
    }
}
