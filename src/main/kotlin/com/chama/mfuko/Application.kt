package com.chama.mfuko

import com.chama.mfuko.db.DatabaseFactory
import com.chama.mfuko.plugins.configureRouting
import com.chama.mfuko.plugins.configureSecurity // <-- Make sure this import is here
import com.chama.mfuko.plugins.configureSerialization
import io.ktor.server.application.*

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init(environment.config)
    configureSerialization()
    configureSecurity()
    configureRouting()
}