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
    /** Sincroniza las geocercas activas: registra las de la lista y quita las demás. */
    syncPlaces(options: { placesJson: string }): Promise<void>;
    /** Quita todas las geocercas registradas. */
    clearAll(): Promise<void>;
    /** Estado actual de permisos y disponibilidad de Play Services. */
    permStatus(): Promise<GeofencePermStatus>;
    /** Pide el permiso de ubicación (y el de notificaciones en Android 13+) y resuelve con el estado resultante. */
    requestLocation(): Promise<GeofencePermStatus>;
    /** Abre la pantalla de ajustes de la app (para conceder ubicación en segundo plano). */
    openSettings(): Promise<void>;
}
