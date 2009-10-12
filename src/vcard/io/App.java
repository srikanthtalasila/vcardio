package vcard.io;

import java.util.ArrayList;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Contacts;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TextView.OnEditorActionListener;

public class App extends Activity {
	
	public static final String PREFS_NAME = "VCardIOPrefsFile";
	public static final String PREF_MONITOR_SMS = "monitorsms";
	public static final String PREF_REPLACE = "replace";
	public static final String PREF_CONTACT_GROUP = "contactgroup";
	public static final String PREF_EXPORT_ALLGROUPS = "exportall";
	public static final String PREF_EXPORT_FILE = "exportfile";
	public static final String PREF_IMPORT_FILE = "importfile";
	public static final String DEFAULT_IMPORT_FILE = "/sdcard/contacts.vcf";
	public static final String DEFAULT_EXPORT_FILE = "/sdcard/backup.vcf";
	private static final int DIALOG_NEWGROUP = 1; 
	
    /** Called when the activity is first created. */
	boolean isActive; 
	
	Handler mHandler = new Handler();
	VCardIO mBoundService = null;
	
	int mLastProgress;
	TextView mStatusText = null;
	
	private CheckBox mReplaceOnImport = null;
	private CheckBox mReceiveSMS;
	
	private CheckBox mExportAllGroups;
	private ListView mGroupChooser;
	private ArrayAdapter<String> mListAdapter;
	private ArrayList<String> mContactGroups;

	@Override
	protected void onPause() {
		isActive = false; 

		savePrefs();
		super.onPause();
	}


	@Override
	protected void onResume() {
		super.onResume();
		
		isActive = true;

        // Restore preferences
        loadPrefs();
        
		updateProgress(mLastProgress);
	}	
	
	ArrayList<String> getSelectedGroups() {
		SparseBooleanArray selected = mGroupChooser.getCheckedItemPositions();
		ArrayList<String> groups = new ArrayList<String>(selected.size());
		
		for (int i = 0; i < selected.size(); ++i) {
			int key = selected.keyAt(i);
			if (selected.get(key)) {
				groups.add(mContactGroups.get(key));
			}
		}
		
		return groups;
	}
	
	protected void savePrefs() {
	    // Save user preferences. We need an Editor object to
	    // make changes. All objects are from android.context.Context
	    SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
	    SharedPreferences.Editor editor = settings.edit();
	    editor.putString(PREF_IMPORT_FILE, ((EditText) findViewById(R.id.ImportFile)).getText().toString());
	    editor.putString(PREF_EXPORT_FILE, ((EditText) findViewById(R.id.ExportFile)).getText().toString());

	    editor.putBoolean(PREF_MONITOR_SMS, mReceiveSMS.isChecked());
	    editor.putBoolean(PREF_REPLACE, mReplaceOnImport.isChecked());
	    editor.putBoolean(PREF_EXPORT_ALLGROUPS, mExportAllGroups.isChecked());
	    editor.putString(PREF_CONTACT_GROUP, TextUtils.join(",",getSelectedGroups()));

	    // Don't forget to commit your edits!!!
	    editor.commit();
	}
	
	protected void loadPrefs() {
	    // Save user preferences. We need an Editor object to
	    // make changes. All objects are from android.context.Context
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ((EditText) findViewById(R.id.ImportFile)).setText(settings.getString(PREF_IMPORT_FILE, DEFAULT_IMPORT_FILE));
        ((EditText) findViewById(R.id.ExportFile)).setText(settings.getString(PREF_EXPORT_FILE, DEFAULT_EXPORT_FILE));
        mReceiveSMS.setChecked(settings.getBoolean(PREF_MONITOR_SMS, false));
        mReplaceOnImport.setChecked(settings.getBoolean(PREF_REPLACE, false));
        mExportAllGroups.setChecked(settings.getBoolean(PREF_EXPORT_ALLGROUPS, false));
        
        String contactGroupStr = settings.getString(PREF_CONTACT_GROUP, Contacts.Groups.GROUP_MY_CONTACTS);
        String[] contactGroups = TextUtils.split(contactGroupStr, ",");
        
        for (String group : contactGroups) {
	        int groupPos = mContactGroups.indexOf(group); 
	        if (groupPos >= 0)
	        	mGroupChooser.setItemChecked(groupPos, true);
        }
	}
	
	@Override
	protected void onStop() {
		super.onStop();
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

	
	public ArrayList<String> getContactGroups() {
		ArrayList<String> groups = new ArrayList<String>();
		
		ContentResolver cRes = this.getContentResolver();
		Cursor cur = cRes.query(Contacts.Groups.CONTENT_URI, null, null, null, null);
		
		final int groupNameCol = cur.getColumnIndex(Contacts.GroupsColumns.NAME); 
		
		if (cur.moveToFirst()) {
			do {
				groups.add(cur.getString(groupNameCol));
			} while (cur.moveToNext());
		}
		cur.close();
		return groups;
	}

	protected void addContactGroup(String groupName) {
		ContentResolver cRes = this.getContentResolver();
		ContentValues cv = new ContentValues();
		
		cv.put(Contacts.GroupsColumns.NAME, groupName);
		
		Uri newGroup = cRes.insert(Contacts.Groups.CONTENT_URI, cv);
		if (newGroup != null) {
			mContactGroups.add(mContactGroups.size() - 1, groupName);
			mListAdapter.notifyDataSetChanged();
		}
	}
	
	protected Dialog onCreateDialog(int id) {
	    final Dialog dialog;
	    switch(id) {
	    case DIALOG_NEWGROUP:
	    	dialog = new Dialog(this);

	    	dialog.setContentView(R.layout.edit_dialog);
	    	dialog.setTitle("New Group");
	    	
	    	final EditText editor = ((EditText) dialog.findViewById(R.id.EditText));
	    	final Button okButton = ((Button) dialog.findViewById(R.id.DialogOk));
	    	final Button cancelButton = ((Button) dialog.findViewById(R.id.DialogCancel));
	    	
	    	editor.setOnEditorActionListener(new OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					addContactGroup(editor.getText().toString());
	    			dialog.dismiss();
					return true;
				}
			});
	    	
	    	okButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					addContactGroup(editor.getText().toString());
	    			dialog.dismiss();
				}
			});
	    	
	    	cancelButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});
	    	
	        break;
	    default:
	        dialog = null;
	    }
	    return dialog;
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
    	mExportAllGroups = ((CheckBox) findViewById(R.id.AllGroups));
    	mGroupChooser = ((ListView) findViewById(R.id.ChooseGroup));
    	
    	// Fill the spinner with the currently existing contact groups
    	mContactGroups = getContactGroups();
    	mContactGroups.add("Create New...");
    	
    	mListAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_multiple_choice, mContactGroups);
    	mGroupChooser.setAdapter(mListAdapter);
    	mGroupChooser.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    	mGroupChooser.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				if (position == mContactGroups.size() - 1) {
					// "Create New..." was selected
					mGroupChooser.setItemChecked(position, false);
					showDialog(DIALOG_NEWGROUP);
				}
			}
		});

    	
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
    	            mBoundService.doImport(fileName, getSelectedGroups(), mReplaceOnImport.isChecked(), App.this);
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
    	            
    	            // Start the export
    	            ArrayList<String> selectedGroups;
    	            if (mExportAllGroups.isChecked())
    	            	selectedGroups = null;
    	            else
    	            	selectedGroups = getSelectedGroups();
    	            
    	            mBoundService.doExport(fileName, selectedGroups, App.this);
    			}
    		}
    	};
    	
    	// Start the service using startService so it won't be stopped when activity is in background.
    	startService(app);
        bindService(app, mConnection, Context.BIND_AUTO_CREATE);
        importButton.setOnClickListener(listenImport);
        exportButton.setOnClickListener(listenExport);
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