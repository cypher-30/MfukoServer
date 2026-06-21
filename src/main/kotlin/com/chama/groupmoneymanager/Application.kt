package com.chama.groupmoneymanager

import com.chama.groupmoneymanager.db.DatabaseFactory
import com.chama.groupmoneymanager.plugins.configureRouting
import com.chama.groupmoneymanager.plugins.configureSecurity // <-- Make sure this import is here
import com.chama.groupmoneymanager.plugins.configureSerialization
import io.ktor.server.application.*

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(environment.config)
    configureSerialization()
    configureSecurity()
    configureRouting()
}