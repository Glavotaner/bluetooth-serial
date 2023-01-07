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

class BluetoothSerial(
    private val mAdapter: BluetoothAdapter,
    private val connectionHandler: Handler,
    private val writeHandler: Handler,
    private val readHandler: Handler
) {
    // Member fields
    private var mConnectedDevice: ConnectedDevice? = null
    private var mConnectJob: Job? = null
    private var mState: ConnectionState
    fun echo(value: String): String {
        return value
    }

    fun getRemoteDevice(address: String?): BluetoothDevice? {
        return mAdapter.getRemoteDevice(address)
    }

    val isEnabled: Boolean
        get() = mAdapter.isEnabled

    @get:SuppressLint("MissingPermission")
    val bondedDevices: Set<BluetoothDevice>
        get() = mAdapter.bondedDevices

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        mAdapter.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun cancelDiscovery() {
        mAdapter.cancelDiscovery()
    }

    @get:Synchronized
    @set:Synchronized
    var state: ConnectionState
        get() = mState
        private set(state) {
            if (D) Log.d(TAG, "setState() $mState -> $state")
            mState = state
            sendStateToPlugin(state)
        }

    fun resetService() {
        if (D) Log.d(TAG, "start")
        closeConnection()
        state = ConnectionState.NONE
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

    private suspend fun connect(socket: BluetoothSocket, socketType: String) {
        Log.i(TAG, "BEGIN mConnectThread SocketType: $socketType")
        // Always cancel discovery because it will slow down a connection
        mAdapter.cancelDiscovery()
        mState = ConnectionState.CONNECTING
        mConnectJob?.cancel()
        Log.i(TAG, "Connecting to socket...")
        withContext(Dispatchers.IO) {
            mConnectJob = launch {
                try {
                    // This is a blocking call and will only return on a successful connection or an exception
                    @Suppress("BlockingMethodInNonBlockingContext")
                    socket.connect()
                    if (D) Log.d(TAG, "connected, Socket Type:$socketType")
                    mConnectedDevice = ConnectedDevice(socket, socketType)
                    if (mState === ConnectionState.CONNECTED) {
                        Log.i(TAG, "Connected")
                        mConnectedDevice!!.read()
                    }
                } catch (e: IOException) {
                    handleConnectionError(e.message ?: "Unable to connect")
                }
            }
        }
    }

    private fun sendConnectionErrorToPlugin(error: String) {
        mState = ConnectionState.NONE
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
    suspend fun write(out: ByteArray?) {
        if (mState === ConnectionState.CONNECTED) {
            coroutineScope {
                mConnectedDevice!!.write(out)
            }
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
                mState = ConnectionState.CONNECTED
            } catch (e: IOException) {
                handleConnectionError(e.message ?: "Could not get streams")
                mConnectJob?.cancel()
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
        mConnectJob?.cancel()
        mConnectedDevice?.disconnect()
        mConnectedDevice = null
    }

    private fun handleConnectionError(message: String) {
        Log.e(TAG, message)
        sendConnectionErrorToPlugin(message)
        resetService()
    }

    companion object {
        // Debugging
        private const val TAG = "BluetoothSerialService"
        private const val D = true

        // Well known SPP UUID
        private val UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    init {
        mState = ConnectionState.NONE
    }
}