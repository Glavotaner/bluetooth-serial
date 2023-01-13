package com.glavotaner.bluetoothserial

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.*
import android.os.Message
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.core.location.LocationManagerCompat
import com.getcapacitor.*
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.glavotaner.bluetoothserial.Message.SUCCESS
import org.json.JSONArray
import org.json.JSONException
import java.nio.charset.StandardCharsets

const val CONNECT = "connect"
const val SCAN = "scan"
const val COARSE_LOCATION = "coarseLocation"
const val FINE_LOCATION = "fineLocation"

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
        strings = [Manifest.permission.ACCESS_FINE_LOCATION],
        alias = FINE_LOCATION
    ), Permission(
        strings = [Manifest.permission.ACCESS_COARSE_LOCATION],
        alias = COARSE_LOCATION
    )]
)
class BluetoothSerialPlugin : Plugin() {
    // Debugging
    private val TAG = "BluetoothSerial"

    private var implementation: BluetoothSerial? = null
    private var connectCall: PluginCall? = null
    private var writeCall: PluginCall? = null
    private var discoveryCall: PluginCall? = null
    private var discoveryReceiver: BroadcastReceiver? = null
    private var requiresLocationForDiscovery = true
    private val discoveryPermissions: MutableList<String> = ArrayList()

    private var buffer = StringBuffer()

    override fun load() {
        super.load()
        setDiscoveryPermissions()
        val looper = Looper.getMainLooper()
        val connectionHandler = Handler(looper) { message: Message ->
            val data: Bundle = message.data
            val connectionState = ConnectionState.values()[data.getInt("state")]
            if (message.what == SUCCESS) {
                val state = JSObject().put("state", connectionState.value())
                notifyListeners("connectionChange", state)
                if (connectionState === ConnectionState.CONNECTED) {
                    connectCall?.resolve(state)
                    connectCall = null
                }
                if (connectionState === ConnectionState.NONE) connectCall = null
            } else if (connectCall != null) {
                val error: String = data.getString("error") ?: "Error"
                connectCall?.reject(error)
                connectCall = null
            }
            false
        }
        val writeHandler = Handler(looper) { message: Message ->
            if (message.what == SUCCESS) {
                writeCall?.resolve()
            } else if (writeCall !== null) {
                val error = message.data.getString("error")
                writeCall?.reject(error)
            }
            writeCall = null
            false
        }
        val readHandler = Handler(looper) { message: Message ->
            buffer.append(message.data.getString("data"))
            false
        }
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        implementation =
            BluetoothSerial(bluetoothManager.adapter, connectionHandler, writeHandler, readHandler)
    }

    private fun setDiscoveryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            discoveryPermissions.add(SCAN)
            if (config.getBoolean("neverScanForLocation", false)) {
                requiresLocationForDiscovery = false
            } else {
                discoveryPermissions.add(FINE_LOCATION)
            }
        } else {
            val requiredLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) FINE_LOCATION
            else COARSE_LOCATION
            discoveryPermissions.add(requiredLocation)
        }
    }

    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value")
        val ret = JSObject().put("value", implementation!!.echo(value!!))
        call.resolve(ret)
    }

    @PluginMethod
    fun connect(call: PluginCall) {
        connect(call) { device -> implementation!!.connect(device) }
    }

    @PluginMethod
    fun connectInsecure(call: PluginCall) {
        connect(call) { device -> implementation!!.connectInsecure(device) }
    }

    private fun connect(call: PluginCall, connect: (BluetoothDevice) -> Unit) {
        if (rejectIfBluetoothDisabled(call)) return
        connectCall?.reject("Connection interrupted")
        if (hasCompatPermission(CONNECT)) connectToDevice(call, connect)
        else requestConnectPermission(call)
    }

    private fun connectToDevice(call: PluginCall, connect: (BluetoothDevice) -> Unit) {
        val macAddress = call.getString("address")
        val device: BluetoothDevice? = try {
            implementation!!.getRemoteDevice(macAddress)
        } catch (error: IllegalArgumentException) {
            call.reject(error.message)
            return
        }
        if (device != null) {
            cancelDiscovery()
            connectCall = call
            connect(device)
            buffer.setLength(0)
        } else {
            call.reject("Could not connect to $macAddress")
        }
    }

    @PluginMethod
    fun disconnect(call: PluginCall) {
        implementation!!.resetService()
        call.resolve()
    }

    @PluginMethod
    @Throws(JSONException::class)
    fun write(call: PluginCall) {
        if (rejectIfBluetoothDisabled(call)) return
        val data: ByteArray = (call.data["data"] as String).toByteArray(StandardCharsets.UTF_8)
        writeCall = call
        implementation!!.write(data)
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
        val result = JSObject().put("isEnabled", implementation!!.isEnabled)
        call.resolve(result)
    }

    @PluginMethod
    fun isConnected(call: PluginCall) {
        val result = JSObject().put("isConnected", implementation!!.isConnected())
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
        Log.d(TAG, "User enabled Bluetooth: $isEnabled")
        val result = JSObject().put("isEnabled", isEnabled)
        call.resolve(result)
    }

    @PluginMethod
    fun list(call: PluginCall) {
        if (rejectIfBluetoothDisabled(call)) return
        if (hasCompatPermission(CONNECT)) listPairedDevices(call)
        else requestConnectPermission(call)
    }

    private fun listPairedDevices(call: PluginCall) {
        val devices = implementation!!.bondedDevices.map { deviceToJSON(it) }
        val result = JSObject().put("devices", JSArray(devices))
        call.resolve(result)
    }

    @PluginMethod
    fun discoverUnpaired(call: PluginCall) {
        if (rejectIfBluetoothDisabled(call)) return
        if (requiresLocationForDiscovery && !isLocationEnabled()) {
            call.reject("Location services are not enabled")
            return
        }
        if (hasDiscoveryPermissions()) startDiscovery(call)
        else requestDiscoveryPermissions(call)
    }

    private fun isLocationEnabled(): Boolean {
        val lm: LocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    private fun hasDiscoveryPermissions(): Boolean {
        return discoveryPermissions.all { alias ->
            getPermissionState(alias) === PermissionState.GRANTED
        }
    }

    private fun requestDiscoveryPermissions(call: PluginCall) {
        val requiredPermissions = discoveryPermissions.toTypedArray()
        requestPermissionForAliases(requiredPermissions, call, "discoveryPermissionsCallback")
    }

    @PermissionCallback
    private fun discoveryPermissionsCallback(call: PluginCall) {
        for (alias in discoveryPermissions) {
            if (getPermissionState(alias) !== PermissionState.GRANTED) {
                call.reject("$alias permission denied")
                return
            }
        }
        startDiscovery(call)
    }

    @PluginMethod
    fun cancelDiscovery(call: PluginCall) {
        if (hasCompatPermission(SCAN)) {
            cancelDiscovery()
            call.resolve()
        } else {
            requestPermissionForAlias(SCAN, call, "scanPermissionCallback")
        }
    }

    private fun cancelDiscovery() {
        if (discoveryCall != null) {
            discoveryCall!!.reject("Discovery cancelled")
            discoveryCall = null
            implementation!!.cancelDiscovery()
            activity.unregisterReceiver(discoveryReceiver)
            discoveryReceiver = null
        }
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
                else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        activity.registerReceiver(discoveryReceiver, filter)
        implementation!!.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun deviceToJSON(device: BluetoothDevice): JSObject? {
        val btClass: BluetoothClass? = device.bluetoothClass
        return JSObject()
            .put("address", device.address)
            .put("name", device.name)
            .put("deviceClass", btClass?.deviceClass ?: JSObject.NULL)
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
        if (getPermissionState(SCAN) == PermissionState.GRANTED) cancelDiscovery(call)
        else call.reject("Scan permission denied")
    }

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) super.checkPermissions(call)
        else checkCompatPermissions(call)
    }

    private fun checkCompatPermissions(call: PluginCall) {
        // scan and connect don't exist on older versions of Android so we only check location
        val permissions = JSObject()
            .put(COARSE_LOCATION, getPermissionState(COARSE_LOCATION))
            .put(FINE_LOCATION, getPermissionState(FINE_LOCATION))
            .put(SCAN, PermissionState.GRANTED)
            .put(CONNECT, PermissionState.GRANTED)
        call.resolve(permissions)
    }

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) super.requestPermissions(call)
        else requestCompatPermissions(call)
    }

    private fun requestCompatPermissions(call: PluginCall) {
        try {
            val requestedPermissions = call.getArray("permissions").toList<String>()
            val locationPermissions =
                requestedPermissions.filter { alias -> alias.contains("Location") }
            if (locationPermissions.isNotEmpty()) {
                requestPermissionForAliases(
                    locationPermissions.toTypedArray(),
                    call,
                    "requestCompatPermissionCallback"
                )
            } else {
                val permissions = JSObject().apply {
                    for (permission in requestedPermissions) {
                        put(permission, PermissionState.GRANTED)
                    }
                }
                call.resolve(permissions)
            }
        } catch (exception: JSONException) {
            call.reject(exception.message)
        }
    }

    @PermissionCallback
    private fun requestCompatPermissionCallback(call: PluginCall) {
        try {
            val requestedPermissions = call.getArray("permissions").toList<String>()
            val permissions = JSObject()
            for (alias in requestedPermissions) {
                if (alias.contains("Location")) {
                    permissions.put(alias, getPermissionState(alias))
                } else {
                    permissions.put(alias, PermissionState.GRANTED)
                }
            }
            call.resolve(permissions)
        } catch (exception: JSONException) {
            call.reject(exception.message)
        }
    }

    // This is called only for permissions that may not exist on older Android versions,
    // otherwise getPermissionState(alias) is used
    private fun hasCompatPermission(alias: String): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            getPermissionState(alias) == PermissionState.GRANTED
        else
            true
    }

    private fun rejectIfBluetoothDisabled(call: PluginCall): Boolean {
        val disabled = !implementation!!.isEnabled
        if (disabled) {
            call.reject("Bluetooth is not enabled")
        }
        return disabled
    }

    private fun requestConnectPermission(call: PluginCall) {
        requestPermissionForAlias(CONNECT, call, "connectPermissionCallback")
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        implementation?.resetService()
    }

}