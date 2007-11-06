/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2004-2007, Beneficent
Technology, Inc. (The Benetech Initiative).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/

package org.martus.mspa.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.Vector;

import org.martus.common.LoggerInterface;
import org.martus.common.LoggerToConsole;
import org.martus.common.MagicWords;
import org.martus.common.MartusLogger;
import org.martus.common.MartusUtilities;
import org.martus.common.Version;
import org.martus.common.MartusUtilities.FileVerificationException;
import org.martus.common.MartusUtilities.InvalidPublicKeyFileException;
import org.martus.common.MartusUtilities.PublicInformationInvalidException;
import org.martus.common.crypto.MartusCrypto;
import org.martus.common.crypto.MartusCrypto.MartusSignatureException;
import org.martus.common.database.FileDatabase;
import org.martus.common.database.ServerFileDatabase;
import org.martus.common.network.MartusNonSSLXmlrpcClient;
import org.martus.common.network.MartusSecureWebServer;
import org.martus.common.network.MartusXmlRpcServer;
import org.martus.common.packet.UniversalId;
import org.martus.common.serverside.ServerSideUtilities;
import org.martus.common.utilities.MartusServerUtilities;
import org.martus.mspa.common.AccountAdminOptions;
import org.martus.mspa.common.ManagingMirrorServerConstants;
import org.martus.mspa.common.RetrievePublicKey;
import org.martus.mspa.common.network.NetworkInterfaceConstants;
import org.martus.mspa.common.network.NetworkInterfaceXmlRpcConstants;
import org.martus.mspa.common.network.ServerSideHandler;
import org.martus.mspa.roothelper.RootHelperHandler;
import org.martus.mspa.roothelper.Status;
import org.martus.util.DirectoryUtils;
import org.martus.util.FileTransfer;
import org.martus.util.MultiCalendar;
import org.martus.util.UnicodeWriter;


public class MSPAServer implements NetworkInterfaceXmlRpcConstants
{
		
	public MSPAServer(File dir) 
	{				
		serverDirectory = dir;			
		authorizedMartusAccounts = new Vector();
		authorizeMSPAClients = new Vector();
		logger = new LoggerToConsole();	
		mspaHandler = new ServerSideHandler(this);
								
	}
	
	public void initConfig() throws Exception
	{			
		initializedEnvironmentDirectory();	
		loadConfigurationFiles();
		verifyAssignedMirrorServersInfo();
	}	
	
	private void verifyAssignedMirrorServersInfo()
	{
		loadAvailabeMirrorServerPublicKeys();
		Vector assignedServer = loadAssignedMirrorServerInfo();	
		File destDirectory = MSPAServer.getAvailableServerDirectory();		
		
		try
		{
			System.out.println("\nVerify mirror/backup server(s) environments ...");
			for (int i=0; i< assignedServer.size();i++)
			{
				File file = (File) assignedServer.get(i);			
				String assignedPublicKey = retrievePublickey(file);
				if (!isPublicKeyMatched(assignedPublicKey))
				{
					FileTransfer.copyFile(file, new File(destDirectory, file.getName()));
					logAction("Warning: FileTransfer()", "The file ("+file.getPath()+") is not existing in AvailableServers/.");
				}
			}		
			System.out.println("Completed mirror/backup server(s) checking ...\n");
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}				
	}	
	
	private boolean isPublicKeyMatched(String key)
	{		
		for (int i=0; i< availabelMirrorServerPublicKeys.size();i++)
		{
			String avaialbeKey = (String) availabelMirrorServerPublicKeys.get(i);
			if (key.equals(avaialbeKey))
				return true;			
		}			
		return false;
	}			
	
	private Vector loadAssignedMirrorServerInfo()
	{
		Vector assignedServerFiles = new Vector();
		addAssignedBackupServers (assignedServerFiles, getServerWhoWeCallDirectory().listFiles());
		addAssignedBackupServers (assignedServerFiles, getMirrorServerWhoCallUsDirectory().listFiles());
		addAssignedBackupServers (assignedServerFiles, getMirrorServerWhoWeCallDirectory().listFiles());
		addAssignedBackupServers (assignedServerFiles, getAmpsWhoCallUsDirectory().listFiles());
			
		return assignedServerFiles;	
	}	
	
	private void addAssignedBackupServers(Vector assignedServer, File[] files)
	{	
		if (files.length > 0)
			assignedServer.addAll(Arrays.asList(files));	
	}
	
	private void loadAvailabeMirrorServerPublicKeys()
	{
		availabelMirrorServerPublicKeys = new Vector();
		File availableServerDir = MSPAServer.getAvailableServerDirectory();
		File[] keyFiles = availableServerDir.listFiles();

		for (int i=0;i< keyFiles.length;i++)
		{	
			String serverPublicKey = retrievePublickey(keyFiles[i]);
			if (serverPublicKey != "")
				availabelMirrorServerPublicKeys.add(serverPublicKey);
		}	
	}
	
	private String retrievePublickey(File publicKey)
	{				
		String serverPublicKey ="";						
		try
		{
			Vector publicInfo = MartusUtilities.importServerPublicKeyFromFile(publicKey, security);		
			serverPublicKey = (String)publicInfo.get(0);				
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (InvalidPublicKeyFileException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (PublicInformationInvalidException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return serverPublicKey;
	}
	
	private void setMagicWords() throws Exception
	{
		magicWords = new MagicWords(logger);
		magicWords.loadMagicWords(getMagicWordsFile());
	}			
	
	private void initializedEnvironmentDirectory()
	{			
		try
		{
			System.out.println("Initialize environments ...");
											
			getServerWhoWeCallDirectory().mkdirs();
			getMirrorServerWhoCallUsDirectory().mkdirs();
			getMirrorServerWhoWeCallDirectory().mkdirs();
			getAmpsWhoCallUsDirectory().mkdirs();
			getAvailableServerDirectory().mkdirs();
			getMartusServerDataDirectory().mkdirs();
				
			initAccountConfigFiles(new File(getMartusServerDataDirectory(), MAGICWORDS_FILENAME));
			initAccountConfigFiles(new File(getMartusServerDataDirectory(), UPLOADSOK_FILENAME));		
			initAccountConfigFiles(new File(getMartusServerDataDirectory(), CLIENTS_NOT_TO_AMPLIFY_FILENAME));
			initAccountConfigFiles(new File(getMartusServerDataDirectory(), UPLOADSOK_FILENAME));
			initAccountConfigFiles(new File(getMartusServerDataDirectory(), BANNEDCLIENTS_FILENAME));
			initAccountConfigFiles(new File(getMartusServerDataDirectory(), HIDDEN_PACKETS_FILENAME));
			
			System.out.println("Completed setting up server environments...\n");	
		}
		catch (RemoteException e)
		{
			e.printStackTrace();
		}
		catch (IOException e)
		{
			log(" Error when initialized configuration files."+e.toString());
		}		
	}
	
	private void initAccountConfigFiles(File targetFile) throws IOException
	{		
		targetFile.createNewFile();			
	}
	
	private void loadConfigurationFiles() throws IOException
	{						
		clientsBanned = MartusUtilities.loadClientListAndExitOnError(getBannedFile());
		clientsAllowedUpload = MartusUtilities.loadClientList(getAllowUploadFile());		
		clientNotSendToAmplifier = MartusUtilities.loadClientListAndExitOnError(getClientsNotToAmplifiyFile());
				
		hiddenBulletins = new HiddenBulletins(getDatabase(),security, getLogger(), getHiddenPacketsFile());
		loadAuthorizedClients();
		loadMagicWords();
	}

	private void loadAuthorizedClients() 
	{
		if (!getAuthorizedClientsDir().exists())
		{			
			System.out.println("Warning: missing authorizedclients information.");
			return;
		}	
		
		File[] authorizedDir = getAuthorizedClientsDir().listFiles();
		if (authorizedDir.length ==0)
		{	
			System.out.println("No client has been authorized yet. "+getAuthorizedClientsDir().getPath()+" was empty.");
			return;
		}
				
		System.out.println("Load authorized clients now.");
		for (int i=0; i<authorizedDir.length;i++)
		{	
			File authorizedFile = authorizedDir[i];
			if(!authorizedFile.isDirectory())
			{				
				try
				{
					Vector publicInfo = MartusUtilities.importServerPublicKeyFromFile(authorizedFile, security);
					String serverPublicKey = (String)publicInfo.get(0);
					authorizeMSPAClients.add(serverPublicKey);
					System.out.println("Client authorized to call us: "+authorizedFile.getName());
				}
				catch (Exception e)
				{
					e.printStackTrace();
					log("Error when load "+authorizedFile.getName());
				}
			}
		}			
	}	
	
	private void loadMagicWords() throws IOException
	{
		getMagicWordsInfo().loadMagicWords(getMagicWordsFile());
		log("Loaded " + getMagicWordsInfo().getNumberOfAllMagicWords() + " magic words");
	}
	
	public void initalizeFileDatabase()
	{									
		martusDatabaseToUse = new ServerFileDatabase(getPacketDirectory(), security);		

		try
		{
			martusDatabaseToUse.initialize();
		}
		catch(FileDatabase.MissingAccountMapException e)
		{
			e.printStackTrace();
			System.out.println("Missing Account Map File");
			System.exit(7);
		}
		catch(FileDatabase.MissingAccountMapSignatureException e)
		{
			e.printStackTrace();
			System.out.println("Missing Account Map Signature File");
			System.exit(7);
		}
		catch(FileVerificationException e)
		{
			e.printStackTrace();
			System.out.println("Account Map did not verify against signature file");
			System.exit(7);
		}		
	}	
	
	public void loadMSPAKeypair(char[] passphrase) 
	{		
		try
		{
			security = MartusServerUtilities.loadCurrentMartusSecurity(getMSPAServerKeyPairFile(), passphrase);
			System.out.println("Passphrase correct.");		
		}
		catch (MartusCrypto.AuthorizationFailedException e)
		{
			System.err.println("Error probably bad passphrase: " + e + "\n");
			System.exit(1);
		}
		catch(Exception e)
		{
			System.err.println("Error loading keypair: " + e + "\n");
			System.exit(3);
		}
	}	
	
	public static File getMSPADeleteOnStartup()
	{
		return new File(getAppDirectoryPath(),DELETE_ON_STARTUP);
	}
	
	public static File getMartusServerDeleteOnStartup()
	{
		return new File(getMartusServerDataDirectory(), DELETE_ON_STARTUP);
	}
	
	public File getTriggerDirectory()
	{
		return new File(getMartusServerDataDirectory(), ADMINTRIGGERDIRECTORY);
	}

	public static File getAuthorizedClientsDir()
	{
		return new File(getMSPADeleteOnStartup(),MSPA_CLIENT_AUTHORIZED_DIR );
	}
	
	public File getMSPAServerKeyPairFile()
	{
		return new File(getMSPADeleteOnStartup().getPath(), KEYPAIR_FILE);
	}
	
	public String getMSAPKeypairFileName()
	{
		return getMSPAServerKeyPairFile().getPath();
	}
	
	public File getBannedFile()
	{
		return new File(getMartusServerDataDirectory(), BANNEDCLIENTS_FILENAME);
	}
	
	public File getAllowUploadFile()
	{
		return new File(getMartusServerDataDirectory(), UPLOADSOK_FILENAME);
	}
	
	public File getClientsNotToAmplifiyFile()
	{
		return new File(getMartusServerDataDirectory(), CLIENTS_NOT_TO_AMPLIFY_FILENAME);
	}
	
	public File getMagicWordsFile()
	{
		return new File(getMartusServerDataDirectory(), MAGICWORDS_FILENAME);		
	}	
	
	public File getPacketDirectory()
	{
		return new File(getMartusServerDataDirectory(), "packets");
	}
	
	public File getServerDirectory()
	{
		return serverDirectory;
	}		
	
	private File getHiddenPacketsFile()
	{
		return new File(getMartusServerDataDirectory(), HIDDEN_PACKETS_FILENAME);
	}	
	
	public static File getServerWhoWeCallDirectory()
	{
		return new File(getMartusServerDataDirectory(),"serversWhoWeCall");
	}
	
	public static File getMirrorServerWhoCallUsDirectory()
	{
		return new File(getMartusServerDataDirectory(),"mirrorsWhoCallUs");
	}
	
	public static File getMirrorServerWhoWeCallDirectory()
	{
		return new File(getMartusServerDataDirectory(),"mirrorsWhoWeCall");
	}
	
	public static File getAmpsWhoCallUsDirectory()
	{
		return new File(getMartusServerDataDirectory(),"ampsWhoCallUs");
	}
	
	public static File getAvailableServerDirectory()
	{
		return new File(getMartusServerDataDirectory(),"AvailableServers");
	}		
	
	public File getMartusServerDataComplianceFile()
	{
		return new File(getMartusServerDataDirectory(),COMPLIANCE_FILE );
	}	
	
	public static File getMartusServerDataDirectory()
	{
		return new File(getAppDirectoryPath(),MARTUS_SERVER_DATA);
	}
	
	public static File getMartusServerDataBackupDirectory()
	{
		return new File(getMartusServerDataDirectory(),MARTUSSERVER_BACKUP_DIRECTORY);
	}	
	
	public MagicWords getMagicWordsInfo()
	{
		return magicWords;
	}
	
	public synchronized Vector getComplianceFile(String accountId)
	{
		Vector results = new Vector();
		File complianceFile = getMartusServerDataComplianceFile();	
		try
		{
			if (complianceFile.createNewFile())				
				return new Vector();
			results = FileTransfer.readDataFromFile(complianceFile);
		}
		catch (RemoteException e)
		{
			e.printStackTrace();
			log(" Error when try to get a compliance file."+e.toString());
		}
		catch (IOException e)
		{
			e.printStackTrace();
			log(" Error when try to get a compliance file."+e.toString());
		}
		
		return results;	
	}

	public synchronized void updateComplianceFile(String accountId, String compliantsMsg)
	{		
		File complianceFile = getMartusServerDataComplianceFile();		
		try
		{
		
			logAction("Update compliance file", compliantsMsg);
			complianceFile.mkdirs();
			backupFile(complianceFile);
						
			UnicodeWriter writer = new UnicodeWriter(complianceFile);
			writer.writeln(compliantsMsg);
			writer.close();
			
		}
		catch (RemoteException e)
		{			
			e.printStackTrace();
			log(" Error when try to update a compliance file."+e.toString());
		}
		catch (IOException e)
		{			
			e.printStackTrace();
			log(" Error when try to update a compliance file."+e.toString());
		}	
	}	
	 
	private void writeHiddenBulletinToFile()
	{
		try
		{		
			backupFile(getHiddenPacketsFile());
						
			UnicodeWriter writer = new UnicodeWriter(getHiddenPacketsFile());							
			for (int aId = 0; aId < authorizedMartusAccounts.size();++aId)
			{	
				String currentAccountId = (String) authorizedMartusAccounts.get(aId);
				writer.writeln(currentAccountId);	 	
				hiddenBulletins.writeLineOfHiddenBulletinsToFile(currentAccountId, writer);
			}
			writer.close();
		}
		catch (Exception ieo)
		{	
			log("Unable to read/write isHidden.txt."+ ieo.toString());		
		}			
	}
	
	public boolean containHiddenBulletin(UniversalId uid)
	{
		return hiddenBulletins.containHiddenUids(uid);
	}
	
	public synchronized boolean hideBulletins(String accountId, Vector localIds)
	{	
		hiddenBulletins.hideBulletins(accountId, localIds);
		logActions("Hide Bulletins", localIds);			
		writeHiddenBulletinToFile();		

		return true;
	}
	
	public synchronized boolean recoverHiddenBulletins(String accountId, Vector localIds)
	{	
		if (!hiddenBulletins.recoverHiddenBulletins(accountId, localIds))
			return false;

		logActions("Recover Bulletins", localIds);				
		writeHiddenBulletinToFile();	
		return true;
	}
	
	public Vector getListOfHiddenBulletins(String accountId)
	{
		return hiddenBulletins.getListOfHiddenBulletins(accountId);
	}
	
	public synchronized boolean addAvailableServer(Vector mirrorInfo)
	{	
		if (mirrorInfo.size() > 0)
			return false;

		boolean success=true;
		try 
		{				
			String ip = (String) mirrorInfo.get(0);
			String publicCode = (String) mirrorInfo.get(1);				
			String fileName = (String) mirrorInfo.get(2);			
			String port = String.valueOf(getPortToUse());

			logActions("Add New Server<dir>"+ fileName, mirrorInfo);					 
			
			File outputFileName = new File(getAvailableServerDirectory(), fileName.trim());
			RetrievePublicKey retrievePubKey = new RetrievePublicKey(ip, port, publicCode, outputFileName.getPath());				 
			
			success = retrievePubKey.isSuccess();
		}
		catch (MartusSignatureException e)
		{
			e.printStackTrace();
			log("MartusSignatureException: when tried to generate a publickey"+e.getMessage());
			return false;
		}	

		return success;
	}	
	
	public synchronized void updateAssignedServerInfo(Vector mirrorInfo, int mirrorType)
	{
		File sourceDirectory = MSPAServer.getAvailableServerDirectory();
		File destDirectory = MSPAServer.getMirrorDirectory(mirrorType);		
		
		try 
		{				
			logActions("Update Other Server configuration <dir>"+destDirectory.getName(), mirrorInfo);					 
			deleteAllFilesFromMirrorDirectory(destDirectory.listFiles());
			
			for (int i =0; i<mirrorInfo.size();i++)
			{
				String file = (String) mirrorInfo.get(i);
				FileTransfer.copyFile(new File(sourceDirectory, file), new File(destDirectory, file));
			}	
		}
		catch (IOException e) 
		{
			e.printStackTrace();
			log("(Update Mirror Server) Problem when try to update/copy files: "+ e.toString());
		}	
	}	
	
	void deleteAllFilesFromMirrorDirectory(File[] files)
	{
		for (int i=0;i<files.length;i++)
			files[i].delete();
	}
	
	public synchronized Vector getActiveMagicWords()
	{
		logAction("getActiveMagicWords", "");
		return getMagicWordsInfo().getActiveMagicWords();
	}

	public synchronized Vector getAllMagicWords()
	{
		logAction("getAllMagicWords", "");
		return getMagicWordsInfo().getAllMagicWords();
	}

	public synchronized Vector getInactiveMagicWordsWithNoSign()
	{
		logAction("getInactiveMagicWords", "");
		return getMagicWordsInfo().getInactiveMagicWordsWithNoSign();
	}

	public synchronized void updateMagicWords(Vector words) throws Exception
	{				
		logActions("Update MagicWords", words);				
							
		backupFile(getMagicWordsFile());
		magicWords.writeMagicWords(getMagicWordsFile(), words);
		magicWords.loadMagicWords(getMagicWordsFile());
			
	}	
	
	public Vector getAccountAdminInfo(String manageAccountId)
	{		
		AccountAdminOptions options = new AccountAdminOptions();
		options.setCanSendOption(!isAccountNotSendToAmplifier(manageAccountId));
		options.setBannedOption(isAccountBanned(manageAccountId));
		options.setCanUploadOption(isAccountAllowedUpload(manageAccountId));
		
		return options.getOptions();
	}
	
	public synchronized void updateAccountInfo(String manageAccountId, Vector accountInfo)
	{			
		AccountAdminOptions options = new AccountAdminOptions();
		options.setOptions(accountInfo);

		updateBannedAccount(options.isBannedSelected(), manageAccountId);
		updateAccountAllowedUpload(options.canUploadSelected(), manageAccountId);
		updateAccountSendToAmplifier(options.canSendToAmplifySelected(), manageAccountId);
	
		updateAccountConfigFiles();								
	}	
	
	private void updateAccountConfigFiles()
	{
		writeListToFile(getBannedFile(), clientsBanned);
		writeListToFile(getAllowUploadFile(), clientsAllowedUpload);
		writeListToFile(getClientsNotToAmplifiyFile(), clientNotSendToAmplifier);
	}
	
	private void writeListToFile(File file, Vector list)
	{
		try
		{
			backupFile(file);
			MartusUtilities.writeListToFile(file, list);
		}
		catch (Exception ieo)
		{	
			log(file.getPath()+" file not found."+ ieo.toString());		
		} 
	}	
	
	private void updateBannedAccount(boolean isSelected, String accountId)
	{			
		if (isSelected)
		{							
			addBannedAccount(accountId);
			logAction("Add banned account ", accountId);				
		}
		else
		{				
			clientsBanned.remove(accountId);
			logAction("Remove banned account ", accountId);				
		}
	}
	
	private void updateAccountAllowedUpload(boolean isSelected, String accountId)
	{
		if (isSelected)	
		{						
			addAccountAllowedUpload(accountId);
			logAction("Add allowed upload account ", accountId);			
		}
		else
		{	
			clientsAllowedUpload.remove(accountId);
			logAction("Remove allowed upload account ", accountId);
		}	
	}
	
	private void updateAccountSendToAmplifier(boolean isSelected, String accountId)
	{
		if (isSelected)
		{					
			clientNotSendToAmplifier.remove(accountId);
			logAction("Remove <mirrorsWhoWeCall> from directory", accountId);				
		}	
		else			
		{	
			addAccountNotSendToAmplifier(accountId);
			logAction("Add <mirrorsWhoWeCall> to directory", accountId);
		}
	}
	
	private void addBannedAccount(String clientId)
	{
		if (!isAccountBanned(clientId))
			clientsBanned.add(clientId);
	}
	
	private void addAccountAllowedUpload(String clientId)
	{
		if (!isAccountAllowedUpload(clientId))
			clientsAllowedUpload.add(clientId);
	}
	
	private void addAccountNotSendToAmplifier(String clientId)
	{
		if (!isAccountNotSendToAmplifier(clientId))
			clientNotSendToAmplifier.add(clientId);
	}
	
	public boolean isAccountBanned(String clientId)
	{
		return clientsBanned.contains(clientId);
	}	
	
	public boolean isAccountAllowedUpload(String clientId)
	{
		return clientsAllowedUpload.contains(clientId);
	}
	
	public boolean isAccountNotSendToAmplifier(String clientId)
	{
		return clientNotSendToAmplifier.contains(clientId);
	}	
	
	public void createMSPAXmlRpcServerOnPort(int port) throws Exception
	{
		MartusLogger.log("Initializing SSL server (this can take up to a minute)...");
		MartusSecureWebServer.security = getSecurity();
		MartusXmlRpcServer.createSSLXmlRpcServer(getMSPAHandler(),serverObjectName, port, getMainIpAddress());
	}
	
	public ServerSideHandler getMSPAHandler()
	{
		return mspaHandler;
	}
	
	private void backupFile(File from) throws IOException
	{			
		String file = from.getName()+"."+getBackupFileExtension();
		File backupFile = new File(getMartusServerDataBackupDirectory(), file);
		if (!backupFile.exists())			
		{
			deletePreviousBackupFile(from.getName());	
			FileTransfer.copyFile(from, backupFile);
		}
	}
	
	private String getBackupFileExtension()
	{
		MultiCalendar calendar = new MultiCalendar();
		return calendar.toIsoDateString();
	}	
	
	private void deletePreviousBackupFile(String targetFileName)
	{		
		File backupDir = getMartusServerDataBackupDirectory();
		backupDir.mkdirs();
		File[] files = backupDir.listFiles();
		for (int i=0; i<files.length;++i)
		{			
			String filename = files[i].getName();
			if (filename.startsWith(targetFileName))
				files[i].delete();
		}					
	}
	
	private void logActions(String action, Vector data)
	{
		String actionMsg = "["+action+"]: "; 
		StringBuffer recordMsg = new StringBuffer();
		recordMsg.append(actionMsg).append("\n");
		for (int i=0;i<data.size();++i)
		{
			recordMsg.append("("+i+")").append((String)data.get(i)).append("\n");
		}
		log(recordMsg.toString());		
	}
	
	private void logAction(String action, String msg)
	{
		String actionMsg = "["+action+"]: "; 
		StringBuffer recordMsg = new StringBuffer();
		recordMsg.append(actionMsg).append(msg);
		log(recordMsg.toString());		
	}
	
	public LoggerInterface getLogger()
	{
		return logger;
	}	
	
	public InetAddress getMainIpAddress() throws UnknownHostException
	{
		return InetAddress.getByName(ipAddress);
	}
	
	public MartusCrypto getSecurity()
	{
		return security;
	}
	
	public ServerFileDatabase getDatabase()
	{		
		return martusDatabaseToUse;
	}
	
	boolean isAuthorizedMSPAClient(String myAccountId)
	{
		return authorizedMartusAccounts.contains(myAccountId);
	}
	
	public String ping()
	{
		return "" + NetworkInterfaceConstants.VERSION;
	}
	
	public static File getMartusDefaultDataDirectory()
	{
		return new File(MSPAServer.getMartusDefaultDataDirectoryPath());
	}	
	
	public static String getMartusDefaultDataDirectoryPath()
	{
		String dataDirectory = null;
		if(Version.isRunningUnderWindows())
			dataDirectory = WINDOW_MARTUS_ENVIRONMENT;
		else
			dataDirectory = UNIX_MARTUS_ENVIRONMENT;
		return dataDirectory;
	}
	
	
	public static File getAppDirectoryPath()
	{
		String appDirectory = null;
		if(Version.isRunningUnderWindows())
			appDirectory = WINDOW_MSPA_ENVIRONMENT;
		else
			appDirectory = UNIX_MSPA_ENVIRONMENT;
		return new File(appDirectory);
	}	
	
	public void setPortToUse(int port)
	{
		portToUse = port;
	}

	public int getPortToUse()
	{
		return (portToUse <= 0)? DEFAULT_PORT:portToUse;
	}
	
	public synchronized void addAuthorizedMartusAccounts(String authorizedClientId)
	{
		if (!isAuthorizedMartusAccounts(authorizedClientId))
			authorizedMartusAccounts.add(authorizedClientId);
	}
	
	public synchronized void addAuthorizedMSPAClients(String authorizedClientId)
	{
		if (!isAuthorizedMSPAClients(authorizedClientId))
			authorizeMSPAClients.add(authorizedClientId);
	}
	
	public boolean isAuthorizedMSPAClients(String authorizedClientId)
	{
		return authorizeMSPAClients.contains(authorizedClientId);
	}

	public boolean isAuthorizedMartusAccounts(String authorizedClientId)
	{
		return authorizedMartusAccounts.contains(authorizedClientId);
	}
	
	public Vector getAuthorizedMartusAccounts()
	{
		return authorizedMartusAccounts;
	}

	public void setListenersIpAddress(String ipAddr) 
	{
		ipAddress = ipAddr;
	}
	
	public synchronized void log(String message)
	{
		getLogger().logNotice(message);
	}

	public synchronized void updateMartusServerArguments(Vector props)
	{
		File propertyFile = new File(getMSPADeleteOnStartup(), MARTUS_ARGUMENTS_PROPERTY_FILE);
		LoadMartusServerArguments args = new LoadMartusServerArguments();
		args.convertFromVector(props);
		args.writePropertyFile(propertyFile.getPath());
	}
	
	public synchronized static LoadMartusServerArguments getMartusServerArguments()
	{
		File propertyFile = new File(getMSPADeleteOnStartup(), MARTUS_ARGUMENTS_PROPERTY_FILE);
		LoadMartusServerArguments property = null;

		try
		{
			if (propertyFile.createNewFile())
			{				
				property = loadDefaultMartusServerArguments(propertyFile.getPath());			
			}
			else
				property = new LoadMartusServerArguments( propertyFile.getPath());
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
						
		return property;
	}	

	public synchronized static LoadMartusServerArguments loadDefaultMartusServerArguments(String propertyFile)
	{
		LoadMartusServerArguments property = new LoadMartusServerArguments(propertyFile);

		property.setProperty(LoadMartusServerArguments.LISTENER_IP,"");
		property.setProperty(LoadMartusServerArguments.PASSWORD,"no");
		property.setProperty(LoadMartusServerArguments.AMPLIFIER_IP,"");
		property.setProperty(LoadMartusServerArguments.AMPLIFIER_INDEXING_MINUTES,"5");
		property.setProperty(LoadMartusServerArguments.AMPLIFIER,"no");
		property.setProperty(LoadMartusServerArguments.CLIENT_LISTENER,"no");
		property.setProperty(LoadMartusServerArguments.MIRROR_LISTENER,"no");
		property.setProperty(LoadMartusServerArguments.AMPLIFIER_LISTENER,"no");	

		property.writePropertyFile(propertyFile);
		
		return property;
	}
	
	public boolean deleteStartupFiles()
	{
		if(!isSecureMode())
			return true;
			
		Vector deleteList = new Vector();	
		File[] startupFiles = getMSPADeleteOnStartup().listFiles();
		for (int i=0; i<startupFiles.length;++i)
		{
			if (startupFiles[i].isDirectory())
			{
				File[] files = startupFiles[i].listFiles();	
				deleteList.addAll(Arrays.asList(files));			
			}
		
			deleteList.add(startupFiles[i]);
		}
				
		MartusUtilities.deleteAllFiles(deleteList);
		
		File[] remainingStartupFiles = getMSPADeleteOnStartup().listFiles();
		if(remainingStartupFiles.length != 0)
		{
			log("Files still exist in the folder: " + getMSPADeleteOnStartup().getAbsolutePath());
			return false;
		}
		return true;
	}

	public void enterSecureMode()
	{
		secureMode = true;
	}

	public boolean isSecureMode()
	{
		return secureMode;
	}	
	
	private Vector getStartupFiles()
	{
		Vector files = new Vector();
		
		files.add(getMSPAServerKeyPairFile());		
		files.add(getBannedFile());
		files.add(getAllowUploadFile());
		files.add(getClientsNotToAmplifiyFile());
		files.add(getMagicWordsFile());
		files.add(getHiddenPacketsFile());
		files.add(getMartusServerDataComplianceFile());
		files.add(new File(getMSPADeleteOnStartup(), MARTUS_ARGUMENTS_PROPERTY_FILE));	
		files.add(new File(getMartusServerDataDirectory(), "webauthorized.txt"));				
		files.add(new File(getMartusServerDataDirectory(), "authorizelog.txt"));				
		
		return files;		
	}
	
	private Vector getStartupFolders()
	{
		Vector folders = new Vector();		
						
		folders.add(getAuthorizedClientsDir());	
		folders.add(getServerWhoWeCallDirectory());
		folders.add(getMirrorServerWhoCallUsDirectory());
		folders.add(getMirrorServerWhoWeCallDirectory());
		folders.add(getAmpsWhoCallUsDirectory());
		folders.add(getAvailableServerDirectory());
		folders.add(getMartusServerDataBackupDirectory());
		folders.add(getTriggerDirectory());
		folders.add(new File(getMartusServerDataDirectory(), "ampIndex"));
		folders.add(new File(getMartusServerDataDirectory(), "ampPackets"));
		folders.add(new File(getMartusServerDataDirectory(), "signatures"));
		folders.add(getMartusServerDeleteOnStartup());
		folders.add(getPacketDirectory());
		
		return folders;
	}
	
	private Vector getCurrentFilesAndFolders()
	{
		File[] deleteOnStartUp = DirectoryUtils.listFiles(getMSPADeleteOnStartup());
		File[] martusServerData = DirectoryUtils.listFiles(getMartusServerDataDirectory());
		
		Vector currentList = new Vector(Arrays.asList(deleteOnStartUp));
		currentList.addAll(Arrays.asList(martusServerData));			
		return currentList;
	}
	
	private boolean anyUnexpectedFilesOrFoldersInStartupDirectory()
	{
		Vector startupFilesWeExpect = getStartupFiles();
		Vector startupFoldersWeExpect = getStartupFolders();
		Vector allFilesAndFoldersInStartupDirectory = getCurrentFilesAndFolders();
		for(int i = 0; i<allFilesAndFoldersInStartupDirectory.size(); ++i)
		{
			File file = (File) allFilesAndFoldersInStartupDirectory.get(i);
			if(file.isFile()&&!startupFilesWeExpect.contains(file))
			{	
				log("Startup File not expected: " + file.getAbsolutePath());
				return true;
			}
			if(file.isDirectory()&&!startupFoldersWeExpect.contains(file))
			{	
				log("Startup Folder not expected: " + file.getAbsolutePath());
				return true;
			}
		}
		return false;
	}
	
	private void processCommandLine(String[] args) 
	{	
		String listenersIpTag = "--listener-ip=";	
		String portToListenTag = "--port=";
		String secureModeTag = "--secure";
		String rootPortTag = "--roothelper-port=";		
		String noPasswordTag = "--nopassword";

		System.out.println("");
		for(int arg = 0; arg < args.length; ++arg)
		{
			String argument = args[arg];
			
			if(argument.startsWith(listenersIpTag))
			{	
				String ip = argument.substring(listenersIpTag.length());
				setListenersIpAddress(ip);
				System.out.println("Listener IP to use: "+ ip);
			}
				
			if(argument.startsWith(portToListenTag))
			{	
				String portToListen = argument.substring(portToListenTag.length());
				setPortToUse(Integer.parseInt(portToListen));	
				System.out.println("Port to use for clients: "+ getPortToUse());
			}
			
			if(argument.startsWith(rootPortTag))
			{	
				String portToListen = argument.substring(rootPortTag.length());
				rootHelperPort = Integer.parseInt(portToListen);	
				System.out.println("Port to use for connect to RootHelper: "+ rootHelperPort);
			}
						
			if(argument.equals(secureModeTag))
				enterSecureMode();
			
			if(argument.equals(noPasswordTag))
				insecurePassword = "password".toCharArray();
				
		}
		
		if (isSecureMode())
			System.out.println("**** Running in SECURE mode ****");
		else
			System.out.println("**** Running in INSECURE mode ****");
			
		System.out.println("");
	}

	public Status startServer()
	{
		logger.logDebug("startServer");
		return executeRootHelperCommand(RootHelperHandler.RootHelperStartServicesCommand);
	}

	public Status stopServer()
	{
		logger.logDebug("stopServer");
		return executeRootHelperCommand(RootHelperHandler.RootHelperStopServicesCommand);
	}
	
	public Status restartServer()
	{
		logger.logDebug("restartServer");
		return executeRootHelperCommand(RootHelperHandler.RootHelperRestartServicesCommand);
	}
	
	private Status executeRootHelperCommand(String command)
	{
		try
		{
			MartusNonSSLXmlrpcClient rootHelper = new MartusNonSSLXmlrpcClient(LOCALHOST, rootHelperPort);
			String result = (String)rootHelper.callserver(RootHelperHandler.RootHelperObjectName, command, new Vector());
			Status status = new Status(result);
			return status;
		} 
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			Status status = new Status(NetworkInterfaceConstants.EXEC_ERROR);
			return status;
		}
	}
	
	private void deleteRunningFile()
	{
		getRunningFile().delete();
	}

	private File getRunningFile()
	{
		File runningFile = new File(getTriggerDirectory(), "running");
		return runningFile;
	}
	
	public File getShutdownFile()
	{
		return new File(getTriggerDirectory(), SHUTDOWN_FILENAME);
	}
	
	public boolean isShutdownRequested()
	{
		boolean exitFile = getShutdownFile().exists();
		if(exitFile && !loggedShutdownRequested)
		{
			loggedShutdownRequested = true;
			log("Exit file found, attempting to shutdown.");
		}
		return(exitFile);
	}
	
	protected void startBackgroundTimers()
	{
		MartusUtilities.startTimer(new ShutdownRequestMonitor(), shutdownRequestIntervalMillis);
	}	
	
	public static File getMirrorDirectory(int type)
	{		
		if (type == ManagingMirrorServerConstants.SERVERS_WHOSE_DATA_WE_BACKUP)
			return getServerWhoWeCallDirectory();
		else if (type == ManagingMirrorServerConstants.SERVERS_WHO_BACKUP_OUR_DATA)
			return getMirrorServerWhoCallUsDirectory();
		else if (type == ManagingMirrorServerConstants.SERVERS_WHO_AMPLIFY_OUR_DATA)
			return getAmpsWhoCallUsDirectory();	

		return getMirrorServerWhoWeCallDirectory();		
	}	
	
	public static void main(String[] args)
	{
		System.out.println("MSPA Server");
		try
		{					
			System.out.println("Setting up socket connection for listener ...");
			
			MSPAServer server = new MSPAServer(MSPAServer.getMartusDefaultDataDirectory());
			server.deleteRunningFile();				
						
			server.processCommandLine(args);

			if(server.anyUnexpectedFilesOrFoldersInStartupDirectory())
				exitWithCause("Unexpected files or folders in startup directory", ServerSideUtilities.EXIT_UNEXPECTED_FILE_STARTUP);
				
			File serverKeyPairFile = server.getMSPAServerKeyPairFile();
			if (!serverKeyPairFile.exists())
				exitWithCause("File not found: " + serverKeyPairFile, ServerSideUtilities.EXIT_KEYPAIR_FILE_MISSING);

			char[] passphrase = server.insecurePassword;
			if(passphrase == null)
				passphrase = ServerSideUtilities.getPassphraseFromConsole(server.getTriggerDirectory(),"MSPAServer.main");
				
			server.loadMSPAKeypair(passphrase);
			server.initalizeFileDatabase();			
			server.setMagicWords();
			server.createMSPAXmlRpcServerOnPort(server.getPortToUse());	
			server.initConfig();																						
						
			if(!server.deleteStartupFiles())
				exitWithCause("Delete startup files failed", ServerSideUtilities.EXIT_UNEXPECTED_EXCEPTION);		
				
			server.startBackgroundTimers();		
			ServerSideUtilities.writeSyncFile(server.getRunningFile(), "MSPAServer.main");
			System.out.println("\nWaiting for connection...");	
		
		}
		catch(Exception e) 
		{
			MartusLogger.logException(e);
			exitWithCause("Unexpected Exception", ServerSideUtilities.EXIT_UNEXPECTED_EXCEPTION);			
		}
	}
	
	private static void exitWithCause(String cause, int exitStatus)
	{
		MartusLogger.logError(cause);
		MartusLogger.logError("Exiting");
		System.exit(exitStatus);
	}
	
	class ShutdownRequestMonitor extends TimerTask
	{
		public void run()
		{
			if( isShutdownRequested())
			{
				log("Shutdown request acknowledged, preparing to shutdown.");										
				getShutdownFile().delete();
				log("Server has exited.");
				try
				{
					System.exit(0);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
	}
		
	ServerSideHandler mspaHandler;
	String ipAddress;
	int portToUse;
	Vector authorizedMartusAccounts;
	Vector authorizeMSPAClients;
	ServerFileDatabase martusDatabaseToUse;	
	MartusCrypto security;
	LoggerInterface logger;
	MagicWords magicWords;
	Vector clientsBanned;
	Vector clientsAllowedUpload;
	Vector clientNotSendToAmplifier;
	HiddenBulletins hiddenBulletins;
	Vector availabelMirrorServerPublicKeys;	
	
	private int rootHelperPort = ROOTHELPER_DEFAULT_PORT;
		
	private File serverDirectory;
	private boolean secureMode;
	public char[] insecurePassword;
	private boolean loggedShutdownRequested;
		
	private final static String DELETE_ON_STARTUP = "deleteOnStartup";	
	private static final String ADMINTRIGGERDIRECTORY = "adminTriggers";
	private final static String MARTUSSERVER_BACKUP_DIRECTORY = "Backups";
	private final static String MARTUS_SERVER_DATA = "MartusServerData";	
	private final static String MAGICWORDS_FILENAME = "magicwords.txt";
	private static final String BANNEDCLIENTS_FILENAME = "banned.txt";
	private static final String UPLOADSOK_FILENAME = "uploadsok.txt";
	private static final String HIDDEN_PACKETS_FILENAME = "isHidden.txt";
	private static final String CLIENTS_NOT_TO_AMPLIFY_FILENAME = "accountsNotAmplified.txt";
	private static final String COMPLIANCE_FILE =  "compliance.txt";
	private static final String MARTUS_ARGUMENTS_PROPERTY_FILE = "serverarguments.props";
	private static final String MSPA_CLIENT_AUTHORIZED_DIR = "clientsWhoCallUs"; 

	private static final String SHUTDOWN_FILENAME = "exit";
	private final static String KEYPAIR_FILE ="keypair.dat"; 
	private final static String WINDOW_MARTUS_ENVIRONMENT = "C:/MartusServer/";
	private final static String UNIX_MARTUS_ENVIRONMENT = "/var/MartusServer/";
	private final static String WINDOW_MSPA_ENVIRONMENT = "C:/MSPAServer/";
	private final static String UNIX_MSPA_ENVIRONMENT = "/var/MSPAServer/";
	private static final long shutdownRequestIntervalMillis = 1000;
	
	private final static int DEFAULT_PORT = 984;
	private final static int ROOTHELPER_DEFAULT_PORT=983;
	private final static String LOCALHOST = "127.0.0.1";
}
