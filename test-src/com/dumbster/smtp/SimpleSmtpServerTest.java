/*
 * Dumbster - a dummy SMTP server
 * Copyright 2004 Jason Paul Kitchen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dumbster.smtp;

import org.junit.*;

import static org.junit.Assert.*;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.Date;

public class SimpleSmtpServerTest {
    private static final int SMTP_PORT = 1081;

    private SimpleSmtpServer server;

    private final String Server = "localhost";
    private final String From = "sender@here.com";
    private final String To = "receiver@there.com";
    private final String Subject = "Test";
    private final String Body = "Test Body";
    private final String FileName = "license.txt";

    @Before
    public void setup() {
        server = SimpleSmtpServer.start(SMTP_PORT);
    }

    @After
    public void teardown() {
        server.stop();
        server = null;
    }

    @Test
    public void testNoMessageSentButWaitingDoesNotHang() {
        server.anticipateMessageCountFor(1, 10);
        assertEquals(0, server.getEmailCount());
    }

    @Test
    public void testSend() {
        try {
            sendMessage(SMTP_PORT, From, Subject, Body, To);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception: " + e);
        }
        server.anticipateMessageCountFor(1, 500);
        assertTrue(server.getEmailCount() == 1);
        MailMessage email = server.getMessage(0);
        assertEquals("Test", email.getFirstHeaderValue("Subject"));
        assertEquals("Test Body", email.getBody());
    }

    @Test
    public void testSendWithLongSubject() {
        StringBuffer b = new StringBuffer();
        for(int i=0; i<500; i++)
            b.append("X");
        String longSubject = b.toString();
        try {
            sendMessage(SMTP_PORT, From, b.toString(), Body, To);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception: " + e);
        }
        server.anticipateMessageCountFor(1, 500);
        assertTrue(server.getEmailCount() == 1);
        MailMessage email = server.getMessage(0);
        assertEquals(longSubject, email.getFirstHeaderValue("Subject"));
        assertEquals(500, longSubject.length());
        assertEquals("Test Body", email.getBody());
    }

    @Test
    public void testThreadedSend() {
        server.setThreaded(true);
        try {
            sendMessage(SMTP_PORT, From, Subject, Body, To);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception: " + e);
        }
        server.anticipateMessageCountFor(1, 500);
        assertTrue(server.getEmailCount() == 1);
        MailMessage email = server.getMessage(0);
        assertEquals("Test", email.getFirstHeaderValue("Subject"));
        assertEquals("Test Body", email.getBody());
    }

    @Test
    public void testSendMessageWithCarriageReturn() {
        String bodyWithCR = "\n\nKeep these pesky carriage returns\n\n";
        try {
            sendMessage(SMTP_PORT, From, Subject, bodyWithCR, To);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception: " + e);
        }
        server.anticipateMessageCountFor(1, 500);
        assertEquals(1, server.getEmailCount());
        MailMessage email = server.getMessage(0);
        assertEquals(bodyWithCR, email.getBody());
    }

    @Test
    public void testSendTwoMessagesSameConnection() {
        try {
            MimeMessage[] mimeMessages = new MimeMessage[2];
            Properties mailProps = getMailProperties(SMTP_PORT);
            Session session = Session.getInstance(mailProps, null);

            mimeMessages[0] = createMessage(session, "sender@whatever.com", "receiver@home.com", "Doodle1", "Bug1");
            mimeMessages[1] = createMessage(session, "sender@whatever.com", "receiver@home.com", "Doodle2", "Bug2");

            Transport transport = session.getTransport("smtp");
            transport.connect("localhost", SMTP_PORT, null, null);

            for (MimeMessage mimeMessage : mimeMessages) {
                transport.sendMessage(mimeMessage, mimeMessage.getAllRecipients());
            }

            transport.close();
        } catch (MessagingException e) {
            e.printStackTrace();
            fail("Unexpected exception: " + e);
        }
        server.anticipateMessageCountFor(2, 500);
        assertEquals(2, server.getEmailCount());
    }

    @Test
    public void testSendingFileAttachment() throws MessagingException {
        Properties props = getMailProperties(SMTP_PORT);
        props.put("mail.smtp.host", "localhost");
        Session session = Session.getDefaultInstance(props, null);
        MimeMessage message = new MimeMessage(session);

        message.setFrom(new InternetAddress(From));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(To));
        message.setSubject(Subject);

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(buildMessageBody());
        multipart.addBodyPart(buildFileAttachment());
        message.setContent(multipart);
        Transport.send(message);
        server.anticipateMessageCountFor(1, 500);
        assertTrue(server.getMessage(0).getBody().indexOf("Apache License") > 0);
    }

    private MimeBodyPart buildFileAttachment() throws MessagingException {
        MimeBodyPart messageBodyPart = new MimeBodyPart();
        DataSource source = new javax.activation.FileDataSource(FileName);
        messageBodyPart.setDataHandler(
                new DataHandler(source));
        messageBodyPart.setFileName(FileName);
        return messageBodyPart;
    }

    private MimeBodyPart buildMessageBody() throws MessagingException {
        MimeBodyPart messageBodyPart =
                new MimeBodyPart();
        messageBodyPart.setText(Body);
        return messageBodyPart;
    }

    @Test
    public void testSendTwoMsgsWithLogin() {
        try {

            Properties props = System.getProperties();

            Session session = Session.getDefaultInstance(props, null);
            Message msg = new MimeMessage(session);

            msg.setFrom(new InternetAddress(From));

            InternetAddress.parse(To, false);
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(To, false));
            msg.setSubject(Subject);

            msg.setText(Body);
            msg.setHeader("X-Mailer", "musala");
            msg.setSentDate(new Date());
            msg.saveChanges();

            Transport transport = null;

            try {
                transport = session.getTransport("smtp");
                transport.connect(Server, SMTP_PORT, "ddd", "ddd");
                transport.sendMessage(msg, InternetAddress.parse(To, false));
                transport.sendMessage(msg, InternetAddress.parse("dimiter.bakardjiev@musala.com", false));
            } catch (javax.mail.MessagingException me) {
                me.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (transport != null) {
                    transport.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        server.anticipateMessageCountFor(2, 500);
        assertEquals(2, server.getEmailCount());
        MailMessage email = server.getMessage(0);
        assertEquals("Test", email.getFirstHeaderValue("Subject"));
        assertEquals("Test Body", email.getBody());
    }

    private Properties getMailProperties(int port) {
        Properties mailProps = new Properties();
        mailProps.setProperty("mail.smtp.host", "localhost");
        mailProps.setProperty("mail.smtp.port", "" + port);
        mailProps.setProperty("mail.smtp.sendpartial", "true");
        return mailProps;
    }


    private void sendMessage(int port, String from, String subject, String body, String to) throws MessagingException {
        Properties mailProps = getMailProperties(port);
        Session session = Session.getInstance(mailProps, null);

        MimeMessage msg = createMessage(session, from, to, subject, body);
        Transport.send(msg);
    }

    private MimeMessage createMessage(
            Session session, String from, String to, String subject, String body) throws MessagingException {
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.setSubject(subject);
        msg.setSentDate(new Date());
        msg.setText(body);
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        return msg;
    }
}
