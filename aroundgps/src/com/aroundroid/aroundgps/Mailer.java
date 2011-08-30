package com.aroundroid.aroundgps;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

//mailer for one session
public class Mailer {
	
	private static final String emailRegularExpression = "^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$"; //$NON-NLS-1$
	
	private Properties props = new Properties();
	private Session session = Session.getDefaultInstance(props, null);
	
	private InternetAddress iA;
	
	public Mailer(String mailAddress,String Personal) throws UnsupportedEncodingException{
		iA = new InternetAddress(mailAddress, Personal);
	}
	
	public void sendOneMail(String recpMailAdress,String subject,String msgBody) throws MessagingException{
		Message msg = new MimeMessage(session);
		msg.setFrom(iA);
		msg.addRecipient(Message.RecipientType.TO,
				new InternetAddress(recpMailAdress));
		msg.setSubject(subject);
		msg.setText(msgBody);
		Transport.send(msg);
	}
	
	public static boolean validMail(String mailAdress){
		return mailAdress.matches(emailRegularExpression);
	}
}
