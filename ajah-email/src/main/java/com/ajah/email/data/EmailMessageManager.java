/*
 *  Copyright 2011 Eric F. Savage, code@efsavage.com
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
package com.ajah.email.data;

import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.springframework.stereotype.Service;

import com.ajah.email.EmailMessage;
import com.ajah.util.AjahUtils;
import com.ajah.util.StringUtils;
import com.ajah.util.config.Config;
import com.ajah.util.data.format.EmailAddress;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.AWSJavaMailTransport;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.ListVerifiedEmailAddressesResult;
import com.amazonaws.services.simpleemail.model.VerifyEmailAddressRequest;

/**
 * Manages the persistance and transport of email messages.
 * 
 * @author <a href="http://efsavage.com">Eric F. Savage</a>, <a
 *         href="mailto:code@efsavage.com">code@efsavage.com</a>.
 * 
 */
@Service
public class EmailMessageManager {

	private static final Logger log = Logger.getLogger(EmailMessageManager.class.getName());

	/**
	 * Sends a message through Amazon's SES service.
	 * 
	 * @param message
	 *            The message to send. Subject, from and to are required.
	 * @throws AddressException
	 *             If there is a problem with one of the email addresses.
	 * @throws MessagingException
	 *             If there is a problem with the transport of the message.
	 */
	public void send(EmailMessage message) throws AddressException, MessagingException {
		AjahUtils.requireParam(message, "message");
		AjahUtils.requireParam(message.getSubject(), "message.subject");
		AjahUtils.requireParam(message.getFrom(), "message.from");
		AjahUtils.requireParam(message.getTo(), "message.to");

		AWSCredentials credentials = new BasicAWSCredentials(Config.i.get("aws.accessKey", null), Config.i.get("aws.secretKey", null));
		if (Config.i.getBoolean("aws.ses.verify", false)) {
			// Verification is active so we'll need to check that first
			AmazonSimpleEmailService email = new AmazonSimpleEmailServiceClient(credentials);
			ListVerifiedEmailAddressesResult verifiedEmails = email.listVerifiedEmailAddresses();
			boolean verified = true;
			if (!isVerified(message.getFrom(), email, verifiedEmails)) {
				log.warning("Sender " + message.getFrom() + " is not verified");
				verified = false;
			}
			for (EmailAddress emailAddress : message.getTo()) {
				if (!isVerified(emailAddress, email, verifiedEmails)) {
					log.warning("Recipient " + emailAddress + " is not verified");
					verified = false;
				}
			}
			if (!verified) {
				throw new MessagingException("Message not sent because one or more addresses need to be verified");
			}
		}
		Properties props = new Properties();
		props.setProperty("mail.transport.protocol", "aws");
		props.setProperty("mail.aws.user", credentials.getAWSAccessKeyId());
		props.setProperty("mail.aws.password", credentials.getAWSSecretKey());

		Session session = Session.getInstance(props);

		MimeMessage mimeMessage = new MimeMessage(session);
		mimeMessage.setFrom(new InternetAddress(message.getFrom().toString()));
		for (EmailAddress to : message.getTo()) {
			mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(to.toString()));
		}
		mimeMessage.setSubject(message.getSubject());
		String htmlContent = message.getHtml();
		if (StringUtils.isBlank(htmlContent)) {
			// No HTML so we'll just send a plaintext message.
			mimeMessage.setText(message.getText());
		} else {
			Multipart multiPart = new MimeMultipart("alternative");

			BodyPart text = new MimeBodyPart();
			text.setText(message.getText());
			multiPart.addBodyPart(text);

			BodyPart html = new MimeBodyPart();
			html.setContent(message.getHtml(), "text/html");
			multiPart.addBodyPart(html);

			mimeMessage.setContent(multiPart);
		}
		mimeMessage.saveChanges();

		Transport transport = new AWSJavaMailTransport(session, null);
		transport.connect();
		transport.sendMessage(mimeMessage, null);
		transport.close();

	}

	private boolean isVerified(EmailAddress emailAddress, AmazonSimpleEmailService email, ListVerifiedEmailAddressesResult verifiedEmails) {
		if (!verifiedEmails.getVerifiedEmailAddresses().contains(emailAddress.toString())) {
			email.verifyEmailAddress(new VerifyEmailAddressRequest().withEmailAddress(emailAddress.toString()));
			return false;
		}
		return true;
	}
	
}