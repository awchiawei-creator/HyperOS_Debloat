package com.miui.debloater

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val out = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val err = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor()
            (out + "\n" + err).trim()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
