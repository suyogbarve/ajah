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

import lombok.extern.java.Log;

import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.io.GZipDeflatingInputStream;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;

import com.ajah.lang.ConfigException;
import com.ajah.util.config.Config;
import com.ajah.util.lang.StreamUtils;

/**
 * Wrapper for {@link RestS3Service}.
 * 
 * @author <a href="http://efsavage.com">Eric F. Savage</a>, <a
 *         href="mailto:code@efsavage.com">code@efsavage.com</a>.
 * 
 */
@Log
public class S3Client {

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
		} catch (final S3Exception e) {
			throw new ConfigException(e);
		}
	}

	private RestS3Service s3Service;

	/**
	 * Public constructor. Consider using {@link #getDefaultClient()}.
	 * 
	 * @param accessKey
	 *            The AWS access key to use to authenticate.
	 * @param secretKey
	 *            The AWS secret key to use to authenticate.
	 * @throws S3Exception
	 *             If an S3 service could not be provisioned.
	 */
	public S3Client(final String accessKey, final String secretKey) throws S3Exception {
		final AWSCredentials awsCredentials = new AWSCredentials(accessKey, secretKey);
		try {
			this.s3Service = new RestS3Service(awsCredentials);
		} catch (final S3ServiceException e) {
			throw new S3Exception(e);
		}
	}

	/**
	 * Gets an object.
	 * 
	 * @see S3Client#getDefaultClient()
	 * 
	 * @param bucket
	 *            The bucket to put the object into, required.
	 * @param name
	 *            The name to store the object as, required.
	 * @param gzip
	 *            Un-Gzip the data? (if it has .gz to the end of it)
	 * @return The file's data
	 * @throws S3Exception
	 *             If an error occurs storing the object.
	 */
	public byte[] get(final Bucket bucket, final String name, final boolean gzip) throws S3Exception {

		try {
			log.finest("Fetching " + name + " from bucket " + bucket.getName());
			final S3Object object = this.s3Service.getObject(bucket.toString(), name);
			if (gzip && name.endsWith(".gz")) {
				throw new UnsupportedOperationException();
			}
			return StreamUtils.toByteArray(object.getDataInputStream());
		} catch (final UnsupportedEncodingException e) {
			throw new ConfigException(e);
		} catch (IOException | ServiceException e) {
			throw new S3Exception(e);
		}

	}

	/**
	 * Puts an object. Consider using {@link S3#put(Bucket, String, String)}.
	 * 
	 * @see S3Client#getDefaultClient()
	 * 
	 * @param bucket
	 *            The bucket to put the object into, required.
	 * @param name
	 *            The name to store the object as, required.
	 * @param data
	 *            The data of the object.
	 * @param overwrite
	 *            Overwrite the file if it already exists?
	 * @param gzip
	 *            Gzip the data (and add .gz to the end of it)?
	 * @throws S3Exception
	 *             If an error occurs storing the object.
	 */
	public void put(final Bucket bucket, final String name, final byte[] data, final boolean overwrite, final boolean gzip) throws S3Exception {

		try {
			log.finest(data.length + " bytes to upload");
			S3Object object;
			if (gzip) {
				object = new S3Object(name + ".gz");
				object.setDataInputStream(new GZipDeflatingInputStream(new ByteArrayInputStream(data)));
			} else {
				object = new S3Object(name);
				object.setDataInputStream(new ByteArrayInputStream(data));
			}
			if (!overwrite && this.s3Service.isObjectInBucket(bucket.getName(), object.getName())) {
				log.fine(object.getName() + " already exists in bucket " + bucket.getName() + " and overwriting is disabled");
				return;
			}
			object = this.s3Service.putObject(bucket.toString(), object);
			log.fine("Uploaded " + object.getName() + " to bucket " + bucket.getName());
		} catch (final UnsupportedEncodingException e) {
			throw new ConfigException(e);
		} catch (IOException | ServiceException e) {
			throw new S3Exception(e);
		}

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
	public void put(final Bucket bucket, final String name, final String data) throws S3Exception {
		byte[] input;
		try {
			input = data.getBytes("UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new ConfigException(e);
		}
		put(bucket, name, input, true, true);
	}

}
