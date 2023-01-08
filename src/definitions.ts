import type { PermissionState, PluginListenerHandle } from "@capacitor/core";

export interface BluetoothSerialPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  /**
   * Connects to the bluetooth device with the given address.
   * The plugin only retains one connection at a time; upon connecting to a device, while there is already an existing connection,
   * the previous device is disconnected.
   */
  connect(options: connectionOptions): Promise<void>;
  /**
   * Connects to the bluetooth device with the given address.
   * The plugin only retains one connection at a time; upon connecting to a device, while there is already an existing connection,
   * the previous device is disconnected.
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
   */
  enable(): Promise<{ isEnabled: boolean }>;
  /**
   * Opens the native bluetooth settings activity. Resolves immediately upon being called.
   */
  settings(): Promise<void>;
  /**
   * Returns a list of bonded devices. This includes devices that were previously paired with the user's device
   */
  list(): Promise<devices>;
  /**
   * Begins the discovery of nearby devices and resolves with them once discovery is finished.
   * There may only be one discovery process at a time.
   * @throws "Discovery cancelled" if discovery is cancelled either by calling discoverUnpaired() while there is a previous
   * discoverUnpaired() call in progress, or by calling cancelDiscovery().
   */
  discoverUnpaired(): Promise<devices>;
  /**
   * Cancels current unpaired devices discovery, if there is one in progress. If there is no discovery in progress, resolves with void.
   * Be sure to note that calling this will reject any existing discoverUnpaired() call which hasn't resolved yet.
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
  class?: number;
}

export type permissions = 'location' | 'scan' | 'connect';
export type PermissionStatus = { [permission in permissions]?: PermissionState };
export type devices = { devices: BluetoothDevice[] };
export enum ConnectionState {
  NONE, CONNECTING, CONNECTED,
}
type connectionOptions = { address: string };