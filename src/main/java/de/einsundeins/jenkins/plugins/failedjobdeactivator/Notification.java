/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Jochen A. Fuerbacher
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package de.einsundeins.jenkins.plugins.failedjobdeactivator;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static java.util.logging.Level.*;

import java.util.logging.Logger;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import de.einsundeins.jenkins.plugins.failedjobdeactivator.FailedJobDeactivator.DescriptorImpl;
import jenkins.model.Jenkins;
import hudson.tasks.Mailer;

/**
 * All ways of notification.
 * 
 * @author Jochen A. Fuerbacher
 *
 */
public class Notification {

    /**
     * The class logger.
     */
    private final Logger logger = Logger.getLogger(Notification.class
            .getName());

    /**
     * The current data.
     */
    private Date date;


    /**
     * All SMTP information, configured with the mailer plugin.
     */
    private String smtpServer;
    private String smtpPort;
    private String smtpAuthUserName;
    private String smtpAuthPassword;
    private boolean useSsl;
    private String replyToAddress;
    private boolean mailerConfigured;
    
    /**
     * The mailer configuration session.
     */
    private Session session;
    
    /**
     * Default constructor.
     */
    public Notification(){
        date = new Date();
        initSmtp();
    }

    /**
     * Starts all notification features.
     * @param detectedJobs, the list of all detected jobs.
     */
    public void doNotification(List<DetectedJob> detectedJobs) {

        int x = 0;
        while (x < detectedJobs.size()) {

            updateJobDescription(detectedJobs.get(x));
            logAction(detectedJobs.get(x));
            if (mailerConfigured) {
                notifyUsers(detectedJobs.get(x));
            }

            x++;
        }

    }

    /**
     * Updates the description field of the jobs which will be deactivated or
     * deleted.
     * 
     * @param detectedJob
     *            is a detected Job
     */
      private void updateJobDescription(DetectedJob detectedJob) {
        try {
            if (!detectedJob.isDeleteJob()) {
                detectedJob.getaProject().setDescription(detectedJob.getaProject().getDescription() + "<br>"
                        + date.toString() + " - Deactivated: " + detectedJob.getFailureCause()
                        + "\n");
            } else {
                detectedJob.getaProject().setDescription(detectedJob.getaProject().getDescription() + "<br>"
                        + date.toString() + " - Deleted: " + detectedJob.getFailureCause() + "\n");
            }
        } catch (IOException e) {
            logger.log(INFO, "Failed to update job description.", e);
        }
    }

    /**
     * Loggs the jobs which will be deleted or deactivated.
     * 
     * @param detectedJob
     *            is a detected Job
     */
    private void logAction(DetectedJob detectedJob) {

        if (!detectedJob.isDeleteJob()) {
            logger.log(INFO, date.toString() + " - " + detectedJob.getaProject().getFullName()
                    + " deactivated: " + detectedJob.getFailureCause());
        } else {
            logger.log(WARNING,
                    date.toString() + " - " + detectedJob.getaProject().getFullName()
                            + " deleted: " + detectedJob.getFailureCause());
        }
    }

    /**
     * Notifies users via e mail.
     * 
     * @param detectedJob
     *            is a detected Job
     */
    private void notifyUsers(DetectedJob detectedJob) {

        FailedJobDeactivator property = (FailedJobDeactivator) detectedJob.getaProject()
                .getProperty(FailedJobDeactivator.class);
        FailedJobDeactivator.DescriptorImpl descriptor = (DescriptorImpl) Jenkins
                .getInstance().getDescriptor(FailedJobDeactivator.class);

        MimeMessage msg = new MimeMessage(session);

        try {
            msg.setSubject("Failed Job Deactivator");
            if (!detectedJob.isDeleteJob()) {
                msg.setText("The job " + detectedJob.getaProject().getFullName()
                        + " was deactivated. - " + detectedJob.getFailureCause());
            } else {
                msg.setText("The job " + detectedJob.getaProject().getFullName()
                        + " was deleted. - " + detectedJob.getFailureCause());
            }
            msg.setFrom(new InternetAddress(replyToAddress));
            msg.setSentDate(new Date());

            if ((property != null) && (property.getUserNotification() != null)) {
                msg.setRecipients(Message.RecipientType.TO, InternetAddress
                        .parse(property.getUserNotification(), true));
            }

            if ((descriptor.getAdminNotification() != null)) {
                msg.addRecipients(Message.RecipientType.TO,
                        descriptor.getAdminNotification());
            }

            if (msg.getRecipients(Message.RecipientType.TO) != null) {
                Transport.send(msg);
            }

        } catch (MessagingException e) {
            logger.log(WARNING, "Sending email failed: " + e);
        }
    }

    /**
     * Initializes SMTP configuration.
     */
    private void initSmtp() {

        Mailer.DescriptorImpl mailer = (Mailer.DescriptorImpl) Jenkins.getInstance()
                .getDescriptorByType(Mailer.DescriptorImpl.class);

        smtpServer = mailer.getSmtpServer();
        smtpPort = mailer.getSmtpPort();
        smtpAuthUserName = mailer.getSmtpAuthUserName();
        smtpAuthPassword = mailer.getSmtpAuthPassword();
        useSsl = mailer.getUseSsl();
        replyToAddress = mailer.getReplyToAddress();
        
        if(smtpServer != null && replyToAddress != null){
            mailerConfigured = true;
            createSession();
        }else{
            mailerConfigured = false;
        }
    }
    
    /**
     * Creates mailing session
     */
      private void createSession() {

        Properties props = new Properties(System.getProperties());
        props.put("mail.smtp.host", smtpServer);
        
        if (smtpPort != null) {
            props.put("mail.smtp.port", smtpPort);
        }else{
            props.put("mail.smtp.port", "25");
        }        

        if (useSsl) {
            if (props.getProperty("mail.smtp.socketFactory.port") == null) {
                String port = smtpPort == null ? "465" : smtpPort;
                props.put("mail.smtp.port", port);
                props.put("mail.smtp.socketFactory.port", port);
            }

            if (props.getProperty("mail.smtp.socketFactory.class") == null) {
                props.put("mail.smtp.socketFactory.class",
                        "javax.net.ssl.SSLSocketFactory");
            }
            props.put("mail.smtp.socketFactory.fallback", "false");
        }

        if (smtpAuthUserName != null) {
            props.put("mail.smtp.auth", "true");
        }

        props.put("mail.smtp.timeout", "60000");
        props.put("mail.smtp.connectiontimeout", "60000");
        
        session = Session.getInstance(props, authenticator());
    }
      
    /**
     * Generates the mailer authenticator
     * @return null, if no user name, else authenticator
     */
    private Authenticator authenticator() {

        if (smtpAuthUserName == null) {
          return null;
        }
        
        return new Authenticator() {
            
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(smtpAuthUserName, smtpAuthPassword);
          }
        };
    }
        
    /**
     * Used for testing only.
     * @return value of mailerConfigured
     */
    protected boolean isMailerConfigured(){
        return mailerConfigured;
    }
    
    /**
     * Used for testing only
     * @return SMTP host
     */
    protected String getSmtpServer(){
        return smtpServer;
    }
    
    /**
     * Used for testing only
     * @return SMTP port
     */
    protected String getSmtpPort(){
        return smtpPort;
    }
    
    /**
     * Used for testing only
     * @return Reply-to address
     */
    protected String getReplyToAddress(){
        return replyToAddress;
    }
    
    /**
     * Used for testing only
     */
    protected void testUpdateJobDescription(DetectedJob detectedJob){
        updateJobDescription(detectedJob);
    }
}