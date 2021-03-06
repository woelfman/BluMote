// Copyright (C) 2011 Woelfware

package com.woelfware.blumote;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.backup.BackupManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.woelfware.blumote.Activities.ImageActivityItem;
import com.woelfware.database.DeviceDB;

/**
 * Primary controller class for the project.
 * @author keusej
 *
 */
@SuppressLint("HandlerLeak")
public class BluMote extends Activity implements OnClickListener,OnItemClickListener,OnItemSelectedListener
{
	// Debugging
	private static final String TAG = "BlueMote";
	static final boolean DEBUG = false; // for debugging only, set to false for production		

	private boolean isRunning = false;
	
	// Preferences file for this application
	static final String PREFS_FILE = "BluMoteSettings";
	
	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private static final int REQUEST_MANAGE_DEVICE = 3;
	// private static final int REQUEST_RENAME_POD = 4;
	static final int ACTIVITY_ADD = 5;	
	private static final int ACTIVITY_RENAME = 6;
	private static final int MISC_RENAME = 7;
	static final int ACTIVITY_INIT_EDIT = 8;
	private static final int PREFERENCES = 9;
	static final int UPDATE_FW = 10;
	
	//AlarmManager intent string
	public static final String ACTION_ALARM_DISCONNECT =
			"com.woelfware.ACTION_ALARM_TIMEOUT_DISCONNECT";
	
	// Message types sent from the BluetoothChatService Handler
	static final int MESSAGE_STATE_CHANGE = 1;
	static final int MESSAGE_READ = 2;
	static final int MESSAGE_WRITE = 3;
	static final int MESSAGE_DEVICE_NAME = 4;
	static final int MESSAGE_TOAST = 5;
	// Message time for repeat-key-delay
	static final int MESSAGE_KEY_PRESSED = 6;

	// Key names received from the BluetoothChatService Handler
	static final String DEVICE_NAME = "device_name";
	static final String TOAST = "toast";

	// Dialog menu constants
	static final int DIALOG_SHOW_INFO = 0;
	private static final int DIALOG_INIT_DELAY = 1;
	static final int DIALOG_INIT_PROGRESS = 2; 
	private static final int DIALOG_ABOUT = 3;
	static final int DIALOG_LEARN_WAIT = 4;
	private static final int DIALOG_FW_WAIT = 5;
	private static final int FLASH_PROGRESS_DIALOG = 6;
	private static final int DIALOG_WAIT_BSL = 7;
	private static final int DIALOG_RESET_POD = 8;
	//private static final int WARN_BSL = 9;
	
	// for firmware flashing process
	ProgressDialog progressDialog2;
	
	EnterBSLTask bsl;
	
	// these are all in milli secs
	private static int LONG_DELAY_TIME = 1000; // starts repeated button clicks
	static int DELAY_TIME = 200; // repeat button click delay (after button held)
	
	boolean BLOCK_TRANSMIT = false;
	
	// Layout Views
	private TextView mTitle;

	// Name of the connected device
	private String mConnectedDeviceName = null;

	// connecting device name - temp storage
	private String connectingDevice;
	// connecting device MAC address - temp storage
	private String connectingMAC = null;

	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	BluetoothChatService mChatService = null;	

	// where to download the list of firmware updates from the web
	private static String FW_IMAGE_URL;
	
	// SQL database class
	DeviceDB device_data;
	// Shared preferences class - for storing config settings between runs
	SharedPreferences prefs;
	// for storing data we receive from bluetooth
	private DataCache cache;

	// reference to alarmmanager pendingintent
	PendingIntent alarmIntent;
	
	boolean alarmSet = false;
	
	// This is the device database results for each button on grid
	// Note that when we are working with an activity then this structure
	// is updated for the button mappings but must be refreshed with getActivityButtons()
	// whenever a modification is performed to the activity button mappings
	ButtonData[] buttons;

	// Data associated with the power_off button of an activity
	ButtonData[] activityPowerOffData = null;
	
	// currently selected device
	String cur_device;

	// Currently selected button resource id (for training mode operations)
	private int LAST_PUSHED_BUTTON_ID = 0;
	
	// used to prevent the drop-down from changing the last device, unless we specifically
	// selected a new device after the program is all set up, seems like when the interface
	// loads we get spurious onItemSelected() events firing.
	boolean LOCK_LAST_DEVICE = false;  				
	
	// Flag that tells us if we are holding our finger on a button and should loop
	static boolean BUTTON_LOOPING = false;
	
	// arraylist position of activity that we want to rename
	private static int activity_rename;
	
	// misc button id that we want to rename
	private static String misc_button;

	// a unique integer code for each time a button is pushed, used for preventing
	// a user from double pushing a button and the long timer accidentally activates
	private static int buttonPushID = 0;	
	
	// for holding the activity init sequence while it's being built
	// designed to hold 2 element String[]
	ArrayList<String[]> activityInit = new ArrayList<String[]>();

	// used to convert device/activity names into IDs that do not change
	InterfaceLookup lookup;

	private boolean LOCK_RECONNECT = false; 
	
	// These are used for activities display window
	private static final int ID_DELETE = 0;
	private static final int ID_RENAME = 1;
	private static final int ID_MANAGE = 2;	
	 
	// Menu object - initalized in onCreateOptionsMenu()
	Menu myMenu;		

	private MainInterface mainScreen = null;
	private Activities activities;
	private Pod pod;
	
	GestureDetector gestureDetector;
	View.OnTouchListener gestureListener;
	private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    
    // Flag to indicate that the next keypress is a new activity button association
    private boolean captureButton = false;
    // keeps track of what the actiivty button was that we clicked on that wanted to be associated
    // with the device button
    private String activityButton = "";   
    
    // these variables are all used in the gesture listener logic
	private static boolean isButtonTimerStarted = false;
    private static View lastView;
    private static boolean isButtonPushed;
    
    // preference : haptic button feedback
    private boolean hapticFeedback = true;
    // preference : HTC insecure BT connection
    private static boolean htcInsecure = false;
    
    // for cloud backups
    BackupManager mBackupManager;
    
    // Broadcast receiver for BT state change
    ConnectivityListener connectionListener;
    
    // Broadcast receiver for Screen state changes
    BroadcastReceiver screenReceiver;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {	
		
		super.onCreate(savedInstanceState);
		Log.v("BluMote_State", "In onCreate");
		
		FW_IMAGE_URL = getString(R.string.FW_URL);
		
		LOCK_LAST_DEVICE = true;					
		
		// get preferences file
		prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE);
		
		// get SQL database class
		device_data = new DeviceDB(this);
		device_data.open();

		pod = Pod.getInstance();
		// set blumote reference for Pod instance
		pod.setBluMoteRef(this);
		
		// instantiate button screen helper classes
		mainScreen = new MainInterface(this);		
		
		// initialize the InterfaceLookup
		lookup = new InterfaceLookup(prefs);
		
		// instantiate activities helper class
		activities = new Activities(this, mainScreen, pod);
		
		cache = DataCache.getInstance();
		
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			//finish();
			return;
		}

		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled() ) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// set a flag indicating this request was sent out
			// this prevents our 'timeout' code from initiating
			BluetoothChatService.BT_ENABLING = true;
			// Otherwise, setup the session
		} 

		// the gesture class is used to handle fling events
		gestureDetector = new GestureDetector(new MyGestureDetector());
		// the gesture listener will listen for any touch event
		// NOTE: gestureListener needs to be initialized before calling initializeInterface()
		gestureListener = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {            	
                if (gestureDetector.onTouchEvent(e)) { // check if it is a fling event
                	isButtonPushed = false;
                    return true;
                }                
                else // non fling event
                {                     	
                	if (mainScreen.button_map.get(v.getId()) != null) {
                		// if this is a valid button press then execute the button logic
                		if (e.getAction() == MotionEvent.ACTION_DOWN) {
                			isButtonPushed = true;
                			buttonPushID++;
                			// check if buttonTimerStarted is not started
                			if (!isButtonTimerStarted) {
                				isButtonTimerStarted = true;
                				lastView = v; // save the last view 
                				// provide haptic feedback on button click if that preference is set
                				if (hapticFeedback) {
                					lastView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                				}
                				// fire off count down timer before executing button press (ms)
                				new ButtonCountDown(LONG_DELAY_TIME, buttonPushID) {
                					public void onFinish() {
                						// called when timer expired
                						isButtonTimerStarted = false;
                						if ((getButtonID() == buttonPushID) && isButtonPushed) {
                							// if this was the original button push we started with
                							// and button is still being pushed then execute the long button push
                							executeButtonLongDown();        
                						}
                					}
                				}.start();			
                			} 
                		}  // END if (e.getAction() == MotionEvent.ACTION_DOWN

                		// only execute this one time and only if not in learn mode
                		// if we don't have !LOOP_KEY you can hit button multiple times
                		// and hold finger on button and you'll get duplicates
                		if (e.getAction() == MotionEvent.ACTION_UP) {
                			isButtonTimerStarted = false;
                			isButtonPushed = false; 
                			// if we were doing a long press, 
							// make sure that we exit repeat mode
                			if (BUTTON_LOOPING) {  	
                				pod.abortTransmit();
                				BUTTON_LOOPING = false;
                			}
                		}
                	}
                } // END else
                return false; // allows XML to consume
            } // END onTouch(View v, MotionEvent e)
		}; // END gestureListener										
		
		// Load the last pod that we connected to, onResume() will try to connect to this
		SharedPreferences myprefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean autoConnect = myprefs.getBoolean("autoconnect", true);
		if (autoConnect) { connectingMAC = prefs.getString("lastPod", null); }
		// Launch the pod selection screen if preference not set for autoconnect					
		else if ( BluetoothChatService.BT_ENABLING != true ) {
			// Launch the DeviceListActivity to see devices and do scan
			Intent serverIntent = new Intent(this, PodListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
		}
		
		// Set up the window layout
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		
		setContentView(R.layout.blank_interface); // need this for title setup to work
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.custom_title);
		// Set up the custom title
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mTitle = (TextView) findViewById(R.id.title_right_text);

		connectionListener = new ConnectivityListener();
		
		screenReceiver = new ScreenReceiver();
		
		mBackupManager = new BackupManager(this);
	}		
	
	private void setRunning(boolean status) {		
		isRunning = status;
	}
	
	private boolean getRunning() {
		return isRunning;
	}
	
	private void setTimerArmed(boolean status) {
		alarmSet = status;
	}
	
	private boolean getTimerArmed() {
		return alarmSet;
	}
	
	@Override
	protected void onStart() {		
		super.onStart();
		Log.v("BluMote_State", "In onStart");
		
		setRunning(true);
		
		if (mChatService == null) { // then first time this was called
			// Initialize the BluetoothChatService to perform bluetooth
			// connections
			mChatService = new BluetoothChatService(this, mHandler);						
			
			// initialize activities framework to mainScreen
			mainScreen.setActivities(activities);			
			
			// determine last device, set layout to that device's preferred layout
			String prefs_table = prefs.getString("lastDevice", null);
			
			if (prefs_table != null) {
				// determine if it is a device or an activity
				// note: lastDevice uses underscores in place of spaces
				String buttonConfig;
				if (prefs_table.startsWith(Activities.ACTIVITY_PREFIX_SPACE)) { 
					buttonConfig = activities.getButtonConfig(prefs_table);
					mainScreen.initializeInterface(buttonConfig, MainInterface.TYPE.ACTIVITY);
				} else {
					// look into database for type of button layout
					buttonConfig = device_data.getButtonConfig(prefs_table);
					mainScreen.initializeInterface(buttonConfig, MainInterface.TYPE.DEVICE);
				}			
			} else {
				// if no "lastDevice" set then just initialize the blank layout
				mainScreen.initializeInterface(MainInterface.DEVICE_LAYOUTS.BLANK.getValue(),
						MainInterface.TYPE.DEVICE);
			}			
			
			mainScreen.fetchButtons();
			
			// context menu on array list
			registerForContextMenu(findViewById(R.id.activities_list));
		}
	}
	
	@Override
	protected synchronized void onResume() {
		super.onResume();
		Log.v("BluMote_State", "In onResume");
		
		// refresh the haptic feedback pref
		SharedPreferences myprefs = PreferenceManager.getDefaultSharedPreferences(this);
		hapticFeedback = myprefs.getBoolean("hapticPREF", false);
		// get the HTC insecure pref
		htcInsecure = myprefs.getBoolean("htcInsecure", true);
		
		device_data.open(); // make sure database open

		// register broadcast receiver for BT state change
		//this.registerReceiver(connectionListener, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
		if ( !BluetoothChatService.BT_ENABLING )
			connectPod();
		
		// register broadcast receiver for AlarmManager triggers
		this.registerReceiver(connectionListener, new IntentFilter(BluMote.ACTION_ALARM_DISCONNECT));
		
		// register broadcast receiver for Screen State changes
		// initialize receiver
        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);        
		this.registerReceiver(screenReceiver, filter);
		
		if (alarmIntent != null) { 
			Log.v("BLUMOTE", "Stopping Alarm");
			// Get a reference to the Alarm Manager
		    AlarmManager alarmManager = 
		     (AlarmManager)getSystemService(Context.ALARM_SERVICE);
		    alarmManager.cancel(alarmIntent);
		}
		
		setTimerArmed(false);	
	}

	int getBluetoothState() {
		if (mChatService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// started already
			return mChatService.getState();
		}
		else return -1;
	}
	
	// called when a user initiates a connection to the pod, or when the application first starts or resumes
	void connectPod() {
		if (mBluetoothAdapter != null) {			
			if (getBluetoothState() != BluetoothChatService.STATE_CONNECTED) {
				// Start the Bluetooth chat services
				try {
					if (mChatService == null) {
						mChatService = new BluetoothChatService(this, mHandler);
					}
					if (!mBluetoothAdapter.isEnabled()) {
						mChatService.enableBt();
						Log.d("BluMote", "Turning on BT from connectPod()");
					} else {						
						reconnectPod(false);
						waitForConnection(false);
					}
				} catch (Exception e) {
					// do nothing
				}								
			}
		}
	}
	
	// called from connectPod() or after bluetooth is enabled, should
	// not be called from anywhere else, use connectPod() instead.
	void reconnectPod(boolean terminateBluetooth) {
		// Performing this check covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity
		// returns.
		if (mChatService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// started already
			if (getBluetoothState() == BluetoothChatService.STATE_NONE) {
				// Start the Bluetooth chat services
				try {
					mChatService.start();			
					Log.d("BluMote", "starting bt chat services from reconnectpod()");
				} catch (Exception e) {
					// do nothing
				}
			}
		} 			
		Log.d("BluMote", "checking if bluetooth enabled in reconnectPod()");
		try {
			// See if the bluetooth device is connected, if not try to connect
			if (mBluetoothAdapter.isEnabled()) {
				if ( (getBluetoothState() != BluetoothChatService.STATE_CONNECTING) &&
						(getBluetoothState() != BluetoothChatService.STATE_CONNECTED)) {
					// Get the BLuetoothDevice object
					if (connectingMAC != null) {
						BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(connectingMAC);
						// Attempt to connect to the device
						Log.d("BluMote", "attempting connection from reconnectPod()");
						mChatService.connect(device);
					}
				}
				else if (getBluetoothState() == BluetoothChatService.STATE_CONNECTING
						&& terminateBluetooth) {
					mChatService.disableBt();
					Log.d("BluMote", "disabling BT from reconnectPod()");					
				}
			} else {
				Log.d("BluMote", "error: bluetooth not enabled in reconnectPod()");
			}
		} catch (Exception e) {
			Log.d("BluMote", "Exception caught in reconnectPod!");
		}
	}

	private void waitForConnection(final boolean terminateConnection) {
		// Start a timer thread to ensure connection is established within a timeout
		// if not then toggle the BT connection and retry.
		new CountDownTimer(BluetoothChatService.BT_CONNECT_TIMEOUT, 1000) {

		     public void onTick(long millisUntilFinished) {
		         // called once a second, if connected then cancel		    	 
		    	 if (getBluetoothState() != BluetoothChatService.STATE_CONNECTING) {
		    		 this.cancel();
		    	 }
		     }

		     public void onFinish() {
		         if (getBluetoothState() != BluetoothChatService.STATE_CONNECTED) {
		        	 Log.d("BluMote", "waitForConnection() timed out - going to disable BT");
		        	 // close bluetooth connection
		        	 if (mChatService != null) {
		        		 if (terminateConnection) 
		        			 mChatService.disableBt();
		        		 else 
		        			 reconnectPod(false);
		        	 }
		         }
		     }
		  }.start();
	}
	
	@Override
	protected synchronized void onPause() {
		super.onPause();
		Log.v("BluMote_State", "In onPause");
		// unregister broadcast receiver for BT state change
//		this.unregisterReceiver(connectionListener);
		
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.v("BluMote_State", "In onStop");
		
		// close sqlite database connection
		device_data.close();
		
		setRunning(false);
		
		setAlarm(); 
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.v("BluMote_State", "In onDestroy");
		
		disconnectPod();
	}

	// this is called after resume from another full-screen activity
	@Override
	protected void onRestart() {
		super.onRestart();
		Log.v("BluMote_State", "In onRestart");
		
		device_data.open();				
	}
	
	private void setAlarm() {
		// 150 minutes = 9,000,000 ms
		// 120 minutes = 7,200,000 ms
	    // 60 minutes = 3600 seconds = 3,600,000 ms
	    // 30 minutes = 1,800,000 ms
		// 10 minutes = 600,000 ms
	    ////long timeOrLengthofWait = 600000;
	    //DEBUG 30 seconds = 30000;
	    long timeOrLengthofWait = 9000000;
	    
		// Check if user disabled this feature
		SharedPreferences myprefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean disconnectPref = myprefs.getBoolean("disconnectInactivity", true);			
		if (!disconnectPref) {
			Log.v("Blumote", "Alarm not enabled in prefs");
			return; // return if user disabled the disconnection timeout feature
		}
		
		if ( getTimerArmed() ) {
			Log.v("Blumote", "Alarm already armed..canceling");
			return; // don't re-arm if already armed.
		}
		
	    // Get a reference to the Alarm Manager
	    AlarmManager alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
	    
	    Log.v("BLUMOTE", "Setting Alarm");
	  
	    Intent intent = new Intent(BluMote.ACTION_ALARM_DISCONNECT);
	    alarmIntent = PendingIntent.getBroadcast(this, 0,intent, 0);
	  
	    alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
	        + timeOrLengthofWait, alarmIntent);
	    
	    setTimerArmed(true);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (gestureDetector != null) {
			return gestureDetector.onTouchEvent(event);
		} else 
			return false;
	}	
	
	/**
	 * Execute a button long press event.  This is called after a timer expires.
	 * The button will repeat for as long as the user keeps their finger on the button.
	 */
	protected void executeButtonLongDown() {				
		// This method entered if the long key press is triggered from countdown timer,
		// also if we re-launch the method after a short delay time which is done if 
		// the user is still holding the button down.
		if (isButtonPushed) { // check if user moved finger off button before firing button press
			// indicate to onClick() that we are in repeat key mode, prevents double click of final release
			BUTTON_LOOPING = true;
			// check if it is a navigational button
			// checkNavigation returns true if it is a navigation button
			if (mainScreen.isNavigationButton(lastView.getId())) {
				// execute navigation
				executeNavigationButton(lastView.getId());
			} else if (mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.ACTIVITY || 
					mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.MAIN) {				
				sendButton(lastView.getId());
				// start a new shorter timer that will call this method
				buttonPushID++; // increment global button push id
				new ButtonCountDown(DELAY_TIME, buttonPushID) {
					public void onFinish() {
						// called when timer expired
						if (getButtonID() == buttonPushID) {
							// if same push ID then execute this function again
							//executeButtonLongDown();
						}
					}
				}.start();
			}		
		} else {
			BUTTON_LOOPING = false;
			// send abort IR transmit command if button not being held any longer
			pod.abortTransmit();
		}
	}
	
	/**
	 * Execute the movement of a navigational button
	 * @param buttonID the button name
	 * @deprecated navigation buttons removed from interface so this function not used anymore
	 */
	public void executeNavigationButton(int buttonID) {
		String buttonName = mainScreen.button_map.get(buttonID);
		if (buttonName != null) {
			try {
				// see if we have a navigation page move command....
				if (buttonName == "move_left_btn") {
					mainScreen.moveLeft();
					return;
				}
				// check if the navigation move_right was pushed
				if (buttonName == "move_right_btn") {
					mainScreen.moveRight();
					return;
				}
			} catch (Exception e) {
				// do nothing				
			}
		}
		return;
	}	
	
	// interface implementation for buttons
	public void onClick(View v) {
		if (BLOCK_TRANSMIT == true) {
			BLOCK_TRANSMIT = false;
			return;
		}
		LAST_PUSHED_BUTTON_ID = v.getId(); // save Button ID - besides this function, also referenced in storeButton()
								// when a new button is learned	
		String buttonName = null;
		buttonName = mainScreen.button_map.get(LAST_PUSHED_BUTTON_ID); // convert ID to button name
		if (mainScreen.isNavigationButton(LAST_PUSHED_BUTTON_ID)) {
			return;  // navigation buttons don't need an onClick handler
		}
		if (mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.RENAME_STATE) {
			// store the button that we want to update if it is a valid misc key
			// if it isn't then exit and Toast user, change state back to idle
			if (buttonName.startsWith(MainInterface.BTN_MISC)) {
				// if compare works then we can go ahead and implement the rename
				misc_button = buttonName;
				// launch window to get new name to use
				Intent i = new Intent(BluMote.this, EnterName.class);
				startActivityForResult(i, MISC_RENAME);
			}
			else {
				Toast.makeText(BluMote.this, "Not a valid Misc button, canceling", 
						Toast.LENGTH_SHORT).show();
			}

			//verify if should set state to MAIN or to ACTIVITY based on dropdown
			if (mainScreen.getInterfaceType() == MainInterface.TYPE.ACTIVITY) {
				mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.ACTIVITY); // reset state in any case
			} else {
				mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.MAIN); // reset state in any case
			}
		} else if (mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.ACTIVITY_EDIT) {
			if (Activities.isValidActivityButton(LAST_PUSHED_BUTTON_ID)) {
				if (captureButton) {
					// if we are in this mode then what we want to do is to associate the button that was
					// pushed (from a device) with the original activity button

					// activityButton holds the original activity button we want to associate to
					//addActivityKeyBinding(String btnID, String device, String deviceBtn)
					activities.addActivityKeyBinding(activityButton, cur_device, mainScreen.button_map.get(LAST_PUSHED_BUTTON_ID));
					captureButton = false; 
					// make sure to jump back to original activity and then re-show the drop-down				
					mainScreen.setDropDown(activities.getWorkingActivity());					
					Toast.makeText(this, "Button associated with device",
							Toast.LENGTH_SHORT).show();
					
					mainScreen.setDropDownVis(false); // make sure drop-down stays hidden until completel finished
				}
				else {
					// else we want to associate a new button on the activity interface
					final CharSequence[] items = mainScreen.getDropDownDevices();

					AlertDialog.Builder builder = new AlertDialog.Builder(this);
					builder.setTitle("Pick an existing device");
					builder.setItems(items, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							// items[item] is the name of the item that was selected
							// need to set the drop-down to this and then hide the drop-down and switch to 
							// this device for the next step.  Also set the global flags for the button that
							// we are working on and the flag that indicates we are waiting for a keypress
							captureButton = true;
							activityButton = mainScreen.button_map.get(LAST_PUSHED_BUTTON_ID);		
							activities.setWorkingActivity(mainScreen.getCurrentDropDown());
							// switch to the selected device
							mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.MAIN); // setting drop-down only works in ACTIVITY/MAIN modes
							mainScreen.setDropDown(items[item].toString());
							mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.ACTIVITY_EDIT);
							mainScreen.fetchButtons();
							mainScreen.setDropDownVis(false);
						}
					});
					AlertDialog alert = builder.create();
					alert.show();
				}
			} else {
				Toast.makeText(this, "The power off activity button has no devices to turn off",
						Toast.LENGTH_SHORT).show();
			}
		} else if (mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.ACTIVITY_INIT) {
			// store init entries by device, button-id
			if (Activities.isValidActivityButton(LAST_PUSHED_BUTTON_ID)) {				
				activityInit.add(new String[]{cur_device, mainScreen.button_map.get(LAST_PUSHED_BUTTON_ID)});
				Toast.makeText(this, "Button press added to initialization list!",
						Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(this, "Invalid button!",	Toast.LENGTH_SHORT).show();
			}
		} else if (mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.LEARN) {
			pod.requestLearn();
			showDialog(DIALOG_LEARN_WAIT);
			
		} else { 
			if (getBluetoothState() == BluetoothChatService.STATE_CONNECTED) {				
				if (BUTTON_LOOPING == false) {
					sendButton(LAST_PUSHED_BUTTON_ID);
				}
			} else {
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
				.show();
			}
		}
	}			

	/**
	 * Returns setting of preference for HTC BT insecure mode
	 * @return
	 */
	protected static boolean getHtcInsecureSetting() {
		return htcInsecure;
	}
	
	/**
	 * Set the current button_map that contains all the data for the buttons on the interface 
	 * to a new map.
	 * @param map the new map to use
	 */
	protected void setButtonMap(HashMap<Integer, String> map) {
		mainScreen.button_map = map;
	}

	/**
	 * Ensure that the bluetooth device is discoverable, if it is not it requests it
	 */
	@SuppressWarnings("unused")
	private void ensureDiscoverable() {
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivity(discoverableIntent);
		}
	}

	

	/**
	 * The Handler that gets information back from other activities/classes
	 */
	@SuppressLint("HandlerLeak")
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				switch (msg.arg1) {
				case BluetoothChatService.STATE_CONNECTED:
					mTitle.setText(R.string.title_connected_to);
					if (mConnectedDeviceName != null) {
						mTitle.append(mConnectedDeviceName);
					}
					// Store the address of connecting device to preferences for
					// re-connect on resume
					// this global gets setup in onActivityResult()
					Editor mEditor = prefs.edit();
					mEditor.putString("lastPod", connectingMAC);
					
					// first need to pull current known devices list so we can
					// append to it
					if (connectingDevice != null) {
						String prefs_table = prefs.getString("knownDevices",null);

						// then pull name of device off and append
						if (prefs_table == null) {
							prefs_table = connectingDevice; // '\t' is the
															// delimeter between
															// items
						} else {
							// make sure isn't already in the list
							String devices[] = prefs_table.split("\t");
							boolean foundIt = false;
							for (String device : devices) {
								if (device.equals(connectingDevice)) {
									foundIt = true;
									break;
								}
							}
							if (foundIt == false) {
								prefs_table = prefs_table + "\t"
										+ connectingDevice; // '\t' is the
															// delimeter between
															// items
							}
						}
						mEditor.putString("knownDevices", prefs_table);
						// commit changes to the database
						mEditor.commit();
					}
					break;

				case BluetoothChatService.STATE_CONNECTING:
					mTitle.setText(R.string.title_connecting);
					break;

//				case BluetoothChatService.STATE_LISTEN:
				case BluetoothChatService.STATE_NONE:
					try {
						mTitle.setText(R.string.title_not_connected);
					} catch (Exception e) {
						// can throw exception if the message gets sent when app
						// is destroyed after system reclaims memory
						// do nothing
					}
					break;
				}
				break;

			case MESSAGE_WRITE:
				break;

			case MESSAGE_READ:
				// arg1 is the # of bytes read
				//byte[] readBuf = (byte[]) msg.obj;
				//pod.interpretResponse(readBuf, msg.arg1, msg.arg2);
				cache.storeBluetoothData((byte[])msg.obj, msg.arg1, msg.arg2);		
				Log.d("Blumote MESSAGE_READ #bytes", String.valueOf(msg.arg1));
				// process the data that was returned
				try {				
					pod.interpretResponse();
				} catch (Exception e) {
					Log.e("Blumote", "Error in interpretResponse()");
				}
				break;

			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				if (mConnectedDeviceName != null) {					
					// see if there is a user-defined name attached to this device name
					String translatedName = PodListActivity.translatePodName(
							mConnectedDeviceName, prefs);
					if (translatedName != null) {
						mConnectedDeviceName = translatedName;
					}
					
				}
				break;

			case MESSAGE_TOAST:
				Log.v("BluMote",msg.getData().getString(TOAST));
//				Toast.makeText(getApplicationContext(),
//						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
//						.show();
				break;			
			}
		}
	};	
	
	byte[] getDataReceived() {
		return cache.getData();
	}
	
	boolean hasData() {
		return cache.hasData();
	}
	
	void clearDataCache() {
		cache.clearBluetoothData();
	}
	
	// called when activities finish running and return to this activity,
	// this is called BEFORE the onResume() function
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// when returning from activity, make sure database is opened again
		device_data.open();
		
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			LOCK_RECONNECT = false;
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				connectingMAC = data.getExtras().getString(
						PodListActivity.EXTRA_DEVICE_ADDRESS);
				connectingDevice = data.getExtras().getString(
						PodListActivity.EXTRA_DEVICE_NAME);
				// the onResume() function will connect to the "lastPod" item,
				// it is called after this function completes.
			}
			break;

		case REQUEST_ENABLE_BT:		// When the request to enable Bluetooth returns
			// reset a flag indicating this request was sent out
			// this allows our 'timeout' code to initiate
			BluetoothChatService.BT_ENABLING = false;

			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled
				// Launch the DeviceListActivity to see devices and do scan
				// if autoconnect disabled in prefs, then launch activity to view pods
				SharedPreferences myprefs = PreferenceManager.getDefaultSharedPreferences(this);
				boolean autoConnect = myprefs.getBoolean("autoconnect", true);
				if (!autoConnect) {
					Intent serverIntent = new Intent(this, PodListActivity.class);
					startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
				}
			} else {
				// User did not enable Bluetooth or an error occured
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
			break;

		case REQUEST_MANAGE_DEVICE:
			// when the manage devices activity returns
			if (resultCode == Activity.RESULT_OK) {
				// refresh drop-down items				
				mainScreen.populateDropDown();
				mainScreen.fetchButtons();
				// refresh InterfaceLookup
				lookup.refreshLookup();
			}
			break;

		case ACTIVITY_ADD:
			if (resultCode == Activity.RESULT_OK) {
				// add the new item to the database
				Bundle return_bundle = data.getExtras();
				if (return_bundle != null) {
					String return_string = return_bundle.getString("returnStr");
					String button_config = return_bundle.getString(CreateActivity.BUTTON_CONFIG);
					int imageIndex = return_bundle.getInt(CreateActivity.IMAGE_ID);					
					// Add item to list
					activities.addActivity(return_string, imageIndex, button_config);		
				}				
			}
			break;
			
		case ACTIVITY_RENAME:
			if (resultCode == Activity.RESULT_OK) {
				Bundle return_bundle = data.getExtras();
				if (return_bundle != null) {
					String return_string = return_bundle.getString("returnStr");
					activities.renameActivity(return_string, activity_rename);					
				}				
			}
			break;
		
		case MISC_RENAME:
			if (resultCode == Activity.RESULT_OK) {
				Bundle return_bundle = data.getExtras();
				if (return_bundle != null) {
					String return_string = return_bundle.getString("returnStr");
					mainScreen.renameMisc(return_string, misc_button);
				}
			}
			break;
			
		case ACTIVITY_INIT_EDIT:
			if (resultCode == Activity.RESULT_OK) {
				Bundle return_bundle = data.getExtras();
				if (return_bundle != null) {
					String request = return_bundle.getString("returnStr");				
					if (request.equals(ActivityInitEdit.REDO)) {
						// check if the "REDO" was requested, if so re-enter ACTIVITY_INIT mode
						activityInit.clear();
						mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.ACTIVITY_INIT);
					}
					else if (request.equals(ActivityInitEdit.APPEND) ) {
						// append to end of existing init items
						activityInit.clear();						
						// get list of activity init items into local data structure
						// assumption here is setWorkingActivity was called prior 
						// to launching the intent that got us here
						ArrayList<String[]> newInitItems = 
							Activities.getActivityInitSequence(activities.getWorkingActivity(), prefs);		
						if (newInitItems != null) {
							for (int i=0; i< newInitItems.size(); i++) {
								activityInit.add(newInitItems.get(i));
							}
						}
						// enter ACTIVITY_INIT mode to begin adding more items
						mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.ACTIVITY_INIT);
					}
					// otherwise just go back into normal activity mode
				}								
			}
			break;
			
		case PREFERENCES:
			// refresh the haptic feedback pref
			SharedPreferences myprefs = PreferenceManager.getDefaultSharedPreferences(this);
			hapticFeedback = myprefs.getBoolean("hapticPREF", true);			
			break;			
			
		case UPDATE_FW:
			if (resultCode == Activity.RESULT_OK) {
				Bundle return_bundle = data.getExtras();
				if (return_bundle != null) {
					pod.FW_LOCATION = return_bundle.getString(FwUpdateActivity.FW_LOCATION);
					if (return_bundle.containsKey(FwUpdateActivity.ORIGINAL_FW_LOCATION) ) {
						pod.ORIGINAL_FW_LOCATION = 
								return_bundle.getString(FwUpdateActivity.ORIGINAL_FW_LOCATION);						
					} else {
						pod.ORIGINAL_FW_LOCATION = null;
					}	
					boolean podWorking = return_bundle.getBoolean(FwUpdateActivity.POD_WORKING);
					pod.setPodWorking(podWorking);
					
//					if (pod.ORIGINAL_FW_LOCATION == null && !podWorking) {
//						showDialog(WARN_BSL);
//					}
					if (podWorking) {					
						showDialog(DIALOG_WAIT_BSL);
						bsl = new EnterBSLTask();						
						bsl.execute(pod.ENABLE_RESET);
					} else {
						showDialog(DIALOG_RESET_POD);
					}
				} else {
					Toast.makeText(this, "Undetected error, please try again",
							Toast.LENGTH_SHORT).show();
				}
			}
			break;
		}				
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// save the menu so we can change it depending on context
		myMenu = menu;
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		return true;
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		// called everytime menu is shown
		menu.clear();
		
		if (mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.ACTIVITY_EDIT) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.activity_edit_menu, menu);
		}
		else if (mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.ACTIVITY_INIT) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.initialization_menu, menu);
		}
		else {	
			if (mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.ACTIVITY) {
				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.activities_menu, menu);
			} else { // use default menu (MAIN menu)
				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.options_menu, menu);
				if (pod.isLearnState() ) {
					// if we are currently in learn mode, then offer up the 'cancel learn' item
					menu.findItem(R.id.stop_learn).setVisible(true);
					menu.findItem(R.id.learn_mode).setVisible(false);
				} else {
					// else hide stop learn and show learn
					menu.findItem(R.id.stop_learn).setVisible(false);
					menu.findItem(R.id.learn_mode).setVisible(true);;															
				}
			}			
			if (getBluetoothState() == BluetoothChatService.STATE_CONNECTED) {
				menu.findItem(R.id.disconnect).setVisible(true);
				menu.findItem(R.id.scan).setVisible(false);
			} else {
				menu.findItem(R.id.disconnect).setVisible(false);
				menu.findItem(R.id.scan).setVisible(true);
			}	 
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i; 
		switch (item.getItemId()) {
		case R.id.scan:
			if (getBluetoothState() != BluetoothChatService.STATE_CONNECTED) {
				//disconnectPod();
				// ensure BT device is enabled, and block reconnects until we return
				try {
					if (!mBluetoothAdapter.isEnabled()) {
						LOCK_RECONNECT = true;
						mChatService.enableBt();
					}
				} catch (Exception e) {
					// intentionally blank
				}
			}
			// Launch the DeviceListActivity to see devices and do scan
			Intent serverIntent = new Intent(this, PodListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			return true;
		
		case R.id.disconnect:
			// disconnect the bluetooth link
			disconnectPod();
			stopLearning();
			return true;
			
		case R.id.about:
			showDialog(DIALOG_ABOUT);
			return true;
			
		case R.id.fw_update:
			// Make sure we are connected to pod before allowing this
			if (getBluetoothState() != BluetoothChatService.STATE_CONNECTED) {
				Toast.makeText(this, "You need to connect to a Pod first",
						Toast.LENGTH_SHORT).show();
				return true;
			}
			showDialog(DIALOG_FW_WAIT);			
			DownloadTextFileTask downloadFile = new DownloadTextFileTask();
			downloadFile.execute(FW_IMAGE_URL);
			return true;
			
		case R.id.preferences:
			// display the preferences
			Intent prefsIntent = new Intent(this,MyPreferences.class);
			startActivityForResult(prefsIntent, PREFERENCES);
			return true;
			
		case R.id.backup:
			// start the backup to the SD card	    
			ExportDatabaseFileTask backupStuff = new ExportDatabaseFileTask(this);
			backupStuff.execute("");
			// now backup prefs file
			exportPreferences();
			// now call the cloud backup
			mBackupManager.dataChanged();
			return true;

		case R.id.restore:
			// restores a backup from the SD card of databases and prefs file
			if (device_data.restore() && importPreferences() ) {
				Toast.makeText(this, "Successfully restored!", Toast.LENGTH_SHORT).show();
				// get preferences file
				prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE);

				// initialize the InterfaceLookup
				lookup = new InterfaceLookup(prefs);	
				
				// populate activities arraylist with initial items
				// need to pass in the arrayadapter we want to populate
				Activities.populateImageActivities(activities.mActivitiesArrayAdapter, prefs); 
			} else {
				Toast.makeText(this, "Import failed!", Toast.LENGTH_SHORT).show();
			}
			mainScreen.populateDropDown();
			mainScreen.fetchButtons();
			return true;
			
		/*	This function removed except for debug cases
		case R.id.discoverable:
			// Ensure this device is discoverable by others
			ensureDiscoverable();
			return true;
		*/
		
		// this is to manage the button configurations in database
		case R.id.manage_devices:
			// need to launch the manage devices view now
			i = new Intent(this, ManageDevices.class);
			startActivityForResult(i, REQUEST_MANAGE_DEVICE);
			return true;

		case R.id.get_info:
			pod.unlockDialog();
			pod.retrieveFwVersion();
			return true;
			
		case R.id.reset_pod:
			try {
				pod.resetRn42();
			} catch (Exception e) {
				Toast.makeText(this, "Reset failed!", Toast.LENGTH_SHORT).show();
			}
			// disconnect the bluetooth link
			disconnectPod();
			return true;

		case R.id.learn_mode:
			try {
				mainScreen.toplevel.setBackgroundResource(R.drawable.background_gry_scaled);
			} catch (Exception e) {
				Toast.makeText(this, "You need to select a device", Toast.LENGTH_SHORT).show();
				return true;
			}
			// need to make sure we are connected to a pod.
			if (getBluetoothState() != BluetoothChatService.STATE_CONNECTED) {
				Toast.makeText(this, "You need to be connected to a pod first", Toast.LENGTH_SHORT).show();
				return true;
			}			
			// need to make sure that user has a device selected in the drop down before
			// entering learn mode
			if (mainScreen.getCurrentDropDown() != null) {
				Toast.makeText(this, "Select button to train", Toast.LENGTH_SHORT).show();
				pod.setOperationalState(Pod.BT_STATES.LEARN);
				mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.LEARN);
				// disable drop-down
				mainScreen.setDropDownVis(false);
			} else {
				Toast.makeText(this, "You must have a device selected in the drop-down menu!", Toast.LENGTH_SHORT).show();
			}
			return true;

		case R.id.stop_learn:
			Toast.makeText(this, "Stopped Learning", Toast.LENGTH_SHORT).show();
			stopLearning();
			return true;
			
		case R.id.rename_misc:
			Toast.makeText(this, "Select Misc Button to rename", Toast.LENGTH_SHORT).show();
			mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.RENAME_STATE);
			return true;
			
		case R.id.activity_edit_init:
			// save menu item that was selected, if a "REDO" is requested
			// then need this information to set the currently selected activity
			activities.setWorkingActivity(mainScreen.getCurrentDropDown());
			startActivityEdit();
			return true;
			
		case R.id.activity_associate_btn:
			mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.ACTIVITY_EDIT);
			Toast.makeText(this, "Press a button to associate with a device...", Toast.LENGTH_SHORT).show();
			mainScreen.setDropDownVis(false);
			return true;
			
		case R.id.insert_delay:
			// launch a selector window to ask for how long of a delay
			// then store this result in the activityInit List
			// else we want to associate a new button on the activity interface
			showDialog(DIALOG_INIT_DELAY);
			return true;
			
		case R.id.end_init:
			mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.ACTIVITY_EDIT); // next step is associating buttons			
			// when ending the activity init then we should store the init sequence to the prefs file
			activities.addActivityInitSequence(activityInit);
			// after we add it then we need to clear out the activityInit
			activityInit.clear();
			Toast.makeText(this, "Now in activity button association mode", Toast.LENGTH_SHORT).show();
			// now put us back into the original activity for the drop-down
			mainScreen.setDropDown(activities.getWorkingActivity());
			// disable changing devices/activities while in this button association mode
			mainScreen.setDropDownVis(false);
			return true;
			
		case R.id.end_activity_edit:
			mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.ACTIVITY); // go to activity mode (to use new activity)			
			Toast.makeText(this, "Done editing activity", Toast.LENGTH_SHORT).show();
			// now put us back into the original activity for the drop-down
			mainScreen.setDropDown(activities.getWorkingActivity());
			mainScreen.setDropDownVis(true);
			// refresh data behind buttons
			mainScreen.fetchButtons();
			return true;
			
		case R.id.exit:
			// quit the application
			finish();
			return true;
			
		}

		return false;
	}

	void disconnectPod() {
		// disconnect the bluetooth link
		if (mChatService != null)
			try {
				mChatService.stop();
				mChatService = null; 
				if (DEBUG) {
					Toast.makeText(this, "Disconnected from pod",
							Toast.LENGTH_LONG).show();
				}
						
			} catch (Exception e) {
				Log.d(TAG, "Error closing bluetooth connection");
			}
		return;
	}
	
	void stopLearning() {
		pod.abortLearn();
		pod.setOperationalState(Pod.BT_STATES.ABORT_LEARN);
		mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.MAIN);
		mainScreen.setDropDownVis(true);
		pod.learn_state = Pod.LEARN_STATE.IDLE;
		// refresh buttons after done learning
		mainScreen.fetchButtons();
	}
	
	private String getDataDir() { 
		try { 
			PackageInfo packageInfo = 
				getPackageManager().getPackageInfo(getPackageName(), 0); 
			if (packageInfo == null) return null; 
			ApplicationInfo applicationInfo = 
				packageInfo.applicationInfo; 
			if (applicationInfo == null) return null; 
			if (applicationInfo.dataDir == null) return null; 
			return applicationInfo.dataDir; 
		} catch (NameNotFoundException ex) { 
			return null; 
		} 
	} 

	private boolean importPreferences() {   
        
		File sd = Environment.getExternalStorageDirectory();
		File currentDB = new File(getDataDir(),"/shared_prefs/"+PREFS_FILE+".xml");
        File backupDB = new File(sd, BluMote.PREFS_FILE+".bak");

        if (backupDB.exists()) {
        	try {
        		Util.FileUtils.copyFile(backupDB, currentDB);	       
        	} catch (IOException e) {
        		Log.e("IMPORT",e.getMessage(),e);
        		Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show();
        		return false;
        	}
	    }
	    return true;
	}
	
	private boolean exportPreferences() {
		// backup the preferences file for activities/etc
		File sd = Environment.getExternalStorageDirectory();
        File prefsFile = new File(getDataDir(),"/shared_prefs/"+PREFS_FILE+".xml");
        File backupDB = new File(sd,BluMote.PREFS_FILE+".bak");
        
        if (prefsFile.exists()) {
        	try {
            	backupDB.createNewFile();
            	Util.FileUtils.copyFile(prefsFile, backupDB);
            } catch (IOException e) {
            	Log.e("BACKUP",e.getMessage(),e);
            	Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
        		return false;  
            }
        }
        return true;
	}
	
	void startActivityEdit() {
		Intent i = new Intent(this, ActivityInitEdit.class);
		// tack on data to tell the activity what the "activities" item is
		i.putExtra(ActivityInitEdit.ACTIVITY_NAME, activities.getWorkingActivity());
		startActivityForResult(i, ACTIVITY_INIT_EDIT);
		
		// set selected drop-down item to the item being managed
		mainScreen.setDropDown(activities.getWorkingActivity());
	}
	
	/**
	 * OnItemSelectedListener interface definition
	 * called when user selects an item in the drop-down
	 * @param parent
	 * @param view the view of the arraylist
	 * @param pos the position in the arraylist that was selected
	 * @param id the resource-ID of the arraylist that was operated on
	 */
	public void onItemSelected(AdapterView<?> parent, View view, int pos,
			long id) {		
		mainScreen.setDropDown(mainScreen.getCurrentDropDown());
		LOCK_LAST_DEVICE = false; // release lock after initial spurious trigger
	}	

	/**
	 * Part of the OnItemSelectedListener interface, not used for this appliaction
	 */
	public void onNothingSelected(AdapterView<?> parent) {
		// Do nothing.
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case ID_DELETE:
			activities.deleteActivity((int)info.id);
			mainScreen.populateDropDown();
			mainScreen.fetchButtons();
			return true;
			
		case ID_RENAME:
			// store ID of the item to be renamed
			activity_rename = (int)info.id;
			//launch window to get new name to use
			Intent i = new Intent(this, EnterDevice.class);
			startActivityForResult(i, ACTIVITY_RENAME);			
			return true;
			
		case ID_MANAGE:					
			// save menu item that was selected, if a "REDO" is requested
			// then need this information to set the currently selected activity
			activities.setWorkingActivity((int)info.id);
			startActivityEdit();
			return true;
		}
		return super.onContextItemSelected(item);
	}

	// context menu is for activities list
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.activities_list) {
			// AdapterView.AdapterContextMenuInfo info =
			// (AdapterView.AdapterContextMenuInfo)menuInfo;
			menu.setHeaderTitle("Menu");
			menu.add(0, ID_DELETE, 0, "Delete Activity");
			menu.add(0, ID_RENAME, 0, "Rename Activity");
			menu.add(0, ID_MANAGE, 0, "Change Startup");
		}
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	/**
	 * The on-click listener for all devices in the activities ListViews
	 * @param av
	 * @param v The View object of the listview
	 * @param position the position that was clicked in the listview
	 * @param id the resource-id of the listview
	 */
	public void onItemClick(AdapterView<?> av, View v, int position, long id) {		
		
		// Change to activities state after init is run
		mainScreen.setInterfaceState(MainInterface.INTERFACE_STATES.ACTIVITY);

		// extract the name of activity that was selected
		ImageActivityItem item = activities.mActivitiesArrayAdapter.getItem(position);
		String activity = item.title;
		
		// set the working activity before using any activities functions
		activities.setWorkingActivity(activity);
		
		// begin executing init sequence
		activities.startActivityInitSequence(activity);
		
		activity = MainInterface.ACTIVITY_PREFIX + activity;
		mainScreen.setDropDown(activity); // set drop down to selected item
		
		// move screen 1 to the right
		mainScreen.moveRight();
		
	}

	/**
	 * This function sends the code to the pod based on the button 
	 * that was selected.
	 * @param buttonID The resource ID of the button that was pushed
	 */
	protected void sendButton(int buttonID) {		
		boolean foundIt = false;
		if (buttonID == R.id.power_off_btn) {
			// if this is an activity power off button, then treat differently
			ButtonData[] powerOff = activities.getPowerOffButtonData(mainScreen.getCurrentDropDown());
			// send all the data that we retrieved
			if (powerOff != null && powerOff.length > 0) {
				activities.sendPowerOffData(powerOff);
			}
		}
		else if (buttons != null && buttons.length > 0) {
			String buttonName = mainScreen.button_map.get(buttonID);
			if (buttonName != null) {
				for (int i=0; i < buttons.length && foundIt == false; i++) {
					if ( buttonName.equals(buttons[i].getButtonName()) ) {
						// then extract the button data out to be sent, check if data is non-null
						byte[] code = buttons[i].getButtonData();
						if (code != null) {
							foundIt = true;
							//TODO consider caching this repeat count on device selection
							int repeat = device_data.getRepeatCount(cur_device);
							pod.sendButtonCode(code, repeat);
						}
					}				 
				}
			}
			if (!foundIt) {
				Toast.makeText(this, "No IR code found!", Toast.LENGTH_SHORT).show();
			}
		} else {
			// if not looping let user know button not setup
			if (!BUTTON_LOOPING) {
				Toast.makeText(this, "Button not setup!", Toast.LENGTH_SHORT).show();
			}
		}		
	}

	/**
	 * called after learn mode is finished and has data to store
	 */
	protected void storeButton() {
		String buttonName = null;
		buttonName = mainScreen.button_map.get(LAST_PUSHED_BUTTON_ID);

		// make sure payload is not null and make sure we are in learn mode
		if (buttonName != null && mainScreen.INTERFACE_STATE == MainInterface.INTERFACE_STATES.LEARN) {
			device_data.insertButton(
					cur_device, 
					buttonName,
					pod.pod_data);
		}

		Toast.makeText(this, "Button Code Received", Toast.LENGTH_SHORT).show();
		mainScreen.fetchButtons();
		// presumably we are in learn mode so need to refresh view of which buttons
		// have codes and which don't (colorizing)
		mainScreen.setButtonBackground(false, LAST_PUSHED_BUTTON_ID);
	}	

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case DIALOG_SHOW_INFO:
			AlertDialog alertdialog = (AlertDialog)dialog;
			StringBuilder podData;			
			// define dialog			
			podData = new StringBuilder();
			byte[] rev = Pod.getFwVersion();
			if (rev != null) {
				podData.append(pod.componentMap.get(Integer.valueOf(rev[0])));
				podData.append(" Rev: ");
				podData.append(rev[1] + ".");
				podData.append(rev[2] + ".");
				podData.append(rev[3]);
			} else {
				podData.append("ERROR RECEIVING POD VERSION");
			}
			alertdialog.setMessage(podData);
			break;
		}
		super.onPrepareDialog(id, dialog);		
	}
	
	@Override
	protected Dialog onCreateDialog(int id) {
		super.onCreateDialog(id);
		AlertDialog alert = null;
		AlertDialog.Builder builder;
				
		switch (id) {
		case DIALOG_SHOW_INFO:			
			builder = new AlertDialog.Builder(this);
			alert = builder.create();
			alert.setTitle("Pod revision");	
			alert.setMessage("ERROR RECEIVING POD VERSION");
			return alert;
			
		case DIALOG_INIT_DELAY:
			// create a custom alertdialog using our xml interface for it				
			LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.dialog_init_delay,
			                               (ViewGroup) findViewById(R.id.dialog_init_root));
			final EditText text = (EditText) layout.findViewById(R.id.enter_init_dly);
			builder = new AlertDialog.Builder(this);
			builder.setView(layout);
			builder.setTitle("Enter Delay")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   try {
			        		   if (Integer.parseInt(text.getText().toString()) > 0) {
			        			   activityInit.add(new String[]{
			        					   "DELAY", ""+Integer.parseInt(text.getText().toString()) } );
			        			   Toast.makeText(BluMote.this, "Delay added to initialization list!",
			        						Toast.LENGTH_SHORT).show();
			        		   }
			        	   }
			        	   catch (NumberFormatException e) {
			        		   // do nothing, just quit
			        		   Toast.makeText(BluMote.this, "Invalid number, ignoring...",
			       					Toast.LENGTH_SHORT).show();
			        	   }
			        	   dialog.dismiss();
			           }
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			    });
			alert = builder.create();
			return alert;
		
		case DIALOG_INIT_PROGRESS:
			// should create a new progressdialog 
			// the dialog should exit after all the initItems are processed
			ProgressDialog progressDialog = new ProgressDialog(BluMote.this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setCancelable(false); // don't allow back button to cancel it
            progressDialog.setMessage("Sending commands, please wait...");
            return progressDialog;
            
		case DIALOG_LEARN_WAIT:
			// should create a new progressdialog 
			// the dialog should exit after all the initItems are processed
			ProgressDialog learnWait = new ProgressDialog(BluMote.this);
			learnWait.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			learnWait.setCancelable(true); // don't allow back button to cancel it
			learnWait.setMessage("Aim the remote control at the pod and push the selected button");
            return learnWait;
			
		case DIALOG_FW_WAIT:
			// should create a new progressdialog 
			// the dialog should exit after the FW image list is downloaded
			ProgressDialog fwListWait = new ProgressDialog(BluMote.this);
			fwListWait.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			fwListWait.setCancelable(true); // allow back button to cancel it
			fwListWait.setMessage("Downloading list of firmware images...");
            return fwListWait;
            
		case DIALOG_ABOUT:
			// define dialog			
			StringBuilder aboutDialog = new StringBuilder();
			aboutDialog.append(getString(R.string.about_license));
			aboutDialog.append("\nSW Revision: ");
			String versionName = "";
			try {
				versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;				
			} catch (NameNotFoundException e) {
				e.printStackTrace();				
			}
			aboutDialog.append(versionName + "\n");			
			builder = new AlertDialog.Builder(this);
			// .setCancelable(false)
			builder.setMessage(aboutDialog).setTitle("About BluMote");
			alert = builder.create();
			return alert;
			
		case FLASH_PROGRESS_DIALOG:	          
			progressDialog2 = new ProgressDialog(BluMote.this);
            progressDialog2.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog2.setCancelable(false); // don't allow back button to cancel it
            progressDialog2.setMessage("Loading...");
            //progressDialog2.setProgressNumberFormat(null);
            return progressDialog2;
            
		case DIALOG_WAIT_BSL:
			// should create a new progressdialog 
			ProgressDialog bslWait = new ProgressDialog(BluMote.this);
			bslWait.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			bslWait.setCancelable(false); // allow back button to cancel it
			bslWait.setMessage("Resetting pod...this may take a minute\n" +
					"If it takes more than a minute remove and reconnect power to the pod");
			bslWait.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int which) {
			        try {
			        	bsl.cancel(true);
			        } catch (Exception e) {
			        	// give up
			        }
			        dialog.dismiss();
			    }
			});
            return bslWait;
            			
		case DIALOG_RESET_POD:
			builder = new AlertDialog.Builder(this);
			builder.setMessage("Automatic reset failed.  Please remove power to the pod then " +
					"re-attach power and press OK.  Press CANCEL to stop the FW update utility.")
			       .setCancelable(false)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			    	   public void onClick(DialogInterface dialog, int id) {
			    		   dialog.cancel();
			    		   // launch another instance of EnterBslTask			    		   
			    		   bsl = new EnterBSLTask();
			    		   bsl.execute(pod.INHIBIT_RESET);
			    		   showDialog(DIALOG_WAIT_BSL);
			           }
			       })
			       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   try {
			    			   dismissDialog(DIALOG_WAIT_BSL);
			    		   } catch (Exception e) {
			    			   // guess it wasn't open
			    		   }
			               dialog.cancel();
			           }
			       });
			alert = builder.create();
			return alert;
//			
//		case WARN_BSL:
//			builder = new AlertDialog.Builder(this);
//			builder.setMessage("Warning!  The pod is not responding and the original firmware on the pod could not be located. " +
//					"This can lead to loss of function to the pod.  It is recommended that the golden image be used if the pod becomes " +
//					"unresponsive after the FW update. \n" +
//					"Press OK to quit and try again (with the golden image), press cancel if you want to continue anyways.")
//			       .setCancelable(false)
//			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
//			    	   public void onClick(DialogInterface dialog, int id) {	
//			    		   dismissDialog(WARN_BSL);
//			    		   dialog.cancel();			    		   
//			           }
//			       })
//			       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//			           public void onClick(DialogInterface dialog, int id) {
//			                dialog.cancel();
//			                showDialog(DIALOG_RESET_POD);
//			           }
//			       });
//			alert = builder.create();
//			return alert;
            
		default:
			return alert;
		}
	}
	
	/**
	 * This class will allow Fling events to be captured and processed, if a Fling event is captured
	 * the the appropriate movement function is called to perform that action.
	 * @author keusej
	 *
	 */
	class MyGestureDetector extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            try {
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
                    return false;
                // right to left swipe
                if(e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    mainScreen.moveRight();
                }  else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                	mainScreen.moveLeft();
                }
            } catch (Exception e) {
                // nothing
            }
            return false;
        }

    }
	
	/**
	 * CountDownTimer extended to support a local ButtonID variable and unused onTick()
	 * @author keusej
	 *
	 */
	abstract class ButtonCountDown extends CountDownTimer{
		int buttonID = 0;
		/**
		 * Public constructor
		 * @param millisInFuture
		 * @param buttonID
		 */
        public ButtonCountDown(long millisInFuture, int buttonID) {
            super(millisInFuture, millisInFuture); 	// instantiate super with onTick and onFinish with
            										// same delay
            this.buttonID = buttonID;
            }
        
        int getButtonID() {
        	return buttonID;
        }
        
        @Override
		public abstract void onFinish();

        @Override
        public void onTick(long millisUntilFinished) {
            // this method unused
        }
    }

	/**
	 * Download a file from the website which is used to determine the history
	 * of firmware revisions.
	 * @author keusej
	 *
	 */
	private class DownloadTextFileTask extends AsyncTask<String, Integer, String[]> {
		protected String[] doInBackground(String... urls) {
			try {
				URL url = new URL(urls[0]);
				BufferedReader bufferReader = new BufferedReader(
						new InputStreamReader(url.openStream()));
				String StringBuffer;
				ArrayList<String> stringText = new ArrayList<String>();
				while ((StringBuffer = bufferReader.readLine()) != null) {
					stringText.add(StringBuffer);
				}             
				bufferReader.close();

				String[] returnStrings = new String[stringText.size()];
				return stringText.toArray(returnStrings);
			} catch (Exception e) {
				Log.w("test", "failed: "+e.getMessage());
			}
			return null;
		}

		protected void onPostExecute(String[] result) {	
			dismissDialog(DIALOG_FW_WAIT);
			
			// save the data we downloaded for the firmware process
			pod.setFirmwareRevision(result);
			// get currently installed pod version
			pod.setFwVersion(null); // clear it out first
			pod.lockDialog(); // don't display pop-up
			// time-out if we don't receive it
			pod.retrieveFwVersion();	
			waitForGetVersion(1000, 0); // 1 sec max wait time						
			return;
		}
	}

	/**
	 * Recursive function to implement a time-out waiting for data
	 * to arrive from the BT device.
	 * @param maxWait
	 * @param current
	 */
	private void waitForGetVersion(int maxWait, int current) {
		final int maxWaitTime = maxWait;
		final int currentWait = current;
		
		if ( currentWait < maxWaitTime && Pod.getFwVersion() == null) {			
			new CountDownTimer(10, 10) {
				public void onTick(long millisUntilFinished) {
					// no need to use this function
				}
				public void onFinish() {
					// called when timer expired
					// max recursions are maxWait / 10 
					waitForGetVersion(maxWaitTime, currentWait + 10);
				}
			}.start();
		} else {
			if (currentWait >= maxWaitTime) {
				// we timed out.
				Toast.makeText(this, "Warning: Unable to get version of pod FW",
						Toast.LENGTH_SHORT).show();
			} 
			Intent i = new Intent(BluMote.this, FwUpdateActivity.class);							
			startActivityForResult(i, BluMote.UPDATE_FW);
		}
	}		

	 /**
	  * Facilitates implementing a wait time-out feature
	  * usually used in conjunction with a CountDownTimer class.
	  * Gets around Java not being able to talk to anything but Final
	  * fields from inside an inner class thread
	  * @author keusej
	  *
	  */
	 public static class Wait {
		 public final int maxWaitTime;
		 public int waitTime = 0;
		 public boolean unlocked = true;

		 public Wait(int maxWait) {
			 maxWaitTime = maxWait;
		 }		
	 }

	 // part 1 of BSL routine, need to reset rn42 and kick off BSL entry sequence, then
	 // call the FlashFileToPod task
	 private class EnterBSLTask extends AsyncTask<Integer, Integer, String> {
			protected String doInBackground(Integer... flag) {
				try {											
					pod.getCalibrationData();								

					pod.setOperationalState(Pod.BT_STATES.BSL);
		 			
					if (flag[0] == pod.INHIBIT_RESET) {
						pod.startBSL(pod.INHIBIT_RESET);
					} else {						
						pod.startBSL(pod.ENABLE_RESET);						
					}		 	
					while (pod.BSL_FINISHED == false) { 
						// wait 
					}					
				} catch (BslException e) {
					if (e.getTag() == BslException.RESET_FAILED) {
						return "FAILED";
					} else return "PASSED";
				}
				return "PASSED";
			}

			protected void onPostExecute(String result) {	
				// check return code
				if (result.matches("FAILED")) {
					showDialog(DIALOG_RESET_POD);
				} else {
					// need to change visual indicator screen
					dismissDialog(DIALOG_WAIT_BSL);
					showDialog(FLASH_PROGRESS_DIALOG);
					FlashFileToPodTask flasher = new FlashFileToPodTask();
					flasher.execute((Void)null);
				}
				return;
			}
		}
	 
	 /**
	  * AsyncTask to flash the file to the pod
	  * @author keusej
	  *
	  */
	 private class FlashFileToPodTask extends AsyncTask<Void, Integer, Integer> {
		 	Exception e = null;
		 	int byteCounter = 0;   
		 	int count = 0;
		 	
		 	@Override 
		 	protected Integer doInBackground(Void... voids) {
		 		try {				
		 			publishProgress(0); // start at 0															 			
		 			
		 			FileInputStream f = new FileInputStream(new File(pod.FW_LOCATION));
		 			BufferedReader br = new BufferedReader(new InputStreamReader(f));
		 			String strLine;	

		 			// count # of lines in the file (used for progress bar)
		 			while (br.readLine() != null) count++;

		 			// set progress style
					progressDialog2.setMax(count);
		 			
		 			f.getChannel().position(0); // reset to beginning of file
		 			br = new BufferedReader(new InputStreamReader(f));					

		 			//Read File Line By Line
		 			int lineCounter = 0;
		 			while ((strLine = br.readLine()) != null)   {		 					
		 				try { 
		 					pod.sendFwLine(strLine);		 				
		 				} catch (BslException e) {
		 					// retry once more
		 					pod.sendFwLine(strLine);		 					
		 				}
		 				lineCounter++;
		 				publishProgress(lineCounter);
		 			}
		 			f.close();
		 			br.close();
		 			
		 			// Enter cmd mode and instruct Pod to exit BSL and reset FW
		 			try {
			 			pod.sendBSLString("$$$");
			 			pod.receiveResponse(1);
			 			pod.exitBsl();
		 			} catch (Exception exception) {
		 				// do nothing if BSL fails here
		 			}		 				 			
		 		} catch (Exception e) {		 			
		 			this.e = e;
		 		}
				return Integer.valueOf(byteCounter);
			}

			@Override
			protected void onProgressUpdate(Integer... progress) {
				// update progress bar
				progressDialog2.setProgress(progress[0]);
			}

			@Override
			protected void onPostExecute(Integer bytes) {
				dismissDialog(FLASH_PROGRESS_DIALOG);
				// when finished reset bluetooth state
				pod.setOperationalState(Pod.BT_STATES.IDLE);
				
				// tell user it was not successful
				if (e != null) {
					//Log.e("BSL", e.getMessage());
					new AlertDialog.Builder(BluMote.this)
			        	.setIcon(android.R.drawable.ic_dialog_info)
				        .setMessage(R.string.error_fw_download)
				        .setPositiveButton(R.string.OK, null)
				        .show();
					return;
				}
								 
				// Tell user it was successful
				new AlertDialog.Builder(BluMote.this)
		        	.setIcon(android.R.drawable.ic_dialog_info)
			        .setTitle(R.string.success)
			        .setMessage(R.string.fw_installed)
			        .setPositiveButton(R.string.OK, null)
			        .show();	
				return;
			}
		}

	 // Was using this to listen for loss of bluetooth and then restart the bluetooth and
	 // attempt a reconnect.  The reconnectPod() method needs to be passed 'true' 
	 // to enable this behavior.  
	 public class ConnectivityListener extends BroadcastReceiver {
		 
		@Override
		public void onReceive(Context context, Intent intent) {
			
			String action = intent.getAction();
			Log.v("BLUMOTE", "Received broadcast intent");
			if(action.equalsIgnoreCase(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				/**
				 * Depracated functionality
				 * NOTE - if we decide to re-enable this receiver then we will need to be careful
				 * that unregisterReceiver() is not called on the same object that is used to 
				 * catch the Alarm disconnect (or it will stop both from working when app is minimized)
				 */
				int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);

				if (LOCK_RECONNECT) {
					return;
				}
				if (state == BluetoothAdapter.STATE_OFF) {
					connectPod();
				} else if (state == BluetoothAdapter.STATE_ON) {
					if (mChatService == null || mChatService.getState() != BluetoothChatService.STATE_CONNECTED)
						reconnectPod(false);
				}
			}
			// disconnect if receive alarm and the screen is off or app is not running
			if(action.equalsIgnoreCase(BluMote.ACTION_ALARM_DISCONNECT) && 
					( !((ScreenReceiver)screenReceiver).screenOn ) || !getRunning() ) {
				Log.v("BLUMOTE", "Received Action Alarm Disconnect");
				disconnectPod();

			}
		}		 
	 }
	 
	 public class ScreenReceiver extends BroadcastReceiver {

		    private boolean screenOn = true;
		   
		    @Override
		    public void onReceive(final Context context, final Intent intent) {
		        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
		            // do whatever you need to do here
		            screenOn = false;
		            Log.d("BluMote", "Screen off, enabling alarm");
		            setAlarm();
		        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
		            // and do whatever you need to do here
		            screenOn = true;
		        }
		    }

		}
}