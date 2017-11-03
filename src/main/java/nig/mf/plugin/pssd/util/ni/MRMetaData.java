package nig.mf.plugin.pssd.util.ni;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Date;

import nig.io.BinaryInputStream;
import nig.io.LittleEndianDataInputStream;
import nig.util.DateUtil;

/**
 * Siemens VB15-VB17 RaidFile structure
 * This is derived from the same parsing code as is in the raw MR upload client
 * 
 * @author nebk
 *
 */
public class MRMetaData {

	// These three remain null if not working with a file
	private File _file = null;
	private String _ext = null;            // File extension
	private String _fileName = null;
	//
	private String _firstName = null;
	private String _lastName = null;
	private String _patientID = null;
	private String _patientSex = null;
	private Date _patientDOB = null;
	private Date _date = null;             // Extracted from FrameOfReference. Date at patient registration.
	private String _FoR = null;            // The full  FrameOfReference

	public MRMetaData (File file) throws Throwable {
		parse(file);
	}

	public MRMetaData (LittleEndianDataInputStream din) throws Throwable {
		parseAndClose(din);
	}


	public String getFileName  () {return _fileName;};
	public String getFirstName() {return _firstName;};
	public String getFullName() {
		if (_firstName!=null) {
			return _firstName + " " + _lastName;
		} else {
			return _lastName;
		}
	}
	public String getLastName() {return _lastName;};
	public String getID () {return _patientID;};
	public Date getFoRDate () {return _date;};
	public String getFoR () {return _FoR;};
	public String getExtension () {return _ext;};
	public Date getDOB () {return _patientDOB;};
	public String getSex () {return _patientSex;};


	public void parse (File file) throws Throwable {
		_file = file;
		_fileName = _file.getName();

		// Get file extension (should be .dat)
		String[] parts = _fileName.split("\\.");
		int l = parts.length;
		_ext = parts[l-1];

		// Read file
		InputStream in = new FileInputStream(_file);

		// JVM Assumes Big Endian. Need to flip.
		LittleEndianDataInputStream din = new LittleEndianDataInputStream(in);

		parseAndClose (din);
	}
	
	
	private void parseAndClose (LittleEndianDataInputStream din) throws Throwable {

		Integer nProtHeaderLen = din.readInt();
		Integer nNrProtocols = din.readInt();
//		System.out.println("nProtocols="+nNrProtocols);
		for (int i=0; i<nNrProtocols; i++) {

			// Protocol Name
			byte[] pcProtName = new byte[32];
			int idx = 0;
			while (true) {
				din.read(pcProtName, idx, 1);
				if (pcProtName[idx]==0) break;
				idx++;
			}
			String protocolName = new String(pcProtName, 0, idx);
//			System.out.println("Protocol Name='"+protocolName +"'");

			// Read the protocol buffer
			Integer protocolLength = din.readInt();
			byte[] header = new byte[protocolLength];
			din.read(header, 0, protocolLength);

			// Convert to a string
			String stringHeader = new String(header);

			//			PrintWriter p = new PrintWriter(protocolName + ".txt");
			//			p.write(stringHeader);
			//
			if (protocolName.equals("Config")) {

				// Find things of interest
				_patientID = find ("PatientID", stringHeader, false);

				String patientName = find ("tPatientName", stringHeader, false);
				parseName (patientName);
				//
				String dob = find ("PatientBirthDay", stringHeader, false);
				_patientDOB = parseDOB (dob);
				//
				String sex = find ("PatientSex", stringHeader, true);
				_patientSex = parseSex (sex);

				//
				_FoR = find ("FrameOfReference", stringHeader, false);
				_date = parseDate (_FoR);
			}
			//			p.close();
		}

		din.close();
		if (_patientID==null || _lastName==null || _date==null || _FoR==null) {
			throw new Exception("Failed to extract minimum meta-data from header.");
		}
	}


	private String parseSex (String sex) throws Throwable {
		if (sex.equals("1")) {	
			return "Female";
		} else if (sex.equals("2")) {
			return "Male";
		} else {
			return "Unknown";
		}
	}

	private Date parseDOB (String dob) throws Throwable {
		//  <ParamString."PatientBirthDay">  { "19800101"  }
		return DateUtil.dateFromString(dob, "yyyyMMdd");
	}


	/*
	public void parseNew () throws Throwable {

		// Get file extension (should be .dat)
		String[] parts = _fileName.split("\\.");
		int l = parts.length;
		_ext = parts[l-1];

		// Read file
		InputStream in = new FileInputStream(_file);

		// JVM Assumes Big Endian. Need to flip.
		BinaryInputStream din = new BinaryInputStream(in, true);

		Integer nProtHeaderLen = din.readInt();
		Integer nNrProtocols = din.readInt();
		System.out.println("nProtocols="+nNrProtocols);
		for (int i=0; i<nNrProtocols; i++) {

			// Protocol Name
			byte[] pcProtName = new byte[32];
			int idx = 0;
			while (true) {
				din.read(pcProtName, idx, 1);
				if (pcProtName[idx]==0) break;
				idx++;
			}
			String protocolName = new String(pcProtName, 0, idx);
			System.out.println("Protocol Name='"+protocolName +"'");

			// Read the protocol buffer
			Integer protocolLength = din.readInt();
			byte[] header = new byte[protocolLength];
			din.read(header, 0, protocolLength);

			// Convert to a string
			String stringHeader = new String(header);

//			PrintWriter p = new PrintWriter(protocolName + ".txt");
//			p.write(stringHeader);
			//
			if (protocolName.equals("Config")) {

				// Find things of interest
				_patientID = find ("PatientID", stringHeader);
				String patientName = find ("tPatientName", stringHeader);
				parseName (patientName);
				//
				String frOfRef = find ("FrameOfReference", stringHeader);
				_uid = frOfRef;
				_date = parseDate (frOfRef);
			}
//			p.close();
		}

		din.close();
		if (_patientID==null || _lastName==null || _date==null || _uid==null) {
			throw new Exception("Failed to extract meta-data from header.");
		}
	}

	 */



	private String find (String thing, String header, Boolean isLong) throws Throwable {
		int idx = header.indexOf(thing);
		if (idx<=0) return null;

		// The pattern is  e.g.   
		// <ParamString."PatientID">  { "H000429"  }
		// or
		// <ParamLong."PatientSex">  { 1  }

		// where  e.g. thing=PatientID or PatientSex
		int start = header.indexOf("{", idx);
		int end = header.indexOf("}", start);
		if (start<=0 || end<=0 || end<=start) {
			return null;
		}

		// Items are surrounded by '" ' for Strings
		if (isLong) {
			return header.substring(start+2, end-2);
		} else {
			// Items are surrounded by '" ' for Strings
			return header.substring(start+3, end-3);
		}
	}

	private Date parseDate (String date) throws Throwable {
		// Of the form  1.3.12.2.1107.5.2.34.18978.1.20140718161933906.0.0.0
		//                                           DATEHERE
		// The time after the date isn't useful to us as its the time
		// when the patient was registered not the time when the
		// data were acquired
		if (date==null) return null;
		String[] parts = date.split("\\.");
		int l = parts.length;
		if (l==14) {
			String t = parts[10];
			String t2 = t.substring(0,8);
			return DateUtil.dateFromString(t2, "yyyyMMdd");
		}
		return null;
	}

	private void parseName (String name) {
		// Last^First
		String[] parts = name.split("\\^");
		int l = parts.length;
		if (l>=2) {
			_firstName = parts[l-1];
			_lastName = parts[0];
		} else {
			_firstName = null;
			_lastName = name;
		}
	}
	public void print () throws Throwable {
		System.out.println("  File name           = " + _fileName);
		if (_firstName!=null) {
			System.out.println("  Patient first name  = " + _firstName);
		}
		if (_lastName!=null) {
			System.out.println("  Patient last name   = " + _lastName);
		}
		if (_patientID != null) {
			System.out.println("  Patient ID          = " + _patientID);
		}
		if (_patientDOB != null) {
			String tDate = DateUtil.formatDate(_patientDOB, false, false);
			System.out.println("  Patient DOB         = " + tDate);
		}
		if (_patientSex != null) {
			System.out.println("  Patient Sex         = " + _patientSex);
		}
		if (_date != null) {
			String tDate = DateUtil.formatDate(_date, false, false);
			System.out.println("  Date                = " + tDate);
		}
		if (_FoR!=null) {
			System.out.println("  FrameOfReference     = " + _FoR);
		}
	}

	public void printToWriter (PrintWriter writer) throws Throwable {
		writer.println("  File name           = " + _fileName);
		if (_firstName!=null) {
			writer.println("  Patient first name  = " + _firstName);
		}
		if (_lastName!=null) {
			writer.println("  Patient last name   = " + _lastName);
		}
		if (_patientID != null) {
			writer.println("  Patient ID          = " + _patientID);
		}
		if (_patientDOB != null) {
			String tDate = DateUtil.formatDate(_patientDOB, false, false);
			writer.println("  Patient DOB         = " + tDate);
		}
		if (_patientSex != null) {
			writer.println("  Patient Sex         = " + _patientSex);
		}
		if (_date != null) {
			String tDate = DateUtil.formatDate(_date, false, false);
			writer.println("  Date                = " + tDate);
		}
		if (_FoR!=null) {
			writer.println("  FrameOfReference     = " + _FoR);
		}
	}


}
