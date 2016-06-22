package nig.mf.plugin.pssd.ni;

import java.sql.ResultSet;
import java.util.Date;
import java.util.Vector;

import mbc.FMP.MBCFMP;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;


public class SvcMBCPETVarCheck extends PluginService {
	// Relative location of resource file on server
	private static final String FMP_CRED_REL_PATH = "/.fmp/petct_fmpcheck";
	private static final String EMAIL = "williamsr@unimelb.edu.au";
	//private static final String EMAIL = "nkilleen@unimelb.edu.au";

	private Interface _defn;

	public SvcMBCPETVarCheck() throws Throwable {

		_defn = new Interface();
		Interface.Element me = new Interface.Element(
				"cid",
				CiteableIdType.DEFAULT,
				"The identity of the parent Study.  All child DataSets (and in a federation children will be found on all peers in the federsation) containing DICOM data will be found and sent.",
				0, Integer.MAX_VALUE);

		_defn.add(me);
		//
		me = new Interface.Element("id", AssetType.DEFAULT, "The asset identity (not citable ID) of the parent Study.",
				0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("email", StringType.DEFAULT, "The destination email address. Over-rides any built in one.",
				0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("debug", BooleanType.DEFAULT, "Add some print diagnostics to the mediaflux server log Default is false.",
				0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("force", BooleanType.DEFAULT, "Over-ride the meta-data on the Study indicating that this Study has alreayd been checked, and chedk regardless. Defaults to false.",
				0, 1);
		_defn.add(me);

	}

	@Override
	public String name() {
		return "nig.pssd.mbic.petvar.check";
	}

	@Override
	public String description() {

		return "Compares DICOM variables with values stored in FileMakerPro.  Inserts scan time into FMP and compares pharmaceutical injection start time and dose and reports discrepancies.  Meta-data are written on the parent study in nig-pssd:pssd-mbic-fmp-check when the check has been completed. ";
	}

	@Override
	public Interface definition() {

		return _defn;
	}

	@Override
	public Access access() {

		return ACCESS_ACCESS;
	}

	@Override
	public boolean canBeAborted() {

		return false;
	}

	@Override
	public void execute(XmlDoc.Element args, Inputs in, Outputs out, XmlWriter w) throws Throwable {

		// Parse input ID
		Boolean force = args.booleanValue("force", false);
		Boolean dbg = args.booleanValue("debug", false);
		String studyID = args.value("id");
		String studyCID = args.value("cid");
		String email = args.stringValue("email", EMAIL);
		if (studyID!=null && studyCID!=null) {
			throw new Exception ("Can't supply 'id' and 'cid'");
		}
		if (studyID==null && studyCID==null) {
			throw new Exception ("Must supply 'id' or 'cid'");
		}
		if (studyCID==null) studyCID = CiteableIdUtil.idToCid(executor(), studyID);

		// Get the parent
		XmlDoc.Element studyMeta = AssetUtil.getAsset(executor(), studyCID, null);
		String model = studyMeta.value("asset/model");
		if (model==null || (model!=null && !model.equals("om.pssd.study"))) {
			throw new Exception ("Supplied parent is not a Study");
		}

		// Have we already checked this Study sufficiently ?
		Boolean petIsChecked = checked(executor(), true, studyMeta);
		Boolean ctIsChecked = checked(executor(), false, studyMeta);
		if (force) {
			petIsChecked = false;
			ctIsChecked = false;
		}
		if (petIsChecked && ctIsChecked) return;

		// We are going to work with one PET DataSet (any will do) and
		// one CT DataSet (has to be the right one). We expect one or both
		// to be present generally.

		// Find one (any) PET DataSet  under the given parent Study
		String petDataSetCID = null;
		if (!petIsChecked) petDataSetCID = findPETDataSet (executor(), studyCID);

		// Now find the CT DataSet that we want under the parent Study
		String ctDataSetCID = null;
		if (!ctIsChecked) ctDataSetCID = findCTDataSet(executor(), studyCID);

		// Return if nothing found
		if (petDataSetCID==null && ctDataSetCID==null) {
			return;
		}

		// Extract the native DICOM meta-data from the DataSets. Null if no cid
		XmlDoc.Element petDICOMMeta = dicomMetaData (executor(), petDataSetCID);
		XmlDoc.Element ctDICOMMeta = dicomMetaData (executor(), ctDataSetCID);

		// Find one "acquisition date" from the DataSets.  We need to fetch this
		// from the DICOM header as its not in indexed meta-data.
		Date date = findDate (executor(), petDICOMMeta, ctDICOMMeta, studyCID, email);

		// No date means no PET or CT DataSet was found (or they didn't have a date)
		// So there is nothing to do
		if (date==null) return;

		// Open FMP database with credential in server side resource file holding
		// <dbname>,<ip>,<user>,<encoded pw>
		MBCFMP mbc = null;
		try {
			String t = System.getenv("HOME");
			String path = t +  FMP_CRED_REL_PATH;
			mbc = new MBCFMP(path);
		} catch (Throwable tt) {
			throw new Exception ("Failed to establish JDBC connection to FileMakerPro");
		}

		// Find parent Subject
		String subject = null;
		if (petDataSetCID!=null) {
			subject = CiteableIdUtil.getSubjectId(petDataSetCID);
		} else if (ctDataSetCID!=null) {
			subject = CiteableIdUtil.getSubjectId(ctDataSetCID);
		}
		if (subject==null) {
			String message = "nig.pssd.mbc.petvar.check : Could not extract Subject citable ID from PET DataSet '" + petDataSetCID + "' or CT DataSet '" + ctDataSetCID + "'";
			System.out.println (message);
			send(executor(), email, message);
			throw new Exception (message);
		}
		// Fetch Subject meta-data
		XmlDoc.Element subjectMeta = AssetUtil.getAsset(executor(), subject, null);

		// Fetch DICOM patient Meta from Subject and extract patient name
		XmlDoc.Element dicomPatient = subjectMeta.element("asset/meta/mf-dicom-patient");
		if (dicomPatient==null) {
			System.out.println ("nig.pssd.mbc.petvar.check : Could not locate mf-dicom-patient record on Subject " + subject);
			throw new Exception ("Could not locate mf-dicom-patient record on Subject " + subject);
		}

		DICOMPatient dp = new DICOMPatient(dicomPatient);
		String mbcPatientID = dp.getID();
		String patientName = dp.getFullName();


		// Find PET visits in FMP for this patient and date.
		// There may be one (usual) or two (the 'blood sample' which always comes first)
		ResultSet petVisits = null;
		try {
			//
			petVisits = mbc.getPETVisits(mbcPatientID, null, date);
		} catch (Throwable t) {
			// Skip this one with message
			String error = "nig.pssd.mbc.petvar.check : For DataSet '" + petDataSetCID + "' no PET visit found in FMP for subject " +
					patientName + "(MBCID=" + mbcPatientID + ") and date " + date.toString() + "\n";
			send (executor(), email, error);
			mbc.closeConnection();
			return;
		}

		// Check there are only one or two visits found in FMP
		int n = numberVisits (petVisits);
		// System.out.println("Number visits = " + n);
		if (n!=1 && n!=2) {
			String error = "For DataSet '" + petDataSetCID + "' found " + n + " PET visits (expecting 1 or 2) in FMP for patient " +
					patientName + "(MBCID=" + mbcPatientID + ") and date " + date.toString() + "\n";
			send (executor(), email, error);
			mbc.closeConnection();
			return;
		}

		// Compare DICOM with FMP values
		String diff = compareSetValues (executor(), dbg, studyCID, mbc, date, petDICOMMeta, ctDICOMMeta, petVisits, patientName, mbcPatientID, 
				petDataSetCID, ctDataSetCID);

		// Populate email if errors found
		if (diff!=null) {
			send (executor(), email, diff);
		}


		// Close FMP
		mbc.closeConnection();
	}


	private Boolean checked (ServiceExecutor executor, Boolean isPET, XmlDoc.Element studyMeta) throws Throwable {

		XmlDoc.Element meta = studyMeta.element("asset/meta/nig-daris:pssd-mbic-fmp-check");
		if (meta==null) return false;
		if (isPET) {
			XmlDoc.Element t = meta.element("pet");
			if (t==null) return false;
			if (t.booleanValue()==true) return true;		
		} else {
			XmlDoc.Element t = meta.element("ct");
			if (t==null) return false;
			if (t.booleanValue()==true) return true;		
		}
		return false;
	}

	private XmlDoc.Element dicomMetaData (ServiceExecutor executor, String cid) throws Throwable {
		if (cid==null) return null;
		String id = CiteableIdUtil.cidToId(executor, cid);
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", id);
		return executor.execute("dicom.metadata.get", dm.root());
	}

	private Date findDate (ServiceExecutor executor, XmlDoc.Element petDICOM, XmlDoc.Element ctDICOM, String studyCID, String email) throws Throwable {
		Date petDate = null;
		Date ctDate = null;

		// We may have PET or CT or both
		if (petDICOM!=null) {
			petDate = petDICOM.dateValue("de[@tag='00080022']/value");
		}
		if (ctDICOM!=null) {
			ctDate = ctDICOM.dateValue("de[@tag='00080022']/value");
		}
		// Compare
		if (petDate==null && ctDate==null) return null;
		if (petDate!=null && ctDate!=null) {
			String t1 = DateUtil.formatDate(petDate, "dd-MMM-yyyy");
			String t2 = DateUtil.formatDate(ctDate, "dd-MMM-yyyy");
			if (t1.equals(t2)) {
				return petDate;
			} else {
				String message = "nig.pssd.mbc.petvar.check : The acquisition dates of the located PET and CT DataSets under Study " + studyCID +
						" have different dates - something is seriously wrong";
				send (executor, email, message);
				throw new Exception (message);
			}
		} else if (petDate!=null) {
			return petDate;
		} else if (ctDate!=null) {
			return ctDate;
		}
		return null;
	}

	private String findPETDataSet (ServiceExecutor executor, String cid) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pdist", "0");
		dm.add("action", "get-cid");
		dm.add("size", "1");
		String query = "(cid='" + cid + "' or cid starts with '" + cid + "') and model='om.pssd.dataset' and xpath(mf-dicom-series/modality)='PT'";
		dm.add("where", query);
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r==null) return null;

		// FInd CID of DataSet
		return r.value("cid");
	}

	private String findCTDataSet (ServiceExecutor executor, String cid) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pdist", "0");
		dm.add("action", "get-cid");
		dm.add("size", "1");

		// To get the right CT that matches what is expected in FMP, we *don't* want the Topogram scan or the Patient Protocol scan
		String query = "(cid='" + cid + "' or cid starts with '" + cid + "') and model='om.pssd.dataset' and xpath(mf-dicom-series/modality)='CT'" +
				" and (not(xpath(mf-dicom-series/description) contains literal('Topogram')) and " +
				" not(xpath(mf-dicom-series/description) contains literal('Patient Protocol')))";
		dm.add("where", query);
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r==null) return null;

		// FInd CID of DataSet
		return r.value("cid");
	}


	private void send (ServiceExecutor executor, String email,  String body) throws Throwable {
		// Send email if errors were found
		if (body!=null) {
			String subject = "Errors found comparing PET DICOM meta-data with FileMakerPro data base entries";
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("async", "true");
			dm.add("to", email);
			dm.add("body", body);
			dm.add("subject", subject);
			executor.execute("mail.send", dm.root());
		}
	}
	private int numberVisits (ResultSet rs) throws Throwable {
		if (rs==null) return 0;
		int n = 0;
		rs.beforeFirst();
		while (rs.next()) {
			n++;
		}
		return n;
	}


	private String compareSetValues (ServiceExecutor executor, Boolean dbg, String studyCID, MBCFMP mbc, Date date, XmlDoc.Element petDICOMMeta, XmlDoc.Element ctDICOMMeta, 
			ResultSet petVisits, String patientName, String mbcPatientID, String petDataSetCID, String ctDataSetCID) throws Throwable {

		if (dbg) System.out.println("Enter SvcMBCPETVarCheck:compareSetValues");

		// Fetch Values from PET DICOM if available
		String doseDICOMFormatted = null;
		String startInjectionTimeDICOM = null;
		String startInjectionTimeDICOM2 = null;
		String petScanTimeDICOM = null;
		if (petDataSetCID!=null) { 
			if (dbg) System.out.println("   petDataSetCID = " + petDataSetCID);
			XmlDoc.Element rp = petDICOMMeta.element("de[@tag='00540016']");
			XmlDoc.Element rp2 = rp.element("de[@tag='FFFEE000']");
			//
			String doseDICOM = rp2.value("de[@tag='00181074']/value");
			Float f = Float.parseFloat(doseDICOM) / 1000000.0f;
			doseDICOMFormatted = String.format("%.1f%n", f).trim();
			if (dbg) System.out.println("      DICOM dose = " + doseDICOM);
			//
			startInjectionTimeDICOM = rp2.value("de[@tag='00181072']/value");
			startInjectionTimeDICOM2 = startInjectionTimeDICOM.substring(0,4);    // Rob enters just hh:mm in FMP
			if (dbg) System.out.println("      DICOM injection time = " + startInjectionTimeDICOM2);
			// Acquisition Time
			petScanTimeDICOM = petDICOMMeta.value("de[@tag='00080032']/value");
			if (dbg) System.out.println("      PET DICOM Scan time = " + petScanTimeDICOM);
		}


		// Fetch values from CT DICOM header if available
		String ctScanTimeDICOM = null;
		if (ctDataSetCID!=null) {
			if (dbg) System.out.println("   ctDataSetCID = " + ctDataSetCID);
			// Acquisition Time
			ctScanTimeDICOM = ctDICOMMeta.value("de[@tag='00080032']/value");
			if (dbg) System.out.println("      CT DICOM Scan time = " + ctScanTimeDICOM);
		}

		// Prepare
		Boolean matchedDose = false;
		Boolean matchedInjectionTime = false;
		Vector<String> fmpInjectionTimes = new Vector<String>();
		Vector<String> fmpDoses = new Vector<String>();

		// Is this the special 'blood pool' PET DataSet.  It is guarenteed that the string '1_9' will
		// be found in the blood pool DataSet.  That's not very robust is it Rob !
		String studyDescription = petDICOMMeta.value("de[@tag='00081030']/value");
		
		
		// Algorithm  changed from .contains("1_9") to the current one as below.
		// This changed on 07-Jan-2016 as blood pool scans are currently
		// not being done and the use of the string PERF at the end only
		// for future scans is slightly more robust.
		String s[] = studyDescription.split("_"); 
		int n = s.length;
		String s2 = s[n-1];
		Boolean isBloodPool = false;
		if (s2!=null && s2.contains("1_PERF")) isBloodPool = true;
		if (dbg) System.out.println("   DICOM isBloodPool = " + isBloodPool);

		// Iterate over the FMP PET Visits
		petVisits.beforeFirst();
		int iVisit = 0;
		while (petVisits.next()) {

			// Fetch FMP values
			if (dbg) System.out.println ("   PET VIsit " + iVisit++);
			String doseFMP = petVisits.getString("injected_activity");
			String startInjectionTimeFMP = petVisits.getString("Injection Time");
			String scanTypeFMP = petVisits.getString("Scan_Type");

			if (dbg) {
				System.out.println("      Patient ID '" + mbcPatientID + "'");
				System.out.println("      Scan Type '" + scanTypeFMP + "'");
				System.out.println("      FMP dose '" + doseFMP + "'");
				System.out.println("      FMP injection time '" + startInjectionTimeFMP + "'");
			}

			// Compare the right DICOM values with the right PET Visit (first visit [blood pool] 
			// or second visit [normal]). Each Study we handle is either for the blood pool visit or not
			if (  (scanTypeFMP.contains("EARLY") && isBloodPool) ||
					(!scanTypeFMP.contains("EARLY") && !isBloodPool) ) {

				// Compare Injection start time. Compare or insert if missing
				if (petDataSetCID!=null) {
					if (dbg) {
						System.out.println("      petDataSetCID = " + petDataSetCID);
					}
					if (!startInjectionTimeFMP.isEmpty()) {
						String[] t = startInjectionTimeFMP.split(":");
						if (t.length!=3) {
							fmpInjectionTimes.add(startInjectionTimeFMP);
						} else {
							String t2 = t[0] + t[1];   // Rob only enters hh:mm
							if (dbg) System.out.println("         Start times DICOM and FMP : " + startInjectionTimeDICOM2 + " " + t2);
							if (!startInjectionTimeDICOM2.equals(t2)) {
								fmpInjectionTimes.add(startInjectionTimeFMP.substring(0,5));
							} else {
								matchedInjectionTime = true;
							}
						} 
					}


					// Compare dose
					if (!doseFMP.isEmpty()) {
						Float f2 = Float.parseFloat(doseFMP);
						String f2s = String.format("%.1f%n", f2).trim();
						if (dbg) System.out.println("         Doses DICOM and FMP : " +doseDICOMFormatted + " " + f2s);
						//		s2 = "380.0";
						if (!doseDICOMFormatted.equals(f2s)) {
							fmpDoses.add(f2s);
						} else {
							matchedDose = true;
						}
					}


					// Insert PET scan time from DICOM (hh:mm:ss) which is primary
					String t2 = petScanTimeDICOM.substring(0,2) + ":" + 
							petScanTimeDICOM.substring(2,4) + ":" +
							petScanTimeDICOM.substring(4,6);
					if (dbg) {
						System.out.println("         Inserting PET scan start time " + t2 + " into FMP");
					}
					mbc.updateScanTime (mbcPatientID, date, scanTypeFMP, t2, true, dbg);
					
					// Insert DaRIS ID into FMP
					if (dbg) {
						System.out.println("         Inserting PET DataSet DaRIS ID " + petDataSetCID + " into FMP");
					}
					mbc.updateDaRISID (mbcPatientID, date, scanTypeFMP, studyCID, dbg);

					// Update the parent Study meta-data saying it's been checked for PET
					setStudyMetaData (executor, studyCID, true);
				}

				// Insert CT scan time from DICOM (hh:mm:ss) which is primary
				if (ctDataSetCID!=null) {
					if (dbg) {
						System.out.println("      ctDataSetCID = " + ctDataSetCID);
					}
					String t2 = ctScanTimeDICOM.substring(0,2) + ":" + 
							ctScanTimeDICOM.substring(2,4) + ":" +
							ctScanTimeDICOM.substring(4,6);

					if (dbg) {
						System.out.println("      Inserting CT scan start time " + t2 + " into FMP");
					}
					mbc.updateScanTime (mbcPatientID, date, scanTypeFMP, t2, false, dbg);

					if (dbg) {
						System.out.println("      Inserting CT DataSet DaRIS ID " + ctDataSetCID + " into FMP");
					}
					mbc.updateDaRISID (mbcPatientID, date, scanTypeFMP, studyCID, dbg);

					// Update the parent Study meta-data saying it's been checked for CT
					setStudyMetaData (executor, studyCID, false);
				}
			}
		}


		// Report inconsistencies 
		String error = "";
		Boolean err = false;
		if (petDataSetCID!=null) {
			if (!matchedInjectionTime) {
				String t = formatVector(fmpInjectionTimes);
				String t2 = startInjectionTimeDICOM.substring(0,2) + ":" + 
						startInjectionTimeDICOM.substring(2,4);
				error += "\nFor " + patientName + "(MBC ID=" + mbcPatientID + ") and date " +
						DateUtil.formatDate(date, "dd-MMM-yyyy") + " : DICOM ( " + 
						t2 + " ) and FMP " + t + " injection start times do not agree " +
						" (DataSet='" + petDataSetCID + "')";	
				err = true;
			}

			if (!matchedDose) {
				String t = formatVector(fmpDoses);
				error +=  "\nFor " + patientName + "(MBC ID=" + mbcPatientID + ") and date " + 
						DateUtil.formatDate(date, "dd-MMM-yyyy") + " : DICOM (" + 
						doseDICOMFormatted + ") and FMP  " + t + " dose values do not agree " +
						" (DataSet='" + petDataSetCID + "')";	
				err = true;
			}
		}

		if (err) {
			//System.out.println(error);
			return error;
		}
		return null;
	}


	private void setStudyMetaData (ServiceExecutor executor, String studyCID, Boolean isPET) throws Throwable {


		// Set meta-data on the Study indicating the PET or CT checking has been done
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", studyCID);
		dm.push("meta", new String[] {"action", "merge"});
		dm.push("nig-daris:pssd-mbic-fmp-check");
		if (isPET) {
			dm.add("pet", "true");
		} else {
			dm.add("ct", "true");
		}
		dm.pop();
		dm.pop();
		executor.execute("om.pssd.study.update", dm.root());

	}

	private String formatVector (Vector<String> vv) {
		if (vv.size()==0) return null;
		String t = "( ";
		for (String v : vv) {
			t += v + " ";
		}
		t += ")";
		return t;
	}
}