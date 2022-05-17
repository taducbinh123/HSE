/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package applications.mail.connection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.sun.mail.iap.ProtocolException;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPFolder.ProtocolCommand;
import com.sun.mail.imap.IMAPStore;
import com.sun.mail.imap.protocol.IMAPProtocol;
import com.sun.mail.imap.protocol.ListInfo;

//import applications.mail.MailConfiguration;
import applications.mail.domain.Settings;
import applications.mail.domain.User;

@Service
@Qualifier("InMemoryIMAPStoreLRUCache")
@Primary
public class InMemoryIMAPStoreLRUCache implements IMAPStoreCache {

	protected final Logger logger = LogManager.getLogger(InMemoryIMAPStoreLRUCache.class);

	private static final int MAX_ENTRIES_STORE = 50;

	private int connectionPoolSize;
	private int timeout;
	private boolean debug;
	private boolean trustSSL;

	private final Map<String, CachedIMAPStore> pool = Collections
			.synchronizedMap(new LinkedHashMap<String, CachedIMAPStore>() {
				private static final long serialVersionUID = 1L;

				@Override
				protected boolean removeEldestEntry(Map.Entry<String, CachedIMAPStore> eldest) {
					boolean result = size() > MAX_ENTRIES_STORE;
					if (result) {
						Store eldestStore = eldest.getValue().getStore();
						if (eldestStore != null && eldestStore.isConnected()) {
							try {
								eldestStore.close();
							} catch (MessagingException e) {
								// Ignore on close
							}
						}
					}
					return result;
				}
			});

//	@Autowired
//	public InMemoryIMAPStoreLRUCache(MailConfiguration mailConfiguration) {
//		this.connectionPoolSize = mailConfiguration.getImapConnectionPoolSize();
//		this.timeout = mailConfiguration.getImapConnectionPoolTimeoutMilliseconds();
//		this.trustSSL = mailConfiguration.getIsTrustSSL();
//		if (!StringUtils.isEmpty(mailConfiguration.trueStoreName)) {
//			System.setProperty("javax.net.ssl.trustStore", mailConfiguration.getTrustStorePath());
//		}
//		if (!StringUtils.isEmpty(mailConfiguration.trueStorePassword)) {
//			System.setProperty("javax.net.ssl.trustStorePassword", mailConfiguration.trueStorePassword);
//		}
//		System.setProperty("javax.net.ssl.trustStoreType", mailConfiguration.trustStoreType);
//		System.setProperty("mail.mime.decodetext.strict", "false");
//	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.hupa.server.IMAPStoreCache#get(org.apache.hupa.shared.data.User)
	 */
	public IMAPStore get(User user) throws MessagingException {
		// FIXME, there will be a NullPointerException thrown here when user session
		// expired

		String id = user.getId();
		String username = user.getName();
		String password = user.getPassword();
		Settings settings = user.getSettings();

		CachedIMAPStore cstore = pool.get(username);
		if (cstore == null) {
			logger.debug("No cached store found for user " + username);
		} else {
			if (cstore.isExpired() == false) {
				try {
					cstore.validate();
				} catch (MessagingException e) {
				}
			} else {
				pool.remove(username);

				try {
					cstore.getStore().close();
					cstore = null;
				} catch (MessagingException e) {
				}
			}
		}

		if (cstore == null) {
			cstore = createCachedIMAPStore(user);
		}

		if (cstore.getStore().isConnected() == false) {
			Store store = cstore.getStore();
			store.connect(settings.getImapServer(), settings.getImapPort(), id, password);
		}

		pool.put(username, cstore);
		IMAPStore ret = cstore.getStore();
//		logger.info("Size of CachedIMAPStore = " + pool.size());

		// TODO: this is a hack for gmail
		if (settings.getImapServer().contains("gmail.com")) {
			internationalizeGmailFolders(user, ret);
		}

		return ret;
	}

	public void internationalizeGmailFolders(User user, IMAPStore store) {
		// TODO: this is a hack, we should have a default domain suffix in configuration
		// files
		if (!user.getName().contains("@")) {
			user.setName(user.getName() + "@gmail.com");
		}
		try {
			final IMAPFolder folder = (IMAPFolder) store.getDefaultFolder();
			final char c = folder.getSeparator();

			ListInfo[] li = (ListInfo[]) folder.doCommandIgnoreFailure(new ProtocolCommand() {
				public Object doCommand(IMAPProtocol p) throws ProtocolException {
					String arg = folder.getFullName() + c + "*";
					return p.lsub("", arg);
				}
			});

			for (ListInfo l : li) {
				if (l.attrs != null && l.attrs.length > 1) {
					// * LIST (\HasNoChildren \Drafts) "/" "[Gmail]/Borradores"
					String n = l.attrs[1];
					if ("\\Drafts".equals(n)) {
						user.getSettings().setDraftsFolderName(l.name);
					} else if ("\\Sent".equals(n)) {
						user.getSettings().setSentFolderName(l.name);
					} else if ("\\Trash".equals(n)) {
						user.getSettings().setTrashFolderName(l.name);
					} else if ("\\Junk".equals(n)) {
					}
				}
			}
		} catch (Exception e) {
		}
	}

	public CachedIMAPStore createCachedIMAPStore(User user) throws NoSuchProviderException {
		Session ses = createSession(user);
		IMAPStore store = (IMAPStore) ses.getStore(user.getSettings().getImapSecure() ? "imaps" : "imap");
		CachedIMAPStore ret = new CachedIMAPStore(store, 600);
		ret.setSession(ses);
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.apache.hupa.server.IMAPStoreCache#delete(org.apache.hupa.shared.data.
	 * User)
	 */
	public synchronized void delete(User user) {
		delete(user.getName());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.hupa.server.IMAPStoreCache#delete(java.lang.String)
	 */
	public synchronized void delete(String username) {
		CachedIMAPStore cstore = pool.get(username);
		if (cstore != null && cstore.getStore().isConnected()) {
			try {
				cstore.getStore().close();
			} catch (MessagingException e) {
				// Ignore on close
			}
		}
		pool.remove(username);
	}

	public void sendMessage(Message msg) throws MessagingException {
		Transport.send(msg);
	}

	public Session getMailSession(User user) {
		CachedIMAPStore cstore = pool.get(user.getName());
		return cstore.getSession();
	}

	private Session createSession(final User user) {
		Properties props = new Properties();
		Settings settings = user.getSettings();

		props.setProperty("mail.mime.decodetext.strict", "false");
		if (settings.getImapSecure()) {
			props.setProperty("mail.store.protocol", "imaps");
			props.setProperty("mail.imaps.connectionpoolsize", connectionPoolSize + "");
			props.setProperty("mail.imaps.connectionpooltimeout", timeout + "");
			props.setProperty("mail.imaps.timeout", timeout + "");
			props.setProperty("mail.imaps.connectiontimeout", timeout + "");
			if (trustSSL) {
				props.setProperty("mail.imaps.ssl.trust", settings.getImapServer());
			}
		} else {
			props.setProperty("mail.imap.connectionpoolsize", connectionPoolSize + "");
			props.setProperty("mail.imap.connectionpooltimeout", timeout + "");
			props.setProperty("mail.imap.timeout", timeout + "");
			props.setProperty("mail.imap.connectiontimeout", timeout + "");
		}

		if (settings.getSmtpSecure()) {
			if (settings.getSmtpPort() == 587 || settings.getSmtpPort() == 10587) {
				props.setProperty("mail.smtp.starttls.enable", "true");
				props.setProperty("mail.transport.protocol.rfc822", "smtp");
			} else {
				props.setProperty("mail.transport.protocol.rfc822", "smtps");
				props.setProperty("mail.smtps.ssl.enable", "true");
				if (trustSSL) {
					props.setProperty("mail.smtps.ssl.trust", settings.getSmtpServer());
				}
			}
		} else {
			props.setProperty("mail.transport.protocol.rfc822", "smtp");
		}

		props.setProperty("mail.smtp.host", settings.getSmtpServer());
		props.setProperty("mail.smtps.host", settings.getSmtpServer());
		props.setProperty("mail.smtp.port", settings.getSmtpPort() + "");
		props.setProperty("mail.smtps.port", settings.getSmtpPort() + "");

		Authenticator auth = null;
		if (settings.getSmtpAuth() && user.getPassword() != null && user.getName() != null) {
			props.setProperty("mail.smtp.auth", "true");
			props.setProperty("mail.smtps.auth", "true");
			auth = new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					String userId = user.getId();
					StackTraceElement[] sElms = Thread.currentThread().getStackTrace();
					for (StackTraceElement e : sElms) {
						if (e.getClassName().equals(InMemoryIMAPStoreLRUCache.class.getName())
								&& e.getMethodName().equals("get")) {
							// We try with the id part the second time (unix imap/smtp auth compatible)
							if (userId.matches(".*@.*")) {
								userId = userId.replaceFirst("@.*", "");
								user.setId(userId);
								break;
							} else {
								return null;
							}
						}
					}
					return new PasswordAuthentication(userId, user.getPassword());
				}
			};
		}

		Session ses = Session.getInstance(props, auth);
		ses.setDebug(debug && logger.isDebugEnabled());
		logger.debug("Created session " + user.getName() + "\n" + settings + "\n"
				+ props.toString().replaceAll(",", ",\n "));
		return ses;
	}
}
