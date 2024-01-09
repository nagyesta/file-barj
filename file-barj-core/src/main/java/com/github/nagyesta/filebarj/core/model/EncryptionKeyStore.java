package com.github.nagyesta.filebarj.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.nagyesta.filebarj.io.stream.crypto.EncryptionUtil;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contains the encryption keys used for a backup.
 */
@Data
@SuperBuilder
@Jacksonized
public class EncryptionKeyStore {
    /**
     * The number of Data Encryption Keys generated for this manifest.
     */
    public static final int DEK_COUNT = 16;
    /**
     * Base64 encoder for key encoding.
     */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    /**
     * Base64 decoder for key decoding.
     */
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
    /**
     * The version numbers of the backup increments.
     * <br/><br/>
     * THe full backups use the index 0, every subsequent incremental backup increments the version
     * by 1. A manifest can contain more numbers if the backup increments were merged (consolidated)
     * into a single archive.
     */
    @NonNull
    @JsonProperty("backup_versions")
    private SortedSet<Integer> versions;
    /**
     * The byte arrays containing the data encryption keys (DEK) encrypted with the key encryption
     * key (KEK).
     */
    @JsonProperty("encryption_keys")
    private Map<Integer, Map<Integer, String>> encryptionKeys;
    /**
     * The unencrypted data encryption keys (DEK) which were just generated. This property is not
     * serialized.
     */
    @EqualsAndHashCode.Exclude
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private Map<Integer, Map<Integer, SecretKey>> rawEncryptionKeys;

    /**
     * Decrypts the byte arrays stored in {@link #encryptionKeys} using the provided kekPrivateKey
     * and returns the key which was used for data index encryption.
     *
     * @param kekPrivateKey The private key we need to use for decryption.
     * @param version       The version of the backup
     * @return The decrypted DEK
     */
    @NotNull
    @JsonIgnore
    public SecretKey dataIndexDecryptionKey(
            @NonNull final PrivateKey kekPrivateKey, final int version) {
        return decryptionKeyByIndex(kekPrivateKey, version, 0);
    }

    /**
     * Decrypts the byte arrays stored in {@link #encryptionKeys} using the provided kekPrivateKey
     * and returns the key which is assigned to the provided entryLocator.
     *
     * @param kekPrivateKey The private key we need to use for decryption.
     * @param entryLocator  The name of the entry inside the archive
     * @return The decrypted DEK
     */
    @NotNull
    @JsonIgnore
    public SecretKey dataDecryptionKey(
            @NonNull final PrivateKey kekPrivateKey,
            @NonNull final ArchiveEntryLocator entryLocator) {
        final var index = selectKeyIndex(entryLocator.getEntryName());
        return decryptionKeyByIndex(kekPrivateKey, entryLocator.getBackupIncrement(), index);
    }

    /**
     * Generates a new DEK and overwrites the values stored in the {@link #encryptionKeys} field
     * after encrypting the DEKs with the provided KEK.
     *
     * @param kekPublicKey The KEK we will use for encrypting the DEKs.
     * @return The generated DEKs.
     */
    @NotNull
    @JsonIgnore
    public Map<Integer, SecretKey> generateDataEncryptionKeys(@NonNull final PublicKey kekPublicKey) {
        final Map<Integer, SecretKey> keys = new HashMap<>();
        final Map<Integer, String> encodedKeys = new HashMap<>();
        for (var i = 0; i < DEK_COUNT; i++) {
            final var dek = EncryptionUtil.generateAesKey();
            keys.put(i, dek);
            encodedKeys.put(i, encryptKey(kekPublicKey, dek));
        }

        final var encrypted = Collections.unmodifiableMap(encodedKeys);
        final var raw = Collections.unmodifiableMap(keys);
        encryptionKeys = new ConcurrentHashMap<>();
        rawEncryptionKeys = new ConcurrentHashMap<>();
        versions.forEach(version -> {
            encryptionKeys.put(version, encrypted);
            rawEncryptionKeys.put(version, raw);
        });
        return raw;
    }

    /**
     * Returns an appropriate key for the data index encryption.
     *
     * @return The optional key
     */
    @Nullable
    @JsonIgnore
    public SecretKey dataIndexEncryptionKey() {
        return Optional.ofNullable(rawEncryptionKeys)
                .map(map -> map.get(versions.first()))
                .map(map -> map.get(0))
                .orElse(null);
    }

    /**
     * Returns an appropriate key for the provided fileId.
     *
     * @param archiveEntryName The name of the entry inside the archive
     * @return The optional key
     */
    @Nullable
    @JsonIgnore
    public SecretKey dataEncryptionKey(@NonNull final ArchiveEntryLocator archiveEntryName) {
        final var index = selectKeyIndex(archiveEntryName.getEntryName());
        return Optional.ofNullable(rawEncryptionKeys)
                .map(map -> map.get(archiveEntryName.getBackupIncrement()))
                .map(map -> map.get(index))
                .orElse(null);
    }

    private SecretKey decryptionKeyByIndex(@NotNull final PrivateKey kekPrivateKey, final int version, final int index) {
        if (rawEncryptionKeys == null) {
            rawEncryptionKeys = new ConcurrentHashMap<>();
        }
        if (rawEncryptionKeys.containsKey(version)) {
            final var versionMap = rawEncryptionKeys.get(version);
            if (versionMap.containsKey(index)) {
                return versionMap.get(index);
            }
        } else {
            rawEncryptionKeys.put(version, new ConcurrentHashMap<>());
        }
        final var dek = BASE64_DECODER.decode(encryptionKeys.get(version).get(index));
        final var decryptedBytes = EncryptionUtil.decryptBytes(kekPrivateKey, dek);
        final var secretKey = EncryptionUtil.byteArrayToAesKey(decryptedBytes);
        rawEncryptionKeys.get(version).put(index, secretKey);
        return secretKey;
    }

    private int selectKeyIndex(final UUID fileId) {
        return Math.abs((int) fileId.getLeastSignificantBits() % DEK_COUNT);
    }

    private String encryptKey(final PublicKey kekPublicKey, final SecretKey key) {
        final var dek = key.getEncoded();
        final var encrypted = EncryptionUtil.encryptBytes(kekPublicKey, dek);
        return BASE64_ENCODER.encodeToString(encrypted);
    }
}
