export interface BackupDownloadPlugin {
    save(options: { filename: string; data: string; mime?: string; base64?: boolean; folder?: string }): Promise<{ uri: string; name: string }>;
}
