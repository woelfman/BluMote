// Copyright (C) 2011 Woelfware

package com.woelfware.blumote;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

public class EnterDevice extends Activity {
	private EditText entered_device;
	String device_string;
	private Button closeButton;
	private Spinner layoutSpinner;
	
	static final String BUTTON_CONFIG = "BUTTON_CONFIG";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.enter_device);
		entered_device = (EditText) findViewById(R.id.device_name);

		entered_device.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				// register the text when "enter" is pressed
				if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
						(keyCode == KeyEvent.KEYCODE_ENTER)) {
					// not yet implemented
					return true;
				}
				return false;
			}
		});
		
		layoutSpinner = (Spinner) findViewById(R.id.layout_spinner);
		// populate drop-down with the list of button configs defined in MainInterface
	    String[] layouts = new String[MainInterface.ACTIVITY_LAYOUTS.values().length];
	    int index = 0;
	    for (MainInterface.ACTIVITY_LAYOUTS item : MainInterface.ACTIVITY_LAYOUTS.values() ) {
	    	layouts[index++] = item.getValue();
	    }
		ArrayAdapter<String> mAdapter = new ArrayAdapter<String>(this, R.layout.spinner_entry, layouts);
		mAdapter.setDropDownViewResource(R.layout.spinner_entry);
		layoutSpinner.setAdapter(mAdapter);

		closeButton = (Button)findViewById(R.id.enter_button);
		closeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// grab the text for use in the activity
				device_string = entered_device.getText().toString();
				// remove invalid/unwanted characters.....
				device_string = removeBannedChars(device_string);
				String buttonLayout = (String)layoutSpinner.getSelectedItem();
				Intent i = getIntent();
				i.putExtra("returnStr", device_string);
				i.putExtra(BUTTON_CONFIG, buttonLayout);
				setResult(RESULT_OK,i);
				finish();	
			}
		});

		Intent i = getIntent();
		setResult(RESULT_OK,i);
	} // end of oncreate
	
	@Override
	protected void onStart() {		
		super.onStart();
    	DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        double inches = Math.sqrt((metrics.widthPixels * metrics.widthPixels) + (metrics.heightPixels * metrics.heightPixels)) / metrics.densityDpi;
        Resources res = getResources();
        int TABLET_SIZE = res.getInteger(R.integer.tablet_size);
        if (inches > TABLET_SIZE) {
        	this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
        	this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }
	
	private String removeBannedChars(String device_string) {
		String[] invalidList = {"\\+", "," , "DELAY"};
		
		String returnString = device_string;
		for (int i=0; i< invalidList.length; i++) {
			// replace invalid strings with a underscore
			returnString = returnString.replaceAll(invalidList[i], "_");
		}
		return returnString;
	}
}
