package com.github.nagyesta.filebarj.core.crypto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.stream.Stream;

class EncryptionKeyUtilTest {

    private static final int RSA_KEY_SIZE = 2048;
    private static final String RSA = "RSA";

    public static Stream<Arguments> rsaCryptoProvider() {
        final KeyPair keyPair = EncryptionKeyUtil.generateRsaKeyPair();
        return Stream.<Arguments>builder()
                .add(Arguments.of(keyPair, ""))
                .add(Arguments.of(keyPair, "a"))
                .add(Arguments.of(keyPair, "ab"))
                .add(Arguments.of(keyPair, "abcd"))
                .add(Arguments.of(keyPair, "abcdefgh"))
                .add(Arguments.of(keyPair, "lorem ipsum a longer text we will encrypt"))
                .build();
    }

    @ParameterizedTest
    @MethodSource("rsaCryptoProvider")
    void testDecryptBytesShouldReturnOriginalBytesWhenCalledOnOutputOfEncryptBytes(
            final KeyPair keyPair, final String expected) {
        //given
        final byte[] encrypted = EncryptionKeyUtil.encryptBytes(keyPair.getPublic(), expected.getBytes());

        //when
        final byte[] actualBytes = EncryptionKeyUtil.decryptBytes(keyPair.getPrivate(), encrypted);

        //then
        final String actual = new String(actualBytes);
        Assertions.assertEquals(expected, actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testDecryptBytesShouldThrowExceptionWhenCalledWithNullKey() {
        //given
        final byte[] bytes = EncryptionKeyUtil.generateSecureRandomBytes();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> EncryptionKeyUtil.decryptBytes(null, bytes));

        //then + exception
    }

    @Test
    void testDecryptBytesShouldThrowExceptionWhenCalledWithNullBytes() {
        //given
        final KeyPair keyPair = EncryptionKeyUtil.generateRsaKeyPair();

        //when
        Assertions.assertThrows(CryptoException.class, () -> EncryptionKeyUtil.decryptBytes(keyPair.getPrivate(), null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testEncryptBytesShouldThrowExceptionWhenCalledWithNullKey() {
        //given
        final byte[] bytes = EncryptionKeyUtil.generateSecureRandomBytes();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> EncryptionKeyUtil.encryptBytes(null, bytes));

        //then + exception
    }

    @Test
    void testEncryptBytesShouldThrowExceptionWhenCalledWithNullBytes() {
        //given
        final KeyPair keyPair = EncryptionKeyUtil.generateRsaKeyPair();

        //when
        Assertions.assertThrows(CryptoException.class, () -> EncryptionKeyUtil.encryptBytes(keyPair.getPublic(), null));

        //then + exception
    }

    @Test
    void testGenerateAesKeyShouldReturnAGeneratedSecretKeyWhenCalled() {
        //given

        //when
        final SecretKey actual = EncryptionKeyUtil.generateAesKey();

        //then
        Assertions.assertEquals("AES", actual.getAlgorithm());
    }

    @Test
    void testByteArrayToAesKeyShouldReturnTheAesKeyWhenCalledWithTheEncodedByteArray() {
        //given
        final SecretKey expected = EncryptionKeyUtil.generateAesKey();

        //when
        final SecretKey actual = EncryptionKeyUtil.byteArrayToAesKey(expected.getEncoded());

        //then
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testByteArrayToRsaPublicKeyShouldReturnThePublicKeyWhenCalledWithTheEncodedByteArray() {
        //given
        final KeyPair keyPair = EncryptionKeyUtil.generateRsaKeyPair();

        //when
        final PublicKey actual = EncryptionKeyUtil.byteArrayToRsaPublicKey(keyPair.getPublic().getEncoded());

        //then
        Assertions.assertEquals(keyPair.getPublic(), actual);
    }

    @Test
    void testByteArrayToRsaPublicKeyShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(CryptoException.class, () -> EncryptionKeyUtil.byteArrayToRsaPublicKey(null));

        //then + exception
    }
}
