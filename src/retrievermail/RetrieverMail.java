package retrievermail;

import com.sun.mail.util.MailSSLSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeBodyPart;
import javax.mail.search.ComparisonTerm;
import javax.mail.search.ReceivedDateTerm;
import javax.mail.search.SearchTerm;

/**
 *
 * @author Gustavo Cardenas Alba
 */
public class RetrieverMail {

    private static Folder retrivedFolder;
    private static Store store;

    /*
    *  Para poder utilizar POP debe estar habilitado para el correo utilizado de lo contrario marcara error de autenticacion.
     */
    private enum ENTRY_SERVER_TYPE {
        IMAP, POP3s
    };

    private enum DELIVERY_SERVER_TYPE {
        SMTP
    };

    private static Session getMailSession(String serverType, String host, String port, String sslEnable, String user, String password) throws GeneralSecurityException {
        Properties props = new Properties();
        if (serverType.equals(ENTRY_SERVER_TYPE.IMAP.name())) {
            props.setProperty("mail.store.protocol", serverType);
            props.setProperty("mail.imap.host", host);
            props.setProperty("mail.imap.port", port);
            props.setProperty("mail.imap.ssl.enable", sslEnable);
            if (Boolean.valueOf(sslEnable)) {
                props.setProperty("mail.smtp.starttls.enable", sslEnable);
                props.setProperty("mail.smtp.ssl.protocols", "TLSv1.2");
                MailSSLSocketFactory sf = new MailSSLSocketFactory();
                sf.setTrustAllHosts(true);
                props.put("mail.imap.starttls.enable", "true");
                props.put("mail.imap.ssl.socketFactory", sf);
            }
        } else {
            props.setProperty("mail.store.protocol", serverType);
            props.setProperty("mail.pop3.host", host);
            props.setProperty("mail.pop3.port", port);
            props.setProperty("mail.smtp.auth.plain.disable", "true");
        }
        Session session = Session.getDefaultInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });
        session.setDebug(true);
        return session;
    }

    public static Message[] retrieveEmails(String serverType, String host, String port, String sslEnable, String userMail, String password, String folderToRetrieve, SearchTerm searchTerm)
            throws Exception {
        Message[] retrivedMessages = null;
        Session session = getMailSession(serverType, host, port, sslEnable, userMail, password);
        try {
            store = session.getStore(serverType.toLowerCase());
            if (serverType.equals(ENTRY_SERVER_TYPE.IMAP.name())) {
                store.connect(session.getProperty("mail.imap.host"), Integer.valueOf(port), userMail, password);
            } else {
                store.connect(session.getProperty("mail.pop3.host"), userMail, password);
            }

            if (store.isConnected()) {
                retrivedFolder = store.getFolder(folderToRetrieve);
            }

            if (retrivedFolder != null) {
                retrivedFolder.open(Folder.READ_ONLY);
                retrivedMessages = searchTerm != null ? retrivedFolder.search(searchTerm) : retrivedFolder.getMessages();
            } else {
                throw new Exception("No fue posible encontrar el folder: " + folderToRetrieve);
            }
        } catch (Exception e) {
            throw e;
        }
        return retrivedMessages;
    }

    public static void closeFolder() throws MessagingException {
        if (retrivedFolder != null && retrivedFolder.isOpen()) {
            retrivedFolder.close(true);
        }
    }

    public static void closeStore() throws MessagingException {
        if (store != null && store.isConnected()) {
            store.close();
        }
    }

    public static void downloadAttachments(String path, Message message) throws IOException, MessagingException {
        Multipart multiPart = (Multipart) message.getContent();
        int numberOfParts = multiPart.getCount();
        for (int partCount = 0; partCount < numberOfParts; partCount++) {
            MimeBodyPart part = (MimeBodyPart) multiPart.getBodyPart(partCount);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                part.saveFile(path + File.separator + part.getFileName());
            }
        }
    }

    public static Message[] getMailsFromFolder(InputStream propertiesFile, String serverType, String folderToRetrieve, SearchTerm searchTerm) throws MessagingException, IOException, Exception {
        Properties properties = new Properties();
        properties.load(propertiesFile);
        return retrieveEmails(serverType, properties.getProperty("host"), properties.getProperty("port"), properties.getProperty("sslEnabled"), properties.getProperty("user"), properties.getProperty("password"), folderToRetrieve, searchTerm);
    }

    /*
    *  NOTAS: Al utilizar POP los mensajes no regresan la fecha de recepcion por lo tanto el filtro no funciona, 
    *   se debe buscar por direccion de correo o asunto.
     */
    public static void main(String[] args) throws Exception {
        InputStream inputStream = RetrieverMail.class.getResourceAsStream("../properties/configuration.properties");
        String serverType = ENTRY_SERVER_TYPE.IMAP.name();
        String folderToRetrieve = "INBOX";
        SearchTerm searchTerm = new ReceivedDateTerm(ComparisonTerm.EQ, new Date());

        try {
            Message[] retrivedMessages = getMailsFromFolder(inputStream, serverType, folderToRetrieve, searchTerm);
            if (retrivedMessages != null && retrivedMessages.length > 0) {
                for (Message message : retrivedMessages) {
                    if (message.getContentType().contains("multipart")) {
                        downloadAttachments("path", message);
                    }
                }
            } else {
                throw new Exception("No se encontraron correos para el folder: " + folderToRetrieve + " para el filtro solicitado.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeFolder();
            closeStore();
        }
    }
}
