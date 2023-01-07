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
    private var mConnectThread: ConnectThread? = null
    private var mIOThread: IOThread? = null
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

    @Synchronized
    fun resetService() {
        if (D) Log.d(TAG, "start")
        cancelRunningThreads()
        state = ConnectionState.NONE
    }

    // connect
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(device: BluetoothDevice) {
        if (D) Log.d(TAG, "connect to: $device")
        try {
            val secureSocket = device.createRfcommSocketToServiceRecord(UUID_SPP)
            connect(secureSocket, "secure")
        } catch (e: IOException) {
            Log.e(TAG, "Socket Type: secure create() failed", e)
            sendConnectionErrorToPlugin(e.message!!)
        }
    }

    // connect
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connectInsecure(device: BluetoothDevice) {
        if (D) Log.d(TAG, "connect to: $device")
        try {
            val insecureSocket = device.createInsecureRfcommSocketToServiceRecord(UUID_SPP)
            connect(insecureSocket, "insecure")
        } catch (e: IOException) {
            Log.e(TAG, "Socket Type: insecure create() failed", e)
            sendConnectionErrorToPlugin(e.message!!)
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     */
    @Synchronized
    fun connect(socket: BluetoothSocket?, socketType: String) {
        cancelRunningThreads()
        if (socket != null) {
            // Start the thread to connect with the given device
            mConnectThread = ConnectThread(socket, socketType).also { it.start() }
            state = ConnectionState.CONNECTING
        }
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun startIOThread(socket: BluetoothSocket?, socketType: String) {
        if (D) Log.d(TAG, "connected, Socket Type:$socketType")
        cancelRunningThreads()
        // Start the thread to manage the connection and perform transmissions
        mIOThread = IOThread(socket, socketType).also { it.start() }
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
     * @see IOThread.write
     */
    fun write(out: ByteArray?) {
        // Create temporary object
        var ioThread: IOThread?
        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState === ConnectionState.CONNECTED) {
                ioThread = mIOThread
            } else {
                writeHandler.obtainMessage(ERROR).apply {
                    data = Bundle().apply { putString("error", "Not connected") }
                }.sendToTarget()
                return
            }
        }
        // Perform the write unsynchronized
        ioThread!!.write(out)
    }

    private fun sendStateToPlugin(state: ConnectionState) {
        val stateBundle = Bundle().apply { putInt("state", state.value()) }
        sendConnectionStateToPlugin(SUCCESS, stateBundle)
    }

    private fun sendConnectionStateToPlugin(status: Int, bundle: Bundle) {
        connectionHandler.obtainMessage(status).apply { data = bundle }.sendToTarget()
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val socket: BluetoothSocket, private val socketType: String) : Thread() {

        override fun run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:$socketType")
            name = "ConnectThread$socketType"
            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery()
            connectToSocket()
            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothSerial) { mConnectThread = null }
            startIOThread(socket, socketType)
            mState = ConnectionState.CONNECTED
        }

        private fun connectToSocket() {
            try {
                // This is a blocking call and will only return on a successful connection or an exception
                Log.i(TAG, "Connecting to socket...")
                socket.connect()
                Log.i(TAG, "Connected")
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
                sendConnectionErrorToPlugin(e.message ?: "Unable to connect")
                resetService()
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect $socketType socket failed", e)
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class IOThread(socket: BluetoothSocket?, socketType: String) : Thread() {
        private val mmSocket: BluetoothSocket?
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
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
                    Log.e(TAG, "disconnected", e)
                    sendConnectionErrorToPlugin("Device connection was lost")
                    resetService()
                    break
                }
            }
        }

        @Throws(IOException::class)
        private fun getBufferData(buffer: ByteArray): String {
            val bytes = mmInStream!!.read(buffer)
            return String(buffer, 0, bytes)
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

        fun cancel() {
            try {
                mmSocket!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }

        init {
            Log.d(TAG, "create ConnectedThread: $socketType")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket!!.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
                sendConnectionErrorToPlugin(e.message ?: "Could not get streams")
                resetService()
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    private fun sendReadData(data: String) {
        readHandler.obtainMessage(SUCCESS).apply {
            this.data = Bundle().apply { putString("data", data) }
        }.sendToTarget()
    }

    private fun cancelRunningThreads() {
        mConnectThread?.cancel()
        mConnectThread = null
        mIOThread?.cancel()
        mIOThread = null
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