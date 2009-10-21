package vcard.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Contacts;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class ContactGroupChooser extends Activity {
	private static final int DIALOG_NEWGROUP = 1;
	private static final int DIALOG_DELGROUP = 2;
	private static final int DIALOG_MESSAGE = 3;
    private static final String GROUP_ID_QUERY = Contacts.Groups.NAME + "=?";
    private static final String GROUP_NAME_QUERY = Contacts.Groups._ID + "=?";
    
    
    public static long getGroupId(ContentResolver cResolver, String groupName) {
    	Cursor cur = cResolver.query(Contacts.Groups.CONTENT_URI, null, GROUP_ID_QUERY, new String[] { groupName }, null);
    	long groupId = -1;
		if (cur != null) {
			if (cur.moveToFirst()) {
				groupId = cur.getLong(cur.getColumnIndex(Contacts.Groups._ID));
			}
			cur.close();
		}
		return groupId;
    }
    
    public static String getGroupName(ContentResolver cResolver, String groupId) {
    	Cursor cur = cResolver.query(Contacts.Groups.CONTENT_URI, null, GROUP_NAME_QUERY, new String[] { groupId }, null);
    	String groupName = null;
		if (cur != null) {
			if (cur.moveToFirst()) {
				groupName = cur.getString(cur.getColumnIndex(Contacts.Groups.NAME));
			}
			cur.close();
		}
		return groupName;
    }
	
	public static String[] getSelectedGroupIds(Context context) {
		final String PREF_CONTACT_GROUP = context.getResources().getString(R.string.PREF_CONTACT_GROUP);
	    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
	    String contactGroupStr = settings.getString(PREF_CONTACT_GROUP, "");
	    if (contactGroupStr.length() == 0) {
	    	// Default group is "My Contacts"
	    	contactGroupStr = Long.toString(getGroupId(context.getContentResolver(), Contacts.Groups.GROUP_MY_CONTACTS));
	    }
    
	    String[] contactGroups = TextUtils.split(contactGroupStr, ",");
	    return contactGroups;
	}
	
	public static void getSelectedGroupNames(ArrayList<String> groupNames, Context context) {
		groupNames.clear();
		String[] groupIds = getSelectedGroupIds(context);
		
		ContentResolver cResolver = context.getContentResolver();
		for (String id : groupIds) {
			String name = getGroupName(cResolver, id);
			if (name != null)
				groupNames.add(name);
		}
	}

	
	private ListView mGroupChooser;
	private CursorAdapter mContactsAdapter;
    private Cursor mContactGroupCursor;
	//private ArrayList<String> mContactGroups;

	protected void addContactGroup(String groupName) {
		ContentResolver cRes = this.getContentResolver();
		ContentValues cv = new ContentValues();
		
		cv.put(Contacts.GroupsColumns.NAME, groupName);
		
		Uri newGroup = cRes.insert(Contacts.Groups.CONTENT_URI, cv);
		if (newGroup != null) {
			mContactsAdapter.notifyDataSetChanged();
		}
	}

	
	private final static String[] GROUPS_NAMEID_PROJECTION = new String[] {
	        Contacts.Groups._ID, Contacts.Groups.NAME
	 };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        setContentView(R.layout.contact_group_chooser);
        
    	mGroupChooser = ((ListView) findViewById(R.id.ChooseGroup));

    	Context context = getApplicationContext();
    	
    	mContactGroupCursor = managedQuery(Contacts.Groups.CONTENT_URI, GROUPS_NAMEID_PROJECTION, null, null, null);
    	mContactsAdapter = new SimpleCursorAdapter(context, android.R.layout.simple_list_item_multiple_choice, 
    			mContactGroupCursor, new String[] {Contacts.Groups.NAME}, new int[] { android.R.id.text1 });

    	mGroupChooser.setAdapter(mContactsAdapter);
    	mGroupChooser.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
	}

	
	
	private void loadSelectedGroups(Context context) {
		// Do an n^2 search since the number of groups ids is small.
		String[] groupIds = getSelectedGroupIds(context);
		List<String> groupIdList = Arrays.asList(groupIds);
		
		int count = mContactsAdapter.getCount();
		for (int i = 0; i < count; ++i) {
			long id = mContactsAdapter.getItemId(i);
			if (groupIdList.contains(Long.toString(id))) {
				mGroupChooser.setItemChecked(i, true);
			} else {
				mGroupChooser.setItemChecked(i, false);
			}
		}
	}
	
	private void storeSelectedGroups(Context context) {
		SparseBooleanArray checked = mGroupChooser.getCheckedItemPositions();
		int count = checked.size();
		
		ArrayList<String> groupIds = new ArrayList<String>();
		
		for (int i = 0; i < count; ++i) {
			if (checked.valueAt(i)) {
				long id = mContactsAdapter.getItemId(checked.keyAt(i));
				groupIds.add(Long.toString(id));
			}
		}
		
		String PREF_CONTACT_GROUP = context.getResources().getString(R.string.PREF_CONTACT_GROUP);
	    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
	    SharedPreferences.Editor editor = settings.edit();
	    
	    editor.putString(PREF_CONTACT_GROUP, TextUtils.join(",",groupIds));
	    
	    editor.commit();
	}
	
	@Override
	protected void onPause() {
		storeSelectedGroups(getApplicationContext());
		super.onPause();
	}


	@Override
	protected void onResume() {
		super.onResume();

        // Restore preferences
		loadSelectedGroups(getApplicationContext());
	}	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.contactgroup_options, menu);

	    return true;
	}

	/* Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.addgroup:
			showDialog(DIALOG_NEWGROUP);
	        return true;
	    case R.id.delgroup:
	    	showDialog(DIALOG_DELGROUP);
	        return true;
	    }
	    
	    return false;
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
	
	/**
	 * Title to show in message dialog
	 */
	private String mShowTitle;
	
	/**
	 * Message to show in message dialog
	 */
	private String mShowMessage;
	
	
	
	protected Dialog onCreateDialog(int dialogId) {
	    final Dialog dialog;
	    switch(dialogId) {
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
	    case DIALOG_DELGROUP:
	    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	builder.setTitle("Delete an Empty Group");
	    	
	    	
	    	class GroupParams {
	    		String id;
	    		String name;
	    		int membersCount;
	    		
	    		public String toString() {
	    			return name + " (" + membersCount + ")";
	    		}
	    	};
	    	
	    	final ArrayAdapter<GroupParams> adapter = new ArrayAdapter<GroupParams>(getApplicationContext(), android.R.layout.select_dialog_item);
	    	Cursor contactGroups =  getContentResolver().query(Contacts.Groups.CONTENT_URI, null, null, null, null);
	    	if (contactGroups != null) {
	    		int idCol = contactGroups.getColumnIndex(Contacts.Groups._ID);
	    		int nameCol = contactGroups.getColumnIndex(Contacts.Groups.NAME);
	    		while(contactGroups.moveToNext()) {
	    			GroupParams params = new GroupParams();

	    			params.id = contactGroups.getString(idCol);
	    			params.name = contactGroups.getString(nameCol);
	    			params.membersCount = 0;
	    			
	    			Cursor groupMembers = getContentResolver().query(Contacts.GroupMembership.CONTENT_URI, null, Contacts.GroupMembership.GROUP_ID + "=?",
	    	    			new String[] { params.id }, null);
	    	    	if (groupMembers != null) { 
	    	    		params.membersCount = groupMembers.getCount();
	    	    		groupMembers.close();
	    	    	}
	    	    	adapter.add(params);
	    		}
	    		contactGroups.close();
	    	}

	    	builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
	    	    public void onClick(DialogInterface dialog, int item) {
	    	    	GroupParams param = adapter.getItem(item);
	    	    	if (param.membersCount > 0) {
	    	    		dialog.dismiss();
	    	    		mShowTitle = "Can't delete";
	    	    		mShowMessage = "Group has " + param.membersCount + " members";
	    	    		showDialog(DIALOG_MESSAGE);
	    	    	} else {
	    	    		ContentResolver cResolver = getContentResolver();
	    	    		cResolver.delete(Contacts.Groups.CONTENT_URI, Contacts.Groups._ID + "=?", new String[] { param.id });
	    	    		adapter.remove(param);
	    	    		dialog.dismiss();
	    	    	}
	    	    }
	    	});
	    	
	    	dialog = builder.create();
	    	break;
	    case DIALOG_MESSAGE:
	    	builder = new AlertDialog.Builder(this);
	    	builder.setTitle(mShowTitle);
	    	builder.setMessage(mShowMessage);
	    	builder.setNeutralButton("Ok", null);
	    	dialog = builder.create();
	    	break;
	    default:
	        dialog = null;
	    }
	    return dialog;
	}
}
