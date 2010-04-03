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
import java.io.UnsupportedEncodingException;
import java.util.Hashtable;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.provider.Contacts;
import android.provider.Contacts.ContactMethodsColumns;

import com.funambol.util.QuotedPrintable;
import com.funambol.util.StringUtil;

/**
 * Import/Export to vCard
 */
public class VCardParser {
    static final String NL = "\r\n";

	public static final String DEFAULT_CHARSET = "ISO-8859-1";
	
    // Property name for Instant-message addresses
    static final String IMPROP = "X-IM-NICK";

    // Property parameter name for custom labels
    static final String LABEL_PARAM = "LABEL";
    
    // Property parameter for IM protocol
    static final String PROTO_PARAM = "PROTO";
   

	/*==========================================
	 *  Regular expressions for parsing vCards
	 *==========================================
	 */
    final static Pattern[] phonePatterns = {
		Pattern.compile("[+](1)(\\d\\d\\d)(\\d\\d\\d)(\\d\\d\\d\\d.*)"),
		Pattern.compile("[+](972)(2|3|4|8|9|50|52|54|57|59|77)(\\d\\d\\d)(\\d\\d\\d\\d.*)"),
    };

	final static Pattern beginPattern = Pattern.compile("BEGIN:VCARD",Pattern.CASE_INSENSITIVE);
	final static Pattern propPattern = Pattern.compile("([^:]+):(.*)");
	final static Pattern propParamPattern = Pattern.compile("([^;=]+)(=([^;]+))?(;|$)");
	final static Pattern base64Pattern = Pattern.compile("\\s*([a-zA-Z0-9+/]+={0,2})\\s*$");
	final static Pattern quotedPrintableSoftbreakPattern = Pattern.compile("(.*)=\\s*$");
    
    long parseLen;
    
	Hashtable<String, handleProp> propHandlers;
	
	interface handleProp {
		void parseProp(final Contact contact, final String propName, final Vector<String> propVec, final String val);
	}
	
	// Initializer block
	{
		 propHandlers = new Hashtable<String, handleProp>();

		 handleProp simpleValue = new handleProp() {
			 public void parseProp(final Contact contact, final String propName, final Vector<String> propVec, final String val) {
				 if (propName.equals("FN")) {
					 contact.displayName = val;
				 } else if (propName.equals("NOTE")) {
					 contact.notes = val;
				 } else if (propName.equals("BDAY")) {
					 contact.birthday = val;
				 } else if (propName.equals("X-IRMC-LUID") || propName.equals("UID")) {
					 contact.syncid = val;
				 } else if (propName.equals("N")) {
					 String[] names = StringUtil.split(val, ";");
					 switch(names.length) {
					 	default:
					 	case 5:
					 		contact.sufName = names[4]; // suffixes
					 	case 4:
					 		contact.preName = names[3]; // prefixes
					 	case 3:
					 		contact.midNames = names[2]; // additional names
					 	case 2:
					 		contact.firstName = names[1];
					 		contact.lastName = names[0];
					 		break;
					 	case 1:
					 		contact.parseDisplayName(names[0]);
					 	case 0:
					 }
				 } 
			 }
		 };

		 propHandlers.put("FN", simpleValue);
		 propHandlers.put("NOTE", simpleValue);
		 propHandlers.put("BDAY", simpleValue);
		 propHandlers.put("X-IRMC-LUID", simpleValue);
		 propHandlers.put("UID", simpleValue);
		 propHandlers.put("N", simpleValue);

		 handleProp orgHandler = new handleProp() {

			@Override
			public void parseProp(Contact contact, String propName, Vector<String> propVec,
					String val) {
				String label = null;
				for (String prop : propVec) {
					String[] propFields = StringUtil.split(prop, "=");
					if (propFields[0].equalsIgnoreCase(LABEL_PARAM) && propFields.length > 1) {
						label = propFields[1];
					}
				}
				if (propName.equals("TITLE")) {
					boolean setTitle = false;
					for (Contact.OrgData org : contact.orgs) {
						if (label == null && org.customLabel != null)
							continue;
						if (label != null && !label.equals(org.customLabel))
							continue;
						
						if (org.title == null) {
							org.title = val;
							setTitle = true;
							break;
						}
					}
					if (!setTitle) {
						contact.orgs.add(new Contact.OrgData(label == null ? ContactMethodsColumns.TYPE_WORK : ContactMethodsColumns.TYPE_CUSTOM,
								val, null, label));
					}
				} else if (propName.equals("ORG")) {
					String[] orgFields = StringUtil.split(val, ";");
					boolean setCompany = false;
					for (Contact.OrgData org : contact.orgs) {
						if (label == null && org.customLabel != null)
							continue;
						if (label != null && !label.equals(org.customLabel))
							continue;

						if (org.company == null) {
							org.company = val;
							setCompany = true;
							break;
						}
					}
					if (!setCompany) {
						contact.orgs.add(new Contact.OrgData(label == null ? ContactMethodsColumns.TYPE_WORK : ContactMethodsColumns.TYPE_CUSTOM,
								null, orgFields[0], label));
					}
				 }
			}
		 };
		 

		 propHandlers.put("ORG", orgHandler);
		 propHandlers.put("TITLE", orgHandler);
		 
		 propHandlers.put("TEL", new handleProp() {
			 public void parseProp(Contact contact, final String propName, final Vector<String> propVec, final String val) {
				 String label = null;
				 int subtype = Contacts.PhonesColumns.TYPE_OTHER;
				 boolean preferred = false;
				 for (String prop : propVec) {
					 if (prop.equalsIgnoreCase("HOME") || prop.equalsIgnoreCase("VOICE")) {
						 if (subtype != Contacts.PhonesColumns.TYPE_FAX_HOME)
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
					 } else {
						 String[] propFields = StringUtil.split(prop, "=");
						 
						 if (propFields.length > 1 && propFields[0].equalsIgnoreCase(LABEL_PARAM)) {
							 label = propFields[1];
							 subtype = Contacts.ContactMethodsColumns.TYPE_CUSTOM;
						 }
					 }
				 }
				 contact.phones.add(new Contact.RowData(subtype, toCanonicalPhone(val), preferred, label));
			 }
		 });
		 

		 propHandlers.put("ADR", new handleProp() {
			 public void parseProp(Contact contact, final String propName, final Vector<String> propVec, final String val) {
				 boolean preferred = false;
				 String label = null;
				 int subtype = Contacts.ContactMethodsColumns.TYPE_WORK; // vCard spec says default is WORK
				 for (String prop : propVec) {
					 if (prop.equalsIgnoreCase("WORK")) {
						 subtype = Contacts.ContactMethodsColumns.TYPE_WORK;
					 } else if (prop.equalsIgnoreCase("HOME")) {
						 subtype = Contacts.ContactMethodsColumns.TYPE_HOME;
					 } else if (prop.equalsIgnoreCase("PREF")) {
						 preferred = true;
					 } else {
						 String[] propFields = StringUtil.split(prop, "=");
						 
						 if (propFields.length > 1 && propFields[0].equalsIgnoreCase(LABEL_PARAM)) {
							 label = propFields[1];
							 subtype = Contacts.ContactMethodsColumns.TYPE_CUSTOM;
						 }
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
	             contact.addrs.add(new Contact.RowData(subtype, address, preferred, label));
			 }
		 });
		 

		 propHandlers.put("EMAIL", new handleProp() {
			 public void parseProp(Contact contact, final String propName, final Vector<String> propVec, final String val) {
				 boolean preferred = false;
				 String label = null;
				 int subtype = Contacts.ContactMethodsColumns.TYPE_HOME; 
				 for (String prop : propVec) {
					 if (prop.equalsIgnoreCase("PREF")) {
						 preferred = true;
					 } else if (prop.equalsIgnoreCase("WORK")) {
						 subtype = Contacts.ContactMethodsColumns.TYPE_WORK;
					 } else {
						 String[] propFields = StringUtil.split(prop, "=");
						 
						 if (propFields.length > 1 && propFields[0].equalsIgnoreCase(LABEL_PARAM)) {
							 label = propFields[1];
							 subtype = Contacts.ContactMethodsColumns.TYPE_CUSTOM;
						 }
					 } 
				 }
				 contact.emails.add(new Contact.RowData(subtype, val, preferred, label));
			 }
		 });
		 
		 propHandlers.put(IMPROP, new handleProp() {
			 public void parseProp(Contact contact, final String propName, final Vector<String> propVec, final String val) {
				 boolean preferred = false;
				 String label = null;
				 String proto = null;
				 int subtype = Contacts.ContactMethodsColumns.TYPE_HOME; 
				 for (String prop : propVec) {
					 if (prop.equalsIgnoreCase("PREF")) {
						 preferred = true;
					 } else if (prop.equalsIgnoreCase("WORK")) {
						 subtype = Contacts.ContactMethodsColumns.TYPE_WORK;
					 } else {
						 String[] propFields = StringUtil.split(prop, "=");
						 
						 if (propFields.length > 1) {
							 if (propFields[0].equalsIgnoreCase(PROTO_PARAM)) {
								 proto = propFields[1];
							 } else if (propFields[0].equalsIgnoreCase(LABEL_PARAM)) {
								 label = propFields[1];
							 }
						 }
					 } 
				 }
				 Contact.RowData newRow = new Contact.RowData(subtype, val, preferred, label); 
				 newRow.auxData = proto;
				 contact.ims.add(newRow);
			 }
		 });
		 
		 propHandlers.put("PHOTO", new handleProp() {
			 public void parseProp(Contact contact, final String propName, final Vector<String> propVec, final String val) {
				 boolean isUrl = false;
				 contact.photo = new byte[val.length()];
				 for (int i = 0; i < contact.photo.length; ++i)
					 contact.photo[i] = (byte) val.charAt(i);
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
     * Parse the vCard string into the contacts fields
     */
    public long parseVCard(Contact contact, BufferedReader vCard) throws IOException {
    	// Reset the currently read values.
    	parseLen = 0;
    	contact.reset();
    	
    	// Find Begin.
    	String line = vCard.readLine();
    	if (line != null)
    		parseLen += line.length() + 2;
    	else
    		return -1;
    	
    	while (line != null && !beginPattern.matcher(line).matches()) {
    		line = vCard.readLine();
        	if (line != null)
        		parseLen += line.length() + 2;
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
        	int ch;
        	for (ch = vCard.read(); ch == (int) ' ' || ch == (int) '\t'; ch = vCard.read()) {
        		vCard.reset();
        		String newLine = vCard.readLine();
        		if (newLine != null) {
            		parseLen += 2; // Assume CR-LF line ending;
            		int newLen = newLine.length();
        			int pos = 0;
        			// Search for first non-whitespace char.
        			while (pos < newLen && (newLine.charAt(pos) == ' ' || newLine.charAt(pos) == '\t'))
        				++pos;
		        	parseLen += pos;		
        			
        			line += newLine.substring(pos);
        		}
        		vCard.mark(1);
        	}
        	if (ch >= 0)
        		// Reset as long as we didn't reach end of file
        		vCard.reset();
    		
    		parseLen += line.length() + 2;
    		
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
    			String charSet = DEFAULT_CHARSET;
    			String encoding = "";
    			while (ppm.find()) {
    				String param = ppm.group(1);
    				String paramVal = ppm.group(3);
    				propVec.add(param + (paramVal != null ? "=" + paramVal : ""));
    				if (param.equalsIgnoreCase("CHARSET"))
    					charSet = paramVal;
    				else if (param.equalsIgnoreCase("ENCODING"))
    					encoding = paramVal;
    			}
    			if (encoding.equalsIgnoreCase("QUOTED-PRINTABLE")) {
    				Matcher softBreak = quotedPrintableSoftbreakPattern.matcher(val);
    				if (softBreak.matches()) {
    					// Multi-line quoted-printable
        				StringBuffer tmpVal = new StringBuffer(softBreak.group(1));
        				do {
        					line = vCard.readLine();
         			
        					if ((line == null) || (line.length() == 0)) {
        						//skipRead = true;
        						break;
        					}
        					softBreak = quotedPrintableSoftbreakPattern.matcher(line);
        					if (softBreak.matches()) {
        						tmpVal.append(softBreak.group(1));
        					} else {
        						tmpVal.append(line);
        						break;
        					}
        				} while (true);
        				
        				val = tmpVal.toString();
    				}
    				
    				try {
    					val = QuotedPrintable.decode(val.getBytes("ASCII"), charSet);
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
    				propHandler.parseProp(contact, propName, propVec, val);
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
    public static void formatEmail(Appendable cardBuff, Contact.RowData email) throws IOException {
    	cardBuff.append("EMAIL;INTERNET");
    	if (email.preferred)
    		cardBuff.append(";PREF");

    	if (email.customLabel != null) {
    		cardBuff.append(";" + LABEL_PARAM + "=");
    		cardBuff.append(email.customLabel);
    	}
    	switch (email.type) {
    	case Contacts.ContactMethodsColumns.TYPE_WORK:
    		cardBuff.append(";WORK");
    		break;
    	}
    	
    	if (!StringUtil.isASCII(email.data))
    		cardBuff.append(";CHARSET=UTF-8");

    	cardBuff.append(":").append(email.data.trim()).append(NL);
    }

    /**
     * Format a phone as a vCard field.
     *  
     * @param formatted Formatted phone will be appended to this buffer
     * @param phone The rowdata containing the actual phone data.
     */
    public static void formatPhone(Appendable formatted, Contact.RowData phone) throws IOException  {
    	formatted.append("TEL");
    	if (phone.preferred)
    		formatted.append(";PREF");

    	if (phone.customLabel != null) {
    		formatted.append(";" + LABEL_PARAM + "=");
    		formatted.append(phone.customLabel);
    	}
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
    	
    	
    	if (!StringUtil.isASCII(phone.data))
    		formatted.append(";CHARSET=UTF-8");
    	formatted.append(":").append(phone.data.trim()).append(NL);
    }
    
    /**
     * Format a phone as a vCard field.
     *  
     * @param formatted Formatted phone will be appended to this buffer
     * @param addr The rowdata containing the actual phone data.
     */
    public static void formatAddr(Appendable formatted, Contact.RowData addr)  throws IOException  {
    	formatted.append("ADR");
    	if (addr.preferred)
    		formatted.append(";PREF");

    	if (addr.customLabel != null) {
    		formatted.append(";" + LABEL_PARAM + "=");
    		formatted.append(addr.customLabel);
    	}
    	
    	switch (addr.type) {
    	case Contacts.ContactMethodsColumns.TYPE_HOME:
    		formatted.append(";HOME");
    		break;
    	case Contacts.PhonesColumns.TYPE_WORK:
    		formatted.append(";WORK");
    		break;
    	}
    	if (!StringUtil.isASCII(addr.data))
    		formatted.append(";CHARSET=UTF-8");
    	formatted.append(":;;").append(addr.data.replace(", ", ";").trim()).append(NL);
    }
    
    /**
     * Format an IM contact as a vCard field.
     *  
     * @param formatted Formatted im contact will be appended to this buffer
     * @param addr The rowdata containing the actual phone data.
     */
    public static void formatIM(Appendable formatted, Contact.RowData im)  throws IOException  {
    	formatted.append(IMPROP);
    	if (im.preferred)
    		formatted.append(";PREF");
    	
    	if (im.customLabel != null) {
    		formatted.append(";" + LABEL_PARAM + "=");
    		formatted.append(im.customLabel);
    	}
    	
    	switch (im.type) {
    	case Contacts.ContactMethodsColumns.TYPE_HOME:
    		formatted.append(";HOME");
    		break;
    	case Contacts.ContactMethodsColumns.TYPE_WORK:
    		formatted.append(";WORK");
    		break;
    	}
    	
    	if (im.auxData != null) {
    		formatted.append(";").append(PROTO_PARAM).append("=").append(im.auxData);
    	}
    	if (!StringUtil.isASCII(im.data))
    		formatted.append(";CHARSET=UTF-8");
    	formatted.append(":").append(im.data.trim()).append(NL);
    }
    
    /**
     * Format Organization fields.
     *  
     *  
     *  
     * @param formatted Formatted organization info will be appended to this buffer
     * @param addr The rowdata containing the actual organization data.
     */
    public static void formatOrg(Appendable formatted, Contact.OrgData org)  throws IOException  {
    	if (org.company != null) {
    		formatted.append("ORG");
        	if (org.customLabel != null) {
        		formatted.append(";" + LABEL_PARAM + "=");
        		formatted.append(org.customLabel);
        	}
        	if (!StringUtil.isASCII(org.company))
        		formatted.append(";CHARSET=UTF-8");
        	formatted.append(":").append(org.company.trim()).append(NL);
        	if (org.title == null)
        		formatted.append("TITLE:").append(NL);
    	}
    	if (org.title != null) {
        	if (org.company == null)
        		formatted.append("ORG:").append(NL);
    		formatted.append("TITLE");
        	if (org.customLabel != null) {
        		formatted.append(";" + LABEL_PARAM + "=");
        		formatted.append(org.customLabel);
        	}
        	if (!StringUtil.isASCII(org.title))
        		formatted.append(";CHARSET=UTF-8");
        	formatted.append(":").append(org.title.trim()).append(NL);
    	}
    }
    
    /**
     * Write the contact vCard to an appendable stream.
     */
    public void writeVCard(Contact contact, Appendable vCardBuff) throws IOException {
        // Start vCard

        vCardBuff.append("BEGIN:VCARD").append(NL);
        vCardBuff.append("VERSION:2.1").append(NL);
        
       	appendField(vCardBuff, "X-IRMC-LUID", contact.syncid);
        
        vCardBuff.append("N");

    	if (!StringUtil.isASCII(contact.lastName) || !StringUtil.isASCII(contact.firstName) || 
    			!StringUtil.isASCII(contact.midNames) || !StringUtil.isASCII(contact.preName) || !StringUtil.isASCII(contact.sufName))
    		vCardBuff.append(";CHARSET=UTF-8");
    	
        vCardBuff.append(":").append((contact.lastName != null) ? contact.lastName.trim() : "")
                .append(";").append((contact.firstName != null) ? contact.firstName.trim() : "")
                .append(";").append((contact.midNames != null) ? contact.midNames.trim() : "")
                .append(";").append((contact.preName != null) ? contact.preName.trim() : "")
                .append(";").append((contact.sufName != null) ? contact.sufName.trim() : "").append(NL);
        
        for (Contact.RowData email : contact.emails) {
    		formatEmail(vCardBuff, email);
    	}
        
        for (Contact.RowData phone : contact.phones) {
    		formatPhone(vCardBuff, phone);
    	}

        for (Contact.OrgData org : contact.orgs) {
        	formatOrg(vCardBuff, org);
        }
        
        for (Contact.RowData addr : contact.addrs) {
        	formatAddr(vCardBuff, addr);
        }

        for (Contact.RowData im : contact.ims) {
        	formatIM(vCardBuff, im);
        }

        appendField(vCardBuff, "NOTE", contact.notes);
        appendField(vCardBuff, "BDAY", contact.birthday);
        
        if (contact.photo != null) {
        	appendField(vCardBuff, "PHOTO;TYPE=JPEG;ENCODING=BASE64", " ");
        	vCardBuff.append(" ");
        	Base64Coder.mimeEncode(vCardBuff, contact.photo, 76, NL + " ");
        	vCardBuff.append(NL);
        	vCardBuff.append(NL);
        }

        // End vCard
        vCardBuff.append("END:VCARD").append(NL);
    }

    /**
     * Append the field to the StringBuffer out if not null.
     */
    private static void appendField(Appendable out, String name, String val) throws IOException {
        if(val != null && val.length() > 0) {
        	out.append(name);
        	if (!StringUtil.isASCII(val))
        		out.append(";CHARSET=UTF-8");
            out.append(":").append(val).append(NL);
        }
    }

    /**
     * Check if the email string is well formatted
     */
    @SuppressWarnings("unused")
	private boolean checkEmail(String email) {
        return (email != null && !"".equals(email) && email.indexOf("@") != -1);
    }
}
