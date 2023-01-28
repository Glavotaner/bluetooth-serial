import type { PermissionState, PluginListenerHandle } from "@capacitor/core";

export interface BluetoothSerialPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  /**
   * Creates a secure connection (https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createRfcommSocketToServiceRecord(java.util.UUID)) to the bluetooth device with the given address.
   * The plugin only retains one connection at a time; upon connecting to a device, while there is already an existing connection,
   * the previous device is disconnected. If there is already a running connect call that hasn't resolved, and a new one starts, the original will reject with "Connection interrupted".
   * Requires CONNECT permission on Android API >= 30
   */
  connect(options: connectionOptions): Promise<void>;
  /**
   * Creates an insecure connection (https://developer.android.com/reference/android/bluetooth/BluetoothDevice#createInsecureRfcommSocketToServiceRecord(java.util.UUID)) to the bluetooth device with the given address.
   * The plugin only retains one connection at a time; upon connecting to a device, while there is already an existing connection,
   * the previous device is disconnected. If there is already a running connect call that hasn't resolved, and a new one starts, the original will reject with "Connection interrupted".
   * Requires CONNECT permission on Android API >= 30
   */
  connectInsecure(options: connectionOptions): Promise<void>;
  /**
   * Disconnects from the currently connected device.
   * This may be called while there is no connected device; in that case, the method will resolve with void.
   */
  disconnect(): Promise<void>;
  /**
   * Returns data emitted from the currently connected device.
   */
  read(): Promise<{ data: string }>;
  /**
   * Writes data to the currently connected device.
   */
  write(options: { data: string }): Promise<void>;
  /**
   * Returns the length of the data that can be read by calling read().
   */
  available(): Promise<{ available: number }>;
  /**
   * Returns true or false depending on whether bluetooth is enabled.
   */
  isEnabled(): Promise<{ isEnabled: boolean }>;
  /**
   * Returns true or false depending on whether the plugin is currently connected to a device.
   */
  isConnected(): Promise<{ isConnected: boolean }>;
  /**
   * Clears the data readable by calling read().
   */
  clear(): Promise<void>;
  /**
   * Displays the native prompt for enabling bluetooth. Returns true or false depending on whether the user enabled bluetooth.
   * Requires CONNECT permission on Android API >= 30
   */
  enable(): Promise<{ isEnabled: boolean }>;
  /**
   * Opens the native bluetooth settings activity. Resolves immediately upon being called.
   */
  settings(): Promise<void>;
  /**
   * Returns a list of bonded devices. This includes devices that were previously paired with the user's device
   * Requires CONNECT permission on Android API >= 30
   */
  list(): Promise<devices>;
  /**
   * Begins the discovery of nearby devices and resolves with them once discovery is finished.
   * There may only be one discovery process at a time. If another call starts while there is a discovery in progress,
   * the original call will resolve with "Discovery cancelled".
   * 
   * On Android API >= 30 requires SCAN, CONNECT and FINE_LOCATION permissions.
   * You can declare in your manifest that scanning for devices is not used to derive the user's location. In that case, you may also
   * add the following into your capacitor.config.ts to indicate that the plugin should not require FINE_LOCATION:
   * 
   * BluetoothSerial: {
   *  neverScanForLocation: true,
   * }
   * 
   * In that case, only SCAN and CONNECT are required.
   * 
   * On Android 10 and 11, only FINE_LOCATION is required.
   * 
   * On lower versions, only COARSE_LOCATION is required.
   * 
   * The versions of Android that require location permissions, also require location services to be enabled.
   * So this plugin will reject with "Location services not enabled" if the device requires location for scanning, but it is disabled.
   * 
   * https://developer.android.com/guide/topics/connectivity/bluetooth/permissions
   */
  discoverUnpaired(): Promise<devices>;
  /**
   * Cancels current unpaired devices discovery, if there is one in progress. If there is no discovery in progress, resolves with void.
   * Be sure to note that calling this will reject any existing discoverUnpaired() call which hasn't resolved yet.
   * Requires SCAN permission on Android API >= 30
   */
  cancelDiscovery(): Promise<void>;
  /**
   * Takes into account the fact that SCAN and CONNECT permissions only exist on Android 11+; those permissions will always resolve as GRANTED
   * on devices below Android 11.
   */
  checkPermissions(): Promise<PermissionStatus[]>;
  /**
   * Takes into account the fact that SCAN and CONNECT permissions only exist on Android 11+; those permissions will always resolve as GRANTED
   * on devices below Android 11.
   */
  requestPermissions(options: { permissions: permissions[] }): Promise<PermissionStatus[]>;
  addListener(event: 'discoverUnpaired', listenerFunc: (event: devices) => any): Promise<PluginListenerHandle> & PluginListenerHandle;
  addListener(event: 'connectionChange', listenerFunc: (event: { state: ConnectionState }) => any): Promise<PluginListenerHandle> & PluginListenerHandle;
  removeAllListeners(): Promise<void>;
}

export interface BluetoothDevice {
  address: string;
  name?: string;
  // you may use this property to conclude what sort of device the connected device is
  deviceClass?: number;
}

export type permissions = 'coarseLocation' | 'fineLocation' | 'scan' | 'connect';
export type PermissionStatus = { [permission in permissions]?: PermissionState };
export type devices = { devices: BluetoothDevice[] };
export enum ConnectionState {
  NONE, CONNECTING, CONNECTED,
}
type connectionOptions = { address: string };