/*
 * Created by JFormDesigner on Mon Apr 21 12:50:34 EDT 2008
 */

package Provider.GoogleMapsStatic.TestUI;

import Provider.GoogleMapsStatic.*;
import Task.*;
import Task.Manager.*;
import Task.ProgressMonitor.*;
import Task.Support.CoreSupport.*;
import Task.Support.GUISupport.*;
import com.jgoodies.forms.factories.*;
import info.clearthought.layout.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;

import javax.imageio.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.text.*;
import java.util.concurrent.*;


/** @author nazmul idris */
public class SampleApp extends JFrame {
	
	
	
/** @author Hunter Jansen*/
private void moveUp(){
	double newValue = Double.parseDouble(ttfLat.getText())+.005;
	ttfLat.setText(Double.toString(newValue));
	startTaskAction();	
}	

/** @author Hunter Jansen*/
private void moveDown(){
	double newValue = Double.parseDouble(ttfLat.getText())-.005;
	ttfLat.setText(Double.toString(newValue));
	startTaskAction();	
}

/** @author Hunter Jansen*/
private void moveLeft(){
	double newValue = Double.parseDouble(ttfLon.getText())-.005;
	ttfLon.setText(Double.toString(newValue));
	startTaskAction();	
}

/** @author Hunter Jansen*/
private void moveRight(){
	double newValue = Double.parseDouble(ttfLon.getText())+.005;
	ttfLon.setText(Double.toString(newValue));
	startTaskAction();	
}

	
	
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
// data members
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
/** reference to task */
private SimpleTask _task;
/** this might be null. holds the image to display in a popup */
private BufferedImage _img;
/** this might be null. holds the text in case image doesn't display */
private String _respStr;

//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
// main method...
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

public static void main(String[] args) {
  Utils.createInEDT(SampleApp.class);
}

//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
// constructor
//XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

private void doInit() {
  GUIUtils.setAppIcon(this, "burn.png");
  GUIUtils.centerOnScreen(this);
  setVisible(true);

  int W = 28, H = W;
  boolean blur = false;
  float alpha = .7f;

  try {
    btnGetMap.setIcon(ImageUtils.loadScaledBufferedIcon("ok1.png", W, H, blur, alpha));
    btnQuit.setIcon(ImageUtils.loadScaledBufferedIcon("charging.png", W, H, blur, alpha));
  }
  catch (Exception e) {
    System.out.println(e);
  }

  _setupTask();
}

/** create a test task and wire it up with a task handler that dumps output to the textarea */
@SuppressWarnings("unchecked")
private void _setupTask() {

  TaskExecutorIF<ByteBuffer> functor = new TaskExecutorAdapter<ByteBuffer>() {
    public ByteBuffer doInBackground(Future<ByteBuffer> swingWorker,
                                     SwingUIHookAdapter hook) throws Exception
    {

      _initHook(hook);

      // set the license key
      MapLookup.setLicenseKey(ttfLicense.getText());
      // get the uri for the static map
      String uri = MapLookup.getMap(Double.parseDouble(ttfLat.getText()),
                                    Double.parseDouble(ttfLon.getText()),
                                    512,
                                    512,
                                    Integer.parseInt(ttfZoom.getText())
      );
      sout("Google Maps URI=" + uri);

      // get the map from Google
      GetMethod get = new GetMethod(uri);
      new HttpClient().executeMethod(get);

      ByteBuffer data = HttpUtils.getMonitoredResponse(hook, get);

      try {
        _img = ImageUtils.toCompatibleImage(ImageIO.read(data.getInputStream()));
        sout("converted downloaded data to image...");
      }
      catch (Exception e) {
        _img = null;
        sout("The URI is not an image. Data is downloaded, can't display it as an image.");
        _respStr = new String(data.getBytes());
      }

      return data;
    }

    @Override public String getName() {
      return _task.getName();
    }
  };

  _task = new SimpleTask(
      new TaskManager(),
      functor,
      "HTTP GET Task",
      "Download an image from a URL",
      AutoShutdownSignals.Daemon
  );

  _task.addStatusListener(new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      sout(":: task status change - " + ProgressMonitorUtils.parseStatusMessageFrom(evt));
      lblProgressStatus.setText(ProgressMonitorUtils.parseStatusMessageFrom(evt));
    }
  });

  _task.setTaskHandler(new
      SimpleTaskHandler<ByteBuffer>() {
        @Override public void beforeStart(AbstractTask task) {
          sout(":: taskHandler - beforeStart");
        }
        @Override public void started(AbstractTask task) {
          sout(":: taskHandler - started ");
        }
        /** {@link SampleApp#_initHook} adds the task status listener, which is removed here */
        @Override public void stopped(long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- stopped");
          sout(":: time = " + time / 1000f + "sec");
          task.getUIHook().clearAllStatusListeners();
        }
        @Override public void interrupted(Throwable e, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- interrupted - " + e.toString());
        }
        @Override public void ok(ByteBuffer value, long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- ok - size=" + (value == null
              ? "null"
              : value.toString()));
          if (_img != null) {
            _displayImgInSameFrame();
          }
          else _displayRespStrInFrame();

        }
        @Override public void error(Throwable e, long time, AbstractTask task) {
          sout(":: taskHandler [" + task.getName() + "]- error - " + e.toString());
        }
        @Override public void cancelled(long time, AbstractTask task) {
          sout(" :: taskHandler [" + task.getName() + "]- cancelled");
        }
      }
  );
}

private SwingUIHookAdapter _initHook(SwingUIHookAdapter hook) {
  hook.enableRecieveStatusNotification(checkboxRecvStatus.isSelected());
  hook.enableSendStatusNotification(checkboxSendStatus.isSelected());

  hook.setProgressMessage(ttfProgressMsg.getText());

  PropertyChangeListener listener = new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      SwingUIHookAdapter.PropertyList type = ProgressMonitorUtils.parseTypeFrom(evt);
      int progress = ProgressMonitorUtils.parsePercentFrom(evt);
      String msg = ProgressMonitorUtils.parseMessageFrom(evt);

      progressBar.setValue(progress);
      progressBar.setString(type.toString());

      sout(msg);
    }
  };

  hook.addRecieveStatusListener(listener);
  hook.addSendStatusListener(listener);
  hook.addUnderlyingIOStreamInterruptedOrClosed(new PropertyChangeListener() {
    public void propertyChange(PropertyChangeEvent evt) {
      sout(evt.getPropertyName() + " fired!!!");
    }
  });

  return hook;
}

private void _displayImgInFrame() {

  final JFrame frame = new JFrame("Google Static Map");
  GUIUtils.setAppIcon(frame, "71.png");
  frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

  JLabel imgLbl = new JLabel(new ImageIcon(_img));
  imgLbl.setToolTipText(MessageFormat.format("<html>Image downloaded from URI<br>size: w={0}, h={1}</html>",
                                             _img.getWidth(), _img.getHeight()));
  imgLbl.addMouseListener(new MouseListener() {
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) { frame.dispose();}
    public void mouseReleased(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
  });

  frame.setContentPane(imgLbl);
  frame.pack();

  GUIUtils.centerOnScreen(frame);
  frame.setVisible(true);
}

private void _displayImgInSameFrame() {

  mapLabel.setIcon(new ImageIcon(_img));
  mapLabel.setToolTipText(MessageFormat.format("<html>Image downloaded from URI<br>size: w={0}, h={1}</html>",
          									   _img.getWidth(), _img.getHeight()));
}

private void _displayRespStrInFrame() {

  final JFrame frame = new JFrame("Google Static Map - Error");
  GUIUtils.setAppIcon(frame, "69.png");
  frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

  JTextArea response = new JTextArea(_respStr, 25, 80);
  response.addMouseListener(new MouseListener() {
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) { frame.dispose();}
    public void mouseReleased(MouseEvent e) { }
    public void mouseEntered(MouseEvent e) { }
    public void mouseExited(MouseEvent e) { }
  });

  frame.setContentPane(new JScrollPane(response));
  frame.pack();

  GUIUtils.centerOnScreen(frame);
  frame.setVisible(true);
}

/** simply dump status info to the textarea */
private void sout(final String s) {
  Runnable soutRunner = new Runnable() {
    public void run() {
      if (ttaStatus.getText().equals("")) {
        ttaStatus.setText(s);
      }
      else {
        ttaStatus.setText(ttaStatus.getText() + "\n" + s);
      }
    }
  };

  if (ThreadUtils.isInEDT()) {
    soutRunner.run();
  }
  else {
    SwingUtilities.invokeLater(soutRunner);
  }
}

private void startTaskAction() {
  try {
    _task.execute();
  }
  catch (TaskException e) {
    sout(e.getMessage());
  }
}


public SampleApp() {
  initComponents();
  doInit();
}

private void quitProgram() {
  _task.shutdown();
  System.exit(0);
}

private void initComponents() {
  // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
  // Generated using JFormDesigner non-commercial license
  dialogPane = new JPanel();
  contentPanel = new JPanel();
  panel1 = new JPanel();
  label2 = new JLabel();
  ttfSizeW = new JTextField();
  label4 = new JLabel();
  ttfLat = new JTextField();
  btnGetMap = new JButton();
  label3 = new JLabel();
  ttfSizeH = new JTextField();
  label5 = new JLabel();
  ttfLon = new JTextField();
  btnQuit = new JButton();
  label1 = new JLabel();
  ttfLicense = new JTextField();
  label6 = new JLabel();
  ttfZoom = new JTextField();
  scrollPane1 = new JScrollPane();
  ttaStatus = new JTextArea();
  panel2 = new JPanel();
  panel3 = new JPanel();
  checkboxRecvStatus = new JCheckBox();
  checkboxSendStatus = new JCheckBox();
  ttfProgressMsg = new JTextField();
  progressBar = new JProgressBar();
  lblProgressStatus = new JLabel();
  ddlLocation = new JComboBox();
  lblLocation = new JLabel();
  lblLocName = new JLabel();
  btnSave = new JButton();
  tbxLocName = new JTextField();
  panel4 = new JPanel();
  mapLabel = new JLabel();
  btnUp = new JButton();
  btnDown = new JButton();
  btnLeft = new JButton();
  btnRight = new JButton();

  //======== this ========
  setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
  setTitle("Google Static Maps");
  setIconImage(null);
  Container contentPane = getContentPane();
  contentPane.setLayout(new BorderLayout());

  //======== dialogPane ========
  {
  	dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
  	dialogPane.setOpaque(false);
  	dialogPane.setLayout(new BorderLayout());

  	//======== contentPanel ========
  	{
  		contentPanel.setOpaque(false);
  		contentPanel.setLayout(new TableLayout(new double[][] {
  			{0.44, 0.56, TableLayout.FILL},
  			{TableLayout.PREFERRED, TableLayout.FILL, TableLayout.PREFERRED}}));
  		((TableLayout)contentPanel.getLayout()).setHGap(5);
  		((TableLayout)contentPanel.getLayout()).setVGap(5);

  		//======== panel4 ========
  		panel4.setOpaque(false);
  		panel4.setBorder(new CompoundBorder(
  				new TitledBorder("Map"),
  				Borders.DLU2_BORDER));
  		panel4.setLayout(new TableLayout(new double[][]{
  			{.05,.80,.05},
  			{.05,.80,.05}	
  		}));
  		((TableLayout)panel4.getLayout()).setHGap(1);
  		((TableLayout)panel4.getLayout()).setHGap(1);
  		
  		//----btnUp----
  		btnUp.setText("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
  		btnUp.setHorizontalAlignment(SwingConstants.CENTER);
  		btnUp.setMnemonic('U');
  		btnUp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				moveUp();
			}
		});
  		
  		//----btnDown----
  		btnDown.setText("v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v v");
  		btnDown.setHorizontalAlignment(SwingConstants.CENTER);
  		btnDown.setMnemonic('D');
  		btnDown.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					moveDown();
				}
			});
  		
  		//----btnLeft----
  		btnLeft.setText("<");
  		btnLeft.setHorizontalAlignment(SwingConstants.CENTER);
  		btnLeft.setMnemonic('L');
  		btnLeft.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				moveLeft();
			}
		});
  		
  		//----btnRight----
  		btnRight.setText(">");
  		btnRight.setHorizontalAlignment(SwingConstants.CENTER);
  		btnRight.setMnemonic('R');
  		btnRight.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				moveRight();
			}
		});
  		
  		panel4.add(btnUp, new TableLayoutConstraints(0,0,2,0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		panel4.add(btnDown, new TableLayoutConstraints(0,2,2,2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		panel4.add(btnLeft, new TableLayoutConstraints(0,1,0,1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		panel4.add(btnRight, new TableLayoutConstraints(2,1,2,1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		panel4.add(mapLabel, new TableLayoutConstraints(1,1,1,1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		
  		//======== panel1 ========
  		{
  			panel1.setOpaque(false);
  			panel1.setBorder(new CompoundBorder(
  				new TitledBorder("Configure the inputs to Google Static Maps"),
  				Borders.DLU2_BORDER));
  			panel1.setLayout(new TableLayout(new double[][] {
  				{0.44, 0.56, TableLayout.FILL},
  				{TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED,
  				TableLayout.PREFERRED,TableLayout.PREFERRED, TableLayout.PREFERRED,
  				TableLayout.PREFERRED, TableLayout.PREFERRED  }}));
  			((TableLayout)panel1.getLayout()).setHGap(5);
  			((TableLayout)panel1.getLayout()).setVGap(5);

  			//---- label2 ----
//  			label2.setText("Size Width");
//  			label2.setHorizontalAlignment(SwingConstants.RIGHT);
//  			panel1.add(label2, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfSizeW ----
//  			ttfSizeW.setText("512");
//  			panel1.add(ttfSizeW, new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label4 ----
  			label4.setText("Latitude");
  			label4.setHorizontalAlignment(SwingConstants.LEFT);
  			panel1.add(label4, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfLat ----
  			ttfLat.setText("38.931099");
  			panel1.add(ttfLat, new TableLayoutConstraints(1, 0, 1, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- btnGetMap ----
  			btnGetMap.setText("Get Map");
  			btnGetMap.setHorizontalAlignment(SwingConstants.LEFT);
  			btnGetMap.setMnemonic('G');
  			btnGetMap.addActionListener(new ActionListener() {
  				public void actionPerformed(ActionEvent e) {
  					startTaskAction();
  				}
  			});
  			panel1.add(btnGetMap, new TableLayoutConstraints(0, 5, 0, 5, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label3 ----
//  			label3.setText("Size Height");
//  			label3.setHorizontalAlignment(SwingConstants.RIGHT);
//  			panel1.add(label3, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfSizeH ----
//  			ttfSizeH.setText("512");
//  			panel1.add(ttfSizeH, new TableLayoutConstraints(1, 1, 1, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label5 ----
  			label5.setText("Longitude");
  			label5.setHorizontalAlignment(SwingConstants.LEFT);
  			panel1.add(label5, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfLon ----
  			ttfLon.setText("-77.3489");
  			panel1.add(ttfLon, new TableLayoutConstraints(1, 1, 1, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- btnQuit ----
//  			btnQuit.setText("Quit");
//  			btnQuit.setMnemonic('Q');
//  			btnQuit.setHorizontalAlignment(SwingConstants.LEFT);
//  			btnQuit.setHorizontalTextPosition(SwingConstants.LEFT);
//  			btnQuit.addActionListener(new ActionListener() {
//  				public void actionPerformed(ActionEvent e) {
//  					quitProgram();
//  				}
//  			});
//  			panel1.add(btnQuit, new TableLayoutConstraints(1, 5, 1, 5, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label1 ----
//  			label1.setText("License Key");
//  			label1.setHorizontalAlignment(SwingConstants.RIGHT);
//  			panel1.add(label1, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfLicense ----
//  			ttfLicense.setToolTipText("Enter your own URI for a file to download in the background");
//  			panel1.add(ttfLicense, new TableLayoutConstraints(1, 2, 1, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- label6 ----
  			label6.setText("Zoom");
  			label6.setHorizontalAlignment(SwingConstants.LEFT);
  			panel1.add(label6, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfZoom ----
  			ttfZoom.setText("14");
  			panel1.add(ttfZoom, new TableLayoutConstraints(1, 2, 1, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//----lblLocation
  			lblLocation.setText("Saved Locations");
  			lblLocation.setHorizontalAlignment(SwingConstants.CENTER);
  			panel1.add(lblLocation, new TableLayoutConstraints(0,3,1,3, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  			
			//-----ddlLocation----
  			panel1.add(ddlLocation, new TableLayoutConstraints(0,4,1,4, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  	
  			//----lblLocName----
  			lblLocName.setText("Name to save location as");
  			lblLocation.setHorizontalAlignment(SwingConstants.CENTER);
  			panel1.add(lblLocName, new TableLayoutConstraints(0,6,1,6, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
 
  			//----btnSave----
  			btnSave.setText("Save Location");
  			btnSave.setHorizontalAlignment(SwingConstants.LEFT);
  			btnSave.setMnemonic('S');
  			btnSave.addActionListener(new ActionListener(){
  				public void actionPerformed(ActionEvent e){
  					startTaskAction();
  				}
  			});
  		    panel1.add(btnSave, new TableLayoutConstraints(1,7,1,7, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));  		
  			
  			//----tbxLocName----
  			tbxLocName.setText("");
  			panel1.add(tbxLocName, new TableLayoutConstraints(0,7,0,7, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		
  		}
  		contentPanel.add(panel1, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		
  		
  		//======== scrollPane1 ========
  		{
  			scrollPane1.setBorder(new TitledBorder("System.out - displays all status and progress messages, etc."));
  			scrollPane1.setOpaque(false);

  			//---- ttaStatus ----
  			ttaStatus.setBorder(Borders.createEmptyBorder("1dlu, 1dlu, 1dlu, 1dlu"));
  			ttaStatus.setToolTipText("<html>Task progress updates (messages) are displayed here,<br>along with any other output generated by the Task.<html>");
  			scrollPane1.setViewportView(ttaStatus);
  		}
  		contentPanel.add(scrollPane1, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  		//======== panel2 ========
  		{
  			panel2.setOpaque(false);
  			panel2.setBorder(new CompoundBorder(
  				new TitledBorder("Status - control progress reporting"),
  				Borders.DLU2_BORDER));
  			panel2.setLayout(new TableLayout(new double[][] {
  				{0.45, TableLayout.FILL, 0.45},
  				{TableLayout.PREFERRED, TableLayout.PREFERRED}}));
  			((TableLayout)panel2.getLayout()).setHGap(5);
  			((TableLayout)panel2.getLayout()).setVGap(5);

  			//======== panel3 ========
  			{
  				panel3.setOpaque(false);
  				panel3.setLayout(new GridLayout(1, 2));

  				//---- checkboxRecvStatus ----
  				checkboxRecvStatus.setText("Enable \"Recieve\"");
  				checkboxRecvStatus.setOpaque(false);
  				checkboxRecvStatus.setToolTipText("Task will fire \"send\" status updates");
  				checkboxRecvStatus.setSelected(true);
  				panel3.add(checkboxRecvStatus);

  				//---- checkboxSendStatus ----
  				checkboxSendStatus.setText("Enable \"Send\"");
  				checkboxSendStatus.setOpaque(false);
  				checkboxSendStatus.setToolTipText("Task will fire \"recieve\" status updates");
  				panel3.add(checkboxSendStatus);
  			}
  			panel2.add(panel3, new TableLayoutConstraints(0, 0, 0, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- ttfProgressMsg ----
  			ttfProgressMsg.setText("Loading map from Google Static Maps");
  			ttfProgressMsg.setToolTipText("Set the task progress message here");
  			panel2.add(ttfProgressMsg, new TableLayoutConstraints(2, 0, 2, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- progressBar ----
  			progressBar.setStringPainted(true);
  			progressBar.setString("progress %");
  			progressBar.setToolTipText("% progress is displayed here");
  			panel2.add(progressBar, new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

  			//---- lblProgressStatus ----
  			lblProgressStatus.setText("task status listener");
  			lblProgressStatus.setHorizontalTextPosition(SwingConstants.LEFT);
  			lblProgressStatus.setHorizontalAlignment(SwingConstants.LEFT);
  			lblProgressStatus.setToolTipText("Task status messages are displayed here when the task runs");
  			panel2.add(lblProgressStatus, new TableLayoutConstraints(2, 1, 2, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		}
  		contentPanel.add(panel2, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  		contentPanel.add(panel4, new TableLayoutConstraints(1, 0, 1, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
  	}
  	dialogPane.add(contentPanel, BorderLayout.CENTER);
  }
  contentPane.add(dialogPane, BorderLayout.CENTER);
  setSize(1000, 620);
  setLocationRelativeTo(null);
  // JFormDesigner - End of component initialization  //GEN-END:initComponents
}

// JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
// Generated using JFormDesigner non-commercial license
private JPanel dialogPane;
private JPanel contentPanel;
private JPanel panel1;
private JLabel label2;
private JTextField ttfSizeW;
private JLabel label4;
private JTextField ttfLat;
private JButton btnGetMap;
private JLabel label3;
private JTextField ttfSizeH;
private JLabel label5;
private JTextField ttfLon;
private JButton btnQuit;
private JLabel label1;
private JTextField ttfLicense;
private JLabel label6;
private JTextField ttfZoom;
private JScrollPane scrollPane1;
private JTextArea ttaStatus;
private JPanel panel2;
private JPanel panel3;
private JCheckBox checkboxRecvStatus;
private JCheckBox checkboxSendStatus;
private JTextField ttfProgressMsg;
private JProgressBar progressBar;
private JLabel lblProgressStatus;
private JComboBox ddlLocation;
private JLabel lblLocation;
private JLabel lblLocName;
private JButton btnSave;
private JTextField tbxLocName;
private JPanel panel4;
private JLabel mapLabel;
private JButton btnUp;
private JButton btnDown;
private JButton btnLeft;
private JButton btnRight;


// JFormDesigner - End of variables declaration  //GEN-END:variables
}
