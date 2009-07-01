package vcard.io;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts.People;
import android.telephony.gsm.SmsMessage;

/**
 * The class is called when SMS is received.
 * 
 */
public class SMSReceiver extends BroadcastReceiver {

    /**
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();

        Object messages[] = (Object[]) bundle.get("pdus");
        SmsMessage smsMessage[] = new SmsMessage[messages.length];
        for (int n = 0; n < messages.length; n++) {
            smsMessage[n] = SmsMessage.createFromPdu((byte[]) messages[n]);
        }

        String body;
        for(SmsMessage message:smsMessage){
        	body = message.getMessageBody();
        	if(isVCard(body)){
        		Uri person = readCard(context, body);
        		
        		notify(context, "Adding contact data received from " + message.getOriginatingAddress(), person.toString(), body);
        	}
        }
    }
    
	private static final int VCARD_RECEIVED_ID = 1;
    
    private void notify(final Context context, final String title, final String text, final String vcard){
    	String ns = Context.NOTIFICATION_SERVICE;
    	NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(ns);
    	int icon = R.drawable.icon;
    	
    	CharSequence tickerText = "New contact received";
    	CharSequence contentTitle = title;
    	CharSequence contentText = text;
    	
    	long when = System.currentTimeMillis();

    	Notification notification = new Notification(icon, tickerText, when);
    	notification.flags |= Notification.FLAG_AUTO_CANCEL;
    	
    	
    	Intent notificationIntent = new Intent(context, VCardAdder.class);
    	notificationIntent.putExtra("vcard", vcard);
    	notificationIntent.setAction("vcard.io.SMS");
    	PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

    	notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    	mNotificationManager.notify(VCARD_RECEIVED_ID, notification);
    }
    
    private boolean isVCard(final String content){
    	return content.startsWith("BEGIN:VCARD");
    }
    
    private Uri readCard(final Context context, final String vcard) {
    	Contact contact = new Contact(vcard, null, null, null);
    	long row = contact.addContact(context, 0, false);
    	Uri myPerson = ContentUris.withAppendedId(People.CONTENT_URI, row);
    	return myPerson;
    }    
}
