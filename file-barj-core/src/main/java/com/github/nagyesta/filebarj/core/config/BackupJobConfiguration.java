package com.github.nagyesta.filebarj.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.nagyesta.filebarj.core.config.enums.DuplicateHandlingStrategy;
import com.github.nagyesta.filebarj.core.config.enums.HashAlgorithm;
import com.github.nagyesta.filebarj.core.json.PublicKeyDeserializer;
import com.github.nagyesta.filebarj.core.json.PublicKeySerializer;
import com.github.nagyesta.filebarj.core.model.enums.BackupType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

import java.nio.file.Path;
import java.security.PublicKey;
import java.util.Set;

/**
 * Configuration class defining the parameters of the backup/restore job.
 */
@Data
@EqualsAndHashCode
@Builder
@Jacksonized
public class BackupJobConfiguration {
    /**
     * The desired backup type which should be used when the job is executed.
     * <br/><br/>
     * NOTE: The backup will be automatically a {@link BackupType#FULL} backup
     * every time when there is no previous increment or there is a change in
     * the backup configuration since the last increment was saved. As a side
     * effect, this property is ignored during the first execution after each
     * configuration change.
     */
    @NonNull
    @JsonProperty("backup_type")
    private final BackupType backupType;
    /**
     * The algorithm used for checksum calculations before and after archival.
     * Useful for data integrity verifications.
     * <br/><br/>
     * NOTE: A change of this value requires a {@link BackupType#FULL} backup
     * as the previous increments cannot use a different hash algorithm.
     */
    @NonNull
    @JsonProperty("checksum_algorithm")
    private final HashAlgorithm checksumAlgorithm;
    /**
     * The public key of an RSA key pair used for encryption.
     * The files will be encrypted using automatically generated AES keys (DEK)
     * which will be encrypted using the RSA public key (KEK).
     * <br/><br/>
     * NOTE: A change of this value requires a {@link BackupType#FULL} backup
     * as the previous increments cannot use a different encryption key.
     */
    @JsonSerialize(using = PublicKeySerializer.class)
    @JsonDeserialize(using = PublicKeyDeserializer.class)
    @JsonProperty("encryption_key")
    private final PublicKey encryptionKey;
    /**
     * The strategy used for handling duplicate files.
     * <br/><br/>
     * NOTE: A change of this value requires a {@link BackupType#FULL} backup
     * as the previous increments cannot use a different duplicate handling
     * strategy.
     */
    @NonNull
    @JsonProperty("duplicate_strategy")
    private final DuplicateHandlingStrategy duplicateStrategy;
    /**
     * The desired maximum chunk size for the backup archive part.
     * <br/><br/>
     * NOTE: Using 0 means that the archive won't be chunked.
     */
    @EqualsAndHashCode.Exclude
    @JsonProperty("chunk_size_mebibyte")
    private final int chunkSizeMebibyte;
    /**
     * The prefix of the backup file names.
     * <br/><br/>
     * NOTE: A change of this value requires a {@link BackupType#FULL} backup
     * as the previous increments cannot use a different file name prefix.
     */
    @NonNull
    @JsonProperty("file_name_prefix")
    private final String fileNamePrefix;
    /**
     * The destination where the backup files will be saved.
     * <br/><br/>
     * NOTE: A change of this value requires a {@link BackupType#FULL} backup
     * as the metadata of the previous increments must be found in the destination
     * in order to calculate changes.
     */
    @NonNull
    @JsonProperty("destination_directory")
    private final Path destinationDirectory;
    /**
     * The source files we want to archive.
     */
    @NonNull
    @EqualsAndHashCode.Exclude
    @JsonProperty("sources")
    private final Set<BackupSource> sources;
}
