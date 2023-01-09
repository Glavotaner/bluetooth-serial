package com.glavotaner.bluetoothserial

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResult
import com.getcapacitor.*
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import java.lang.IllegalArgumentException

// Debugging
private const val TAG = "BluetoothSerial"
const val CONNECT = "connect"
const val SCAN = "scan"
const val LOCATION = "location"

@SuppressLint("InlinedApi")
@CapacitorPlugin(
    name = "BluetoothSerial",
    permissions = [Permission(
        strings = [Manifest.permission.BLUETOOTH_SCAN],
        alias = SCAN
    ), Permission(
        strings = [Manifest.permission.BLUETOOTH_CONNECT],
        alias = CONNECT
    ), Permission(
        strings = [Manifest.permission.ACCESS_COARSE_LOCATION],
        alias = LOCATION
    )]
)
class BluetoothSerialPlugin : Plugin() {
    private lateinit var implementation: BluetoothSerial
    private var connectCall: PluginCall? = null
    private var writeCall: PluginCall? = null
    private var discoveryCall: PluginCall? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    private var buffer = StringBuffer()
    override fun load() {
        super.load()
        val looper = Looper.getMainLooper()
        val connectionHandler = Handler(looper) { message: android.os.Message ->
            val data = message.data
            val connectionState = ConnectionState.values()[data.getInt("state")]
            if (message.what == Message.SUCCESS) {
                val state: JSObject = JSObject().put("state", connectionState.value())
                notifyListeners("connectionChange", state)
                if (connectCall != null && connectionState === ConnectionState.CONNECTED) {
                    connectCall!!.resolve(state)
                    connectCall = null
                }
                if (connectionState === ConnectionState.NONE) connectCall = null
            } else if (connectCall != null) {
                val error = data.getString("error")
                connectCall!!.reject(error)
                connectCall = null
            }
            false
        }
        val writeHandler = Handler(looper) { message: android.os.Message ->
            if (message.what == Message.SUCCESS) {
                writeCall!!.resolve()
            } else {
                val error = message.data.getString("error")
                writeCall!!.reject(error)
            }
            writeCall = null
            false
        }
        val readHandler = Handler(looper) { message: android.os.Message ->
            buffer.append(message.data.getString("data"))
            false
        }
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        implementation =
            BluetoothSerial(bluetoothManager.adapter, connectionHandler, writeHandler, readHandler)
    }

    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value") as String
        val ret = JSObject().put("value", implementation.echo(value))
        call.resolve(ret)
    }

    @PluginMethod
    fun connect(call: PluginCall) {
        connect(call) { device -> implementation.connect(device) }
    }

    @PluginMethod
    fun connectInsecure(call: PluginCall) {
        connect(call) { device -> implementation.connectInsecure(device) }
    }

    private fun connect(call: PluginCall, connect: suspend (BluetoothDevice) -> Unit) {
        if (rejectIfBluetoothDisabled(call)) return
        if (hasCompatPermission(CONNECT)) connectToDevice(call, connect)
        else requestConnectPermission(call)
    }

    private fun connectToDevice(call: PluginCall, connect: suspend (BluetoothDevice) -> Unit) {
        val macAddress = call.getString("address")
        try {
            val device = implementation.getRemoteDevice(macAddress)
            if (device != null) {
                connectCall = call
                runBlocking { connect(device) }
                buffer.setLength(0)
            } else {
                call.reject("Could not connect to $macAddress")
            }
        } catch (error: IllegalArgumentException) {
            // most likely invalid mac
            call.reject(error.message)
        }
    }

    @PluginMethod
    fun disconnect(call: PluginCall) {
        implementation.resetService()
        call.resolve()
    }

    @PluginMethod
    @Throws(JSONException::class)
    fun write(call: PluginCall) {
        if (rejectIfBluetoothDisabled(call)) return
        val data = (call.data["data"] as String).toByteArray()
        writeCall = call
        implementation.write(data)
    }

    @PluginMethod
    fun read(call: PluginCall) {
        val length = buffer.length
        val data = buffer.substring(0, length)
        buffer.delete(0, length)
        val result = JSObject().put("data", data)
        call.resolve(result)
    }

    @PluginMethod
    fun available(call: PluginCall) {
        val result = JSObject().put("available", buffer.length)
        call.resolve(result)
    }

    @PluginMethod
    fun isEnabled(call: PluginCall) {
        val result = JSObject().put("isEnabled", implementation.isEnabled)
        call.resolve(result)
    }

    @PluginMethod
    fun isConnected(call: PluginCall) {
        val bluetoothState = implementation.state
        val result = JSObject().put("isConnected", bluetoothState == ConnectionState.CONNECTED)
        call.resolve(result)
    }

    @PluginMethod
    fun clear(call: PluginCall) {
        buffer.setLength(0)
        call.resolve()
    }

    @PluginMethod
    fun settings(call: PluginCall) {
        activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        call.resolve()
    }

    @PluginMethod
    fun enable(call: PluginCall) {
        if (hasCompatPermission(CONNECT)) enableBluetooth(call)
        else requestConnectPermission(call)
    }

    private fun enableBluetooth(call: PluginCall) {
        val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(call, enableIntent, "enableBluetoothActivityCallback")
    }

    @ActivityCallback
    private fun enableBluetoothActivityCallback(call: PluginCall, activityResult: ActivityResult) {
        val isEnabled = activityResult.resultCode == Activity.RESULT_OK
        val result = JSObject().put("isEnabled", isEnabled)
        call.resolve(result)
    }

    @PluginMethod
    fun list(call: PluginCall) {
        if (rejectIfBluetoothDisabled(call)) return
        if (hasCompatPermission(CONNECT)) listPairedDevices(call)
        else requestConnectPermission(call)
    }

    @SuppressLint("MissingPermission")
    private fun listPairedDevices(call: PluginCall) {
        val devices = implementation.bondedDevices.map { deviceToJSON(it) }
        val result = JSObject().put("devices", JSArray(devices))
        call.resolve(result)
    }

    @PluginMethod
    fun discoverUnpaired(call: PluginCall) {
        if (rejectIfBluetoothDisabled(call)) return
        if (hasCompatPermission(SCAN)) startDiscovery(call)
        else requestScanPermission(call)
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery(call: PluginCall) {
        cancelDiscovery()
        discoveryCall = call
        discoveryReceiver = object : BroadcastReceiver() {
            private val unpairedDevices = JSONArray()
            private val result = JSObject().put("devices", unpairedDevices)
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = getDeviceFromIntent(intent)
                        unpairedDevices.put(deviceToJSON(device!!))
                        result.put("devices", unpairedDevices)
                        notifyListeners("discoverUnpaired", result)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        call.resolve(result)
                        activity.unregisterReceiver(this)
                        discoveryCall = null
                    }
                }
            }

            @Suppress("DEPRECATION")
            private fun getDeviceFromIntent(intent: Intent) =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        activity.registerReceiver(discoveryReceiver, filter)
        implementation.startDiscovery()
    }

    @PluginMethod
    fun cancelDiscovery(call: PluginCall) {
        if (hasCompatPermission(SCAN)) {
            cancelDiscovery()
            call.resolve()
        } else {
            requestScanPermission(call)
        }
    }

    private fun cancelDiscovery() {
        if (discoveryCall != null) {
            discoveryCall?.reject("Discovery cancelled")
            discoveryCall = null
            implementation.cancelDiscovery()
            discoveryReceiver?.let { activity.unregisterReceiver(it) }
            discoveryReceiver = null
        }
    }

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) super.checkPermissions(call)
        else checkCompatPermissions(call)
    }

    private fun checkCompatPermissions(call: PluginCall) {
        call.resolve(
            JSObject()
                .put(LOCATION, getPermissionState(LOCATION))
                .put(SCAN, PermissionState.GRANTED)
                .put(CONNECT, PermissionState.GRANTED)
        )
    }

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) super.requestPermissions(call)
        else requestCompatPermissions(call)
    }

    /*
    * Request location permission if requested, otherwise resolve with all requested permissions
    * granted, as Android 11< only requires location permission.
    * */
    private fun requestCompatPermissions(call: PluginCall) {
        val requestedPermissions = call.getArray("permissions")
        try {
            // for Android 11< we only need/can request location permission, all others are granted
            if (requestedPermissions.toList<Any>()
                    .contains(LOCATION) && getPermissionState(LOCATION) != PermissionState.GRANTED
            ) {
                requestPermissionForAlias(LOCATION, call, "requestCompatPermissionsCallback")
            } else {
                val permissions = getGrantedPermissions(requestedPermissions)
                call.resolve(permissions)
            }
        } catch (exception: JSONException) {
            val message = exception.message
            Log.e(TAG, message!!)
            call.reject(message)
        }
    }

    @PermissionCallback
    private fun requestCompatPermissionsCallback(call: PluginCall) {
        val requestedPermissions = call.getArray("permissions")
        try {
            val permissions = getGrantedPermissions(requestedPermissions)
            permissions.put(LOCATION, getPermissionState(LOCATION))
            call.resolve(permissions)
        } catch (exception: JSONException) {
            val message = exception.message
            Log.e(TAG, message!!)
            call.reject(message)
        }
    }

    @Throws(JSONException::class)
    private fun getGrantedPermissions(requestedPermissions: JSArray): JSObject {
        val response = JSObject()
        for (i in 0 until requestedPermissions.length()) {
            val alias = requestedPermissions[i] as String
            response.put(alias, PermissionState.GRANTED)
        }
        return response
    }

    @PermissionCallback
    private fun connectPermissionCallback(call: PluginCall) {
        if (getPermissionState(CONNECT) == PermissionState.GRANTED) {
            when (call.methodName) {
                "enable" -> enableBluetooth(call)
                "list" -> listPairedDevices(call)
                "connect" -> connect(call)
                "connectInsecure" -> connectInsecure(call)
            }
        } else {
            call.reject("Connect permission denied")
        }
    }

    @PermissionCallback
    private fun scanPermissionCallback(call: PluginCall) {
        if (getPermissionState(SCAN) == PermissionState.GRANTED) {
            when (call.methodName) {
                "discoverUnpaired" -> startDiscovery(call)
                "cancelDiscovery" -> cancelDiscovery(call)
            }
        } else {
            call.reject("Scan permission denied")
        }
    }

    private fun rejectIfBluetoothDisabled(call: PluginCall): Boolean {
        val isEnabled = implementation.isEnabled
        if (!isEnabled) {
            call.reject("Bluetooth is not enabled")
            return true
        }
        return false
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        implementation.resetService()
    }

    private fun hasCompatPermission(alias: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            getPermissionState(alias) == PermissionState.GRANTED
        else
            true

    private fun requestConnectPermission(call: PluginCall) {
        requestPermissionForAlias(CONNECT, call, "connectPermissionCallback")
    }

    private fun requestScanPermission(call: PluginCall) {
        requestPermissionForAlias(SCAN, call, "scanPermissionCallback")
    }

    @SuppressLint("MissingPermission")
    private fun deviceToJSON(device: BluetoothDevice): JSObject = JSObject()
        .put("name", device.name)
        .put("address", device.address)
        .put("deviceClass", device.bluetoothClass?.deviceClass ?: JSObject.NULL)

}