package com.lms.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.mediaconvert.MediaConvertClient;
import software.amazon.awssdk.services.mediaconvert.model.*;

@Service
public class VideoTranscodingService {

    @Value("${aws.mediaconvert.endpoint:}")
    private String mediaConvertEndpoint;

    @Value("${aws.mediaconvert.role:}")
    private String mediaConvertRoleArn;

    public void createTranscodingJob(String inputS3Uri, String outputS3Prefix) {
        if (mediaConvertRoleArn == null || mediaConvertRoleArn.isEmpty()) {
            return; // Skip if MediaConvert is not configured
        }

        software.amazon.awssdk.services.mediaconvert.MediaConvertClientBuilder builder = MediaConvertClient.builder();
        if (mediaConvertEndpoint != null && !mediaConvertEndpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(mediaConvertEndpoint));
        }
        MediaConvertClient mcClient = builder.build();

        // 1. Define Input
        Input input = Input.builder()
                .fileInput(inputS3Uri)
                .build();

        // 2. Define Output (HLS)
        Output hlsOutput = Output.builder()
                .nameModifier("-hls")
                .containerSettings(ContainerSettings.builder()
                        .container(ContainerType.M3_U8)
                        .m3u8Settings(M3u8Settings.builder().build())
                        .build())
                .videoDescription(VideoDescription.builder()
                        .codecSettings(VideoCodecSettings.builder()
                                .codec(VideoCodec.H_264)
                                .h264Settings(H264Settings.builder()
                                        .bitrate(5000000) // 5 Mbps
                                        .rateControlMode(H264RateControlMode.CBR)
                                        .build())
                                .build())
                        .build())
                .audioDescriptions(AudioDescription.builder()
                        .codecSettings(AudioCodecSettings.builder()
                                .codec(AudioCodec.AAC)
                                .aacSettings(AacSettings.builder()
                                        .bitrate(96000)
                                        .sampleRate(48000)
                                        .codingMode(AacCodingMode.CODING_MODE_2_0)
                                        .build())
                                .build())
                        .build())
                .build();

        OutputGroup hlsGroup = OutputGroup.builder()
                .name("Apple HLS")
                .outputGroupSettings(OutputGroupSettings.builder()
                        .type(OutputGroupType.HLS_GROUP_SETTINGS)
                        .hlsGroupSettings(HlsGroupSettings.builder()
                                .destination(outputS3Prefix)
                                .minSegmentLength(0)
                                .segmentLength(10)
                                .build())
                        .build())
                .outputs(hlsOutput)
                .build();

        JobSettings jobSettings = JobSettings.builder()
                .inputs(input)
                .outputGroups(hlsGroup)
                .build();

        CreateJobRequest createJobRequest = CreateJobRequest.builder()
                .role(mediaConvertRoleArn)
                .settings(jobSettings)
                .build();

        try {
            CreateJobResponse createJobResponse = mcClient.createJob(createJobRequest);
            System.out.println("Created MediaConvert Job: " + createJobResponse.job().id());
        } catch (MediaConvertException e) {
            e.printStackTrace();
        }
    }
}
