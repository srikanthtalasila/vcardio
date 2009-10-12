package vcard.io;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.gsm.SmsMessage;
import android.util.Log;

/**
 * The class is called when SMS is received.
 * 
 */
public class SMSReceiver extends BroadcastReceiver {
	
	public static final String ACTION_VCARD_SMS = "vcard.io.SMS";
	public static final String EXTRA_VCARD = "vcard";
		
	private static final int VCARD_RECEIVED_ID = 1;
	

    /**
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
    	Log.v("test", "receive");
    	SharedPreferences settings = context.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE);
        boolean monitor = settings.getBoolean(App.PREF_MONITOR_SMS, false);
    	if(monitor){
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
	        		notify(context, message.getOriginatingAddress(), body);
	        	}
	        }
    	}
    }
    

    
    private void notify(final Context context, final String address, final String vcard) {
    	String ns = Context.NOTIFICATION_SERVICE;
    	NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(ns);
    	int icon = R.drawable.icon;
    	
    	CharSequence tickerText = "New contact received";
    	CharSequence contentTitle = "New contact received";
    	CharSequence contentText = "VCARD received from " + address;
    	
    	long when = System.currentTimeMillis();

    	Notification notification = new Notification(icon, tickerText, when);
    	notification.flags |= Notification.FLAG_AUTO_CANCEL;
    	
    	Intent notificationIntent = new Intent(context, VCardAdder.class);
    	notificationIntent.putExtra(EXTRA_VCARD, vcard);
    	notificationIntent.setAction(ACTION_VCARD_SMS);
    	PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);

    	notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
    	mNotificationManager.notify(VCARD_RECEIVED_ID, notification);
    	// TODO increment the id each time or update the existing notification?
    	
    }
    
    private boolean isVCard(final String content){
    	return content.startsWith("BEGIN:VCARD");
    }
    
    
}
