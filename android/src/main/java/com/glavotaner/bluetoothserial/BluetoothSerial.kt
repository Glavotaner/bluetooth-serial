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

// Debugging
private const val TAG = "BluetoothSerialService"
private const val D = true

// Well known SPP UUID
private val UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

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

    fun getRemoteDevice(address: String?): BluetoothDevice {
        return mAdapter.getRemoteDevice(address)
    }

    val isEnabled: Boolean
        get() = mAdapter.isEnabled

    // connect
    @get:SuppressLint("MissingPermission")
    val bondedDevices: Set<BluetoothDevice>
        get() = mAdapter.bondedDevices

    // scan
    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        mAdapter.startDiscovery()
    }

    // scan
    @SuppressLint("MissingPermission")
    fun cancelDiscovery() {
        mAdapter.cancelDiscovery()
    }

    val isConnected: Boolean
        get() = mState === ConnectionState.CONNECTED

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    @Synchronized
    private fun setState(state: ConnectionState) {
        if (D) Log.d(
            TAG,
            "setState() $mState -> $state"
        )
        mState = state
        sendStateToPlugin(state)
    }

    private fun sendStateToPlugin(state: ConnectionState) {
        val bundle = Bundle()
        bundle.putInt("state", state.value())
        sendConnectionStateToPlugin(SUCCESS, bundle)
    }

    @Synchronized
    fun resetService() {
        if (D) Log.d(TAG, "start")
        closeRunningThreads()
        setState(ConnectionState.NONE)
    }

    // connect
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connect(device: BluetoothDevice) {
        if (D) Log.d(
            TAG,
            "connect to: $device"
        )
        connectToSocketOfType("secure") {
            device.createRfcommSocketToServiceRecord(UUID_SPP)
        }
    }

    // connect
    @SuppressLint("MissingPermission")
    @Synchronized
    fun connectInsecure(device: BluetoothDevice) {
        if (D) Log.d(
            TAG,
            "connect to: $device"
        )
        connectToSocketOfType("insecure") {
            device.createInsecureRfcommSocketToServiceRecord(UUID_SPP)
        }
    }

    private fun connectToSocketOfType(socketType: String, createSocket: () -> BluetoothSocket?) {
        try {
            setState(ConnectionState.CONNECTING)
            val socket = createSocket()
            if (socket != null) connect(socket, socketType)
            else sendConnectionErrorToPlugin("Could not create socket")
        } catch (e: IOException) {
            Log.e(
                TAG,
                "Socket Type: $socketType create() failed", e
            )
            handleConnectionError(e.message)
        }
    }

    private fun sendConnectionErrorToPlugin(error: String?) {
        sendConnectionStateToPlugin(ERROR, Bundle().apply { putString("error", error) })
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     */
    @Synchronized
    private fun connect(socket: BluetoothSocket, socketType: String) {
        // Cancel any thread attempting to make a connection
        closeRunningThreads()
        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(socket, socketType)
        mConnectThread!!.start()
    }

    /**
     * Write to the IOThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see IOThread.write
     */
    fun write(out: ByteArray?) {
        if (isConnected) {
            var r: IOThread?
            // Synchronize a copy of the ConnectedThread
            synchronized(this) { r = mIOThread }
            r!!.write(out)
        } else {
            val message = writeHandler.obtainMessage(ERROR)
            message.data = Bundle().apply { putString("error", "Not connected") }
            message.sendToTarget()
        }
    }

    private fun sendConnectionStateToPlugin(status: Int, bundle: Bundle) {
        connectionHandler.obtainMessage(status).apply { data = bundle }.sendToTarget()
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(
        private val mmSocket: BluetoothSocket,
        private val mSocketType: String
    ) :
        Thread() {
        // connect
        @SuppressLint("MissingPermission")
        override fun run() {
            Log.i(
                TAG,
                "BEGIN mConnectThread SocketType:$mSocketType"
            )
            name = "ConnectThread$mSocketType"
            try {
                mmSocket.connect()
                Log.i(TAG, "Connected")
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
                handleConnectionError(e.message)
                return
            }
            // Reset the ConnectThread because we're done
            synchronized(this@BluetoothSerial) { mConnectThread = null }
            startIOThread()
        }

        /**
         * Start the IOThread to begin managing a Bluetooth connection
         */
        private fun startIOThread() {
            synchronized(this@BluetoothSerial) {
                if (D) Log.d(
                    TAG,
                    "connected, Socket Type:$mSocketType"
                )
                // Start the thread to manage the connection and perform transmissions
                mIOThread = IOThread(mmSocket, mSocketType)
                mIOThread!!.start()
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(
                    TAG,
                    "close() of connect $mSocketType socket failed", e
                )
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class IOThread(socket: BluetoothSocket, socketType: String) :
        Thread() {
        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            Log.i(TAG, "BEGIN mConnectedThread")
            val buffer = ByteArray(1024)
            var connected = true
            while (connected) {
                try {
                    val data = getBufferData(buffer)
                    sendToPlugin(data)
                } catch (e: IOException) {
                    connected = false
                    Log.e(TAG, "disconnected", e)
                    handleConnectionError("Device connection was lost")
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
            // Share the sent message back to the UI Activity
            message.sendToTarget()
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }

        private fun sendToPlugin(data: String) {
            val message = readHandler.obtainMessage(SUCCESS)
            message.data = Bundle().apply { putString("data", data) }
            message.sendToTarget()
        }

        init {
            Log.d(TAG, "create ConnectedThread: $socketType")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
                setState(ConnectionState.CONNECTED)
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
                handleConnectionError("Could not create sockets")
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    private fun closeRunningThreads() {
        mConnectThread?.cancel()
        mConnectThread = null
        mIOThread?.cancel()
        mIOThread = null
    }

    private fun handleConnectionError(message: String?) {
        sendConnectionErrorToPlugin(message)
        resetService()
    }

    init {
        mState = ConnectionState.NONE
    }
}