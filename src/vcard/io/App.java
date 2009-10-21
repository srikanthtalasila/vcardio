package vcard.io;

import java.util.Arrays;
import java.util.List;

import org.openintents.intents.FileManagerIntents;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class App extends PreferenceActivity {

	final public static int REQUEST_CODE_CHOOSE_IMPORT = 1;
	final public static int REQUEST_CODE_CHOOSE_EXPORT = 2;

	final public static int DIALOG_NOFILEMANAGER = 1;
	final public static int DIALOG_CONFIRM_OVERWRITE = 2;

	public String PREF_EXPORT_FILE;
	public String PREF_IMPORT_FILE;
	public String DEFAULT_IMPORT_FILE;
	public String DEFAULT_EXPORT_FILE;

	public String PREF_REPLACE;
	public String PREF_EXPORT_ALLGROUPS;

	/*
	 * public String PREF_MONITOR_SMS;
	 * 
	 * public String PREF_CONTACT_GROUP;
	 */
	private void loadResourceStrings() {
		PREF_EXPORT_FILE = getResources().getString(R.string.PREF_EXPORT_FILE);
		PREF_IMPORT_FILE = getResources().getString(R.string.PREF_IMPORT_FILE);
		DEFAULT_IMPORT_FILE = getResources().getString(
				R.string.DEFAULT_IMPORT_FILE);
		DEFAULT_EXPORT_FILE = getResources().getString(
				R.string.DEFAULT_EXPORT_FILE);

		PREF_EXPORT_ALLGROUPS = getResources().getString(
				R.string.PREF_EXPORT_ALLGROUPS);
		PREF_REPLACE = getResources().getString(R.string.PREF_REPLACE);
		/*
		 * PREF_MONITOR_SMS =
		 * getResources().getString(R.string.PREF_MONITOR_SMS);
		 * 
		 * PREF_CONTACT_GROUP =
		 * getResources().getString(R.string.PREF_CONTACT_GROUP);
		 */
	}

	/** Called when the activity is first created. */
	boolean isActive;

	Handler mHandler = new Handler();
	VCardIO mBoundService = null;

	int mLastProgress;
	TextView mStatusText = null;

	@Override
	protected void onPause() {
		isActive = false;

		savePrefs(getApplicationContext());
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();

		isActive = true;

		// Restore preferences
		loadPrefs(getApplicationContext());

		updateProgress(mLastProgress);
	}

	protected void savePrefs(Context context) {
		// Save user preferences. We need an Editor object to
		// make changes. All objects are from android.context.Context
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString(PREF_IMPORT_FILE,
						((EditText) findViewById(R.id.ImportFile)).getText()
								.toString());
		editor.putString(PREF_EXPORT_FILE,
						((EditText) findViewById(R.id.ExportFile)).getText()
								.toString());

		// Don't forget to commit your edits!!!
		editor.commit();
	}

	protected void loadPrefs(Context context) {
		// Save user preferences. We need an Editor object to
		// make changes. All objects are from android.context.Context
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(context);

		((EditText) findViewById(R.id.ImportFile)).setText(settings.getString(
				PREF_IMPORT_FILE, DEFAULT_IMPORT_FILE));
		((EditText) findViewById(R.id.ExportFile)).setText(settings.getString(
				PREF_EXPORT_FILE, DEFAULT_EXPORT_FILE));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_options, menu);

		return true;
	}

	/* Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.settings:
			Intent intent = new Intent();
			intent.setClassName(Settings.class.getPackage().getName(),
					Settings.class.getName());
			startActivity(intent);
			return true;
		}
		return false;
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
	
	void startImport() {
		// Make sure the service is started. It will continue running
		// until someone calls stopService(). The Intent we use to find
		// the service explicitly specifies our service component,
		// because
		// we want it running in our own process and don't want other
		// applications to replace it.

		if (mBoundService != null) {
			String fileName = ((EditText) findViewById(R.id.ImportFile))
					.getText().toString();
			// Update the progress bar
			setProgress(0);
			mStatusText.setText("Importing Contacts...");

			boolean replaceOnImport = PreferenceManager
					.getDefaultSharedPreferences(
							getApplicationContext()).getBoolean(
							PREF_REPLACE, false);
			// Start the import
			mBoundService.doImport(
							fileName,
							Arrays
									.asList(ContactGroupChooser
											.getSelectedGroupIds(getApplicationContext())),
							replaceOnImport, this);
		}
	}
	
	void startExport(boolean forceOverwrite) {
		// Make sure the service is started. It will continue running
		// until someone calls stopService(). The Intent we use to find
		// the service explicitly specifies our service component,
		// because
		// we want it running in our own process and don't want other
		// applications to replace it.

		if (mBoundService != null) {
			String fileName = ((EditText) findViewById(R.id.ExportFile))
					.getText().toString();
			
			if (!forceOverwrite) {
				java.io.File exportFile = new java.io.File(fileName);
				
				if (exportFile.exists()) {
					showDialog(DIALOG_CONFIRM_OVERWRITE);
					return;
				}
			}
			
			// Update the progress bar
			setProgress(0);
			mStatusText.setText("Exporting Contacts...");

			// Start the export
			List<String> selectedGroups;
			boolean exportAllGroups = PreferenceManager
					.getDefaultSharedPreferences(
							getApplicationContext()).getBoolean(
							PREF_EXPORT_ALLGROUPS, false);
			if (exportAllGroups)
				selectedGroups = null;
			else
				selectedGroups = Arrays.asList(ContactGroupChooser
						.getSelectedGroupIds(getApplicationContext()));

			mBoundService.doExport(fileName, selectedGroups, this);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		loadResourceStrings();

		// Request the progress bar to be shown in the title
		requestWindowFeature(Window.FEATURE_PROGRESS);

		super.onCreate(savedInstanceState);

		setProgress(10000); // Turn it off for now

		setContentView(R.layout.main);

		Button importButton = (Button) findViewById(R.id.ImportButton);
		Button exportButton = (Button) findViewById(R.id.ExportButton);

		mStatusText = ((TextView) findViewById(R.id.StatusText));

		final Intent app = new Intent(App.this, VCardIO.class);

		// Start the service using startService so it won't be stopped when
		// activity is in background.
		startService(app);
		bindService(app, mConnection, Context.BIND_AUTO_CREATE);
		importButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startImport();
			}
		});
		exportButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				startExport(false);
			}
		});

		Button chooseImport = (Button) findViewById(R.id.chooseImportButton);

		chooseImport.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String fileName = ((EditText) findViewById(R.id.ImportFile))
						.getText().toString();
				Intent intent = new Intent(FileManagerIntents.ACTION_PICK_FILE);

				// Construct URI from file name.
				intent.setData(Uri.parse("file://" + fileName));

				// Set fancy title and button (optional)
				intent.putExtra(FileManagerIntents.EXTRA_TITLE,
						getString(R.string.choose_import_title));
				intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT,
						getString(R.string.choose_import_file));

				try {
					startActivityForResult(intent, REQUEST_CODE_CHOOSE_IMPORT);
				} catch (ActivityNotFoundException e) {
					// No compatible file manager was found.
					showDialog(DIALOG_NOFILEMANAGER);
				}
			}
		});

		Button chooseExport = (Button) findViewById(R.id.chooseExportButton);
		chooseExport.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				String fileName = ((EditText) findViewById(R.id.ExportFile))
						.getText().toString();
				Intent intent = new Intent(FileManagerIntents.ACTION_PICK_FILE);

				// Construct URI from file name.
				intent.setData(Uri.parse("file://" + fileName));

				// Set fancy title and button (optional)
				intent.putExtra(FileManagerIntents.EXTRA_TITLE,
						getString(R.string.choose_export_title));
				intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT,
						getString(R.string.choose_export_file));

				try {
					startActivityForResult(intent, REQUEST_CODE_CHOOSE_EXPORT);
				} catch (ActivityNotFoundException e) {
					// No compatible file manager was found.
					showDialog(DIALOG_NOFILEMANAGER);
				}
			}
		});

		addPreferencesFromResource(R.xml.settings);

	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		AlertDialog.Builder builder;
		
		switch(id) {
		case DIALOG_NOFILEMANAGER:
	    	builder = new AlertDialog.Builder(this);
	    	builder.setTitle(R.string.error);
	    	builder.setMessage(R.string.no_file_manager_found);
	    	builder.setNegativeButton("Cancel", null);
	    	builder.setPositiveButton("Install", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Uri marketUri = Uri.parse(getString(R.string.filemanager_market_uri));
					Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
					try {
						startActivity(marketIntent);
					} catch (ActivityNotFoundException e) {
						Toast.makeText(App.this, "Market not installed", Toast.LENGTH_SHORT).show();
					}
					dialog.dismiss();
				}
			});
	    	
	    	dialog = builder.create();
			break;
		case DIALOG_CONFIRM_OVERWRITE:
	    	builder = new AlertDialog.Builder(this);
	    	builder.setTitle(R.string.confirm_overwrite_title);
	    	String fileName = ((EditText) findViewById(R.id.ExportFile))
				.getText().toString();
	    	builder.setMessage(getString(R.string.confirm_overwrite_message) + " " + fileName);
	    	builder.setNegativeButton("Cancel", null);
	    	builder.setPositiveButton("Overwrite", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					startExport(true);
				}
			});
	    	dialog = builder.create();
	    	break;
		default:
			dialog = super.onCreateDialog(id);				
		}
		
		return dialog;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == RESULT_OK && data != null) {
			String filename = data.getDataString();
			if (filename != null) {
				// Get rid of URI prefix:
				if (filename.startsWith("file://")) {
					filename = filename.substring(7);
				}
			}

			EditText fileNameField = null;
			switch (requestCode) {
			case REQUEST_CODE_CHOOSE_IMPORT:
				fileNameField = ((EditText) findViewById(R.id.ImportFile));
				break;
			case REQUEST_CODE_CHOOSE_EXPORT:
				fileNameField = ((EditText) findViewById(R.id.ExportFile));
				break;
			}

			fileNameField.setText(filename);
			savePrefs(this);
		}
	}

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. Because we have bound to a explicit
			// service that we know is running in our own process, we can
			// cast its IBinder to a concrete class and directly access it.
			mBoundService = ((VCardIO.LocalBinder) service).getService();

			// Tell the user about this for our demo.
			Toast.makeText(App.this, "Connected to VCard IO Service",
					Toast.LENGTH_SHORT).show();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
			mBoundService = null;
			Toast.makeText(App.this, "Disconnected from VCard IO!",
					Toast.LENGTH_SHORT).show();
		}
	};

}
