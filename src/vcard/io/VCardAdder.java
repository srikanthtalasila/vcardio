package vcard.io;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import vcard.io.VCardIO.DatabaseHelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class VCardAdder extends Activity {
	
	private static final String TAG = VCardAdder.class.getSimpleName();
	
	private TextView textView;
	

    private VCardIO.DatabaseHelper mOpenHelper;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.adder);
        textView = ((TextView) findViewById(R.id.TextView01));
        mOpenHelper = new DatabaseHelper(getApplicationContext());
    }
	
	@Override
	protected void onResume() {
		super.onResume();
		Intent intent = getIntent();
        if(intent != null){

        	SharedPreferences settings = getApplicationContext().getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE);

            String contactGroupStr = settings.getString(App.PREF_CONTACT_GROUP, Contacts.Groups.GROUP_MY_CONTACTS);
            String[] importGroupArr = TextUtils.split(contactGroupStr, ",");
            List<String> importGroups = Arrays.asList(importGroupArr);
            
        	if(SMSReceiver.ACTION_VCARD_SMS.equals(intent.getAction())) {
        		String vcard = intent.getStringExtra(SMSReceiver.EXTRA_VCARD);	
        		textView.setText(vcard);
        		askUserForImport(vcard, importGroups);
        	} else if(Intent.ACTION_VIEW.equals(intent.getAction())) {
        		//data=file:///sdcard/download/addressBookExport-1.vcf
        		Uri uri = intent.getData();
        		File vcfFile = new File(uri.getPath());
        		try{
	    			String vcard = FileUtil.readFile(vcfFile);
	    			textView.setText(vcard);
	        		askUserForImport(vcard, importGroups);
        		} catch (IOException e) {
        			Log.e(TAG, e.getMessage(), e);
        		}
        	}else{
        		Toast.makeText(this, "Unexpected intent: " + intent, Toast.LENGTH_LONG).show();
        	}
        }
	}
	
	private void askUserForImport(final String vcard, final List<String> contactGroups){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Do you wish to import this contact (to groups " + TextUtils.join(",", contactGroups) + ")?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   Uri person = importCard(VCardAdder.this, vcard, contactGroups);
		        	   Toast.makeText(VCardAdder.this, "Contact added.", Toast.LENGTH_SHORT).show();
		        	   showContact(person);
		        	   VCardAdder.this.finish();
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   VCardAdder.this.finish();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	private void showContact(Uri person){
		Intent intent = new Intent(Intent.ACTION_VIEW);
 		intent.setData(person);
 		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
 		startActivity(intent);
	}
    
    private Uri importCard(final Context context, final String vcard, final List<String> contactGroups) {
    	SQLiteDatabase db = mOpenHelper.getWritableDatabase();
    	Contact contact = new Contact(vcard, VCardIO.getStatements(db));
    	long row = contact.addContact(context, 0, contactGroups, false);
    	Uri myPerson = ContentUris.withAppendedId(People.CONTENT_URI, row);
    	return myPerson;
    }    
}
