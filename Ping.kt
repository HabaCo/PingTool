package tw.com.ingee.troubleshooting.tools.net

import android.util.Log
import java.util.concurrent.LinkedBlockingQueue


/** Created by Haba on 2020/3/31.
 *  Copyright (c) 2020 Ingee All rights reserved.
 */

@Suppress("unused")
class Ping private constructor() {

    private val tag = javaClass.simpleName

    companion object {
        /**
         * Ping command
         */
        private const val CommandPattern: String = "ping"

        /**
         * Ping response pattern from console
         */
        private const val ResultPattern: String = "rtt min/avg/max/mdev = "

        /**
         * default option - interval of ping
         */
        val OptionInterval =
            Option<Int>("-i")

        /**
         * default option - count(s) of ping
         */
        val OptionCount =
            Option<Int>("-c")

        /**
         * default option - timeout of ping per count
         */
        val OptionTimeout =
            Option<Int>("-W")

        /**
         * default option - packages of ping
         */
        val OptionPackages =
            Option<Int>("-w")
    }

    /**
     * 目標 host, 預設自己
     */
    var destination: String = "127.0.0.1"

    /**
     * 是否正在等待回應
     * for [runSync]
     */
    @Volatile
    var isBusy = false

    /**
     * 參數集合
     * by default
     * timeout=1(second)
     * count=1
     */
    private val options = HashSet<Option<out Any>>().apply {
        add(OptionTimeout.apply { value = 1 })
        add(OptionCount.apply { value = 1 })
    }

    /**
     * 新增/更新參數
     */
    fun addOption(option: Option<out Any>) = options.add(option)

    /**
     * 移除參數
     */
    fun removeOption(option: Option<out Any>) = options.remove(option)

    /**
     * 清除所有參數
     */
    fun clearOption() = options.clear()

    /**
     * Thread pool with async calls
     */
    private var asyncThread: PingThread? = null

    /**
     * 建置指令參數於
     */
    fun buildRequest(): String {
        val argumentsBuilder = StringBuilder()
        options.forEach { option ->
            argumentsBuilder.append(" ").append(option.parameter)
            if (option.value != null) {
                argumentsBuilder.append(option.value)
            }
        }
        argumentsBuilder.append(" ").append(destination)
        return argumentsBuilder.toString()
    }

    /**
     * run with command from [buildRequest], or custom
     */
    fun runSync(command: String? = buildRequest()): Response {
        isBusy = true
        val timestamp = System.currentTimeMillis()

        if (command.isNullOrEmpty())
            return Response(
                "",
                "",
                "arguments should not be empty",
                timestamp
            )

        var stdOut = ""
        var error = ""

        try {
            with(Runtime.getRuntime().exec(CommandPattern + command)) {

                stdOut = inputStream.bufferedReader().readText()
                error = errorStream.bufferedReader().readText()

                destroy()
            }
        } catch (exception: Exception) {
            error = exception.message ?: ""
        } finally {
            isBusy = false
            Log.d(tag, "args: $command")

            if (stdOut.isNotEmpty())
                Log.d(tag, "stdOut: $stdOut")

            if (error.isNotEmpty())
                Log.e(tag, "errOut: $error")
        }

        return Response(
            command.substringAfterLast(" "),
            stdOut,
            error,
            timestamp
        )
    }

    fun runAsync(command: String? = buildRequest(), callback: (Response) -> Unit) {
        if (asyncThread != null) {
            asyncThread!!.run {
                if (stopped) {
                    stopped = false
                }
                putArgument(command)
            }
        } else {
            asyncThread = PingThread { response ->
                callback(response)
            }.also {
                it.putArgument(command)
                it.start()
            }
        }
    }

    fun destroy() {
        try {
            if (asyncThread != null) {
                asyncThread?.stopped = true
            }
        } finally {
            asyncThread = null
        }
    }

    class Builder {
        private val ping = Ping()

        /**
         * 新增/更新參數
         */
        fun addOption(option: Option<out Any>) = this.apply {
            ping.addOption(option)
        }

        /**
         * 設定目標 host
         */
        fun destination(dest: String) = this.apply {
            ping.destination = dest
        }

        /**
         * 產生 Ping obj
         */
        fun build() = ping
    }

    /**
     * 參數類
     * @param parameter 參數指令
     */
    data class Option<T>(val parameter: String) {
        var value: T? = null

        override fun hashCode(): Int {
            return parameter.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return other is Option<*> && other.parameter == parameter
        }
    }

    /**
     * Response of ping result with simple analysis
     * @param target target host address
     * @param stdOut original standard output from process
     * @param errOut original error output from process
     * @param timeStamp timestamp while this command had been calling
     */
    data class Response(
        val target: String,
        val stdOut: String,
        val errOut: String,
        val timeStamp: Long
    ) {
        val success: Boolean = stdOut.contains(ResultPattern)
        var icmp_seq = 0
        var ttl = 0
        var duration = 0f

        init {
            if (success) {
                try {
                    val icmpSeqPreIndex = stdOut.indexOf("icmp_seq=") + "icmp_seq=".length
                    val icmpSeqLastIndex = stdOut.indexOf(" ", icmpSeqPreIndex)
                    val icmpSeqValue = stdOut.substring(icmpSeqPreIndex, icmpSeqLastIndex)
                    icmp_seq = icmpSeqValue.toInt()

                    val ttlPreIndex = stdOut.indexOf("ttl=") + "ttl=".length
                    val ttlLastIndex = stdOut.indexOf(" ", ttlPreIndex)
                    val ttlValue = stdOut.substring(ttlPreIndex, ttlLastIndex)
                    ttl = ttlValue.toInt()

                    val durationPreIndex = stdOut.indexOf("time=") + "time=".length
                    val durationLastIndex = stdOut.indexOf(" ", durationPreIndex)
                    val durationValue = stdOut.substring(durationPreIndex, durationLastIndex)
                    duration = durationValue.toFloat()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        override fun toString(): String {
            return "\"PingResponse\": { " +
                    "\"target\": \"$target\", " +
                    "\"timestamp\": \"$timeStamp\", " +
                    "\"success\": \"$success\", " +
                    "\"icmp_seq\": \"$icmp_seq\", " +
                    "\"ttl\": \"$ttl\", " +
                    "\"duration\": \"$duration\" " +
                    "}"
        }
    }

    @Suppress("unused")
    inner class PingThread(val action: (response: Response) -> Unit) : Thread() {
        /**
         * arguments queue
         */
        private var queue: LinkedBlockingQueue<String?>? = LinkedBlockingQueue(2)

        var stopped = false
            set(value) {
                field = value
                if (value) {
                    queue?.put(null)
                }
            }

        fun putArgument(argument: String?) {
            try {
                queue?.add(argument)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }

        override fun run() {
            while (!stopped) {
                if (!isBusy) {
                    val argument = queue?.take()

                    if (stopped)
                        break

                    val response = runSync(argument)

                    if (stopped)
                        break

                    action(response)
                }
            }
        }
    }
}