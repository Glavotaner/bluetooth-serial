# bluetooth-serial

Use native bluetooth functionality

## Install

```bash
npm install bluetooth-serial
npx cap sync
```

## API

<docgen-index>

* [`echo(...)`](#echo)
* [`connect(...)`](#connect)
* [`connectInsecure(...)`](#connectinsecure)
* [`disconnect()`](#disconnect)
* [`read()`](#read)
* [`write(...)`](#write)
* [`available()`](#available)
* [`isEnabled()`](#isenabled)
* [`isConnected()`](#isconnected)
* [`clear()`](#clear)
* [`enable()`](#enable)
* [`settings()`](#settings)
* [`list()`](#list)
* [`discoverUnpaired()`](#discoverunpaired)
* [`cancelDiscovery()`](#canceldiscovery)
* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions(...)`](#requestpermissions)
* [`addListener('discoverUnpaired', ...)`](#addlistenerdiscoverunpaired)
* [`addListener('connectionChange', ...)`](#addlistenerconnectionchange)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => Promise<{ value: string; }>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>Promise&lt;{ value: string; }&gt;</code>

--------------------


### connect(...)

```typescript
connect(options: connectionOptions) => Promise<void>
```

Connects to the bluetooth device with the given address.
The plugin only retains one connection at a time; upon connecting to a device, while there is already an existing connection,
the previous device is disconnected.

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#connectionoptions">connectionOptions</a></code> |

--------------------


### connectInsecure(...)

```typescript
connectInsecure(options: connectionOptions) => Promise<void>
```

Connects to the bluetooth device with the given address.
The plugin only retains one connection at a time; upon connecting to a device, while there is already an existing connection,
the previous device is disconnected.

| Param         | Type                                                            |
| ------------- | --------------------------------------------------------------- |
| **`options`** | <code><a href="#connectionoptions">connectionOptions</a></code> |

--------------------


### disconnect()

```typescript
disconnect() => Promise<void>
```

Disconnects from the currently connected device.
This may be called while there is no connected device; in that case, the method will resolve with void.

--------------------


### read()

```typescript
read() => Promise<{ data: string; }>
```

Returns data emitted from the currently connected device.

**Returns:** <code>Promise&lt;{ data: string; }&gt;</code>

--------------------


### write(...)

```typescript
write(options: { data: string; }) => Promise<void>
```

Writes data to the currently connected device.

| Param         | Type                           |
| ------------- | ------------------------------ |
| **`options`** | <code>{ data: string; }</code> |

--------------------


### available()

```typescript
available() => Promise<{ available: number; }>
```

Returns the length of the data that can be read by calling read().

**Returns:** <code>Promise&lt;{ available: number; }&gt;</code>

--------------------


### isEnabled()

```typescript
isEnabled() => Promise<{ isEnabled: boolean; }>
```

Returns true or false depending on whether bluetooth is enabled.

**Returns:** <code>Promise&lt;{ isEnabled: boolean; }&gt;</code>

--------------------


### isConnected()

```typescript
isConnected() => Promise<{ isConnected: boolean; }>
```

Returns true or false depending on whether the plugin is currently connected to a device.

**Returns:** <code>Promise&lt;{ isConnected: boolean; }&gt;</code>

--------------------


### clear()

```typescript
clear() => Promise<void>
```

Clears the data readable by calling read().

--------------------


### enable()

```typescript
enable() => Promise<{ isEnabled: boolean; }>
```

Displays the native prompt for enabling bluetooth. Returns true or false depending on whether the user enabled bluetooth.

**Returns:** <code>Promise&lt;{ isEnabled: boolean; }&gt;</code>

--------------------


### settings()

```typescript
settings() => Promise<void>
```

Opens the native bluetooth settings activity. Resolves immediately upon being called.

--------------------


### list()

```typescript
list() => Promise<devices>
```

Returns a list of bonded <a href="#devices">devices</a>. This includes <a href="#devices">devices</a> that were previously paired with the user's device

**Returns:** <code>Promise&lt;<a href="#devices">devices</a>&gt;</code>

--------------------


### discoverUnpaired()

```typescript
discoverUnpaired() => Promise<devices>
```

Begins the discovery of nearby <a href="#devices">devices</a> and resolves with them once discovery is finished.
There may only be one discovery process at a time.

**Returns:** <code>Promise&lt;<a href="#devices">devices</a>&gt;</code>

--------------------


### cancelDiscovery()

```typescript
cancelDiscovery() => Promise<void>
```

Cancels current unpaired <a href="#devices">devices</a> discovery, if there is one in progress. If there is no discovery in progress, resolves with void.
Be sure to note that calling this will reject any existing discoverUnpaired() call which hasn't resolved yet.

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionStatus[]>
```

Takes into account the fact that SCAN and CONNECT <a href="#permissions">permissions</a> only exist on Android 11+; those <a href="#permissions">permissions</a> will always resolve as GRANTED
on <a href="#devices">devices</a> below Android 11.

**Returns:** <code>Promise&lt;PermissionStatus[]&gt;</code>

--------------------


### requestPermissions(...)

```typescript
requestPermissions(options: { permissions: permissions[]; }) => Promise<PermissionStatus[]>
```

Takes into account the fact that SCAN and CONNECT <a href="#permissions">permissions</a> only exist on Android 11+; those <a href="#permissions">permissions</a> will always resolve as GRANTED
on <a href="#devices">devices</a> below Android 11.

| Param         | Type                                                                    |
| ------------- | ----------------------------------------------------------------------- |
| **`options`** | <code>{ <a href="#permissions">permissions</a>: permissions[]; }</code> |

**Returns:** <code>Promise&lt;PermissionStatus[]&gt;</code>

--------------------


### addListener('discoverUnpaired', ...)

```typescript
addListener(event: 'discoverUnpaired', listenerFunc: (event: devices) => any) => Promise<PluginListenerHandle> & PluginListenerHandle
```

| Param              | Type                                                           |
| ------------------ | -------------------------------------------------------------- |
| **`event`**        | <code>'discoverUnpaired'</code>                                |
| **`listenerFunc`** | <code>(event: <a href="#devices">devices</a>) =&gt; any</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### addListener('connectionChange', ...)

```typescript
addListener(event: 'connectionChange', listenerFunc: (event: { state: ConnectionState; }) => any) => Promise<PluginListenerHandle> & PluginListenerHandle
```

| Param              | Type                                                                                       |
| ------------------ | ------------------------------------------------------------------------------------------ |
| **`event`**        | <code>'connectionChange'</code>                                                            |
| **`listenerFunc`** | <code>(event: { state: <a href="#connectionstate">ConnectionState</a>; }) =&gt; any</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### BluetoothDevice

| Prop          | Type                |
| ------------- | ------------------- |
| **`address`** | <code>string</code> |
| **`name`**    | <code>string</code> |
| **`class`**   | <code>number</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### connectionOptions

<code>{ address: string }</code>


#### devices

<code>{ <a href="#devices">devices</a>: BluetoothDevice[] }</code>


#### PermissionStatus

<code>{ [permission in permissions]?: <a href="#permissionstate">PermissionState</a> }</code>


#### permissions

<code>'location' | 'scan' | 'connect'</code>


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


### Enums


#### ConnectionState

| Members          |
| ---------------- |
| **`NONE`**       |
| **`CONNECTING`** |
| **`CONNECTED`**  |

</docgen-api>
