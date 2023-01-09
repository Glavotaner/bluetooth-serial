package com.glavotaner.bluetoothserial

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.glavotaner.bluetoothserial.Message.ERROR
import com.glavotaner.bluetoothserial.Message.SUCCESS
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

// Debugging
private const val TAG = "BluetoothSerialService"
private const val D = true

// Well known SPP UUID
private val UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class BluetoothSerial(
    private val bluetoothAdapter: BluetoothAdapter,
    private val connectionHandler: Handler,
    private val writeHandler: Handler,
    private val readHandler: Handler
) {
    // Member fields
    private var connectedDevice: ConnectedDevice? = null
    private var connectJob: Job? = null
    private var mConnectionState: ConnectionState
    fun echo(value: String): String {
        return value
    }

    fun getRemoteDevice(address: String?): BluetoothDevice? {
        return bluetoothAdapter.getRemoteDevice(address)
    }

    val isEnabled: Boolean
        get() = bluetoothAdapter.isEnabled

    @get:SuppressLint("MissingPermission")
    val bondedDevices: Set<BluetoothDevice>
        get() = bluetoothAdapter.bondedDevices

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        bluetoothAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun cancelDiscovery() {
        bluetoothAdapter.cancelDiscovery()
    }

    @get:Synchronized
    @set:Synchronized
    var connectionState: ConnectionState
        get() = mConnectionState
        private set(state) {
            if (D) Log.d(TAG, "setState() $mConnectionState -> $state")
            mConnectionState = state
            sendStateToPlugin(state)
        }

    fun resetService() {
        if (D) Log.d(TAG, "start")
        closeConnection()
        connectionState = ConnectionState.NONE
    }

    // connect
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) {
        if (D) Log.d(TAG, "connect to: $device")
        connectToSocketOfType("secure") {
            device.createRfcommSocketToServiceRecord(UUID_SPP)
        }
    }

    // connect
    @SuppressLint("MissingPermission")
    suspend fun connectInsecure(device: BluetoothDevice) {
        if (D) Log.d(TAG, "connect to: $device")
        connectToSocketOfType("insecure") {
            device.createInsecureRfcommSocketToServiceRecord(UUID_SPP)
        }
    }

    private suspend fun connectToSocketOfType(socketType: String, createSocket: () -> BluetoothSocket?) {
        try {
            closeConnection()
            val socket = createSocket()
            if (socket != null) connect(socket, socketType)
            else sendConnectionErrorToPlugin("Could not connect")
        } catch (e: IOException) {
            Log.e(TAG, "Socket Type: $socketType create() failed", e)
            sendConnectionErrorToPlugin(e.message!!)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connect(socket: BluetoothSocket, socketType: String) {
        Log.i(TAG, "BEGIN mConnectThread SocketType: $socketType")
        connectionState = ConnectionState.CONNECTING
        connectJob?.cancel()
        Log.i(TAG, "Connecting to socket...")
        withContext(Dispatchers.IO) {
            connectJob = launch {
                try {
                    // This is a blocking call and will only return on a successful connection or an exception
                    @Suppress("BlockingMethodInNonBlockingContext")
                    socket.connect()
                    if (D) Log.d(TAG, "connected, Socket Type:$socketType")
                    connectedDevice = ConnectedDevice(socket, socketType)
                    if (connectionState === ConnectionState.CONNECTED) {
                        Log.i(TAG, "Connected")
                        connectedDevice!!.read()
                    }
                } catch (e: IOException) {
                    try {
                        @Suppress("BlockingMethodInNonBlockingContext")
                        socket.close()
                    } catch(error: IOException) {
                        Log.e(TAG, "Could not close socket ${error.message ?: ""}")
                    }
                    handleConnectionError(e.message ?: "Unable to connect")
                }
            }
        }
    }

    private fun sendConnectionErrorToPlugin(error: String) {
        val bundle = Bundle().apply {
            putInt("state", ConnectionState.NONE.value())
            putString("error", error)
        }
        sendConnectionStateToPlugin(ERROR, bundle)
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     */
    fun write(out: ByteArray?) {
        if (connectionState === ConnectionState.CONNECTED) {
            connectedDevice!!.write(out)
        } else {
            writeHandler.obtainMessage(ERROR).apply {
                data = Bundle().apply { putString("error", "Not connected") }
            }.sendToTarget()
        }
    }

    private fun sendStateToPlugin(state: ConnectionState) {
        val stateBundle = Bundle().apply { putInt("state", state.value()) }
        sendConnectionStateToPlugin(SUCCESS, stateBundle)
    }

    private fun sendConnectionStateToPlugin(status: Int, bundle: Bundle) {
        connectionHandler.obtainMessage(status).apply { data = bundle }.sendToTarget()
    }

    private inner class ConnectedDevice(socket: BluetoothSocket, socketType: String) {
        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        fun disconnect() {
            try {
                mmSocket.close()
            } catch(error: IOException) {
                Log.e(TAG, "could not close socket ${error.message ?: ""}")
            }
        }

        @Throws(IOException::class)
        private fun getBufferData(buffer: ByteArray): String {
            val bytes = mmInStream!!.read(buffer)
            return String(buffer, 0, bytes)
        }

        fun read() {
            Log.i(TAG, "BEGIN mConnectedThread")
            val buffer = ByteArray(1024)
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    val data = getBufferData(buffer)
                    // Send the new data String to the UI Activity
                    sendReadData(data)
                } catch (e: IOException) {
                    handleConnectionError("Device connection was lost")
                    break
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        fun write(buffer: ByteArray?) {
            val message = writeHandler.obtainMessage(SUCCESS)
            try {
                mmOutStream!!.write(buffer)
            } catch (e: IOException) {
                message.what = ERROR
                Log.e(TAG, "Exception during write", e)
                message.data = Bundle().apply { putString("error", e.message) }
            }
            message.sendToTarget()
        }

        init {
            Log.d(TAG, "create ConnectedThread: $socketType")
            mmSocket = socket
            var inStream: InputStream? = null
            var outStream: OutputStream? = null
            try {
                inStream = socket.inputStream
                outStream = socket.outputStream
                connectionState = ConnectionState.CONNECTED
            } catch (e: IOException) {
                handleConnectionError(e.message ?: "Could not get streams")
                connectJob?.cancel()
            }
            mmInStream = inStream
            mmOutStream = outStream
        }
    }

    private fun sendReadData(data: String) {
        readHandler.obtainMessage(SUCCESS).apply {
            this.data = Bundle().apply { putString("data", data) }
        }.sendToTarget()
    }

    private fun closeConnection() {
        connectJob?.cancel()
        connectJob = null
        connectedDevice?.disconnect()
        connectedDevice = null
    }

    private fun handleConnectionError(message: String) {
        Log.e(TAG, message)
        sendConnectionErrorToPlugin(message)
        resetService()
    }

    init {
        mConnectionState = ConnectionState.NONE
    }
}