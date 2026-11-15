export interface BackupDownloadPlugin {
    save(options: { filename: string; data: string }): Promise<{ uri: string; name: string }>;
}
