package com.solvd.qa.util.workflow;

import java.io.IOException;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.IOUtils;
import com.slack.api.bolt.util.JsonOps;
import com.solvd.qa.model.JenkinsAuth;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmazonS3JenkinsAuthService {

	private String bucketName;

	private AmazonS3 s3 = createS3Client();

	public AmazonS3JenkinsAuthService() {
		AWSCredentials credentials = getCredentials();
		if (credentials == null || credentials.getAWSAccessKeyId() == null) {
			throw new IllegalStateException("AWS credentials not found");
		}
		this.bucketName = System.getenv("S3_BUCKET_NAME");
		boolean bucketExists = createS3Client().doesBucketExistV2(bucketName);
		if (!bucketExists) {
			throw new IllegalStateException("Failed to access the Amazon S3 bucket (name: " + bucketName + ")");
		}
	}

	/**
	 * <pre>
	 * In order to obtain auth for a slack team ID you need to store credentials in
	 * S3 jenkins/ folder preliminarily 
	 * Format: {"username": "username", "apiToken": "apiToken"}
	 * </pre>
	 * 
	 * @param teamId Slack team ID
	 * @return Jenkins credentials for remote api calls
	 */
	public JenkinsAuth getAuthForTeam(String teamId) {
		String fullKey = String.format("jenkins/%s", teamId);
		if (getObjectMetadata(s3, fullKey) != null) {
			S3Object s3Object = getObject(s3, fullKey);
			try {
				String json = IOUtils.toString(s3Object.getObjectContent());
				log.info("Auth entry was successfully found in S3 for team ID: " + teamId);
				return JsonOps.fromJson(json, JenkinsAuth.class);
			} catch (IOException e) {
				log.error("Failed to load Jenkins auth for team_id: {}", teamId);
			}
		}
		return null;
	}

	private AmazonS3 createS3Client() {
		return AmazonS3ClientBuilder.defaultClient();
	}

	private AWSCredentials getCredentials() {
		return DefaultAWSCredentialsProviderChain.getInstance().getCredentials();
	}

	private ObjectMetadata getObjectMetadata(AmazonS3 s3, String fullKey) {
		try {
			return s3.getObjectMetadata(bucketName, fullKey);
		} catch (AmazonS3Exception e) {
			log.info("Amazon S3 object metadata not found (key: {}, AmazonS3Exception: {})", fullKey, e.toString());
			return null;
		}
	}

	private S3Object getObject(AmazonS3 s3, String fullKey) {
		try {
			return s3.getObject(bucketName, fullKey);
		} catch (AmazonS3Exception e) {
			log.info("Amazon S3 object not found (key: {}, AmazonS3Exception: {})", fullKey, e.toString());
			return null;
		}
	}

}
