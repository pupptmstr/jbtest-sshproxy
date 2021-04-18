package com.pupptmstr.jbtest.sshproxy

import java.io.InputStream
import java.io.OutputStream
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
        val clients = ClientList()
        ServerSocket(port).use { server ->
            println("Server is running on port: ${server.localPort}")
            while (true) {
                val sender = server.accept() // подключаю клиентов
                if (sender.isConnected) {
                    clients.addNewClient(sender)
                }
            }
        }
    } catch (e: SocketException) {
        println("Error: server was not closed or there was another problem")
        e.printStackTrace()
    }
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
                        val byteArray = ByteArray(50)
                        val receiverAvailable = receiver.available()
                        if (receiverAvailable > 0) {
                            receiver.read(byteArray, 0, 9).toString()
                            res = String(byteArray).replace("\u0000", "")
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

class ClientThread(private val id: Int, private val clientList: ClientList) : Thread() {
    init {
        this.start()
    } // стартую тред при инициализации

    override fun run() {
        var isWorking = true
        println("New connection with id [$id]")
        while (isWorking) {
            val byteArray = ByteArray(9)
            val inputStream = clientList.getInputStream(id)
            val outputStream = clientList.getOutputStream(id)
            if (inputStream.available() > 0) {
                inputStream.read(byteArray, 0, 9).toString()
                val message = String(byteArray).replace("\u0000", "")
                println("Get message \'$message\' from client $id")
                if (message == "EXIT") {
                    println("Closing connection with id [$id]")
                    clientList.finishConnection(id)
                    isWorking = false
                } else {
                    var res = ""
                    try {
                        res = fibNum(message.toInt()).toString()
                    } catch (e: java.lang.NumberFormatException) {
                        println(
                            "Error while translating message to Int.\n" +
                                    "clientId: [$id], message: \'$message\'"
                        )
                    }
                    outputStream.write(res.toByteArray())
                    println("Sent res [$res] to client [$id]")

                    try {
                        if (!clientList.getSocket(id).isConnected) {
                            println("Connection with id [$id] finished")
                            isWorking = false
                        }
                    } catch (e: Exception) {
                        println("Connection with id [$id] finished")
                        isWorking = false
                    }
                }
            }
        }
    }
}

class ClientList() {
    private val clients = mutableMapOf<Int, Pair<InputStream, OutputStream>>()
    private val socketList = mutableMapOf<Int, Socket>()

    fun addNewClient(socket: Socket) {
        val keys = clients.keys
        var newId = 0
        for (i in keys) {
            if (i == newId) {
                newId++
            }
        }
        clients[newId] = Pair(socket.getInputStream(), socket.getOutputStream())
        socketList[newId] = socket
        ClientThread(newId, this)
    }

    fun finishConnection(id: Int) {
        clients.remove(id)
        socketList[id]!!.close()
        socketList.remove(id)
    }

    fun getInputStream(id: Int): InputStream {
        return clients[id]!!.first
    }

    fun getOutputStream(id: Int): OutputStream {
        return clients[id]!!.second
    }

    fun getSocket(id: Int): Socket {
        return socketList[id]!!
    }
}
