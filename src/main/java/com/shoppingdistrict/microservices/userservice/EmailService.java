package com.shoppingdistrict.microservices.userservice;

import java.io.UnsupportedEncodingException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
	
	@Value("${from.email.address}")
	private String fromEmailAddress;
	
	@Autowired
	private JavaMailSender mailSender;
	
	
	public void sendMails(String recipent, String subject, String content) throws UnsupportedEncodingException, MessagingException {
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message);
		helper.setFrom(fromEmailAddress);
		helper.setTo(recipent);
		helper.setSubject(subject);
		helper.setText(content, false);
		mailSender.send(message);
		
	}

}
