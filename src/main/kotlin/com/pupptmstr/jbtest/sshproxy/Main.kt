package com.pupptmstr.jbtest.sshproxy

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.Executors


fun main(args: Array<String>) {
    when (restoreArguments(args.toList())) {
        WorkType.SERVER_DEFAULT -> {
            startServer(WorkType.SERVER_DEFAULT.port)
        }
        WorkType.CLIENT_DEFAULT -> {
            startClient(WorkType.CLIENT_DEFAULT.host, WorkType.CLIENT_DEFAULT.port)
        }
        WorkType.SERVER_ARGUMENT -> {
            startServer(getPort(args.toList()))
        }
        WorkType.CLIENT_ARGUMENT -> {
            startClient(getHost(args.toList()), getPort(args.toList()))
        }
        WorkType.HELP -> {
            printHelp()
        }
    }
}

fun restoreArguments(args: List<String>): WorkType {
    return if (args.isEmpty()) {
        WorkType.SERVER_DEFAULT
    } else if (args.contains("-help")) {
        WorkType.HELP
    } else if (args.contains("-c")) {
        if (args.size == 1) {
            WorkType.CLIENT_DEFAULT
        } else {
            WorkType.CLIENT_ARGUMENT
        }
    } else {
        WorkType.SERVER_ARGUMENT
    }

}

fun getHost(args: List<String>): String {
    val pointer = args.indexOf("-h")
    return if (pointer < 0) {
        "localhost"
    } else if (args.size > pointer + 1 && !args[pointer + 1].startsWith("-")) {
        args[pointer + 1]
    } else {
        "localhost"
    }
}

fun getPort(args: List<String>): Int {
    val pointer = args.indexOf("-p")
    return if (pointer < 0) {
        8081
    } else if (args.size > pointer + 1 && !args[pointer + 1].startsWith("-")) {
        args[pointer + 1].toInt()
    } else {
        8081
    }
}

fun printHelp() {
    println(
        "Дефолтно запуск без аргументов - запуск сервера с дефолтным хостом и портом\n" +
                "\n" +
                "-с - запуск клиента с дефолтным хостом и портом\n" +
                "\n" +
                "-p - указание порта\n" +
                "\n" +
                "-h - указание хоста\n" +
                "\n" +
                "-help - вывод help меню\n" +
                "\n" +
                "При отсутствии ключа для порта или ключа запускаются на дефолтных"
    )
}

fun startServer(port: Int) {
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.createContext("/", Server())
    server.start()
    println("server started")
    var isWorking = true
    while (isWorking) {
        val command = readLine()
        if (command?.toLowerCase() == "stop" || command?.toLowerCase() == "quit") {
            server.stop(0)
            println("server is stopping")
            isWorking = false
        }
    }
    println("server stopped")
}

fun fibNum(num: Int): Long {
    if (num in 1..2) return 1
    var prev: Long = 1
    var next: Long = 1
    for (i in 0 until num - 2) {
        next += prev
        prev = next - prev
    }
    return next
}

fun startClient(host: String, port: Int) {
    var stillWorking = true
    while (stillWorking) {
        println("Enter the number of the Fibonacci number or \'q\' to exit.")
        print("Your input: ")
        val userInput = readLine()
        if (userInput!!.toLowerCase() == "q") {
            stillWorking = false
        } else {
            try {
                val res = sendRequest(host, port, userInput.toInt())
                println("Answer is $res")
            } catch (e: NumberFormatException) {
                println("Error while parsing number, try again.")
            } catch (e: Exception) {
                println("Error while reading server answer, try again.")
                e.printStackTrace()
            }

        }
    }
}

fun sendRequest(host: String, port: Int, number: Int): String {
    val request = HttpRequest.newBuilder()
        .uri(URI("http", "", host, port, "", "", ""))
        .POST(HttpRequest.BodyPublishers.ofString(number.toString()))
        .build()
    val response: HttpResponse<String> = HttpClient.newBuilder()
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString())
    return response.body()
}

enum class WorkType(val host: String, val port: Int) {
    SERVER_DEFAULT("localhost", 8081),
    CLIENT_DEFAULT("localhost", 8081),
    SERVER_ARGUMENT("", 0),
    CLIENT_ARGUMENT("", 0),
    HELP("", 0),
}

class Server() : HttpHandler {
    private val executor = Executors.newFixedThreadPool(2)
    override fun handle(exchange: HttpExchange?) {
        executor.execute(ClientHandler(exchange!!))
    }
}

class ClientHandler(private val exchange: HttpExchange) : Runnable {
    override fun run() {
        val inputStream: InputStream = exchange.requestBody
        val stringBuilder = StringBuilder()
        BufferedReader(InputStreamReader(inputStream)).lines().forEach {
            stringBuilder.append(it)
        }
        val requestBody = stringBuilder.toString().toInt()
        val res = fibNum(requestBody).toString()
        val outputStream = BufferedOutputStream(exchange.responseBody)
        val resBody = res.toByteArray()
        exchange.responseHeaders.set("Content-Type", "text/plain")
        exchange.sendResponseHeaders(200, resBody.size.toLong())
        outputStream.write(resBody)
        outputStream.flush()
        outputStream.close()
    }

}
