package com.glavotaner.bluetoothserial

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.getcapacitor.JSObject

class BTDevice : Parcelable {
    private val address: String
    private val name: String?
    private val deviceClass: Int?

    internal constructor(address: String, name: String?, deviceClass: Int?) {
        this.address = address
        this.name = name
        this.deviceClass = deviceClass
    }

    internal constructor(`in`: Parcel) {
        (`in`.readBundle(javaClass.classLoader)!!).apply {
            address = getString("address")!!
            name = getString("name")
            deviceClass = if (containsKey("deviceClass")) getInt("deviceClass") else null
        }
    }

    override fun writeToParcel(parcel: Parcel, i: Int) {
        val bundle = Bundle().apply {
            putString("address", address)
            putString("name", name)
            deviceClass?.let { putInt("deviceClass", deviceClass) }
        }
        parcel.writeBundle(bundle)
    }

    override fun describeContents() = 0

    fun toJSObject(): JSObject = JSObject()
            .put("address", address)
            .put("name", name)
            .put("deviceClass", deviceClass ?: JSObject.NULL)

    companion object CREATOR : Parcelable.Creator<BTDevice> {
        override fun createFromParcel(parcel: Parcel): BTDevice {
            return BTDevice(parcel)
        }

        override fun newArray(i: Int): Array<BTDevice?> {
            return arrayOfNulls(i)
        }
    }
}