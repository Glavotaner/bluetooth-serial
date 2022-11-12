package com.glavotaner.bluetoothserial

enum class ConnectionState(private val state: Int) {
    NONE(0), CONNECTING(1), CONNECTED(2);

    fun value(): Int {
        return state
    }
}