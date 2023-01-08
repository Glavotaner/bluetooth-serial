/* eslint-disable @typescript-eslint/no-unused-vars */
import { WebPlugin } from '@capacitor/core';

import type { BluetoothSerialPlugin, devices, permissions, PermissionStatus } from './definitions';

export class BluetoothSerialWeb
  extends WebPlugin
  implements BluetoothSerialPlugin {
  connect(_options: { address: string; }): Promise<void> {
    throw new Error('Method not implemented.');
  }
  connectInsecure(_options: { address: string; }): Promise<void> {
    throw new Error('Method not implemented.');
  }
  disconnect(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  read(): Promise<{ data: string; }> {
    throw new Error('Method not implemented.');
  }
  write(_options: { data: string; }): Promise<void> {
    throw new Error('Method not implemented.');
  }
  available(): Promise<{ available: number }> {
    throw new Error('Method not implemented.');
  }
  isEnabled(): Promise<{ isEnabled: boolean }> {
    throw new Error('Method not implemented.');
  }
  isConnected(): Promise<{ isConnected: boolean }> {
    throw new Error('Method not implemented.');
  }
  clear(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  enable(): Promise<{ isEnabled: boolean; }> {
    throw new Error('Method not implemented.');
  }
  settings(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  list(): Promise<devices> {
    throw new Error('Method not implemented.');
  }
  discoverUnpaired(): Promise<devices> {
    throw new Error('Method not implemented.');
  }
  cancelDiscovery(): Promise<void> {
    throw new Error('Method not implemented.');
  }
  checkPermissions(): Promise<PermissionStatus[]> {
    throw new Error('Method not implemented.');
  }
  requestPermissions(_options: { permissions: permissions[]; }): Promise<PermissionStatus[]> {
    throw new Error('Method not implemented.');
  }
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
