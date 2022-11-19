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
import org.json.JSONArray
import org.json.JSONException

@SuppressLint("InlinedApi")
@CapacitorPlugin(
    name = "BluetoothSerial",
    permissions = [Permission(
        strings = [Manifest.permission.BLUETOOTH_SCAN],
        alias = BluetoothSerialPlugin.SCAN
    ), Permission(
        strings = [Manifest.permission.BLUETOOTH_CONNECT],
        alias = BluetoothSerialPlugin.CONNECT
    ), Permission(
        strings = [Manifest.permission.ACCESS_COARSE_LOCATION],
        alias = BluetoothSerialPlugin.LOCATION
    )]
)
class BluetoothSerialPlugin : Plugin() {
    private lateinit var implementation: BluetoothSerial
    private var connectCall: PluginCall? = null
    private var writeCall: PluginCall? = null
    private var buffer = StringBuffer()
    override fun load() {
        super.load()
        val looper = Looper.getMainLooper()
        val connectionHandler = Handler(looper) { message: android.os.Message ->
            val data = message.data
            val connectionState = ConnectionState.values()[data.getInt("state")]
            if (connectionState == ConnectionState.CONNECTED && connectCall != null) {
                val device = data.getParcelable<BTDevice>("device")
                connectCall!!.resolve(JSObject().put("device", device!!.toJSObject()))
                connectCall = null
            }
            if (message.what == Message.ERROR && connectCall != null) {
                val error = data.getString("error")
                connectCall!!.reject(error)
                connectCall = null
            }
            val result = JSObject().put("state", connectionState.value())
            notifyListeners("connectionChange", result)
            false
        }
        val writeHandler = Handler(looper) { message: android.os.Message ->
            if (message.what == Message.SUCCESS && writeCall != null) {
                writeCall!!.resolve()
            } else if (writeCall != null) {
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
    override fun checkPermissions(pluginCall: PluginCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            super.checkPermissions(pluginCall)
        } else {
            checkCompatPermissions(pluginCall)
        }
    }

    /*
    * Since Android < R doesn't require CONNECT and SCAN permissions, this returns GRANTED for those
    * and checks the true state of COARSE_LOCATION.
    * */
    private fun checkCompatPermissions(pluginCall: PluginCall) {
        val queriedPermissions = pluginCall.getArray("permissions")
        val permissionResults = JSObject()
        try {
            for (permission in queriedPermissions.toList<Any>()) {
                val alias = permission as String
                permissionResults.put(alias, checkCompatPermission(alias))
            }
        } catch (e: JSONException) {
            Log.e(TAG, e.message!!)
        }
        pluginCall.resolve(permissionResults)
    }

    private fun checkCompatPermission(permissionAlias: String): PermissionState {
        var permissionState = PermissionState.GRANTED
        // CONNECT and SCAN are granted as they are not supported by this version of Android
        if (permissionAlias == LOCATION) {
            permissionState = getPermissionState(permissionAlias)
        }
        return permissionState
    }

    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value") as String
        val ret = JSObject().put("value", implementation.echo(value))
        call.resolve(ret)
    }

    @PluginMethod
    fun connect(call: PluginCall) {
        if (hasCompatPermission(CONNECT)) {
            connectToDevice(call)
        } else {
            requestPermissionForAlias(CONNECT, call, "connectPermsCallback")
        }
    }

    private fun connectToDevice(call: PluginCall) {
        val macAddress = call.getString("address")
        val device = implementation.getRemoteDevice(macAddress)
        if (device != null) {
            connectCall = call
            implementation.connect(device)
            buffer.setLength(0)
        } else {
            call.reject("Could not connect to $macAddress")
        }
    }

    @PermissionCallback
    private fun connectPermsCallback(call: PluginCall) {
        if (getPermissionState(CONNECT) == PermissionState.GRANTED) {
            connectToDevice(call)
        } else {
            call.reject("Connect permission denied")
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
        val data = call.data["data"] as ByteArray
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
        if (getPermissionState(LOCATION) == PermissionState.GRANTED) {
            enableBluetooth(call)
        } else {
            requestPermissionForAlias(LOCATION, call, "enablePermsCallback")
        }
    }

    private fun enableBluetooth(call: PluginCall) {
        val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(call, enableIntent, "enableBluetoothActivityCallback")
    }

    @PermissionCallback
    private fun enablePermsCallback(call: PluginCall) {
        if (getPermissionState(LOCATION) == PermissionState.GRANTED) {
            enableBluetooth(call)
        } else {
            call.reject("Location permission denied")
        }
    }

    @ActivityCallback
    private fun enableBluetoothActivityCallback(call: PluginCall, activityResult: ActivityResult) {
        val isEnabled = activityResult.resultCode == Activity.RESULT_OK
        Log.d(TAG, "User enabled Bluetooth: $isEnabled")
        val result = JSObject().put("isEnabled", isEnabled)
        call.resolve(result)
    }

    @PluginMethod
    fun list(call: PluginCall) {
        if (hasCompatPermission(CONNECT)) {
            listPairedDevices(call)
        } else {
            requestPermissionForAlias(CONNECT, call, "listPermsCallback")
        }
    }

    @PermissionCallback
    private fun listPermsCallback(call: PluginCall) {
        if (getPermissionState(CONNECT) == PermissionState.GRANTED) {
            listPairedDevices(call)
        } else {
            call.reject("Connect permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun listPairedDevices(call: PluginCall) {
        val devices = implementation.bondedDevices.map { deviceToJSON(it) }
        val result = JSObject().put("devices", JSArray(devices))
        call.resolve(result)
    }

    @PluginMethod
    fun discoverUnpaired(call: PluginCall) {
        if (hasCompatPermission(SCAN)) {
            startDiscovery(call)
        } else {
            requestPermissionForAlias(SCAN, call, "discoverPermsCallback")
        }
    }

    @PermissionCallback
    private fun discoverPermsCallback(call: PluginCall) {
        if (getPermissionState(SCAN) == PermissionState.GRANTED) {
            startDiscovery(call)
        } else {
            call.reject("Scan permission denied")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery(call: PluginCall) {
        val discoverReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            private val unpairedDevices = JSONArray()
            private val result = JSObject().put("devices", unpairedDevices)
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    unpairedDevices.put(deviceToJSON(device!!))
                    result.put("devices", unpairedDevices)
                    notifyListeners("discoverUnpaired", result)
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                    call.resolve(result)
                    activity.unregisterReceiver(this)
                }
            }
        }
        activity.apply {
            registerReceiver(discoverReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
            registerReceiver(
                discoverReceiver,
                IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            )
        }
        implementation.startDiscovery()
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        implementation.resetService()
    }

    private fun hasCompatPermission(alias: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getPermissionState(alias) == PermissionState.GRANTED
        } else {
            true
        }

    companion object {
        // Debugging
        private const val TAG = "BluetoothSerial"
        const val CONNECT = "connect"
        const val SCAN = "scan"
        const val LOCATION = "location"

        @SuppressLint("MissingPermission")
        fun deviceToJSON(device: BluetoothDevice): JSObject {
            return JSObject()
                .put("name", device.name)
                .put("address", device.address)
                .put("deviceClass", device.bluetoothClass?.deviceClass ?: JSObject.NULL)
        }
    }
}