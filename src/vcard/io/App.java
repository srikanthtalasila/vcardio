package vcard.io;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class App extends Activity {
	
	public static final String PREFS_NAME = "VCardIOPrefsFile";
	public static final String MONITOR_SMS_PREF = "monitorsms";
	
    /** Called when the activity is first created. */
	boolean isActive; 
	
	Handler mHandler = new Handler();
	VCardIO mBoundService = null;
	
	int mLastProgress;
	TextView mStatusText = null;
	
	private CheckBox mReplaceOnImport = null;
	private CheckBox mReceiveSMS;

	@Override
	protected void onPause() {
		isActive = false; 

		super.onPause();
	}


	@Override
	protected void onResume() {
		super.onResume();
		
		isActive = true;
		updateProgress(mLastProgress);
	}	
	
	@Override
	protected void onStop() {
		super.onStop();
	    
	    // Save user preferences. We need an Editor object to
	    // make changes. All objects are from android.context.Context
	    SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
	    SharedPreferences.Editor editor = settings.edit();
	    editor.putBoolean(MONITOR_SMS_PREF, mReceiveSMS.isChecked());

	    // Don't forget to commit your edits!!!
	    editor.commit();

	}
		
	protected void updateProgress(final int progress) {
		// Update the progress bar
		mHandler.post(new Runnable() {
			public void run() {
				if (isActive) {
					setProgress(progress * 100);
					if (progress == 100)
						mStatusText.setText("Done");
				} else {
					mLastProgress = progress;
				}
			}
		});
	}

	void updateStatus(final String status) {
		// Update the progress bar
		mHandler.post(new Runnable() {
			public void run() {
				if (isActive) {
					mStatusText.setText(status);
				}
			}
		});
	}

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request the progress bar to be shown in the title
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setProgress(10000); // Turn it off for now
        
        setContentView(R.layout.main);

        Button importButton = (Button) findViewById(R.id.ImportButton);
        Button exportButton = (Button) findViewById(R.id.ExportButton);

    	mStatusText = ((TextView) findViewById(R.id.StatusText));
    	mReplaceOnImport = ((CheckBox) findViewById(R.id.ReplaceOnImport));
    	mReceiveSMS = ((CheckBox) findViewById(R.id.ReceiveSMS));
    	
    	final Intent app = new Intent(App.this, VCardIO.class);
        OnClickListener listenImport = new OnClickListener() {
    		public void onClick(View v) { 
    	        // Make sure the service is started.  It will continue running
    	        // until someone calls stopService().  The Intent we use to find
    	        // the service explicitly specifies our service component, because
    	        // we want it running in our own process and don't want other
    	        // applications to replace it.
    			
    			if (mBoundService != null) {
    				String fileName = ((EditText) findViewById(R.id.ImportFile)).getText().toString();
    			    // Update the progress bar
    				setProgress(0);
    	            mStatusText.setText("Importing Contacts...");
    	            
    	            // Start the import
    	            mBoundService.doImport(fileName, mReplaceOnImport.isChecked(), App.this);
    			}
    		}
    	};
    	
        OnClickListener listenExport = new OnClickListener() {
    		public void onClick(View v) { 
    	        // Make sure the service is started.  It will continue running
    	        // until someone calls stopService().  The Intent we use to find
    	        // the service explicitly specifies our service component, because
    	        // we want it running in our own process and don't want other
    	        // applications to replace it.
    			
    			if (mBoundService != null) {
    				String fileName = ((EditText) findViewById(R.id.ExportFile)).getText().toString();
    			    // Update the progress bar
    				setProgress(0);
    	            mStatusText.setText("Exporting Contacts...");
    	            
    	            // Start the import
    	            mBoundService.doExport(fileName, App.this);
    			}
    		}
    	};
    	
    	// Start the service using startService so it won't be stopped when activity is in background.
    	startService(app);
        bindService(app, mConnection, Context.BIND_AUTO_CREATE);
        importButton.setOnClickListener(listenImport);
        exportButton.setOnClickListener(listenExport);
        
        // Restore preferences
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean monitor = settings.getBoolean(MONITOR_SMS_PREF, false);
        mReceiveSMS.setChecked(monitor);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
        	mBoundService = ((VCardIO.LocalBinder)service).getService();

        	// Tell the user about this for our demo.
            Toast.makeText(App.this, "Connected to VCard IO Service", Toast.LENGTH_SHORT).show();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;
            Toast.makeText(App.this, "Disconnected from VCard IO!", Toast.LENGTH_SHORT).show();
        }
    };
    

}