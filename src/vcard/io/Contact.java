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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Contact item
 */
public class Contact {
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
    // Contact additional names
    String midNames;
    // Contact Prefixes
    String preName;
    // Contact Suffixes
    String sufName;
    
    static class RowData {
    	RowData(int type, String data, boolean preferred, String customLabel) {
    		this.type = type;
    		this.data = data;
    		this.preferred = preferred;
    		this.customLabel = customLabel;
    		auxData = null;
    	}
    	RowData(int type, String data, boolean preferred) {
    		this(type, data, preferred, null);
    	}

    	int type;
    	String data;
    	boolean preferred;    	
    	String customLabel;
    	String auxData;
    }
    
    static class OrgData {
    	OrgData(int type, String title, String company, String customLabel) {
    		this.type = type;
    		this.title = title;
    		this.company = company;
    		this.customLabel = customLabel;
    	}
    	int type;
    	
        // Contact title
        String title;
        // Contact company name
    	String company;
    	
    	String customLabel;
    }
    
    // Phones dictionary; keys are android Contact Column ids
    List<RowData> phones;
    
    // Emails dictionary; keys are android Contact Column ids
    List<RowData> emails;
    
    // Address dictionary; keys are android Contact Column ids
    List<RowData> addrs;

    // Instant message addr dictionary; keys are android Contact Column ids
    List<RowData> ims;

    // Organizations list
    List<OrgData> orgs;
    
    // Compressed photo
    byte[] photo;
    
    // Contact note
    String notes;

    // Contact's birthday
    String birthday;
    
	public void reset() {
		 _id = null;
		 syncid = null;
		 displayName = null;
		 notes = null;
		 birthday = null;
		 photo = null;
		 firstName = null;
		 lastName = null;
		 midNames = null;
		 preName = null;
		 sufName = null;
		 if (phones == null) phones = new ArrayList<RowData>();
		 else phones.clear();
		 if (emails == null) emails = new ArrayList<RowData>();
		 else emails.clear();
		 if (addrs == null) addrs = new ArrayList<RowData>();
		 else addrs.clear();
		 if (orgs == null) orgs = new ArrayList<OrgData>();
		 else orgs.clear();
		 if (ims == null) ims = new ArrayList<RowData>();
		 else ims.clear();
	}

    final static Pattern namePattern = Pattern.compile("(([^,]+),(.*))|(\\s*(\\S+)((\\s+(\\S+))*)\\s+(\\S+))");
	
	/**
	 * Parse a display name into first, middle and last names.
	 * Note: prefixes and suffixes are not recognized.
	 * @param displayName
	 */
	protected void parseDisplayName(String displayName) {
        if (displayName != null) {
        	Matcher m = namePattern.matcher(displayName);
        	if (m.matches()) {
        		if (m.group(1) != null) {
        			lastName = m.group(2);
        			firstName = m.group(3);
        		} else {
        			firstName = m.group(5);
        			lastName = m.group(9);
        			if (m.group(6) != null) {
        				midNames = m.group(6).trim();
        			}
        		}
        	} else {
        		firstName = displayName.trim();
        		lastName = "";
        	}
        } else {
        	firstName = lastName = "";
        }		
	}
	
    // Constructors------------------------------------------------
	public Contact() {
		reset();
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
}
