package com.se_lab.project.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// 사진에 담긴 촬영 위치가 저장·공개되지 않는지 확인한다.
class ImageMetadataStripperTest {

    private static final byte[] SOI = {(byte) 0xFF, (byte) 0xD8};
    private static final byte[] EOI = {(byte) 0xFF, (byte) 0xD9};

    @Test
    void jpegDropsExifAndXmpButKeepsOrientationAndPixels() {
        byte[] jfif = segment(0xE0, concat(ascii("JFIF\0"), new byte[]{1, 1, 0, 0, 1, 0, 1, 0, 0}));
        byte[] exif = exifSegment(6, "GPS-SECRET");
        byte[] xmp = segment(0xE1, concat(ascii("http://ns.adobe.com/xap/1.0/\0"), ascii("<GPSLatitude>38.2</GPSLatitude>")));
        byte[] iptc = segment(0xED, ascii("Photoshop 3.0\0IPTC-LOCATION"));
        byte[] dqt = segment(0xDB, new byte[]{0, 1, 2, 3});
        byte[] scan = concat(segment(0xDA, new byte[]{1, 2, 3}), new byte[]{0x11, (byte) 0xFF, 0x00, 0x22}, EOI);
        byte[] jpeg = concat(SOI, jfif, exif, xmp, iptc, dqt, scan);

        byte[] stripped = ImageMetadataStripper.strip("image/jpeg", jpeg);

        assertThat(indexOf(stripped, ascii("GPS-SECRET"))).isNegative();
        assertThat(indexOf(stripped, ascii("GPSLatitude"))).isNegative();
        assertThat(indexOf(stripped, ascii("IPTC-LOCATION"))).isNegative();
        // 방향 값 6(90도 회전)은 남아야 세로 사진이 눕지 않는다.
        assertThat(indexOf(stripped, new byte[]{0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6})).isPositive();
        // JFIF는 맨 앞에, 압축된 픽셀 데이터는 그대로 끝에 남는다.
        assertThat(indexOf(stripped, concat(SOI, jfif))).isZero();
        assertThat(indexOf(stripped, concat(dqt, scan))).isEqualTo(stripped.length - dqt.length - scan.length);
    }

    @Test
    void jpegWithUprightOrientationKeepsNoExifAtAll() {
        byte[] scan = concat(segment(0xDA, new byte[]{1}), new byte[]{0x11}, EOI);
        byte[] jpeg = concat(SOI, exifSegment(1, "GPS-SECRET"), scan);

        byte[] stripped = ImageMetadataStripper.strip("image/jpeg", jpeg);

        assertThat(stripped).isEqualTo(concat(SOI, scan));
    }

    @Test
    void brokenJpegIsRejectedInsteadOfStoredWithMetadata() {
        byte[] broken = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x7F, 0x00, 'E', 'x'};

        assertThatThrownBy(() -> ImageMetadataStripper.strip("image/jpeg", broken))
                .isInstanceOf(ImageUploadException.class);
    }

    @Test
    void pngDropsExifAndTextChunks() {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        byte[] ihdr = pngChunk("IHDR", new byte[13]);
        byte[] idat = pngChunk("IDAT", new byte[]{1, 2, 3});
        byte[] iend = pngChunk("IEND", new byte[0]);
        byte[] png = concat(signature, ihdr,
                pngChunk("eXIf", ascii("GPS-SECRET")),
                pngChunk("iTXt", ascii("XML:com.adobe.xmp\0<GPSLatitude/>")),
                pngChunk("tEXt", ascii("Comment\0GPS-SECRET-2")),
                idat, iend);

        byte[] stripped = ImageMetadataStripper.strip("image/png", png);

        assertThat(stripped).isEqualTo(concat(signature, ihdr, idat, iend));
    }

    @Test
    void webpDropsExifAndXmpChunksClearsFlagsAndFixesSize() {
        byte[] vp8x = webpChunk("VP8X", new byte[]{0x1C, 0, 0, 0, 1, 0, 0, 1, 0, 0}); // 알파 + EXIF + XMP
        byte[] image = webpChunk("VP8L", new byte[]{1, 2, 3, 4, 5}); // 홀수 크기 → 패딩
        byte[] webp = riff(concat(ascii("WEBP"), vp8x, image,
                webpChunk("EXIF", ascii("GPS-SECRET")),
                webpChunk("XMP ", ascii("<GPSLatitude/>"))));

        byte[] stripped = ImageMetadataStripper.strip("image/webp", webp);

        byte[] expectedVp8x = webpChunk("VP8X", new byte[]{0x10, 0, 0, 0, 1, 0, 0, 1, 0, 0}); // 알파만 남는다
        assertThat(stripped).isEqualTo(riff(concat(ascii("WEBP"), expectedVp8x, image)));
    }

    private static byte[] exifSegment(int orientation, String secret) {
        // 리틀엔디언 TIFF: IFD0에 Orientation과 GPS IFD 포인터, 그 뒤에 비밀 값
        byte[] tiff = concat(
                new byte[]{'I', 'I', 42, 0, 8, 0, 0, 0},
                new byte[]{2, 0},
                new byte[]{0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0},
                new byte[]{0x25, (byte) 0x88, 4, 0, 1, 0, 0, 0, 38, 0, 0, 0},
                new byte[]{0, 0, 0, 0},
                ascii(secret));
        return segment(0xE1, concat(ascii("Exif\0\0"), tiff));
    }

    private static byte[] segment(int marker, byte[] data) {
        int length = data.length + 2;
        return concat(new byte[]{(byte) 0xFF, (byte) marker, (byte) (length >> 8), (byte) length}, data);
    }

    private static byte[] pngChunk(String type, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(ascii(type));
        crc.update(data);
        long value = crc.getValue();
        return concat(be32(data.length), ascii(type), data, be32((int) value));
    }

    private static byte[] webpChunk(String fourcc, byte[] data) {
        byte[] padding = data.length % 2 == 1 ? new byte[1] : new byte[0];
        return concat(ascii(fourcc), le32(data.length), data, padding);
    }

    private static byte[] riff(byte[] payload) {
        return concat(ascii("RIFF"), le32(payload.length), payload);
    }

    private static byte[] be32(int v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    private static byte[] le32(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
