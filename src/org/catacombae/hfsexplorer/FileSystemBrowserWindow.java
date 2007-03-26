/*-
 * Copyright (C) 2006 Erik Larsson
 * 
 * All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */

package org.catacombae.hfsexplorer;

import org.catacombae.hfsexplorer.apm.*;
import org.catacombae.hfsexplorer.types.*;
import org.catacombae.hfsexplorer.win32.WindowsLowLevelIO;
import org.catacombae.hfsexplorer.gui.FilesystemBrowserPanel;
import org.catacombae.hfsexplorer.gui.JournalInfoBlockPanel;
import java.util.*;
import java.io.*;
import java.text.DateFormat;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.table.*;
import javax.swing.event.*;

public class FileSystemBrowserWindow extends JFrame {
    
    private static class RecordContainer {
	private HFSPlusCatalogLeafRecord rec;
	public RecordContainer(HFSPlusCatalogLeafRecord rec) {
	    this.rec = rec;
	}
	public HFSPlusCatalogLeafRecord getRecord() { return rec; }
	public String toString() { return rec.getKey().getNodeName().toString(); }
    }
    
    private FilesystemBrowserPanel fsbPanel;
    
    // Fast accessors for the corresponding variables in org.catacombae.hfsexplorer.gui.FilesystemBrowserPanel
    private JTable fileTable;
    private JTree dirTree;
    private JTextField addressField;
    private JButton goButton;
    private JButton extractButton;
    private JLabel statusLabel;
    
    // Focus timestamps (for determining what to extract)
    private long fileTableLastFocus = 0;
    private long dirTreeLastFocus = 0;
    
    private final JFileChooser fileChooser = new JFileChooser();
    private final Vector<String> colNames = new Vector<String>();
    private final DefaultTableModel tableModel;
    private HFSFileSystemView fsView;
    
    private static class RecordNodeStorage {
	private HFSPlusCatalogLeafRecord record;
	public RecordNodeStorage(HFSPlusCatalogLeafRecord record) {
	    this.record = record;
	    if(record.getData().getRecordType() != HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER &&
	       record.getData().getRecordType() != HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER_THREAD)
		throw new IllegalArgumentException("Illegal type for record data.");
	}
	public HFSPlusCatalogLeafRecord getRecord() { return record; }
	public String toString() {
	    HFSPlusCatalogLeafRecordData recData = record.getData();
	    if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER)
		return record.getKey().getNodeName().toString();
	    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER_THREAD &&
		    recData instanceof HFSPlusCatalogThread)
		return ((HFSPlusCatalogThread)recData).getNodeName().toString();
	    else
		throw new RuntimeException("Illegal type for record data. (Should NOT happen here!)");
	}
    }
    
    public static class NoLeafMutableTreeNode extends DefaultMutableTreeNode {
	public NoLeafMutableTreeNode(Object o) { super(o); }
	/** Hack to avoid that JTree paints leaf nodes. We have no leafs, only dirs. */
	public boolean isLeaf() { return false; }
    }
    
    public FileSystemBrowserWindow() {
	super("HFSExplorer v" + HFSExplorer.VERSION);
	fsbPanel = new FilesystemBrowserPanel();
	fileTable = fsbPanel.fileTable;
	final JScrollPane fileTableScroller = fsbPanel.fileTableScroller;
	dirTree = fsbPanel.dirTree;
	addressField = fsbPanel.addressField;
	goButton = fsbPanel.goButton;
	extractButton = fsbPanel.extractButton;
	statusLabel = fsbPanel.statusLabel;
	JButton infoButton = fsbPanel.infoButton;

	// UI Features for these are not implemented yet.
	addressField.setEnabled(false);
	goButton.setEnabled(false);
	
	extractButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    if(fsView != null)
			actionExtractToDir();
		    else
			JOptionPane.showMessageDialog(FileSystemBrowserWindow.this, "No file system loaded.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	    });
	
	infoButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent e) {
		    if(fsView != null)
			actionGetInfo();
		    else
			JOptionPane.showMessageDialog(FileSystemBrowserWindow.this, "No file system loaded.", "Error", JOptionPane.ERROR_MESSAGE);
		}
	    });
	
	final Class recordContainerClass = new RecordContainer(null).getClass();
	final Class objectClass = new Object().getClass();
	colNames.add("Name");
	colNames.add("Size");
	colNames.add("Type");
	colNames.add("Date Modified");
	//Vector<Vector<String>> = new Vector<Vector<String>>();
	tableModel = new DefaultTableModel(colNames, 0)  {
// 		public Class getColumnClass(int columnIndex) {
// 		    if(columnIndex == 0)
// 			return recordContainerClass;
// 		    else
// 			return super.getColumnClass(columnIndex);
// 		}
		public boolean isCellEditable(int rowIndex, int columnIndex) {
		    return false;
		}
 	    };
	
	fileTable.setModel(tableModel);
	fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
	fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
	fileTable.getColumnModel().getColumn(0).setPreferredWidth(180);
	fileTable.getColumnModel().getColumn(1).setPreferredWidth(96);
	fileTable.getColumnModel().getColumn(2).setPreferredWidth(120);
	fileTable.getColumnModel().getColumn(3).setPreferredWidth(120);
	final TableCellRenderer objectRenderer = fileTable.getDefaultRenderer(objectClass);
	fileTable.setDefaultRenderer(objectClass, new TableCellRenderer() {
		private JLabel theOne = new JLabel();
		private ImageIcon documentIcon = new ImageIcon("resource/emptydocument.png");
		private ImageIcon folderIcon = new ImageIcon("resource/folder.png");
		private ImageIcon emptyIcon = new ImageIcon("resource/nothing.png");
		
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, final int row, final int column) {
		    if(value instanceof RecordContainer) {
			final Component objectComponent = objectRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);			
			final JLabel jl = theOne;
			HFSPlusCatalogLeafRecord rec = ((RecordContainer)value).getRecord();
			if(rec.getData().getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER)
			    jl.setIcon(folderIcon);
			else if(rec.getData().getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FILE)
			    jl.setIcon(documentIcon);
			else
			    jl.setIcon(emptyIcon);
			jl.setVisible(true);
			//jl.setBackground(objectComponent.getBackground());
// 			jl.addMouseListener(new MouseAdapter() {
// 				public void mousePressed(MouseEvent me) {
// 				    System.err.println("Action at (" + row + "," + column + ")");
// 				}
// 			    });
			Component c = new Component() {
				{
				    jl.setSize(jl.getPreferredSize());
				    jl.setLocation(0, 0);
				    objectComponent.setSize(objectComponent.getPreferredSize());
				    objectComponent.setLocation(jl.getWidth(), 0);
// 				    System.err.println("Setting size to: " + (jl.getWidth()+objectComponent.getWidth()) + "," + Math.max(jl.getHeight(), objectComponent.getHeight()));
				    setSize(jl.getWidth()+objectComponent.getWidth(), Math.max(jl.getHeight(), objectComponent.getHeight()));
				}
				public void paint(Graphics g) {
// 				    if(row == 1) {
// 					Color oldColor = g.getColor();
// 					g.setColor(Color.BLACK);
// 					g.fillRect(0,0,10,10);
// 					g.setColor(oldColor);
// 				    }
				    jl.paint(g);
				    int translatex = jl.getWidth();
				    g.translate(translatex, 0);
				    objectComponent.paint(g);
				    g.translate(-translatex, 0);
				}
			    };
			return c;
		    }
		    else
			return objectRenderer.getTableCellRendererComponent(table, value, false, false, row, column);
		}
	    });
	fileTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
		private final java.text.DecimalFormat sizeFormat = new java.text.DecimalFormat("0.00");
		public void valueChanged(ListSelectionEvent e) {
		    //statusLabel.setText("Selection (" + e.getFirstIndex() + "-" + e.getLastIndex() + ") has changed. isAdjusting()==" + e.getValueIsAdjusting());
		    int[] selection = fileTable.getSelectedRows();
		    long selectionSize = 0;
		    for(int selectedRow : selection) {
			Object o = tableModel.getValueAt(selectedRow, 0);
			HFSPlusCatalogLeafRecord rec;
			HFSPlusCatalogLeafRecordData recData;
			if(o instanceof RecordContainer) {
			    rec = ((RecordContainer)o).getRecord();
			    HFSPlusCatalogLeafRecordData data = rec.getData();
			    if(data.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FILE &&
			       data instanceof HFSPlusCatalogFile)
				selectionSize += ((HFSPlusCatalogFile)data).getDataFork().getLogicalSize();
			}
		    }
		    String sizeString = (selectionSize >= 1024)?SpeedUnitUtils.bytesToBinaryUnit(selectionSize, sizeFormat):selectionSize + " bytes";
		    statusLabel.setText(selection.length + ((selection.length==1)?" object":" objects") + " selected (" + sizeString + ")");
		}
	    });
	
	DefaultMutableTreeNode rootNode = new NoLeafMutableTreeNode("No file system loaded");
	DefaultTreeModel model = new DefaultTreeModel(rootNode);
	dirTree.setModel(model);

	dirTree.addTreeSelectionListener(new TreeSelectionListener() {
		public void valueChanged(TreeSelectionEvent e) {
		    TreePath tp = e.getPath();
		    Object obj = tp.getLastPathComponent();
		    if(obj instanceof NoLeafMutableTreeNode) {
			Object obj2 = ((NoLeafMutableTreeNode)obj).getUserObject();
			if(obj2 instanceof RecordNodeStorage) {
			    HFSPlusCatalogLeafRecord rec = ((RecordNodeStorage)obj2).getRecord();
			    HFSPlusCatalogLeafRecordData recData = rec.getData();
			    HFSCatalogNodeID requestedID;
			    if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER &&
			       recData instanceof HFSPlusCatalogFolder) {
				HFSPlusCatalogFolder catFolder = (HFSPlusCatalogFolder)recData;
				requestedID = catFolder.getFolderID();
			    }
			    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER_THREAD &&
				    recData instanceof HFSPlusCatalogThread) {
				HFSPlusCatalogThread catThread = (HFSPlusCatalogThread)recData;
				requestedID = rec.getKey().getParentID();
			    }
			    else
				throw new RuntimeException("Invalid type.");
			
			    HFSPlusCatalogLeafRecord[] contents = fsView.listRecords(requestedID);
			    populateTable(contents);
			    fileTableScroller.getVerticalScrollBar().setValue(0);
			    
			    StringBuilder path = new StringBuilder("/");
			    Object[] userObjectPath = ((NoLeafMutableTreeNode)obj).getUserObjectPath();
			    for(int i = 1; i < userObjectPath.length; ++i) {
				path.append(userObjectPath[i].toString());
				path.append("/");
			    }
			    addressField.setText(path.toString()); 
			}
		    }
		}
	    });
	dirTree.addTreeWillExpandListener(new TreeWillExpandListener() {
		public void treeWillExpand(TreeExpansionEvent e) 
                    throws ExpandVetoException {
		    //System.out.println("Tree will expand!");
		    TreePath tp = e.getPath();
		    Object obj = tp.getLastPathComponent();
		    if(obj instanceof NoLeafMutableTreeNode) {
			Object obj2 = ((NoLeafMutableTreeNode)obj).getUserObject();
			if(obj2 instanceof RecordNodeStorage) {
			    HFSPlusCatalogLeafRecord rec = ((RecordNodeStorage)obj2).getRecord();
			    HFSPlusCatalogLeafRecordData recData = rec.getData();
			    HFSCatalogNodeID requestedID;
			    if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER &&
			       recData instanceof HFSPlusCatalogFolder) {
				HFSPlusCatalogFolder catFolder = (HFSPlusCatalogFolder)recData;
				requestedID = catFolder.getFolderID();
			    }
			    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER_THREAD &&
				    recData instanceof HFSPlusCatalogThread) {
				HFSPlusCatalogThread catThread = (HFSPlusCatalogThread)recData;
				requestedID = rec.getKey().getParentID();
			    }
			    else
				throw new RuntimeException("Invalid type.");
			
			    HFSPlusCatalogLeafRecord[] contents = fsView.listRecords(requestedID);
			    populateNode(((NoLeafMutableTreeNode)obj), contents);
			}
		    }
		}
		
		public void treeWillCollapse(TreeExpansionEvent e) {}
	    });
	
	// Focus monitoring
	fileTable.addFocusListener(new FocusListener() {
		public void focusGained(FocusEvent e) {
		    //System.err.println("fileTable gained focus!");
		    fileTableLastFocus = System.nanoTime();
		}
		public void focusLost(FocusEvent e) {}
	    });
	dirTree.addFocusListener(new FocusListener() {
		public void focusGained(FocusEvent e) {
		    //System.err.println("dirTree gained focus!");
		    dirTreeLastFocus = System.nanoTime();
		}
		public void focusLost(FocusEvent e) {}
	    });

	
	
	// Menus
	JMenuItem loadFSFromDeviceItem = null;
	JMenuItem loadFSFromDeviceWithAPMItem = null;
	if(System.getProperty("os.name").toLowerCase().startsWith("windows") &&
	   System.getProperty("os.arch").toLowerCase().equals("x86")) {
	    loadFSFromDeviceItem = new JMenuItem("Load file system from device");
	    loadFSFromDeviceItem.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent ae) {
			SelectWindowsDeviceDialog deviceDialog = new SelectWindowsDeviceDialog(FileSystemBrowserWindow.this,
											       true,
											       "Load file system from device");
			deviceDialog.setVisible(true);
			String pathName = deviceDialog.getPathName();
			try {
			    if(pathName != null)
				loadFS(pathName, false, true);
			} catch(Exception e) {
			    e.printStackTrace();
			    JOptionPane.showMessageDialog(FileSystemBrowserWindow.this,
							  "Could not read contents of partition!",
							  "Error", JOptionPane.ERROR_MESSAGE);
			}
		    }
		});
	    loadFSFromDeviceWithAPMItem = new JMenuItem("Load file system from device with APM");
	    loadFSFromDeviceWithAPMItem.addActionListener(new ActionListener() {
		    public void actionPerformed(ActionEvent ae) {
			SelectWindowsDeviceDialog deviceDialog = new SelectWindowsDeviceDialog(FileSystemBrowserWindow.this,
											       true,
											       "Load file system from device with APM");
			deviceDialog.setVisible(true);
			String pathName = deviceDialog.getPathName();
// 			String pathName = JOptionPane.showInputDialog(FileSystemBrowserWindow.this,
// 								      "Enter the UNC path for the file system",
// 								      "\\\\?\\GLOBALROOT\\Device\\Harddisk2\\Partition2");
			//System.out.println("loadFS(" + pathName + ", false, true);");
			try {
			    if(pathName != null)
				loadFS(pathName, true, true);
			} catch(Exception e) {
			    e.printStackTrace();
			    JOptionPane.showMessageDialog(FileSystemBrowserWindow.this,
							  "Could not read contents of partition!",
							  "Error", JOptionPane.ERROR_MESSAGE);
			}
		    }
		});
	}
	JMenuItem loadFSFromFileItem = new JMenuItem("Load file system from file");
	loadFSFromFileItem.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    //JFileChooser fileChooser = new JFileChooser();
		    fileChooser.setMultiSelectionEnabled(false);
		    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		    if(fileChooser.showOpenDialog(FileSystemBrowserWindow.this) == 
		       JFileChooser.APPROVE_OPTION) {
			try {
			    String pathName = fileChooser.getSelectedFile().getCanonicalPath();
			    loadFS(pathName, false, false);
			} catch(IOException ioe) {
			    ioe.printStackTrace();
			    JOptionPane.showMessageDialog(FileSystemBrowserWindow.this,
							  "Count not resolve pathname!",
							  "Error", JOptionPane.ERROR_MESSAGE);
			} catch(Exception e) {
			    e.printStackTrace();
			    JOptionPane.showMessageDialog(FileSystemBrowserWindow.this,
							  "Could not read contents of partition!",
							  "Error", JOptionPane.ERROR_MESSAGE);
			}
		    }
		}
	    });
	JMenuItem loadAPMFSFromFileItem = new JMenuItem("Load file system from file with APM");
	loadAPMFSFromFileItem.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    //JFileChooser fileChooser = new JFileChooser();
		    fileChooser.setMultiSelectionEnabled(false);
		    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		    if(fileChooser.showOpenDialog(FileSystemBrowserWindow.this) == 
		       JFileChooser.APPROVE_OPTION) {
			try {
			    String pathName = fileChooser.getSelectedFile().getCanonicalPath();
			    loadFS(pathName, true, false);
			} catch(IOException ioe) {
			    ioe.printStackTrace();
			    JOptionPane.showMessageDialog(FileSystemBrowserWindow.this,
							  "Count not resolve pathname!",
							  "Error", JOptionPane.ERROR_MESSAGE);
			} catch(Exception e) {
			    e.printStackTrace();
			    JOptionPane.showMessageDialog(FileSystemBrowserWindow.this,
							  "Could not read contents of partition!",
							  "Error", JOptionPane.ERROR_MESSAGE);
			}
		    }
		}
	    });
	JMenuItem fsInfoItem = new JMenuItem("File system info");
	fsInfoItem.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    if(fsView != null) {
			VolumeInfoWindow infoWindow = new VolumeInfoWindow(fsView);
			infoWindow.setVisible(true);
			HFSPlusVolumeHeader vh = fsView.getVolumeHeader();
			infoWindow.setVolumeFields(vh);
			if(vh.getAttributeVolumeJournaled())
			    infoWindow.setJournalFields(fsView.getJournalInfoBlock());
		    }
		    else
			JOptionPane.showMessageDialog(FileSystemBrowserWindow.this, "No file system loaded.", "Error", JOptionPane.ERROR_MESSAGE);
		}		
	    });
	JMenuItem aboutItem = new JMenuItem("About...");
	aboutItem.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent ae) {
		    String message = "";
		    message += "HFSExplorer " + HFSExplorer.VERSION + "\n";
		    //message += "Build #" + BuildNumber.BUILD_NUMBER + "\n";
		    message += HFSExplorer.COPYRIGHT + "\n";
 		    for(String notice : HFSExplorer.NOTICES) {
 			message += notice + "\n";
 			// System.out.println("Message now: " + message);
 		    }
		    message += "\n Operating system: " + System.getProperty("os.name") + " " + System.getProperty("os.version");
		    message += "\n Architecture: " + System.getProperty("os.arch");
		    message += "\n Virtual machine: " + System.getProperty("java.vm.vendor") + " " + 
			System.getProperty("java.vm.name") + " "  + System.getProperty("java.vm.version");
		    JOptionPane.showMessageDialog(FileSystemBrowserWindow.this, message, 
						  "About", JOptionPane.INFORMATION_MESSAGE);
		}
	    });
	
	JMenu fileMenu = new JMenu("File");
	if(loadFSFromDeviceItem != null)
	    fileMenu.add(loadFSFromDeviceItem);
	if(loadFSFromDeviceWithAPMItem != null)
	    fileMenu.add(loadFSFromDeviceWithAPMItem);
	fileMenu.add(loadFSFromFileItem);
	fileMenu.add(loadAPMFSFromFileItem);
	JMenu infoMenu = new JMenu("Info");
	infoMenu.add(fsInfoItem);
	JMenu helpMenu = new JMenu("Help");
	helpMenu.add(aboutItem);
	JMenuBar menuBar = new JMenuBar();
	menuBar.add(fileMenu);
	menuBar.add(infoMenu);
	menuBar.add(helpMenu);
	setJMenuBar(menuBar);
	// /Menus
	
	setDefaultCloseOperation(EXIT_ON_CLOSE);
	add(fsbPanel, BorderLayout.CENTER);
	pack();
	setLocationRelativeTo(null);
    }
    
    public void loadFS(String filename, boolean readAPM, boolean readFromDevice) {
	if(fsView != null) {
	    fsView.getStream().close();
	}
	LowLevelFile fsFile;
	if(System.getProperty("os.name").toLowerCase().startsWith("windows") &&
	   System.getProperty("os.arch").toLowerCase().equals("x86"))
	    fsFile = new WindowsLowLevelIO(filename);
	else
	    fsFile = new RandomAccessLLF(filename);
	
	int blockSize = 0x200; // == 512
	int ddrBlockSize;
	long fsOffset;
	long fsLength;
	if(readAPM) {
	    byte[] firstBlock = new byte[blockSize];
	    fsFile.readFully(firstBlock);
	    DriverDescriptorRecord ddr = new DriverDescriptorRecord(firstBlock, 0);
	    //ddr.print(System.out, "");
	    ApplePartitionMap apm = new ApplePartitionMap(fsFile, 0x200, ddr.getSbBlkSize());
	    //apm.print(System.out, "");
	    APMPartition[] partitions = apm.getPartitions();
	    if(partitions.length == 0) {
		JOptionPane.showMessageDialog(this, "Could not find an Apple Partition Map with any partitions inside.",
					      "APM not found", JOptionPane.ERROR_MESSAGE);
		return;
	    }
		
	    Object selectedValue;
	    while(true) {
		selectedValue = JOptionPane.showInputDialog(this, "Select which partition to read", 
							    "Choose APM partition", JOptionPane.QUESTION_MESSAGE,
							    null, partitions, partitions[0]);
		if(selectedValue != null &&
		   selectedValue instanceof APMPartition) {
		    APMPartition selectedPartition = (APMPartition)selectedValue;
		    String partitionType = selectedPartition.getPmParTypeAsString();
		    if(selectedPartition.getPmParTypeAsString().trim().equals("Apple_HFS"))
			break;
		    else
			JOptionPane.showMessageDialog(this, "Can't find handler for partition type \"" + partitionType + "\"",
						      "Unknown partition type", JOptionPane.ERROR_MESSAGE);
		}
		else
		    return;
	    }
	    if(selectedValue instanceof APMPartition) {
		APMPartition selectedPartition = (APMPartition)selectedValue;
		fsOffset = (selectedPartition.getPmPyPartStart()+selectedPartition.getPmLgDataStart())*blockSize;
		fsLength = selectedPartition.getPmDataCnt()*blockSize;
	    }
	    else
		throw new RuntimeException("Impossible error!");
	}
	else {
	    fsOffset = 0;
	    fsLength = fsFile.length();
	}
	fsView = new HFSFileSystemView(fsFile, fsOffset);
	HFSPlusCatalogLeafRecord rootRecord = fsView.getRoot();
	HFSPlusCatalogLeafRecord[] rootContents = fsView.listRecords(rootRecord);
	populateFilesystemGUI(rootContents);
	statusLabel.setText("0 objects selected (0 bytes)");
    }

    private void populateFilesystemGUI(HFSPlusCatalogLeafRecord[] contents) {
	DefaultMutableTreeNode rootNode = new NoLeafMutableTreeNode("/");
	populateNode(rootNode, contents);
	DefaultTreeModel model = new DefaultTreeModel(rootNode);
	dirTree.setModel(model);

	populateTable(contents);
	addressField.setText("/");
    }
    private void populateNode(DefaultMutableTreeNode rootNode, HFSPlusCatalogLeafRecord[] contents) {
	boolean folderThreadSet = false;
	boolean hasChildren = false;
	LinkedList<HFSPlusCatalogLeafRecord> fileThreads = new LinkedList<HFSPlusCatalogLeafRecord>();
	for(HFSPlusCatalogLeafRecord rec : contents) {
	    Vector<String> currentRow = new Vector<String>(4);
	    
	    HFSPlusCatalogLeafRecordData recData = rec.getData();
	    if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FILE &&
	       recData instanceof HFSPlusCatalogFile) {
		HFSPlusCatalogFile catFile = (HFSPlusCatalogFile)recData;
		if(!hasChildren) hasChildren = true;
	    }
	    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER &&
		    recData instanceof HFSPlusCatalogFolder) {
		HFSPlusCatalogFolder catFolder = (HFSPlusCatalogFolder)recData;
		rootNode.add(new NoLeafMutableTreeNode(new RecordNodeStorage(rec)));
		if(!hasChildren) hasChildren = true;
	    }
	    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER_THREAD &&
		    recData instanceof HFSPlusCatalogThread) {
		HFSPlusCatalogThread catThread = (HFSPlusCatalogThread)recData;
		rootNode.setUserObject(new RecordNodeStorage(rec));
		if(!folderThreadSet) folderThreadSet = true;
	    }
	    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FILE_THREAD &&
		    recData instanceof HFSPlusCatalogThread) {
		HFSPlusCatalogThread catThread = (HFSPlusCatalogThread)recData;
		fileThreads.addLast(rec);
	    }
	    else
		System.err.println("WARNING: Encountered unexpected record type (" + recData.getRecordType() + ")");
	}
	if(hasChildren && !folderThreadSet)
	    System.err.println("ERROR: Found no folder thread!");
	if(!fileThreads.isEmpty())
	    System.err.println("INFORMATION: Found " + fileThreads.size() + " file threads unexpectedly.");
    }
    
    public void populateTable(HFSPlusCatalogLeafRecord[] contents) {
	while(tableModel.getRowCount() > 0) {
	    tableModel.removeRow(tableModel.getRowCount()-1);
	}
	
	for(HFSPlusCatalogLeafRecord rec : contents) {
	    Vector<Object> currentRow = new Vector<Object>(4);
	    
	    HFSPlusCatalogLeafRecordData recData = rec.getData();
	    if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FILE &&
	       recData instanceof HFSPlusCatalogFile) {
		HFSPlusCatalogFile catFile = (HFSPlusCatalogFile)recData;
		currentRow.add(new RecordContainer(rec));
		currentRow.add(SpeedUnitUtils.bytesToBinaryUnit(catFile.getDataFork().getLogicalSize()));
		currentRow.add("File");
		DateFormat dti = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
		currentRow.add("" + dti.format(catFile.getContentModDateAsDate()));
	    }
	    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER &&
		    recData instanceof HFSPlusCatalogFolder) {
		HFSPlusCatalogFolder catFolder = (HFSPlusCatalogFolder)recData;
		currentRow.add(new RecordContainer(rec));
		currentRow.add("");
		currentRow.add("Folder");
		DateFormat dti = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
		currentRow.add("" + dti.format(catFolder.getContentModDateAsDate()));
	    }
	    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER_THREAD &&
		    recData instanceof HFSPlusCatalogThread) {
		HFSPlusCatalogThread catThread = (HFSPlusCatalogThread)recData;
	    }
	    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FILE_THREAD &&
		    recData instanceof HFSPlusCatalogThread) {
		HFSPlusCatalogThread catThread = (HFSPlusCatalogThread)recData;
	    }
	    else
		System.out.println("WARNING: Encountered unexpected record type (" + recData.getRecordType() + ")");
	    if(!currentRow.isEmpty())
		tableModel.addRow(currentRow);
	}
    }

    private void actionExtractToDir() {
	if(dirTreeLastFocus > fileTableLastFocus) {
	    Object o = dirTree.getLastSelectedPathComponent();
	    //System.err.println(o.toString());
	    if(o == null) {
		JOptionPane.showMessageDialog(this, "No file or folder selected.",
					      "Information", JOptionPane.INFORMATION_MESSAGE);
	    }
	    else if(o instanceof DefaultMutableTreeNode) {
		Object o2 = ((DefaultMutableTreeNode)o).getUserObject();
		if(o2 instanceof RecordNodeStorage) {
		    HFSPlusCatalogLeafRecord rec = ((RecordNodeStorage)o2).getRecord();
		    //System.err.println(rec.toString());
		    fileChooser.setMultiSelectionEnabled(false);
		    fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		    if(fileChooser.showDialog(FileSystemBrowserWindow.this, "Extract here") == JFileChooser.APPROVE_OPTION) {
			File outDir = fileChooser.getSelectedFile();
			extract(rec, outDir);
		    }
		}
		else
		   JOptionPane.showMessageDialog(this, "Unexpected data in tree user object. (Internal error, report to developer)" + "\nClass: " + o.getClass().toString(),
						 "Error", JOptionPane.ERROR_MESSAGE); 
	    }
	    else
		JOptionPane.showMessageDialog(this, "Unexpected data in tree model. (Internal error, report to developer)" + "\nClass: " + o.getClass().toString(),
					      "Error", JOptionPane.ERROR_MESSAGE);
	}
	else {
	    int[] selectedRows = fileTable.getSelectedRows();
	    if(selectedRows.length == 0) {
		JOptionPane.showMessageDialog(this, "No file or folder selected.",
					      "Information", JOptionPane.INFORMATION_MESSAGE);
		return;
	    }
	    else {
		fileChooser.setMultiSelectionEnabled(false);
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if(fileChooser.showDialog(FileSystemBrowserWindow.this, "Extract here") == JFileChooser.APPROVE_OPTION) {
		    File outDir = fileChooser.getSelectedFile();
		
		    for(int selectedRow : selectedRows) {
			Object o = tableModel.getValueAt(selectedRow, 0);
			HFSPlusCatalogLeafRecord rec;
			HFSPlusCatalogLeafRecordData recData;
			if(o instanceof RecordContainer) {
			    rec = ((RecordContainer)o).getRecord();
			    extract(rec, outDir);
			}
			else 
			    JOptionPane.showMessageDialog(this, "Unexpected data in table model. (Internal error, report to developer)",
							  "Error", JOptionPane.ERROR_MESSAGE);
		    }
		}
	    }
	}
    }
    
    private void actionGetInfo() {
	int[] selectedRows = fileTable.getSelectedRows();
	if(selectedRows.length == 0) {
	    JOptionPane.showMessageDialog(this, "No file selected.",
					  "Information", JOptionPane.INFORMATION_MESSAGE);
	    return;
	}
	else if (selectedRows.length == 1) {
	    for(int selectedRow : selectedRows) {
		Object o = tableModel.getValueAt(selectedRow, 0);
		HFSPlusCatalogLeafRecord rec;
		HFSPlusCatalogLeafRecordData recData;
		if(o instanceof RecordContainer) {
		    rec = ((RecordContainer)o).getRecord();
		    recData = rec.getData();
		    if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FILE &&
		       recData instanceof HFSPlusCatalogFile) {
			HFSPlusCatalogFile file = (HFSPlusCatalogFile)recData;
			FileInfoWindow fiw = new FileInfoWindow(rec.getKey().getNodeName().toString());
			fiw.setFields(file);
			fiw.setVisible(true);
		    }
		    else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER &&
			    recData instanceof HFSPlusCatalogFolder) {
			HFSPlusCatalogFolder folder = (HFSPlusCatalogFolder)recData;
			FolderInfoWindow fiw = new FolderInfoWindow(rec.getKey().getNodeName().toString());
			fiw.setFields(folder);
			fiw.setVisible(true);
		    }
		    else
			JOptionPane.showMessageDialog(this, "Only supported for files at the moment.", 
						      "Error", JOptionPane.ERROR_MESSAGE);
		}
		else 
		    JOptionPane.showMessageDialog(this, "Unexpected data in table model. (Internal error," +
						  " report to developer)", "Error", JOptionPane.ERROR_MESSAGE);
	    }
	    
	}
	else {
	    JOptionPane.showMessageDialog(this, "Please select one file at a time.",
					  "Error", JOptionPane.ERROR_MESSAGE);
	    return;
	}
    }
    
    private void extract(HFSPlusCatalogLeafRecord rec, File outDir) {
	HFSPlusCatalogLeafRecordData recData = rec.getData();
	if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FILE &&
	   recData instanceof HFSPlusCatalogFile) {
	    try {
		String filename = rec.getKey().getNodeName().toString();
		File outFile = new File(outDir, filename);
		FileOutputStream fos = new FileOutputStream(outFile);
		fsView.extractDataForkToStream(rec, fos);
		fos.close();
		//JOptionPane.showMessageDialog(this, "The file was successfully extracted!\n",
		//			  "Extraction complete!", JOptionPane.INFORMATION_MESSAGE);
	    } catch(Exception e) {
		e.printStackTrace();
		JOptionPane.showMessageDialog(this, e.toString() + ": " + e.getMessage(),
					      "Error", JOptionPane.ERROR_MESSAGE);
	    }
	}
	else if(recData.getRecordType() == HFSPlusCatalogLeafRecordData.RECORD_TYPE_FOLDER &&
		recData instanceof HFSPlusCatalogFolder) {
	    HFSCatalogNodeID requestedID;
	    HFSPlusCatalogFolder catFolder = (HFSPlusCatalogFolder)recData;
	    requestedID = catFolder.getFolderID();
	    
	    HFSPlusCatalogLeafRecord[] contents = fsView.listRecords(requestedID);
	    // We now have the contents of the requested directory
	    File thisDir = new File(outDir, rec.getKey().getNodeName().toString());
	    thisDir.mkdir();
	    for(HFSPlusCatalogLeafRecord outRec : contents)
		extract(outRec, thisDir);
	    //populateNode(((NoLeafMutableTreeNode)obj), contents);
	}
    }
    
    public static void main(String[] args) {
       	try {
	    javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
	    /*
	     * Description of look&feels:
	     *   http://java.sun.com/docs/books/tutorial/uiswing/misc/plaf.html
	     */
	}
	catch(Exception e) {
	    //It's ok. Non-critical.
	}
	FileSystemBrowserWindow fsbWindow = new FileSystemBrowserWindow();
	fsbWindow.setVisible(true);
	if(args.length > 0) {
	    try {
		String pathName = new File(args[0]).getCanonicalPath();
		fsbWindow.loadFS(pathName, true, false);
	    } catch(IOException ioe) {
		ioe.printStackTrace();
		String msg = ioe.toString();
		for(StackTraceElement ste : ioe.getStackTrace())
		    msg += "\n" + ste.toString();
		JOptionPane.showMessageDialog(fsbWindow, msg, "Exception while loading file", JOptionPane.ERROR_MESSAGE);
	    }
	}
    }
}
