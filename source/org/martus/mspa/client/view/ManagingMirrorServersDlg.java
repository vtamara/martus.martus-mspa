/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2004, Beneficent
Technology, Inc. (Benetech).

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
package org.martus.mspa.client.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;

import org.martus.mspa.client.core.ManagingMirrorServerConstants;
import org.martus.mspa.client.core.MirrorServerLabelFinder;
import org.martus.mspa.client.core.MirrorServerLabelInfo;
import org.martus.mspa.main.UiMainWindow;
import org.martus.swing.ParagraphLayout;
import org.martus.swing.Utilities;


public class ManagingMirrorServersDlg extends JDialog
{
	public ManagingMirrorServersDlg(UiMainWindow owner, int manageType, 
			String serverToManage, String serverToManagePublicCode,
			Vector allList, Vector currentList)
	{
		super((JFrame)owner);
		msgLabelInfo = MirrorServerLabelFinder.getMessageInfo(manageType);
		setTitle("Managing Server Mirroring: "+ msgLabelInfo.getTitle());
		parent = owner;
		serverManageType = manageType;
		availableList = allList;
		assignedList = currentList;	
	
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(getTopPanel(), BorderLayout.NORTH);
		getContentPane().add(getCenterPanel(), BorderLayout.CENTER);
		getContentPane().add(getCommitButtonsPanel(), BorderLayout.SOUTH);
						
		Utilities.centerDlg(this);
	}
	
	private JPanel getTopPanel()
	{
		JPanel panel = new JPanel();
		panel.setBorder(new TitledBorder(LineBorder.createGrayLineBorder(),msgLabelInfo.getHeader()));			
		panel.setLayout(new ParagraphLayout());
		
		manageIPAddr = new JTextField(20);
		manageIPAddr.requestFocus();		
		managePublicCode = new JTextField(20);				
		
		panel.add(new JLabel("Manage IP Address: "), ParagraphLayout.NEW_PARAGRAPH);
		panel.add(manageIPAddr);
		panel.add(new JLabel("Public Code: "), ParagraphLayout.NEW_PARAGRAPH);
		panel.add(managePublicCode);
		if (serverManageType != ManagingMirrorServerConstants.LISTEN_FOR_CLIENTS)
		{		
			collectMirrorInfo(panel);
		}
		
		return panel;				
	}
	
	private void collectMirrorInfo(JPanel panel)
	{
		mirrorServerPort = new JTextField(20);
		mirrorServerName = new JTextField(20);
		addNewMirrorServer = createButton("add");	
		panel.add(new JLabel("Which Port: (optional)"), ParagraphLayout.NEW_PARAGRAPH);	
		panel.add(mirrorServerPort);	
		panel.add(new JLabel("Mirror Server Name: (optional)"), ParagraphLayout.NEW_PARAGRAPH);	
		panel.add(mirrorServerName);		
				
		panel.add(addNewMirrorServer);		
		
	}
	
	private JPanel getCenterPanel()
	{
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(5,5,5,5));
		panel.setLayout(new FlowLayout());
				
		panel.add(getAvailablePanel());
		panel.add(getShiftButtons());
		panel.add(getAllowedPanel());

		return panel;
	}
	
	private JPanel getAvailablePanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		
		availableListModel = loadElementsToList(availableList);
		availableServers = new JList(availableListModel);
		availableServers.setFixedCellWidth(200);    
		JScrollPane ps = new JScrollPane();
		ps.getViewport().add(availableServers);
		JLabel availableLabel = new JLabel(msgLabelInfo.getAvailableLabel());
				
		panel.add(availableLabel);
		panel.add(ps);

		return panel;
	}	
	
	private JPanel getAllowedPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		
		allowedListModel = loadElementsToList(assignedList);
		allowedServers = new JList(allowedListModel);
		allowedServers.setFixedCellWidth(200);    
		JScrollPane ps = new JScrollPane();
		ps.getViewport().add(allowedServers);
		JLabel allowedLabel = new JLabel( msgLabelInfo.getAllowedLabel());
				
		panel.add(allowedLabel);
		panel.add(ps);

		return panel;
	}
	
	private DefaultListModel loadElementsToList(Vector items)
	{
		DefaultListModel listModel = new DefaultListModel();
		
		for (int i=0; i<items.size();++i)
			listModel.add(i, items.get(i));
			
		return listModel;
	}	
	
	private JPanel getShiftButtons()
	{
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(5,5,5,5));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		
		addButton = createButton(">>");					
		removeButton = createButton("<<");		
		
		panel.add(addButton);
		panel.add(removeButton);
		
		return panel;

	}
	
	
	private JPanel getCommitButtonsPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new FlowLayout());		
				
		viewComplainButton = createButton("View Compliance");			
		updateButton = createButton("Update");				
		cancelButton = createButton("Close");						
				
		panel.add(viewComplainButton);
		panel.add(updateButton);
		panel.add(cancelButton);
		
		return panel;
	}
	
	private JButton createButton(String label)
	{
		JButton button = new JButton(label);
		button.addActionListener(new ButtonHandler());
		return button;
	}
	
	class ButtonHandler implements ActionListener
	{
		public void actionPerformed(ActionEvent ae)
		{
			if (ae.getSource().equals(cancelButton))				
				dispose();
			else if (ae.getSource().equals(addButton))
				handleAddToAllowedList();
			else if (ae.getSource().equals(updateButton))
				handleUpdateMirrorServerInfo();
			else if (ae.getSource().equals(removeButton))
				handleRemoveFromAllowedList();
			else if (ae.getSource().equals(addNewMirrorServer))
				handleRequestAddNewMirrorServer();
		}
		
		private void handleRequestAddNewMirrorServer()
		{
			Vector mirrorServerInfo = new Vector();
			String mirrorIP = manageIPAddr.getText();
			String mirrorPublicCode = managePublicCode.getText();
			String serverName = mirrorServerName.getText();
			String port = mirrorServerPort.getText();
			
			if (mirrorIP.length()<=0 || 
				mirrorPublicCode.length()<=0 || port.length()<=0)
			{	
				JOptionPane.showMessageDialog(parent, "Ip address, public code and port are required.", 
					"Missing Infomation", JOptionPane.ERROR_MESSAGE);
				return;
			}				
			
			mirrorServerInfo.add(mirrorIP);
			mirrorServerInfo.add(mirrorPublicCode);		
			mirrorServerInfo.add(port);	
					
			if (serverName.length()> 0)
			{
				serverName = serverName.replaceAll(" ","_");	
				mirrorServerInfo.add(serverName);
			}
			else
				mirrorServerInfo.add(mirrorPublicCode);								
				
			boolean result = parent.getMSPAApp().addMirrorServer(mirrorServerInfo);
			if (result)
			{			
				availableList.add(serverName);
				availableListModel.addElement(serverName);				
			}
			else
			{
				JOptionPane.showMessageDialog(parent, "Error no response from server.", 
					"Server Info", JOptionPane.ERROR_MESSAGE);
			}	
			
			manageIPAddr.setText("");
			managePublicCode.setText("");
			mirrorServerName.setText("");
			mirrorServerPort.setText("");					
							
		}		
		
		private void handleAddToAllowedList()
		{
			int selectItem = availableServers.getSelectedIndex();	
			if (!availableServers.isSelectionEmpty())
			{	
				String item = (String) availableServers.getSelectedValue();
				if (!allowedListModel.contains(item))
				{						
					allowedListModel.addElement(item);
					availableListModel.remove(selectItem);
				}				
			}
		}
		
		private void handleUpdateMirrorServerInfo()
		{
			Object[] items = allowedListModel.toArray();
			Vector itemCollection = new Vector();			
			for (int i=0;i<items.length;i++)
				itemCollection.add(items[i]);
				
			items = availableListModel.toArray();	
			for (int i=0;i<items.length;i++)
			{
				String itemString = (String) items[i];
				if (serverManageType == MirrorServerLabelFinder.LISTEN_FOR_CLIENTS)	
						itemCollection.add("#"+itemString);
				else
					itemCollection.add(itemString);
			}
							
			parent.getMSPAApp().updateMagicWords(itemCollection);
			dispose();							
		}
		
		private void handleRemoveFromAllowedList()
		{			
			int selectItem = allowedServers.getSelectedIndex();	
			if (!allowedServers.isSelectionEmpty())
			{	
				String item = (String) allowedServers.getSelectedValue();				
		
				allowedListModel.remove(selectItem);
				if (!availableListModel.contains(item))								
					availableListModel.addElement(item);
			}							
		}				
	}
	
	UiMainWindow parent; 	
	
	JTextField manageIPAddr;
	JTextField managePublicCode;
	JTextField mirrorServerName;
	JTextField mirrorServerPort;
	
	JButton addButton;
	JButton removeButton;
	JButton viewComplainButton;
	JButton updateButton;
	JButton cancelButton;
	JButton addNewMirrorServer;
	
	Vector availableList;
	Vector assignedList;	
	
	JList availableServers;
	JList allowedServers;	
	DefaultListModel availableListModel;
	DefaultListModel allowedListModel;
	
	int serverManageType;
	MirrorServerLabelInfo msgLabelInfo;
	
}
