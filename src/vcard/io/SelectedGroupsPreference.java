package vcard.io;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.AttributeSet;

public class SelectedGroupsPreference extends Preference {
	
	ArrayList<String> mSelectedGroupNames = new ArrayList<String>();
	String mSelectedContacts;

	
	protected void setup(final Context context) {
	    final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

		ContactGroupChooser.getSelectedGroupNames(mSelectedGroupNames, context);
	    mSelectedContacts = TextUtils.join(",", mSelectedGroupNames);
	    
	    settings.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
			
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					String key) {
				ContactGroupChooser.getSelectedGroupNames(mSelectedGroupNames, context);
			    mSelectedContacts = TextUtils.join(",", mSelectedGroupNames);
				
				SelectedGroupsPreference.this.notifyChanged();
			}
		});
	}
	
	public SelectedGroupsPreference(Context context) {
		super(context);
		setup(context);
	}

    public SelectedGroupsPreference(Context context, AttributeSet attrs){ 
    	super(context, attrs);
		setup(context);
    }

	@Override
	public CharSequence getSummary() {
		return mSelectedContacts;
	}

	@Override
	protected void onClick() {
		Context context = getContext();
		
		Intent intent = new Intent();
		intent.setClassName(ContactGroupChooser.class.getPackage().getName(), ContactGroupChooser.class.getName());
		context.startActivity(intent);
	}
}
