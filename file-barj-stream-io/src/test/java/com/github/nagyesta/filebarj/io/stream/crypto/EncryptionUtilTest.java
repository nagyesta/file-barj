package com.github.nagyesta.filebarj.io.stream.crypto;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPair;
import java.util.stream.Stream;

import static com.github.nagyesta.filebarj.io.stream.internal.ChunkingFileOutputStream.MEBIBYTE;
import static org.mockito.Mockito.*;

class EncryptionUtilTest {
    private static final int LONG_STREAM_MIB = 2100;

    public static Stream<Arguments> rsaCryptoProvider() {
        final var keyPair = EncryptionUtil.generateRsaKeyPair();
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
        final var encrypted = EncryptionUtil.encryptBytes(keyPair.getPublic(), expected.getBytes());

        //when
        final var actualBytes = EncryptionUtil.decryptBytes(keyPair.getPrivate(), encrypted);

        //then
        final var actual = new String(actualBytes);
        Assertions.assertEquals(expected, actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testDecryptBytesShouldThrowExceptionWhenCalledWithNullKey() {
        //given
        final var bytes = EncryptionUtil.generateSecureRandomBytes();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> EncryptionUtil.decryptBytes(null, bytes));

        //then + exception
    }

    @Test
    void testDecryptBytesShouldThrowExceptionWhenCalledWithNullBytes() {
        //given
        final var keyPair = EncryptionUtil.generateRsaKeyPair();

        //when
        Assertions.assertThrows(CryptoException.class, () -> EncryptionUtil.decryptBytes(keyPair.getPrivate(), null));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testEncryptBytesShouldThrowExceptionWhenCalledWithNullKey() {
        //given
        final var bytes = EncryptionUtil.generateSecureRandomBytes();

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> EncryptionUtil.encryptBytes(null, bytes));

        //then + exception
    }

    @Test
    void testEncryptBytesShouldThrowExceptionWhenCalledWithNullBytes() {
        //given
        final var keyPair = EncryptionUtil.generateRsaKeyPair();

        //when
        Assertions.assertThrows(CryptoException.class, () -> EncryptionUtil.encryptBytes(keyPair.getPublic(), null));

        //then + exception
    }

    @Test
    void testGenerateAesKeyShouldReturnAGeneratedSecretKeyWhenCalled() {
        //given

        //when
        final var actual = EncryptionUtil.generateAesKey();

        //then
        Assertions.assertEquals("AES", actual.getAlgorithm());
    }

    @Test
    void testByteArrayToAesKeyShouldReturnTheAesKeyWhenCalledWithTheEncodedByteArray() {
        //given
        final var expected = EncryptionUtil.generateAesKey();

        //when
        final var actual = EncryptionUtil.byteArrayToAesKey(expected.getEncoded());

        //then
        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testByteArrayToRsaPublicKeyShouldReturnThePublicKeyWhenCalledWithTheEncodedByteArray() {
        //given
        final var keyPair = EncryptionUtil.generateRsaKeyPair();

        //when
        final var actual = EncryptionUtil.byteArrayToRsaPublicKey(keyPair.getPublic().getEncoded());

        //then
        Assertions.assertEquals(keyPair.getPublic(), actual);

    }

    @Test
    void testByteArrayToRsaPublicKeyShouldThrowExceptionWhenCalledWithNull() {
        //given

        //when
        Assertions.assertThrows(CryptoException.class, () -> EncryptionUtil.byteArrayToRsaPublicKey(null));

        //then + exception
    }

    @Test
    void testGenerateSecureRandomBytesForGcmIvShouldReturnAnArrayWithExpectedLengthWhenCalled() {
        //given
        final var expected = EncryptionUtil.GCM_IV_BYTES;

        //when
        final var actual = EncryptionUtil.generateSecureRandomBytesForGcmIv();

        //then
        Assertions.assertEquals(expected, actual.length);
    }

    @Test
    void testVerifyKeyIsAes256ShouldNotThrowExceptionWhenCalledWithAes256Key() {
        //given
        final var key = EncryptionUtil.generateAesKey();

        //when
        Assertions.assertDoesNotThrow(() -> EncryptionUtil.verifyKeyIsAes256(key));

        //then + No exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testVerifyKeyIsAes256ShouldThrowExceptionWhenCalledWithNullKey() {
        //given

        //when
        Assertions.assertThrows(IllegalArgumentException.class, () -> EncryptionUtil.verifyKeyIsAes256(null));

        //then + exception
    }

    @Test
    void testVerifyKeyIsAes256ShouldThrowExceptionWhenCalledWithNonAesKey() {
        //given
        final var key = mock(SecretKey.class);
        when(key.getAlgorithm()).thenReturn("unknown");
        when(key.getEncoded()).thenReturn(EncryptionUtil.generateSecureRandomBytes());

        //when
        Assertions.assertThrows(CryptoException.class, () -> EncryptionUtil.verifyKeyIsAes256(key));

        //then + exception
        verify(key).getAlgorithm();
    }

    @Test
    void testVerifyKeyIsAes256ShouldThrowExceptionWhenCalledWithWrongKeySize() {
        //given
        final var key = mock(SecretKey.class);
        when(key.getAlgorithm()).thenReturn("AES");
        when(key.getEncoded()).thenReturn(new byte[0]);

        //when
        Assertions.assertThrows(CryptoException.class, () -> EncryptionUtil.verifyKeyIsAes256(key));

        //then + exception
        verify(key).getEncoded();
    }

    @Test
    void testCreateCipherShouldReturnAnAesGcmCipherWhenCalledWithValidParameters()
            throws IllegalBlockSizeException, BadPaddingException {
        //given
        final var key = EncryptionUtil.generateAesKey();
        final var iv = EncryptionUtil.generateSecureRandomBytesForGcmIv();
        final var text = "a couple of words to be encrypted and decrypted during our test.";

        //when
        final var encrypt = EncryptionUtil.createCipher(key, iv, Cipher.ENCRYPT_MODE);
        final var decrypt = EncryptionUtil.createCipher(key, iv, Cipher.DECRYPT_MODE);

        //then
        final var textBytes = text.getBytes();
        final var encryptUpdate = encrypt.update(textBytes);
        final var encryptFinal = encrypt.doFinal();
        final var cipherText = concat(encryptUpdate, encryptFinal);
        final var decryptUpdate = decrypt.update(cipherText);
        final var decryptFinal = decrypt.doFinal();
        final var actual = new String(concat(decryptUpdate, decryptFinal));
        Assertions.assertEquals(text, actual);
    }

    @Test
    void testCreateCipherShouldThrowExceptionWhenCalledWithInvalidIv() {
        //given
        final var key = EncryptionUtil.generateAesKey();

        //when
        Assertions.assertThrows(CryptoException.class,
                () -> EncryptionUtil.createCipher(key, null, Cipher.ENCRYPT_MODE));

        //then + exception
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCreateCipherShouldThrowExceptionWhenCalledWithNullKey() {
        //given
        final var iv = EncryptionUtil.generateSecureRandomBytesForGcmIv();

        //when
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> EncryptionUtil.createCipher(null, iv, Cipher.ENCRYPT_MODE));

        //then + exception
    }

    @Test
    void testNewCipherOutputStreamShouldReturnIdentityFunctionWhenCalledWithNullKey() throws IOException {
        //given
        final var stream = mock(OutputStream.class);

        //when
        final var function = EncryptionUtil.newCipherOutputStream(null);

        //then
        final var actual = function.decorate(stream);
        Assertions.assertSame(stream, actual);
    }

    @Test
    void testNewCipherOutputStreamShouldReturnCipherStreamCreationFunctionWhenCalledWithValidKey() throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        final var key = EncryptionUtil.generateAesKey();

        //when
        final var function = EncryptionUtil.newCipherOutputStream(key);

        //then
        final var actual = function.decorate(stream);
        Assertions.assertNotSame(stream, actual);
    }

    @Test
    void testNewCipherOutputStreamShouldThrowExceptionWhenStreamFunctionIsCalledWithClosedStream()
            throws IOException {
        //given
        final var stream = mock(OutputStream.class);
        doThrow(new IOException()).when(stream).write(any(byte[].class));
        final var key = EncryptionUtil.generateAesKey();

        //when
        Assertions.assertThrows(CryptoException.class,
                () -> EncryptionUtil.newCipherOutputStream(key).decorate(stream));

        //then + exception
    }

    @Test
    void testNewCipherOutputStreamShouldWriteIvWhenStreamIsCreated() throws IOException {
        //given
        final var stream = new ByteArrayOutputStream();
        final var key = EncryptionUtil.generateAesKey();

        //when
        final var function = EncryptionUtil.newCipherOutputStream(key);

        //then
        final var actual = function.decorate(stream);
        stream.flush();
        final var ivBytes = stream.toByteArray();
        IOUtils.closeQuietly(actual);
        Assertions.assertEquals(EncryptionUtil.GCM_IV_BYTES, ivBytes.length);
    }

    @Tag("ci-only")
    @Test
    void testNewCipherOutputStreamShouldNotThrowExceptionWhenALongFileIsEncrypted() throws IOException {
        //given
        final var key = EncryptionUtil.generateAesKey();
        final var function = EncryptionUtil.newCipherOutputStream(key);
        try (var stream = OutputStream.nullOutputStream();
             var underTest = function.decorate(stream)) {

            //when
            final var bytes = new byte[(int) MEBIBYTE];
            for (var i = 0; i < LONG_STREAM_MIB; i++) {
                Assertions.assertDoesNotThrow(() -> underTest.write(bytes));
            }

            //then + no exception
        }
    }

    private static byte[] concat(final byte[] first, final byte[] second) {
        final var result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
