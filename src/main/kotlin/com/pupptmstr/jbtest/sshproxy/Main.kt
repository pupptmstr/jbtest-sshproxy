package com.pupptmstr.jbtest.sshproxy

import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException


fun main(args: Array<String>) {
    when (restoreArguments(args.toList())) {
        WorkType.SERVER_DEFAULT -> {
            startSocketServer(WorkType.SERVER_DEFAULT.port)
        }
        WorkType.CLIENT_DEFAULT -> {
            startClient(WorkType.CLIENT_DEFAULT.host, WorkType.CLIENT_DEFAULT.port)
        }
        WorkType.SERVER_ARGUMENT -> {
            startSocketServer(getPort(args.toList()))
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

fun startSocketServer(port: Int) {
    try {
        ServerSocket(port).use { server ->
            println("Server is running on port: ${server.localPort}")
            while (true) {
                val sender = server.accept() // подключаю клиентов
                if (sender.isConnected) { // если клиент законнектился, то стартую свой тред
                    println("Client connected: ${sender.isConnected} -> ${sender.inetAddress.hostAddress}:${sender.localPort}")
                    val byteArray = ByteArray(9)
                    sender.getInputStream().read(byteArray, 0, 9).toString()
                    val message = String(byteArray).replace("\u0000", "") // делаем из массива байтов стрингу и убираем остальной мусор
                    println("Server get message: [$message]")
                    ClientThread(message, sender)

                }
            }
        }
        println("Server was closed")
    } catch (e: SocketException) {
        println("Error: server was not closed or there was another problem")
        e.printStackTrace()
    } // если сокет не создался или что-то пошло не так принчу стактрейс
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
    val socket = Socket(host, port)
    val sender = socket.getOutputStream()
    val receiver = socket.getInputStream()
    var stillWorking = true
    while (stillWorking) {
        println("Enter the number of the Fibonacci number or \'q\' to exit.")
        print("Your input: ")
        val userInput = readLine()
        if (userInput == null) {
            break
        } else if (userInput.toLowerCase() == "q") {
            sender.write("EXIT".toByteArray())
            stillWorking = false
        } else {
            try {
                userInput.toInt()
                sender.write(userInput.toByteArray())
                var res = ""
                var exitCondition = false
                while (!exitCondition) {
                    if (socket.isConnected) {
                        val receiverAvailable = receiver.available()
                        if (receiverAvailable > 0) {
                            res = receiver.reader().readText()
                            exitCondition = true
                        }
                    }
                }
                println("Answer is $res")
            } catch (e: NumberFormatException) {
                println("Error while parsing number, try again.")
            } catch (e: Exception) {
                println("Error while reading server answer, try again.")
                e.printStackTrace()
            }

        }
    }
    try {
        socket.close()
        println("Bye!")
    } catch (e: SocketException) {
        println("ERROR! Socket wasn't closed!")
        e.printStackTrace()
    }
}

enum class WorkType(val host: String, val port: Int) {
    SERVER_DEFAULT("localhost", 8081),
    CLIENT_DEFAULT("localhost", 8081),
    SERVER_ARGUMENT("", 0),
    CLIENT_ARGUMENT("", 0),
    HELP("", 0),
}

class ClientThread(private val message: String, private val socket: Socket): Thread() {
    init { this.start() } // стартую тред при инициализации
    override fun run() {
        val outputStream = socket.getOutputStream()
        println("New Thread created, id: ${this.id}")
        if (message == "EXIT") {
            try {
                socket.close() // закрываем сокет
                outputStream.close()
                println("Socket(sender) was closed: ${socket.isClosed} ")
            } catch (e: SocketException) {
                println("Error: socket was not closed")
                e.printStackTrace()
            }
        } else {
            val answer = fibNum(message.toInt()).toString()
            if (socket.isConnected) { // проверяем есть ли коннект
                outputStream.write(answer.toByteArray()) // создаем сообщение и отправляем в стрим
                outputStream.flush()
            }
        }
    }
}
