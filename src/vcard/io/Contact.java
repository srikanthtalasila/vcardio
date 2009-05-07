/*
 * Funambol is a mobile platform developed by Funambol, Inc. 
 * Copyright (C) 2003 - 2007 Funambol, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License version 3 as published by
 * the Free Software Foundation with the addition of the following permission 
 * added to Section 15 as permitted in Section 7(a): FOR ANY PART OF THE COVERED
 * WORK IN WHICH THE COPYRIGHT IS OWNED BY FUNAMBOL, FUNAMBOL DISCLAIMS THE 
 * WARRANTY OF NON INFRINGEMENT  OF THIRD PARTY RIGHTS.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Affero General Public License 
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA.
 * 
 * You can contact Funambol, Inc. headquarters at 643 Bair Island Road, Suite 
 * 305, Redwood City, CA 94063, USA, or at email address info@funambol.com.
 * 
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License version 3.
 * 
 * In accordance with Section 7(b) of the GNU Affero General Public License
 * version 3, these Appropriate Legal Notices must retain the display of the
 * "Powered by Funambol" logo. If the display of the logo is not reasonably 
 * feasible for technical reasons, the Appropriate Legal Notices must display
 * the words "Powered by Funambol". 
 * 
 * 2009-02-25 ducktayp: Modified to parse and format more Vcard fields, including PHOTO.
 * 						No attempt was made to preserve compatibility with previous code.
 *    					
 */

package vcard.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.Contacts;

import com.funambol.util.Log;
import com.funambol.util.QuotedPrintable;
import com.funambol.util.StringUtil;

/**
 * A Contact item
 */
public class Contact {
    static final String NL = "\r\n";

    long parseLen;
    
    static final String BIRTHDAY_FIELD = "Birthday:";
    
    /**
     * Contact fields declaration
     */
    // Contact identifier
    String _id;
    
    String syncid;
    
    // Contact displayed name
    String displayName;
    // Contact first name
    String firstName;
    // Contact last name
    String lastName;
    
    static class RowData {
    	RowData(int type, String data, boolean preferred) {
    		this.type = type;
    		this.data = data;
    		this.preferred = preferred;
    	}
    	int type;
    	String data;
    	boolean preferred;    	
    }
    
    // Phones dictionary; keys are android Contact Column ids
    Map<Integer,List<RowData>> phones;
    
    // Emails dictionary; keys are android Contact Column ids
    Map<Integer,List<RowData>> emails;
    
    // Contact company name
    String company;
    
    // Contact title
    String title;

    // Address dictionary; keys are android Contact Column ids
    Map<Integer,List<RowData>> addrs;
    
    // Compressed photo
    byte[] photo;
    
    // Contact note
    String notes;

    // Contact's birthday
    String birthday;
    
	Hashtable<String, handleProp> propHandlers;
	
	/**
	 * Add a row to a RowData list (such as {@link phones}, {@link emails}, {@link addrs}). 
	 * Creates the row if none exists.  
	 * @param database
	 * @param rowdata
	 */
	final static void addRow(Map<Integer,List<RowData>> database, RowData rowdata) {
		Integer column = Integer.valueOf(rowdata.type);
 		List<RowData> list = database.get(column);
 		if (list == null) {
 			list = new ArrayList<RowData>(1);
 			database.put(column, list);
 		}
		list.add(rowdata);
	}
	
	interface handleProp {
		void parseProp(final String propName, final Vector<String> propVec, final String val);
	}
	
	// Initializer block
	{
		reset();
		 propHandlers = new Hashtable<String, handleProp>();

		 handleProp simpleValue = new handleProp() {
			 public void parseProp(final String propName, final Vector<String> propVec, final String val) {
				 if (propName.equals("FN")) {
						displayName = val;
				 } else if (propName.equals("TITLE")) {
						title = val;
				 } else if (propName.equals("ORG")) {
		                String[] orgFields = StringUtil.split(val, ";");
		                company = orgFields[0];
				 } else if (propName.equals("NOTE")) {
						notes = val;
				 } else if (propName.equals("BDAY")) {
						birthday = val;
				 } else if (propName.equals("X-IRMC-LUID")) {
						syncid = val;
				 } else if (propName.equals("N")) {
						String[] names = StringUtil.split(val, ";");
						// We set only the first given name.
						// The others are ignored in input and will not be
						// overridden on the server in output.
						if (names.length >= 2) {
							firstName = names[1];
							lastName = names[0];
						} else {
							String[] names2 = StringUtil.split(names[0], " ");
							firstName = names2[0];
							if (names2.length > 1)
								lastName = names2[1];
						}
				 } 
			 }
		 };

		 propHandlers.put("FN", simpleValue);
		 propHandlers.put("ORG", simpleValue);
		 propHandlers.put("TITLE", simpleValue);
		 propHandlers.put("NOTE", simpleValue);
		 propHandlers.put("BDAY", simpleValue);
		 propHandlers.put("X-IRMC-LUID", simpleValue);
		 propHandlers.put("N", simpleValue);

		 propHandlers.put("TEL", new handleProp() {
			 public void parseProp(final String propName, final Vector<String> propVec, final String val) {
				 int subtype = Contacts.PhonesColumns.TYPE_OTHER;
				 boolean preferred = false;
				 for (String prop : propVec) {
					 if (prop.equalsIgnoreCase("HOME")) {
						 subtype = Contacts.PhonesColumns.TYPE_HOME;
					 } else if (prop.equalsIgnoreCase("WORK")) {
						 if (subtype == Contacts.PhonesColumns.TYPE_FAX_HOME) {
							 subtype = Contacts.PhonesColumns.TYPE_FAX_WORK;
						 } else
						 	subtype = Contacts.PhonesColumns.TYPE_WORK;
					 } else if (prop.equalsIgnoreCase("CELL")) {
						 subtype = Contacts.PhonesColumns.TYPE_MOBILE;
					 } else if (prop.equalsIgnoreCase("FAX")) {
						 if (subtype == Contacts.PhonesColumns.TYPE_WORK) {
							 subtype = Contacts.PhonesColumns.TYPE_FAX_WORK;
						 } else
							 subtype = Contacts.PhonesColumns.TYPE_FAX_HOME;
					 } else if (prop.equalsIgnoreCase("PAGER")) {
						 subtype = Contacts.PhonesColumns.TYPE_PAGER;
					 } else if (prop.equalsIgnoreCase("PREF")) {
						 preferred = true;
					 }
				 }
				 addRow(phones, new RowData(subtype, toCanonicalPhone(val), preferred));
			 }
		 });
		 

		 propHandlers.put("ADR", new handleProp() {
			 public void parseProp(final String propName, final Vector<String> propVec, final String val) {
				 boolean preferred = false;
				 int subtype = Contacts.ContactMethodsColumns.TYPE_WORK; // vCARD Spec says default is WORK
				 for (String prop : propVec) {
					 if (prop.equalsIgnoreCase("WORK")) {
						 subtype = Contacts.ContactMethodsColumns.TYPE_WORK;
					 } else if (prop.equalsIgnoreCase("HOME")) {
						 subtype = Contacts.ContactMethodsColumns.TYPE_HOME;
					 } else if (prop.equalsIgnoreCase("PREF")) {
						 preferred = true;
					 }
				 }
	             String[] addressFields = StringUtil.split(val, ";");
	             StringBuffer addressBuf = new StringBuffer(val.length());
	             if (addressFields.length > 2) {
	            	 addressBuf.append(addressFields[2]);
	            	 int maxLen = Math.min(7, addressFields.length);
		             for (int i = 3; i < maxLen; ++i) {
		            	 addressBuf.append(", ").append(addressFields[i]);
		             }
	             }
	             String address = addressBuf.toString();
				 addRow(addrs, new RowData(subtype, address, preferred));
			 }
		 });
		 

		 propHandlers.put("EMAIL", new handleProp() {
			 public void parseProp(final String propName, final Vector<String> propVec, final String val) {
				 boolean preferred = false;
				 int subtype = Contacts.ContactMethodsColumns.TYPE_HOME; 
				 for (String prop : propVec) {
					 if (prop.equalsIgnoreCase("PREF")) {
						 preferred = true;
					 } else if (prop.equalsIgnoreCase("WORK")) {
						 subtype = Contacts.ContactMethodsColumns.TYPE_WORK;
					 }
				 }
				 addRow(emails, new RowData(subtype, val, preferred));
			 }
		 });
		 

		 propHandlers.put("PHOTO", new handleProp() {
			 public void parseProp(final String propName, final Vector<String> propVec, final String val) {
				 boolean isUrl = false;
				 photo = new byte[val.length()];
				 for (int i = 0; i < photo.length; ++i)
				 	photo[i] = (byte) val.charAt(i);
				 for (String prop : propVec) {
					 if (prop.equalsIgnoreCase("VALUE=URL")) {
						 isUrl = true;
					 }
				 }
				 if (isUrl) {
					 // TODO: Deal with photo URLS
				 }
			 }
		 });
		 
	}
	
	private void reset() {
		 _id = null;
		 syncid = null;
		 parseLen = 0;
		 displayName = null;
		 title = null;
		 company = null;
		 notes = null;
		 birthday = null;
		 photo = null;
		 firstName = null;
		 lastName = null;
		 phones = new HashMap<Integer, List<RowData>>();
		 emails = new HashMap<Integer, List<RowData>>();
		 addrs = new HashMap<Integer, List<RowData>>();
	}

	SQLiteStatement querySyncId;
	SQLiteStatement queryPersonId;
	SQLiteStatement insertSyncId;
	
    // Constructors------------------------------------------------
    public Contact(SQLiteStatement querySyncId, SQLiteStatement queryPersionId, SQLiteStatement insertSyncId) {
    	this.querySyncId = querySyncId;
    	this.queryPersonId = queryPersionId;
    	this.insertSyncId = insertSyncId;
    }

    public Contact(String vcard, SQLiteStatement querySyncId, SQLiteStatement queryPersionId, SQLiteStatement insertSyncId) {
    	this(querySyncId, queryPersionId, insertSyncId);
    	BufferedReader vcardReader = new BufferedReader(new StringReader(vcard)); 
        try {
			parseVCard(vcardReader);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    public Contact(BufferedReader vcfReader, SQLiteStatement querySyncId, SQLiteStatement queryPersionId, SQLiteStatement insertSyncId) throws IOException { 
    	this(querySyncId, queryPersionId, insertSyncId);
    	parseVCard(vcfReader);
    }
    
    public Contact(Cursor peopleCur, ContentResolver cResolver, 
    		SQLiteStatement querySyncId, SQLiteStatement queryPersionId, SQLiteStatement insertSyncId) {
    	this(querySyncId, queryPersionId, insertSyncId);
        populate(peopleCur, cResolver);
    }

    
    final static Pattern[] phonePatterns = {
			Pattern.compile("[+](1)(\\d\\d\\d)(\\d\\d\\d)(\\d\\d\\d\\d.*)"),
			Pattern.compile("[+](972)(2|3|4|8|9|50|52|54|57|59|77)(\\d\\d\\d)(\\d\\d\\d\\d.*)"),
	};
    
    
    /**
     * Change the phone to canonical format (with dashes, etc.) if it's in a supported country.
     * @param phone
     * @return
     */
	String toCanonicalPhone(String phone) {
		for (final Pattern phonePattern : phonePatterns) {
			Matcher m = phonePattern.matcher(phone);
			if (m.matches()) {
				return "+" + m.group(1) + "-" + m.group(2) + "-" + m.group(3) + "-" + m.group(4);
			}
		}

		return phone;
	}

    
    /**
     * Set the person identifier
     */
    public void setId(String id) {
        _id = id;
    }

    /**
     * Get the person identifier
     */
    public long getId() {
        return Long.parseLong(_id);
    }

	final static Pattern beginPattern = Pattern.compile("BEGIN:VCARD");
	final static Pattern propPattern = Pattern.compile("([^:]+):(.*)");
	final static Pattern propParamPattern = Pattern.compile("([^;=]+)(=([^;]+))?(;|$)");
	final static Pattern base64Pattern = Pattern.compile("\\s*([a-zA-Z0-9+/]+={0,2})\\s*$");
    final static Pattern namePattern = Pattern.compile("(([^,]+),(.*))|((.*?)\\s+(\\S+))");
    
	// Parse birthday in notes
	final static Pattern birthdayPattern = Pattern.compile("^" + BIRTHDAY_FIELD + ":\\s*([^;]+)(;\\s*|\\s*$)");
	   
    /**
     * Parse the vCard string into the contacts fields
     */
    public long parseVCard(BufferedReader vCard) throws IOException {
    	// Reset the currently read values.
    	reset();
    	
    	// Find Begin.
    	String line = vCard.readLine();
    	if (line != null)
    		parseLen += line.length();
    	else
    		return -1;
    	
    	while (line != null && !beginPattern.matcher(line).matches()) {
    		line = vCard.readLine();
    		parseLen += line.length();
    	}
    	
    	if (line == null)
    		return -1;
    	
    	boolean skipRead = false;
    	
    	while (line != null) {
    		if (!skipRead)
    			line = vCard.readLine();
    		
    		if (line == null) {
    			return 0;
    		}
    		
    		skipRead = false;
    	
        	// do multi-line unfolding (cr lf with whitespace immediately following is removed, joining the two lines).  
        	vCard.mark(1);
        	for (int ch = vCard.read(); ch == (int) ' ' || ch == (int) '\t'; ch = vCard.read()) {
        		vCard.reset();
        		String newLine = vCard.readLine();
        		if (newLine != null)
        			line += newLine;
        		vCard.mark(1);
        	}
        	vCard.reset();
    		
    		parseLen += line.length(); // TODO: doesn't include CR LFs
    		
    		Matcher pm = propPattern.matcher(line);
    		
    		if (pm.matches()) {
    			String prop = pm.group(1);
    			String val = pm.group(2);

    			if (prop.equalsIgnoreCase("END") && val.equalsIgnoreCase("VCARD")) {
    				// End of vCard
    				return parseLen;
    			}
 
    			Matcher ppm = propParamPattern.matcher(prop);
    			if (!ppm.find())
    				// Doesn't seem to be a valid vCard property
    				continue;
    			
    			String propName = ppm.group(1).toUpperCase();
    			Vector<String> propVec = new Vector<String>();
    			String charSet = "UTF-8";
    			String encoding = "";
    			while (ppm.find()) {
    				propVec.add(ppm.group());
    				String param = ppm.group(1);
    				String paramVal = ppm.group(3);
    				if (param.equalsIgnoreCase("CHARSET"))
    					charSet = paramVal;
    				else if (param.equalsIgnoreCase("ENCODING"))
    					encoding = paramVal;
    			}
    			if (encoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
    				try {
    					val = QuotedPrintable.decode(val.getBytes(charSet), "UTF-8");
    				} catch (UnsupportedEncodingException uee) {
    					
    				}
    			} else if (encoding.equalsIgnoreCase("BASE64")) {
    				StringBuffer tmpVal = new StringBuffer(val);
    				do {
    					line = vCard.readLine();
     			
    					if ((line == null) || (line.length() == 0) || (!base64Pattern.matcher(line).matches())) {
    						//skipRead = true;
    						break;
    					}
   						tmpVal.append(line);
    				} while (true);
    				
    				Base64Coder.decodeInPlace(tmpVal);
    				val = tmpVal.toString();
    			}
    			handleProp propHandler = propHandlers.get(propName);
    			if (propHandler != null)
    				propHandler.parseProp(propName, propVec, val);
    		}
    	}
    	return 0;
    }

    public long getParseLen() {
    	return parseLen;
    }
    
    /**
     * Format an email as a vCard field.
     *  
     * @param cardBuff Formatted email will be appended to this buffer
     * @param email The rowdata containing the actual email data.
     */
    public static void formatEmail(Appendable cardBuff, RowData email) throws IOException {
    	cardBuff.append("EMAIL;INTERNET");
    	if (email.preferred)
    		cardBuff.append(";PREF");
    	
    	switch (email.type) {
    	case Contacts.ContactMethodsColumns.TYPE_WORK:
    		cardBuff.append(";WORK");
    		break;
    	}
    	cardBuff.append(":").append(email.data.trim()).append(NL);
    }

    /**
     * Format a phone as a vCard field.
     *  
     * @param formatted Formatted phone will be appended to this buffer
     * @param phone The rowdata containing the actual phone data.
     */
    public static void formatPhone(Appendable formatted, RowData phone) throws IOException  {
    	formatted.append("TEL");
    	if (phone.preferred)
    		formatted.append(";PREF");
    	
    	switch (phone.type) {
    	case Contacts.PhonesColumns.TYPE_HOME:
    		formatted.append(";VOICE");
    		break;
    	case Contacts.PhonesColumns.TYPE_WORK:
    		formatted.append(";VOICE;WORK");
    		break;
    	case Contacts.PhonesColumns.TYPE_FAX_WORK:
    		formatted.append(";FAX;WORK");
    		break;
    	case Contacts.PhonesColumns.TYPE_FAX_HOME:
    		formatted.append(";FAX;HOME");
    		break;
    	case Contacts.PhonesColumns.TYPE_MOBILE:
    		formatted.append(";CELL");
    		break;
    	case Contacts.PhonesColumns.TYPE_PAGER:
    		formatted.append(";PAGER");
    		break;
    	}
    	formatted.append(":").append(phone.data.trim()).append(NL);
    }
    
    /**
     * Format a phone as a vCard field.
     *  
     * @param formatted Formatted phone will be appended to this buffer
     * @param addr The rowdata containing the actual phone data.
     */
    public static void formatAddr(Appendable formatted, RowData addr)  throws IOException  {
    	formatted.append("ADR");
    	if (addr.preferred)
    		formatted.append(";PREF");
    	
    	switch (addr.type) {
    	case Contacts.ContactMethodsColumns.TYPE_HOME:
    		formatted.append(";HOME");
    		break;
    	case Contacts.PhonesColumns.TYPE_WORK:
    		formatted.append(";WORK");
    		break;
    	}
    	formatted.append(":;;").append(addr.data.replace(", ", ";").trim()).append(NL);
    }

    
    public String toString() {
        StringWriter out = new StringWriter();
        try {
        	writeVCard(out);
        } catch (IOException e) {
        	// Should never happen
        }
        return out.toString();
    }    
    
    /**
     * Write the contact vCard to an appendable stream.
     */
    public void writeVCard(Appendable vCardBuff) throws IOException {
        // Start vCard

        vCardBuff.append("BEGIN:VCARD").append(NL);
        vCardBuff.append("VERSION:2.1").append(NL);
        
       	appendField(vCardBuff, "X-IRMC-LUID:", syncid);
        
        vCardBuff.append("N:").append((lastName != null) ? lastName.trim() : "")
                .append(";").append((firstName != null) ? firstName.trim() : "")
                .append(";").append(";").append(";").append(NL);

        

        for (List<RowData> emailist : emails.values()) {
        	for (RowData email : emailist) {
        		formatEmail(vCardBuff, email);
        	}
        }
        
        for (List<RowData> phonelist : phones.values()) {
        	for (RowData phone : phonelist) {
        		formatPhone(vCardBuff, phone);
        	}
        }

        appendField(vCardBuff, "ORG:", company);
        appendField(vCardBuff, "TITLE:", title);


        for (List<RowData> addrlist : addrs.values()) {
	        for (RowData addr : addrlist) {
	        	formatAddr(vCardBuff, addr);
	        }
        }

        appendField(vCardBuff, "NOTE:", notes);
        appendField(vCardBuff, "BDAY:", birthday);

        
        if (photo != null) {
        	appendField(vCardBuff, "PHOTO;TYPE=JPEG;ENCODING=BASE64:", " ");
        	Base64Coder.mimeEncode(vCardBuff, photo, 76, NL);
        	vCardBuff.append(NL);
        	vCardBuff.append(NL);
        }

        // End vCard
        vCardBuff.append("END:VCARD").append(NL);
    }

    /**
     * Append the field to the StringBuffer out if not null.
     */
    private void appendField(Appendable out, String name, String val) throws IOException {
        if(val != null && val.length() > 0) {
            out.append(name).append(val).append(NL);
        }
    }

    /**
     * Populate the contact fields from a cursor
     */
    public void populate(Cursor peopleCur, ContentResolver cResolver) {
    	reset();
        setPeopleFields(peopleCur);
        String personID = _id;
        
        if (querySyncId != null) {
        	querySyncId.bindString(1, personID);
        	try {
        		syncid = querySyncId.simpleQueryForString();
        	} catch (SQLiteDoneException e) {
        		if (insertSyncId != null) {
	            	// Create a new syncid 
	            	syncid = UUID.randomUUID().toString();
	            	
	            	// Write the new syncid
	            	insertSyncId.bindString(1, personID);
	            	insertSyncId.bindString(2, syncid);
	            	insertSyncId.executeInsert();
        		}
        	}
        }

        Cursor organization = cResolver.query(Contacts.Organizations.CONTENT_URI, null,
        		Contacts.OrganizationColumns.PERSON_ID + "=" + personID, null, null);
        
        // Set the organization fields
        if (organization.moveToFirst()) {
            setOrganizationFields(organization);
        }
        organization.close();
        
        Cursor phones = cResolver.query(Contacts.Phones.CONTENT_URI, null,
        		Contacts.Phones.PERSON_ID + "=" + personID, null, null);

        // Set all the phone numbers
        if (phones.moveToFirst()) {
            do {
                setPhoneFields(phones);
            } while (phones.moveToNext());
        }
        phones.close();

        Cursor contactMethods = cResolver.query(Contacts.ContactMethods.CONTENT_URI,
                null, Contacts.ContactMethods.PERSON_ID + "=" + personID, null, null);

        // Set all the email addresses
        if (contactMethods.moveToFirst()) {
            do {
                setContactMethodsFields(contactMethods);
            } while (contactMethods.moveToNext());
        }
        contactMethods.close();
        
        // Load a photo if one exists.
        Cursor contactPhoto = cResolver.query(Contacts.Photos.CONTENT_URI, null, Contacts.PhotosColumns.PERSON_ID + "=" + personID, null, null);
        if (contactPhoto.moveToFirst()) {
        	photo = contactPhoto.getBlob(contactPhoto.getColumnIndex(Contacts.PhotosColumns.DATA));
        }
        contactPhoto.close();
    }

    /**
     * Retrieve the People fields from a Cursor
     */
    private void setPeopleFields(Cursor cur) {

        int selectedColumn;

        // Set the contact id
        selectedColumn = cur.getColumnIndex(Contacts.People._ID);
        long nid = cur.getLong(selectedColumn);
        _id = String.valueOf(nid);

        //
        // Get PeopleColumns fields
        //
        selectedColumn = cur.getColumnIndex(Contacts.PeopleColumns.NAME);
        displayName = cur.getString(selectedColumn);

        if (displayName != null) {
        	Matcher m = namePattern.matcher(displayName);
        	if (m.matches()) {
        		if (m.group(1) != null) {
        			lastName = m.group(2);
        			firstName = m.group(3);
        		} else {
        			firstName = m.group(5);
        			lastName = m.group(6);
        		}
        	} else {
        		firstName = displayName;
        		lastName = "";
        	}
        } else {
        	firstName = lastName = "";
        }
        
        selectedColumn = cur.getColumnIndex(Contacts.People.NOTES);
        notes = cur.getString(selectedColumn);
        if (notes != null) {
        	Matcher ppm = birthdayPattern.matcher(notes);

        	if (ppm.find()) {
        		birthday = ppm.group(1);
        		notes = ppm.replaceFirst("");
        	}
        }
    }
    
    /**
     * Retrieve the organization fields from a Cursor
     */
    private void setOrganizationFields(Cursor cur) {
        
        int selectedColumn;
        
        //
        // Get Organizations fields
        //
        selectedColumn = cur.getColumnIndex(Contacts.Organizations.COMPANY);
        company = cur.getString(selectedColumn);
        selectedColumn = cur.getColumnIndex(Contacts.Organizations.TITLE);
        title = cur.getString(selectedColumn);
    }

    /**
     * Retrieve the Phone fields from a Cursor
     */
    private void setPhoneFields(Cursor cur) {

        int selectedColumn;
        int selectedColumnType;
        int preferredColumn;
        int phoneType;

        //
        // Get PhonesColums fields
        //
        selectedColumn = cur.getColumnIndex(Contacts.PhonesColumns.NUMBER);
        selectedColumnType = cur.getColumnIndex(Contacts.PhonesColumns.TYPE);
        preferredColumn = cur.getColumnIndex(Contacts.PhonesColumns.ISPRIMARY);
        phoneType = cur.getInt(selectedColumnType);
        String phone = cur.getString(selectedColumn);
        boolean preferred = cur.getInt(preferredColumn) != 0;
        
        
        addRow(phones, new RowData(phoneType, phone, preferred));
    }

    /**
     * Retrieve the email fields from a Cursor
     */
    private void setContactMethodsFields(Cursor cur) {

        int selectedColumn;
        int selectedColumnType;
        int selectedColumnKind;
        int selectedColumnPrimary;
        
        int methodType;
        int kind;

        //
        // Get ContactsMethodsColums fields
        //
        selectedColumn = cur
                .getColumnIndex(Contacts.ContactMethodsColumns.DATA);
        selectedColumnType = cur
                .getColumnIndex(Contacts.ContactMethodsColumns.TYPE);
        selectedColumnKind = cur
                .getColumnIndex(Contacts.ContactMethodsColumns.KIND);
        selectedColumnPrimary = cur
                .getColumnIndex(Contacts.ContactMethodsColumns.ISPRIMARY);
        
        kind = cur.getInt(selectedColumnKind);
        
        methodType = cur.getInt(selectedColumnType);
        String methodData = cur.getString(selectedColumn);
        boolean preferred = cur.getInt(selectedColumnPrimary) != 0;
        
        switch (kind) {
        case Contacts.KIND_EMAIL:
        	addRow(emails, new RowData(methodType, methodData, preferred));
        	break;
        case Contacts.KIND_POSTAL:
        	addRow(addrs, new RowData(methodType, methodData, preferred));
        	break;
        }
    }


    public ContentValues getPeopleCV() {
        ContentValues cv = new ContentValues();
    	
        StringBuffer fullname = new StringBuffer();
        if (displayName != null)
        	fullname.append(displayName);
        else {
        	if (firstName != null)
        		fullname.append(firstName);
        	if (lastName != null) {
        		if (firstName != null)
        			fullname.append(" ");
        		fullname.append(lastName);
        	}
        }
        
        // Use company name if only the company is given.
        if (fullname.length() == 0)
        	fullname.append(company);

        cv.put(Contacts.People.NAME, fullname.toString());

        if (!StringUtil.isNullOrEmpty(_id)) {
            cv.put(Contacts.People._ID, _id);
        }
        
        StringBuffer allnotes = new StringBuffer();
        if (birthday != null) {
        	allnotes.append(BIRTHDAY_FIELD).append(" ").append(birthday);
        }
        if (notes != null) {
        	if (birthday != null) {
        		allnotes.append(";\n");
        	}
        	allnotes.append(notes);
        }
        
        if (allnotes.length() > 0)
        	cv.put(Contacts.People.NOTES, allnotes.toString());
        
        return cv;
    }
    
    public ContentValues getOrganizationCV() {

        if(StringUtil.isNullOrEmpty(company) && StringUtil.isNullOrEmpty(title)) {
            return null;
        }
        ContentValues cv = new ContentValues();
    
        cv.put(Contacts.Organizations.COMPANY, company);
        cv.put(Contacts.Organizations.TITLE, title);
        cv.put(Contacts.Organizations.TYPE, Contacts.Organizations.TYPE_WORK);
        cv.put(Contacts.Organizations.PERSON_ID, _id);
        
        return cv;
    }

    public ContentValues getPhoneCV(RowData data) {
        ContentValues cv = new ContentValues();

        cv.put(Contacts.Phones.NUMBER, data.data);
        cv.put(Contacts.Phones.TYPE, data.type);
        cv.put(Contacts.Phones.ISPRIMARY, data.preferred ? 1 : 0);
        cv.put(Contacts.Phones.PERSON_ID, _id);

        return cv;
    }


    public ContentValues getEmailCV(RowData data) {
        ContentValues cv = new ContentValues();

        cv.put(Contacts.ContactMethodsColumns.DATA, data.data);
        cv.put(Contacts.ContactMethodsColumns.TYPE, data.type);
        cv.put(Contacts.ContactMethodsColumns.KIND,
                Contacts.KIND_EMAIL);
        cv.put(Contacts.ContactMethodsColumns.ISPRIMARY, data.preferred ? 1 : 0);
        cv.put(Contacts.ContactMethods.PERSON_ID, _id);

        return cv;
    }
     
    public ContentValues getAddressCV(RowData data) {
        ContentValues cv = new ContentValues();

        cv.put(Contacts.ContactMethodsColumns.DATA, data.data);
        cv.put(Contacts.ContactMethodsColumns.TYPE, data.type);
        cv.put(Contacts.ContactMethodsColumns.KIND,
                Contacts.KIND_POSTAL);
        cv.put(Contacts.ContactMethodsColumns.ISPRIMARY, data.preferred ? 1 : 0);
        cv.put(Contacts.ContactMethods.PERSON_ID, _id);

        return cv;
    }


    /**
     * Add a new contact to the Content Resolver
     * 
     * @param key the row number of the existing contact (if known)
     * @return The row number of the inserted column
     */
    public long addContact(Context context, long key, boolean replace) {
        ContentResolver cResolver = context.getContentResolver();
        ContentValues pCV = getPeopleCV();
        
        boolean addSyncId = false;
        boolean replacing = false;
        
        if (key <= 0 && syncid != null) {
        	if (queryPersonId != null) try {
        		queryPersonId.bindString(1, syncid);
        		setId(queryPersonId.simpleQueryForString());
        		key = getId();
        	} catch(SQLiteDoneException e) {
        		// Couldn't locate syncid, we'll add it;
        		// need to wait until we know what the key is, though.
        		addSyncId = true;
        	}
        }
        
        Uri newContactUri = null;
        
        if (key > 0) {
        	newContactUri = ContentUris.withAppendedId(Contacts.People.CONTENT_URI, key);
        	Cursor testit = cResolver.query(newContactUri, null, null, null, null);
        	if (testit == null || testit.getCount() == 0) {
        		newContactUri = null;
        		pCV.put(Contacts.People._ID, key);
        	}
        	if (testit != null)
        		testit.close();
        }
        if (newContactUri == null) {
        	newContactUri = insertContentValues(cResolver, Contacts.People.CONTENT_URI, pCV);
        	if (newContactUri == null) {
            	Log.error("Error adding contact." + " (key: " + key + ")");
            	return -1;
            }
            // Set the contact person id
            setId(newContactUri.getLastPathSegment());
            key = getId();
            
            // Add the new contact to the myContacts group
            Contacts.People.addToMyContactsGroup(cResolver, key);
        } else {
        	// update existing Uri
    		if (!replace)
    			return -1;

    		replacing = true;
    		
        	cResolver.update(newContactUri, pCV, null, null);
        }
        	

        // We need to add the syncid to the database so
        // that we'll detect this contact if we try to import
        // it again.
        if (addSyncId && insertSyncId != null) {
        	insertSyncId.bindLong(1, key);
        	insertSyncId.bindString(2, syncid);
        	insertSyncId.executeInsert();
        }
 
        /*
         * Insert all the new ContentValues
         */
        if (replacing) {
        	// Remove existing phones
        	Uri phones = Uri.withAppendedPath(ContentUris.withAppendedId(Contacts.People.CONTENT_URI, key), Contacts.People.Phones.CONTENT_DIRECTORY);
        	String[] phoneID = {Contacts.People.Phones._ID};
        	Cursor existingPhones = cResolver.query(phones, phoneID, null, null, null);
        	if (existingPhones != null && existingPhones.moveToFirst()) {
        		int idColumn = existingPhones.getColumnIndex(Contacts.People.Phones._ID); 
        		List<Long> ids = new ArrayList<Long>(existingPhones.getCount());
        		do {
        			ids.add(existingPhones.getLong(idColumn));
        		} while (existingPhones.moveToNext());
        		existingPhones.close();
        		for (Long id : ids) {
        			Uri phone = ContentUris.withAppendedId(Contacts.Phones.CONTENT_URI, id);
        			cResolver.delete(phone, null, null);
        		}
        	}
        	
        	// Remove existing contact methods (emails, addresses, etc.)
        	Uri methods = Uri.withAppendedPath(ContentUris.withAppendedId(Contacts.People.CONTENT_URI, key), Contacts.People.ContactMethods.CONTENT_DIRECTORY);
        	String[] methodID = {Contacts.People.ContactMethods._ID};
        	Cursor existingMethods = cResolver.query(methods, methodID, null, null, null);
        	if (existingMethods != null && existingMethods.moveToFirst()) {
        		int idColumn = existingMethods.getColumnIndex(Contacts.People.ContactMethods._ID); 
        		List<Long> ids = new ArrayList<Long>(existingMethods.getCount());
        		do {
        			ids.add(existingMethods.getLong(idColumn));
        		} while (existingMethods.moveToNext());
        		existingMethods.close();
        		for (Long id : ids) {
        			Uri method = ContentUris.withAppendedId(Contacts.Phones.CONTENT_URI, id);
        			cResolver.delete(method, null, null);
        		}
        	}
        }
        
        // Phones
        for (List<RowData> phonelist : phones.values()) {
	        for (RowData phone : phonelist) {
	        	insertContentValues(cResolver, Contacts.Phones.CONTENT_URI, getPhoneCV(phone));
	        }
        }

        insertContentValues(cResolver, Contacts.Organizations.CONTENT_URI, getOrganizationCV());

        Builder builder = newContactUri.buildUpon();
        builder.appendEncodedPath(Contacts.ContactMethods.CONTENT_URI.getPath());

        // Emails
        for (List<RowData> emailist : emails.values()) {
	        for (RowData email : emailist) {
	        	insertContentValues(cResolver, builder.build(), getEmailCV(email));
	        }
        }
        
        // Addressess 
        for (List<RowData> addrlist : addrs.values()) {
	        for (RowData addr : addrlist) {
	        	insertContentValues(cResolver, builder.build(), getAddressCV(addr));
	        }
        }
        
        // Photo
        if (photo != null) {
        	Uri person = ContentUris.withAppendedId(Contacts.People.CONTENT_URI, key);
        	Contacts.People.setPhotoData(cResolver, person, photo);
        }

        return key;
    }
    
    /**
     * Insert a new ContentValues raw into the Android ContentProvider
     */
    private Uri insertContentValues(ContentResolver cResolver, Uri uri, ContentValues cv) {
        if (cv != null) {
        	return cResolver.insert(uri, cv);
        }
        return null;
    }

    /**
     * Get the item content
     */
    public String getContent() {
    	return toString();
    }

    /**
     * Check if the email string is well formatted
     */
    @SuppressWarnings("unused")
	private boolean checkEmail(String email) {
        return (email != null && !"".equals(email) && email.indexOf("@") != -1);
    }
}
