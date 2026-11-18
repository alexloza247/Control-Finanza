export interface GeofencePlace {
    id: string;
    name: string;
    lat: number;
    lng: number;
    radius: number;
}
export interface GeofencePermStatus {
    fine: boolean;
    background: boolean;
    notifications: boolean;
    playServices: boolean;
}
export interface GeofencePlugin {
    syncPlaces(options: { placesJson: string }): Promise<void>;
    clearAll(): Promise<void>;
    permStatus(): Promise<GeofencePermStatus>;
    requestLocation(): Promise<GeofencePermStatus>;
    openSettings(): Promise<void>;
}
