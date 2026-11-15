'use strict';

var core = require('@capacitor/core');

const BackupDownload = core.registerPlugin('BackupDownload', {
    web: () => Promise.resolve().then(function () { return web; }).then(m => new m.BackupDownloadWeb()),
});

class BackupDownloadWeb extends core.WebPlugin {
    async save() {
        throw this.unavailable('Guardar en Descargas solo está disponible en la app');
    }
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    BackupDownloadWeb: BackupDownloadWeb,
});

exports.BackupDownload = BackupDownload;
