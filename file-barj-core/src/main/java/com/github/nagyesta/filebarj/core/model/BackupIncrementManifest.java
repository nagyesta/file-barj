package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.nagyesta.filebarj.core.config.BackupJobConfiguration;
import com.github.nagyesta.filebarj.core.crypto.EncryptionKeyUtil;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Models the root of the backup increment metadata.
 * <br/><br/>
 * This manifest contains every piece of metadata known about the
 * original files and their archived variants.
 */
@Data
@Builder
@Jacksonized
public class BackupIncrementManifest {
    /**
     * The version numbers of the backup increments.
     * <br/><br/>
     * THe full backups use the index 0, every subsequent incremental
     * backup increments the version by 1. A manifest can contain more
     * numbers if the backup increments were merged (consolidated)
     * into a single archive.
     */
    @NonNull
    @JsonProperty("versions")
    private Set<Integer> versions;
    /**
     * The time when the backup process was started in UTC epoch
     * seconds.
     */
    @JsonProperty("start_time_utc_epoch_seconds")
    private long startTimeUtcEpochSeconds;
    /**
     * The file name prefix used by the backup archives.
     */
    @NonNull
    @JsonProperty("file_name_prefix")
    private String fileNamePrefix;
    /**
     * The type of the backup.
     */
    @NonNull
    @JsonProperty("backup_type")
    private BackupType backupType;
    /**
     * The snapshot of the backup configuration at the time of backup.
     */
    @NonNull
    @JsonProperty("job_configuration")
    private BackupJobConfiguration configuration;
    /**
     * The map of matching files identified during backup keyed by Id.
     */
    @JsonProperty("files")
    private Map<UUID, FileMetadata> files;
    /**
     * The map of archive entries saved during backup keyed by Id..
     */
    @JsonProperty("archive_entries")
    private Map<UUID, ArchivedFileMetadata> archivedEntries;
    /**
     * The byte array containing the data encryption key (DEK)
     * encrypted with the key encryption key (KEK).
     */
    @JsonProperty("encryption_key")
    private byte[] encryptionKey;

    /**
     * Decrypts the byte array stored in {@link #encryptionKey} using the
     * provided kekPrivateKey.
     *
     * @param kekPrivateKey The private key we need to use for decryption.
     * @return The decrypted DEK
     */
    @JsonIgnore
    public SecretKey dataEncryptionKey(final PrivateKey kekPrivateKey) {
        final byte[] decryptedBytes = EncryptionKeyUtil.decryptBytes(kekPrivateKey, encryptionKey);
        return EncryptionKeyUtil.byteArrayToAesKey(decryptedBytes);
    }

    /**
     * Generates a new DEK and overwrites the value stored in the
     * {@link #encryptionKey} field after encrypting the DEK with the
     * provided KEK.
     *
     * @param kekPublicKey The KEK we will use for encrypting the DEK.
     * @return The generated DEK.
     */
    @JsonIgnore
    public SecretKey generateDataEncryptionKey(final PublicKey kekPublicKey) {
        final SecretKey secureRandomKey = EncryptionKeyUtil.generateAesKey();
        encryptionKey = EncryptionKeyUtil.encryptBytes(kekPublicKey, secureRandomKey.getEncoded());
        return secureRandomKey;
    }
}
