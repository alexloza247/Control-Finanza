import { registerPlugin } from '@capacitor/core';
import type { BackupDownloadPlugin } from './definitions';

const BackupDownload = registerPlugin<BackupDownloadPlugin>('BackupDownload');

export * from './definitions';
export { BackupDownload };
