package com.lhind.virtualinsights.serverpushmongo.rest

import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import java.util.*
import java.util.UUID.randomUUID

@RestController
@RequestMapping("todo")
class TodoController {
    val todos: MutableList<Todo> = mutableListOf()

    val emitters: MutableList<FluxSink<Todo>> = mutableListOf()

    val changeFlux = Flux.create<Todo> { emitter ->
        emitters += emitter
        emitter.onDispose { emitters -= emitter }
    }.share()

    @PostMapping
    fun createTodo(@RequestBody eventualTodo: Mono<Todo>): Mono<Todo> {
        return eventualTodo
            .map { it.copy(id = randomUUID()) }
            .doOnNext { todo ->
                todos += todo
                emitters.forEach { it.next(todo) }
            }
    }

    @GetMapping(produces = ["application/json"])
    fun findAllTodos(): Flux<Todo> {
        return Flux.fromIterable(todos)
    }

    @GetMapping(produces = ["text/event-stream"])
    fun findAllTodosStream(): Flux<Todo> {
        return Flux.fromIterable(todos).concatWith(changeFlux)
    }

    @PutMapping("{id}")
    fun updateTodo(@PathVariable("id") id: UUID, @RequestBody eventualTodo: Mono<Todo>): Mono<ResponseEntity<Todo>> {
        val existingTodo = todos.find { it.id == id } ?: return Mono.just(ResponseEntity.notFound().build())
        return eventualTodo
            .map { it.copy(id = id) }
            .map { todo ->
                todos -= existingTodo
                todos += todo
                emitters.forEach { it.next(todo) }
                ResponseEntity.ok(todo)
            }
    }

    @DeleteMapping("{id}")
    fun deleteTodo(@PathVariable("id") id: UUID): Mono<ResponseEntity<Void>> {
        val existingTodo = todos.find { it.id == id } ?: return Mono.just(ResponseEntity.notFound().build())
        todos -= existingTodo
        emitters.forEach { it.next(existingTodo.copy(deleted = true)) }
        return Mono.just(ResponseEntity.status(NO_CONTENT).build())
    }
}

data class Todo(val id: UUID = randomUUID(), val text: String, val assignee: String, val completed: Boolean = false, val deleted: Boolean = false)