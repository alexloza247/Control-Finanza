import { registerPlugin } from '@capacitor/core';
import type { GeofencePlugin } from './definitions';

const Geofence = registerPlugin<GeofencePlugin>('Geofence');

export * from './definitions';
export { Geofence };
