package com.se_lab.project.service;

import org.springframework.http.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/// 업로드 사진에서 위치 정보가 들어 있을 수 있는 메타데이터를 지운다.
///
/// 휴대폰 사진에는 촬영 위치(GPS)가 EXIF로 들어 있다. 그대로 저장·공개하면 게시물 사진만으로
/// 사용자가 있던 위치가 드러난다 (위치기반서비스 신고 검토, #57). 픽셀은 건드리지 않고 메타데이터 블록만 뺀다.
///
/// JPEG의 EXIF에는 사진 방향(Orientation)도 들어 있어서 통째로 지우면 세로로 찍은 사진이 눕는다.
/// 그래서 방향 값 하나만 담은 최소 EXIF를 다시 넣는다.
final class ImageMetadataStripper {

    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

    // PNG 텍스트 청크에는 촬영 위치가 담긴 XMP가 들어갈 수 있다.
    private static final Set<String> PNG_METADATA_CHUNKS = Set.of("eXIf", "tEXt", "zTXt", "iTXt");

    private static final int PNG_SIGNATURE_LENGTH = 8;

    // WebP VP8X 헤더 플래그: EXIF(0x08), XMP(0x04)
    private static final int WEBP_METADATA_FLAGS = 0x08 | 0x04;

    private ImageMetadataStripper() {
    }

    /// 구조가 깨져 메타데이터를 확실히 지울 수 없는 파일은 거절한다.
    /// 그대로 통과시키면 위치 정보가 남은 채 공개될 수 있다.
    static byte[] strip(String contentType, byte[] content) {
        try {
            return switch (contentType) {
                case "image/jpeg" -> stripJpeg(content);
                case "image/png" -> stripPng(content);
                case "image/webp" -> stripWebp(content);
                default -> content;
            };
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new ImageUploadException(HttpStatus.BAD_REQUEST, "이미지 파일을 읽을 수 없습니다.");
        }
    }

    // ── JPEG ─────────────────────────────────────────────

    /// start는 0xFF 마커 위치, end는 다음 세그먼트가 시작하는 위치.
    private record Segment(int marker, int start, int end) {
    }

    private static byte[] stripJpeg(byte[] in) {
        List<Segment> kept = new ArrayList<>();
        int orientation = 0;
        int imageDataStart = -1;

        int pos = 2; // SOI(FF D8) 다음
        while (pos < in.length) {
            int segmentStart = pos;
            if (u8(in, pos) != 0xFF) {
                throw new IllegalArgumentException("JPEG marker expected at " + pos);
            }
            while (u8(in, pos) == 0xFF) {
                pos++; // 채움 바이트
            }
            int marker = u8(in, pos++);

            if (marker == 0xD9) { // EOI
                imageDataStart = segmentStart;
                break;
            }
            if ((marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) { // 길이 없는 마커
                kept.add(new Segment(marker, segmentStart, pos));
                continue;
            }

            int length = (u8(in, pos) << 8) | u8(in, pos + 1);
            int end = pos + length;
            if (length < 2 || end > in.length) {
                throw new IllegalArgumentException("JPEG segment overflow at " + segmentStart);
            }

            if (marker == 0xDA) { // SOS: 여기부터 끝까지는 압축된 픽셀 데이터라 그대로 복사한다.
                imageDataStart = segmentStart;
                break;
            }

            if (marker == 0xE1) {
                // APP1: EXIF 또는 XMP. 방향 값만 챙기고 버린다.
                if (startsWith(in, pos + 2, end, EXIF_HEADER)) {
                    orientation = readOrientation(in, pos + 2 + EXIF_HEADER.length, end);
                }
            } else if (marker != 0xED) {
                // APP13(0xED): Photoshop·IPTC. 위치와 설명이 들어 있을 수 있어 버린다.
                kept.add(new Segment(marker, segmentStart, end));
            }
            pos = end;
        }

        if (imageDataStart < 0) {
            throw new IllegalArgumentException("JPEG has no image data");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(0xFF);
        out.write(0xD8);
        int i = 0;
        // JFIF(APP0)는 SOI 바로 뒤에 와야 해서 먼저 쓴다.
        while (i < kept.size() && kept.get(i).marker() == 0xE0) {
            write(out, in, kept.get(i++));
        }
        if (orientation > 1) {
            out.writeBytes(orientationExif(orientation));
        }
        for (; i < kept.size(); i++) {
            write(out, in, kept.get(i));
        }
        out.write(in, imageDataStart, in.length - imageDataStart);
        return out.toByteArray();
    }

    /// EXIF TIFF 구조의 첫 IFD에서 Orientation(0x0112) 값을 읽는다. 없거나 이상하면 0.
    private static int readOrientation(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) return 0;
        boolean little;
        if (b[tiff] == 'I' && b[tiff + 1] == 'I') {
            little = true;
        } else if (b[tiff] == 'M' && b[tiff + 1] == 'M') {
            little = false;
        } else {
            return 0;
        }

        long ifdOffset = u32(b, tiff + 4, little);
        if (ifdOffset < 8 || tiff + ifdOffset + 2 > end) return 0;
        int ifd = (int) (tiff + ifdOffset);

        int count = u16(b, ifd, little);
        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) return 0;
            if (u16(b, entry, little) == 0x0112) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 0;
            }
        }
        return 0;
    }

    /// 방향 값 하나만 담은 APP1 EXIF 세그먼트.
    private static byte[] orientationExif(int orientation) {
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,         // 빅엔디언 TIFF 헤더, 첫 IFD는 8바이트 뒤
                0, 1,                                // 항목 1개
                0x01, 0x12, 0, 3, 0, 0, 0, 1,         // Orientation, SHORT, 1개
                0, (byte) orientation, 0, 0,          // 값
                0, 0, 0, 0                           // 다음 IFD 없음
        };
        int length = 2 + EXIF_HEADER.length + tiff.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream(length + 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(EXIF_HEADER);
        out.writeBytes(tiff);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, byte[] in, Segment segment) {
        out.write(in, segment.start(), segment.end() - segment.start());
    }

    // ── PNG ──────────────────────────────────────────────

    private static byte[] stripPng(byte[] in) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
        out.write(in, 0, PNG_SIGNATURE_LENGTH);

        int pos = PNG_SIGNATURE_LENGTH;
        while (pos < in.length) {
            long length = u32(in, pos, false);
            String type = new String(in, pos + 4, 4, StandardCharsets.US_ASCII);
            long end = pos + 12L + length; // 길이 4 + 종류 4 + 데이터 + CRC 4
            if (end > in.length) {
                throw new IllegalArgumentException("PNG chunk overflow at " + pos);
            }
            if (!PNG_METADATA_CHUNKS.contains(type)) {
                out.write(in, pos, (int) (end - pos));
            }
            pos = (int) end;
            if (type.equals("IEND")) break;
        }
        return out.toByteArray();
    }

    // ── WebP ─────────────────────────────────────────────

    private static byte[] stripWebp(byte[] in) {
        ByteArrayOutputStream body = new ByteArrayOutputStream(in.length);
        body.write(in, 8, 4); // "WEBP"

        int pos = 12;
        while (pos + 8 <= in.length) {
            String fourcc = new String(in, pos, 4, StandardCharsets.US_ASCII);
            long size = u32(in, pos + 4, true);
            long end = pos + 8L + size + (size & 1); // 홀수 크기 청크는 패딩 1바이트
            if (end > in.length) {
                // 마지막 청크의 패딩 바이트가 빠진 파일이 흔해서 그 경우만 받아준다.
                if (pos + 8L + size != in.length) {
                    throw new IllegalArgumentException("WebP chunk overflow at " + pos);
                }
                end = in.length;
            }

            if (!fourcc.equals("EXIF") && !fourcc.equals("XMP ")) {
                byte[] chunk = Arrays.copyOfRange(in, pos, (int) end);
                if (fourcc.equals("VP8X") && size >= 1) {
                    chunk[8] = (byte) (chunk[8] & ~WEBP_METADATA_FLAGS);
                }
                body.writeBytes(chunk);
            }
            pos = (int) end;
        }

        byte[] payload = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 8);
        out.writeBytes(new byte[]{'R', 'I', 'F', 'F'});
        int riffSize = payload.length;
        out.write(riffSize & 0xFF);
        out.write((riffSize >> 8) & 0xFF);
        out.write((riffSize >> 16) & 0xFF);
        out.write((riffSize >> 24) & 0xFF);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    // ── 바이트 읽기 ───────────────────────────────────────

    private static int u8(byte[] b, int pos) {
        return b[pos] & 0xFF;
    }

    private static int u16(byte[] b, int pos, boolean little) {
        return little
                ? u8(b, pos) | (u8(b, pos + 1) << 8)
                : (u8(b, pos) << 8) | u8(b, pos + 1);
    }

    private static long u32(byte[] b, int pos, boolean little) {
        return little
                ? (u8(b, pos) | (u8(b, pos + 1) << 8) | (u8(b, pos + 2) << 16) | ((long) u8(b, pos + 3) << 24))
                : (((long) u8(b, pos) << 24) | (u8(b, pos + 1) << 16) | (u8(b, pos + 2) << 8) | u8(b, pos + 3));
    }

    private static boolean startsWith(byte[] b, int from, int end, byte[] prefix) {
        if (from + prefix.length > end) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (b[from + i] != prefix[i]) return false;
        }
        return true;
    }
}
