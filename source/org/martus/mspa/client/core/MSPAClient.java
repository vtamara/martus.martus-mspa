
package org.martus.mspa.client.core;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import org.martus.common.ContactInfo;
import org.martus.common.MartusUtilities;
import org.martus.common.clientside.UiBasicLocalization;
import org.martus.common.crypto.MartusCrypto;
import org.martus.common.crypto.MartusSecurity;
import org.martus.common.network.MartusXmlrpcClient.SSLSocketSetupException;
import org.martus.mspa.main.UiMainWindow;
import org.martus.mspa.network.ClientSideXmlRpcHandler;
import org.martus.mspa.network.NetworkInterfaceConstants;
import org.martus.mspa.server.LoadMartusServerArguments;
import org.martus.util.Base64.InvalidBase64Exception;

public class MSPAClient 
{				
	public MSPAClient(UiBasicLocalization local) throws Exception
	{		
		security = new MartusSecurity();	
	}
	
	public void setXMLRpcEnviornments()
	{
		handler = createXmlRpcNetworkInterfaceHandler();					
		setServerPublicCode(serverPublicCode);	
	}
	
	public ClientSideXmlRpcHandler getClientSideXmlRpcHandler()
	{	
		return handler;
	}
	
	public void setPortToUse(int port)
	{
		portToUse = port;
	}		
	
	private void setServerPublicCode(String key)
	{	

		String serverPublicCode = MartusCrypto.removeNonDigits(key);
		handler.getSimpleX509TrustManager().setExpectedPublicCode(serverPublicCode);	
	}
				
	private ClientSideXmlRpcHandler createXmlRpcNetworkInterfaceHandler()
	{		
		try 
		{					
			return handler =  new ClientSideXmlRpcHandler(ipToUse, portToUse);			
		} 
		catch (SSLSocketSetupException e) 
		{
			e.printStackTrace();			
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();			
		}
		return null;
	}	
	
	public File getUiStateFile()
	{
		return new File(UiMainWindow.getDefaultDirectoryPath(), "UiState.dat");
	}
	
	public MartusCrypto getSecurity()
	{
		return security;
	}
	
	public File getKeypairFile()
	{
		return new File(UiMainWindow.getDefaultDirectoryPath(), KEYPAIR_FILE);
	}
	
	public boolean loadServerToCall() throws
			IOException, 
			MartusUtilities.InvalidPublicKeyFileException, 
			MartusUtilities.PublicInformationInvalidException, 
			SSLSocketSetupException, InvalidBase64Exception
	{
		boolean prompUserToSelectServer=false;
		
		portToUse = DEFAULT_PORT;					
		toCallFiles = getServerToCallDirectory().listFiles();

		if (toCallFiles.length >1)
			prompUserToSelectServer = true;
		
		
		if(toCallFiles != null && !prompUserToSelectServer)
		{
			File toCallFile = toCallFiles[0];		
			if(!toCallFile.isDirectory())
			{
				ipToUse = MartusUtilities.extractIpFromFileName(toCallFile.getName());				
				Vector publicInfo = MartusUtilities.importServerPublicKeyFromFile(toCallFile, security);
				String serverPublicKey = (String)publicInfo.get(0);	
				if (serverPublicKey != null)
				{				
					String nonFormatPublicCode = MartusCrypto.computePublicCode(serverPublicKey);
					serverPublicCode = MartusCrypto.formatPublicCode(nonFormatPublicCode);	
				}
			}			
			setXMLRpcEnviornments();
		}	
		
		return prompUserToSelectServer;
	}
	
	public Vector getLineOfServerIpAndPublicCode() throws
			IOException, 
			MartusUtilities.InvalidPublicKeyFileException, 
			MartusUtilities.PublicInformationInvalidException, 
			SSLSocketSetupException, InvalidBase64Exception
	{
		Vector listOfServers = new Vector();	

		for (int i=0; i<toCallFiles.length;i++)
		{	
			File toCallFile = toCallFiles[i];
			if(!toCallFile.isDirectory())
			{
				String ipToCall = MartusUtilities.extractIpFromFileName(toCallFile.getName());				
				Vector publicInfo = MartusUtilities.importServerPublicKeyFromFile(toCallFile, security);
				String serverPublicKey = (String)publicInfo.get(0);	
				if (serverPublicKey != null)
				{				
					String nonFormatPublicCode = MartusCrypto.computePublicCode(serverPublicKey);
					String serverPublicCall = MartusCrypto.formatPublicCode(nonFormatPublicCode);
					listOfServers.add(ipToCall+"\t"+serverPublicCall);		
				}
			}
		}
		
		return listOfServers;
	}	
	
	private File getServerToCallDirectory()
	{
		return new File(UiMainWindow.getDefaultDirectoryPath(), SERVER_WHO_WE_CALL_DIRIRECTORY);
	}
	
	
	public void signIn(String userName, char[] userPassPhrase) throws Exception
	{
		try
		{				
			getSecurity().readKeyPair(getKeypairFile(), getCombinedPassPhrase(userName, userPassPhrase));		
		}
		catch(Exception e)
		{
			throw e;
		}
	}
	
	public char[] getCombinedPassPhrase(String userName, char[] userPassPhrase)
	{
		char[] combined = new char[userName.length() + userPassPhrase.length + 1];
		System.arraycopy(userPassPhrase,0,combined,0,userPassPhrase.length);
		combined[userPassPhrase.length] = ':';
		System.arraycopy(userName.toCharArray(),0,combined,userPassPhrase.length+1,userName.length());
		
		return(combined);
	}
	
	public String getCurrentServerPublicCode()
	{
		return serverPublicCode;
	}
	
	public String getCurrentServerIp()
	{
		return ipToUse;
	}
	
	public void setCurrentServerPublicCode(String publicCode)
	{
		serverPublicCode = publicCode;
	}
	
	public void setCurrentServerIp(String ip)
	{
		ipToUse = ip;
	}
	
	
			
	
	private Vector getAccountIds(String myAccountId, Vector parameters, String signature) throws IOException 
	{	
		return handler.getAccountIds(myAccountId, parameters, signature);
	}	
	
	public Vector displayAccounts()
	{	
		try
		{			
			Vector parameters = new Vector();							
			String signature = security.createSignatureOfVectorOfStrings(parameters);	
			Vector results = getAccountIds(security.getPublicKeyString(), parameters, signature);			
			
			if (results != null && !results.isEmpty())
			{
				Vector accounts = (Vector) results.get(1);
				if (!accounts.isEmpty())
					return accounts;
			}	 
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		return new Vector();
	}
	
	public Vector getContactInfo(String accountId)
	{	
		try
		{			
			Vector parameters = new Vector();
			parameters.add(accountId);			
			String signature = security.createSignatureOfVectorOfStrings(parameters);				
			Vector results = handler.getContactInfo(security.getPublicKeyString(), parameters, signature, accountId);				
			Vector decodedContactInfoResult = ContactInfo.decodeContactInfoVectorIfNecessary(results);
			
			if (decodedContactInfoResult != null && !decodedContactInfoResult.isEmpty())
			{
				Vector info = (Vector) decodedContactInfoResult.get(1);
				if (!info.isEmpty())
					return info;
			}	 
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}
	
	public Vector getAccountManageInfo(String manageAccountId)
	{	
		try
		{						
			Vector results = handler.getAccountManageInfo(security.getPublicKeyString(), manageAccountId);
		
			if (results != null && !results.isEmpty())
				return (Vector) results.get(1);
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}	
	
	public String getServerCompliant()
	{
		StringBuffer msg = new StringBuffer();
		try
		{
			Vector results = handler.getServerCompliance(security.getPublicKeyString());
			if (results != null && !results.isEmpty())
			{
				Vector compliants = (Vector) results.get(1);				
				for (int i=0; i< compliants.size();++i)
					msg.append((String) compliants.get(i)).append("\n");
			}				
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return msg.toString();
	}
	
	public Vector updateServerCompliant(String msg)
	{
		try
		{
			Vector results =  handler.updateServerCompliance(security.getPublicKeyString(), msg);
			if (results != null && !results.isEmpty())
				return results;
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new Vector();
	}
	
	public String sendCmdToServer(String cmdType, String cmd)
	{
		String msg = "";
		try
		{												
			Vector results = handler.sendCommandToServer(security.getPublicKeyString(), cmdType, cmd);
			
			if (results != null && !results.isEmpty())
				msg = (String) results.get(0);					
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return msg;
	}
	
	public void updateAccountManageInfo(String manageAccountId, Vector manageOptions)
	{		
		try
		{									
			handler.updateAccountManageInfo(security.getPublicKeyString(),
					manageAccountId, manageOptions);				
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
	}		
	
	public  LoadMartusServerArguments getMartusServerArguments()
	{			
		try
		{						
			Vector results = handler.getMartusServerArguments(security.getPublicKeyString());		
			if (results != null && !results.isEmpty())	
			{	
				Vector args = (Vector) results.get(1);
				LoadMartusServerArguments arguments = new LoadMartusServerArguments();
				arguments.convertFromVector(args);

				return arguments;				
			}
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return null;
	}	
	
	public void updateMartusServerArguments(LoadMartusServerArguments args)
	{		
		try
		{									
			handler.updateMartusServerArguments(security.getPublicKeyString(), args.convertToVector());				
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
	}	
	
	public Vector getListOfHiddenBulletins(String accountId)
	{	
		try
		{						
			Vector results = handler.getListOfHiddenBulletinIds(security.getPublicKeyString(), accountId);		
			if (results != null && !results.isEmpty())	
				return (Vector) results.get(1);
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}	
	
	public String removeBulletin(String accountId, Vector localIds)
	{
		String msg = "";
		try
		{												
			Vector results = handler.removeHiddenBulletins(security.getPublicKeyString(), accountId, localIds);			
			if (results != null && !results.isEmpty())
				msg = (String) results.get(0);
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return msg;
	}
	
	public String recoverHiddenBulletin(String accountId, Vector localIds)
	{
		String msg = "";
		try
		{												
			Vector results = handler.recoverHiddenBulletins(security.getPublicKeyString(), accountId, localIds);			
			if (results != null && !results.isEmpty())
				msg = (String) results.get(0);
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return msg;
	}
	
	public Vector getPacketDirNames(String accountId)
	{	
		try
		{						
			Vector results = handler.getListOfBulletinIds(accountId);		
			
			if (results != null && !results.isEmpty())
				return (Vector) results.get(1);
							
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}	
	
	public Vector getInactiveMagicWords()
	{	
		try
		{						
			Vector results = handler.getInactiveMagicWords(security.getPublicKeyString());
			
			if (results != null && !results.isEmpty())
				return (Vector) results.get(1);
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}	
	
	public Vector getActiveMagicWords()
	{	
		try
		{						
			Vector results = handler.getActiveMagicWords(security.getPublicKeyString());
			
			if (results != null && !results.isEmpty())
				return (Vector) results.get(1);			
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}	
	
	public Vector getAllMagicWords()
	{	
		try
		{					
			Vector results = handler.getAllMagicWords(security.getPublicKeyString());
			
			if (results != null && !results.isEmpty())
				return (Vector) results.get(1);
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}		
	
	public void updateMagicWords(Vector magicWords)	
	{	
		try
		{			
			handler.updateMagicWords(security.getPublicKeyString(), magicWords);			
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}				
	}	
	
	public Vector getAvailableAccounts()
	{	
		try
		{					
			Vector results = handler.getListOfAvailableServers(security.getPublicKeyString());
			
			if (results != null && !results.isEmpty())
				return (Vector) results.get(1);
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}	
	
	public Vector getListOfAssignedAccounts(int mirrorType)
	{	
		try
		{					
			Vector results = handler.getListOfAssignedServers(security.getPublicKeyString(), mirrorType);
			
			if (results != null && !results.isEmpty())
				return (Vector) results.get(1);
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return new Vector();
	}	
	
	public boolean addMirrorServer(Vector serverInfo)	
	{	
		try
		{						
			Vector results = handler.addAvailableMirrorServer(security.getPublicKeyString(), serverInfo);
			
			if (results != null && !results.isEmpty())
			{
				String returnCode = (String) results.get(0);		
				if (returnCode.equals(NetworkInterfaceConstants.OK))
					return true;
			}	 
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
		return false;
	}	
	
	public void updateManageMirrorAccounts(Vector mirrorInfo, int manageType)	
	{	
		try
		{			
			handler.updateManagingMirrorServers(security.getPublicKeyString(), mirrorInfo, manageType);			
		}		
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}				
	}		
		

	public String getPublicCode(String accountId)
	{
		String publicCode = null;
		try
		{																		
			publicCode = MartusSecurity.getFormattedPublicCode(accountId);
		}
		catch (InvalidBase64Exception e)
		{						
			e.printStackTrace();					
		}
		
		return publicCode;
	}
	
	public void warningMessageDlg(String message)
	{
		String title = "MSPA Client";
		String cause = message;
		String ok = "OK";
		String[] buttons = { ok };
		JOptionPane pane = new JOptionPane(cause, JOptionPane.INFORMATION_MESSAGE,
				 JOptionPane.DEFAULT_OPTION, null, buttons);
		JDialog dialog = pane.createDialog(null, title);
		dialog.show();
	}
				
	
	ClientSideXmlRpcHandler handler;
	String ipToUse="";
	int portToUse;
	String serverPublicCode="";
	UiBasicLocalization localization;
	MartusCrypto security;
	File keyPairFile;	
	File[] toCallFiles;
	
	final static int DEFAULT_PORT = 443;
	final static String DEFAULT_HOST = "localHost";	
	
	private final static String KEYPAIR_FILE ="\\keypair.dat"; 
	private static final String SERVER_WHO_WE_CALL_DIRIRECTORY = "serverToCall";
	
}
