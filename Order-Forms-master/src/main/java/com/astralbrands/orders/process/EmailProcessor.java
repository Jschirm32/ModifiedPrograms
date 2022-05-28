package com.astralbrands.orders.process;

import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.Message.RecipientType;
import javax.mail.Multipart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import com.astralbrands.orders.constants.AppConstants;

@Component
public class EmailProcessor implements Processor, AppConstants {

	Logger log = LoggerFactory.getLogger(EmailProcessor.class);
	private static final String SUBJECT = "Franchise Order form submition Notification!!";

	@Value("${smtp.host}")
	private String host;
	@Value("${smtp.port}")
	private String port;
	@Value("${smtp.username}")
	private String userName;
	@Value("${smtp.password}")
	private String password;
	@Value("${smtp.from}")
	private String from;
	@Value("${smtp.to}")
	private String toList;

	@Override
	public void process(Exchange exchange) throws Exception {
		String csvFileData = exchange.getProperty(CSV_DATA, String.class);
		String iFile = exchange.getProperty("IFILE", String.class);
		String brand = exchange.getProperty(SITE_NAME, String.class);
		String fileName = exchange.getProperty(INPUT_FILE_NAME, String.class);
		sendEmail(SUBJECT, csvFileData, iFile, brand, fileName);
	}

	public void sendEmail(String subject, String csvFile, String iFile, String site, String fileName) {

		log.info("sending .........");

		JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
		javaMailSender.setHost(host);
		javaMailSender.setPort(587);

		javaMailSender.setUsername(userName);
		javaMailSender.setPassword(password);

		Properties props = javaMailSender.getJavaMailProperties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.starttls.required", "true");
		props.put("mail.smtpClient.EnableSsl", "false");
		props.put("mail.debug", "true");

		MimeMessage mimeMessage = javaMailSender.createMimeMessage();
		try {
			// helper = new MimeMessageHelper(mimeMessage, true);
			if (toList != null && toList.length() > 0 && toList.contains(SEMI_COMMA)) {
				String[] toAdd = toList.split(SEMI_COMMA);
				for (String to : toAdd) {
					mimeMessage.addRecipient(RecipientType.TO, new InternetAddress(to));
				}
			} else {
				mimeMessage.addRecipient(RecipientType.TO, new InternetAddress(toList));
			}

			mimeMessage.setFrom(from);
			mimeMessage.setSubject(subject);
			/*
			 * message.setContent(csvFile, "text/html"); message.setFileName("htmlFile");
			 */
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			BodyPart msgBody = new MimeBodyPart();
			msgBody.setText(
					"Hi Team, \n Please find Attached " + getCountry(site) + " order forms. \n Thanks,\n Shiva");

			Multipart multiPart = new MimeMultipart();
			MimeBodyPart attachFilePart = new MimeBodyPart();
			attachFilePart.setDataHandler(new DataHandler(new ByteArrayDataSource(csvFile.getBytes(), "text/csv")));
			attachFilePart.setFileName(fileName + DOT_CSV);

			MimeBodyPart iFileBody = new MimeBodyPart();
			iFileBody.setDataHandler(new DataHandler(new ByteArrayDataSource(iFile.getBytes(), "text/plain")));
			iFileBody.setFileName(fileName+ DOT_TXT);
			multiPart.addBodyPart(iFileBody);
			multiPart.addBodyPart(attachFilePart);
			multiPart.addBodyPart(msgBody);
			mimeMessage.setContent(multiPart);

		} catch (Exception e) {
			e.printStackTrace();
		}

		javaMailSender.send(mimeMessage);
	}

	private String getCountry(String site) {
		if (US_STR.equals(site)) {
			return "USA";
		} else {
			return "CANADA";
		}
	}

}
