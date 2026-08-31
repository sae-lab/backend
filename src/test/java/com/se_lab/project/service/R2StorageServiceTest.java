package com.se_lab.project.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class R2StorageServiceTest {

    @Test
    void uploadsImageWithS3CompatibleClientAndReturnsPublicUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        ImageUploadValidator validator = new ImageUploadValidator(DataSize.ofMegabytes(10));
        R2StorageService storage = new R2StorageService(
                s3Client,
                validator,
                "kangwonroad-dev",
                "https://images.example.com/",
                "/local/sehwan/");
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        String url = storage.store(new MockMultipartFile("photo", png), "user-routes");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("kangwonroad-dev");
        assertThat(request.getValue().key()).startsWith("local/sehwan/user-routes/").endsWith(".png");
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo("https://images.example.com/" + request.getValue().key());
    }
}
