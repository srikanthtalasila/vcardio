package vcard.io;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts.People;
import android.widget.TextView;
import android.widget.Toast;

public class VCardAdder extends Activity{

	private TextView textView;
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Intent intent = getIntent();
        if(intent != null){
        	if(SMSReceiver.VCARD_ACTION.equals(intent.getAction())){
        		final String vcard = intent.getExtras().getString(SMSReceiver.VCARD_EXTRA);
        		textView.setText(vcard);
        		askUserForImport(vcard);
        	}else{
        		Toast.makeText(this, "Unexpected intent: " + intent, Toast.LENGTH_LONG).show();
        	}
        }
	}
	
	private void askUserForImport(final String vcard){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Do you wish to import this contact?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   Uri person = importCard(VCardAdder.this, vcard);
		        	   Toast.makeText(VCardAdder.this, "Contact added.", Toast.LENGTH_SHORT).show();
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
	
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.adder);
        textView = ((TextView) findViewById(R.id.TextView01));

    }
    
    private Uri importCard(final Context context, final String vcard) {
    	Contact contact = new Contact(vcard, null, null, null);
    	long row = contact.addContact(context, 0, false);
    	Uri myPerson = ContentUris.withAppendedId(People.CONTENT_URI, row);
    	return myPerson;
    }    
}
