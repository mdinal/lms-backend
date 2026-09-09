package com.lms.backend.service;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;
import software.amazon.awssdk.services.cloudfront.url.SignedUrl;

import java.io.StringReader;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class CloudFrontService {

    @Value("${aws.cloudfront.domain:}")
    private String cloudFrontDomain;

    @Value("${aws.cloudfront.key-pair-id:}")
    private String keyPairId;

    @Value("${aws.cloudfront.private-key:}")
    private String privateKeyPem;

    public String generateSignedUrl(String s3Key) throws Exception {
        if (s3Key == null || s3Key.startsWith("http://") || s3Key.startsWith("https://")) {
            return s3Key;
        }
        if (cloudFrontDomain == null || cloudFrontDomain.isEmpty()) {
            return s3Key; // Fallback if CloudFront is not configured
        }

        // 1. Parse Private Key
        PEMParser pemParser = new PEMParser(new StringReader(privateKeyPem.replace("\\n", "\n")));
        PEMKeyPair pemKeyPair = (PEMKeyPair) pemParser.readObject();
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        PrivateKey privateKey = converter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());

        // 2. Generate Signed URL valid for 2 hours
        Instant expirationDate = Instant.now().plus(2, ChronoUnit.HOURS);
        String resourceUrl = "https://" + cloudFrontDomain + "/" + s3Key;

        CannedSignerRequest request = CannedSignerRequest.builder()
                .resourceUrl(resourceUrl)
                .privateKey(privateKey)
                .keyPairId(keyPairId)
                .expirationDate(expirationDate)
                .build();

        CloudFrontUtilities cloudFrontUtilities = CloudFrontUtilities.create();
        SignedUrl signedUrl = cloudFrontUtilities.getSignedUrlWithCannedPolicy(request);

        return signedUrl.url();
    }
}
