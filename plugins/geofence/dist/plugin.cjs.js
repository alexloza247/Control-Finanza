'use strict';

var core = require('@capacitor/core');

const Geofence = core.registerPlugin('Geofence', {
    web: () => Promise.resolve().then(function () { return web; }).then(m => new m.GeofenceWeb()),
});

class GeofenceWeb extends core.WebPlugin {
    async syncPlaces() {
        throw this.unavailable('Las geocercas solo están disponibles en la app');
    }
    async clearAll() {
        throw this.unavailable('Las geocercas solo están disponibles en la app');
    }
    async permStatus() {
        throw this.unavailable('Las geocercas solo están disponibles en la app');
    }
    async requestLocation() {
        throw this.unavailable('Las geocercas solo están disponibles en la app');
    }
    async openSettings() {
        throw this.unavailable('Las geocercas solo están disponibles en la app');
    }
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    GeofenceWeb: GeofenceWeb,
});

exports.Geofence = Geofence;
