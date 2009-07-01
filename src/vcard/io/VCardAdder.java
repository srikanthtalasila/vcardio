package vcard.io;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class VCardAdder extends Activity{

	private TextView textView;
	
	@Override
	protected void onResume() {
		super.onResume();
		
		Intent intent = getIntent();
        if(intent != null){
        	if("vcard.io.SMS".equals(intent.getAction())){
        		String card = intent.getExtras().getString("vcard");
        		textView.setText(card);
        		Toast.makeText(this, card, Toast.LENGTH_SHORT).show();
        	}else{
        		Toast.makeText(this, "Unexpected intent: " + intent, Toast.LENGTH_LONG).show();
        	}
        }
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.adder);
        textView = ((TextView) findViewById(R.id.TextView01));

    }
}
