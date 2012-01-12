/*
 *  Copyright 2012 Eric F. Savage, code@efsavage.com
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.efsavage.amazon.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;

import org.jets3t.service.S3ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.io.GZipDeflatingInputStream;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;

import com.ajah.lang.ConfigException;
import com.ajah.util.config.Config;

/**
 * Wrapper for {@link RestS3Service}.
 * 
 * @author <a href="http://efsavage.com">Eric F. Savage</a>, <a
 *         href="mailto:code@efsavage.com">code@efsavage.com</a>.
 * 
 */
public class S3Client {

	private static final Logger log = Logger.getLogger(S3Client.class.getName());

	private RestS3Service s3Service;

	/**
	 * Public constructor. Consider using {@link #getDefaultClient()}.
	 * 
	 * @param accessKey
	 *            The AWS access key to use to authenticate.
	 * @param secretKey
	 *            The AWS secret key to use to authenticate.
	 * @throws S3ServiceException
	 *             If an S3 service could not be provisioned.
	 */
	public S3Client(String accessKey, String secretKey) throws S3ServiceException {
		AWSCredentials awsCredentials = new AWSCredentials(accessKey, secretKey);
		this.s3Service = new RestS3Service(awsCredentials);
		// System.out.println(this.s3Service.listAllBuckets()[0].getName());
	}

	/**
	 * Puts an object using the default client. Consider using
	 * {@link S3#put(Bucket, String, String)}.
	 * 
	 * @see S3Client#getDefaultClient()
	 * 
	 * @param bucket
	 *            The bucket to put the object into, required.
	 * @param name
	 *            The name to store the object as, required.
	 * @param data
	 *            The data of the object.
	 * @throws S3Exception
	 *             If an error occurs storing the object.
	 */
	public void put(Bucket bucket, String name, String data) throws S3Exception {

		try {
			byte[] input = data.getBytes("UTF-8");
			log.info(input.length + " bytes to upload");
			S3Object object = new S3Object(name + ".gz");
			object.setDataInputStream(new GZipDeflatingInputStream(new ByteArrayInputStream(input)));
			object = this.s3Service.putObject(bucket.toString(), object);
		} catch (UnsupportedEncodingException e) {
			throw new ConfigException(e);
		} catch (IOException | S3ServiceException e) {
			throw new S3Exception(e);
		}

	}

	/**
	 * Returns the default client as configured by the aws.accessKey and
	 * aws.secretKey properties.
	 * 
	 * @return The default client.
	 */
	public static S3Client getDefaultClient() {
		try {
			// TODO Make this a singleton.
			return new S3Client(Config.i.get("aws.accessKey"), Config.i.get("aws.secretKey"));
		} catch (S3ServiceException e) {
			throw new ConfigException(e);
		}
	}

}