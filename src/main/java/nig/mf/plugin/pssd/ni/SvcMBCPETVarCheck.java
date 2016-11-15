package nig.mf.plugin.pssd.ni;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.Date;
import java.util.Vector;

import mbc.FMP.MBCFMP;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginLog;
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

	private Interface _defn;

	public SvcMBCPETVarCheck() throws Throwable {

		_defn = new Interface();
		Interface.Element me = new Interface.Element("cid",
				CiteableIdType.DEFAULT,
				"The identity of the parent Study.  All child DataSets (and in a federation children will be found on all peers in the federsation) containing DICOM data will be found and sent.",
				0, 1);

		_defn.add(me);
		//
		me = new Interface.Element("id", AssetType.DEFAULT,
				"The asset identity (not citable ID) of the parent Study.", 0,
				1);
		_defn.add(me);
		//
		me = new Interface.Element("email", StringType.DEFAULT,
				"The destination email address. Over-rides any built in one.",
				0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("force", BooleanType.DEFAULT,
				"Over-ride the meta-data on the Study indicating that this Study has already been checked, and check regardless. Defaults to false.",
				0, 1);
		_defn.add(me);
		me = new Interface.Element("update", BooleanType.DEFAULT,
				"Actually update FMP with the new values. Defaults to false.",
				0, 1);
		_defn.add(me);

		_defn.add(new Interface.Element("no-email", BooleanType.DEFAULT,
				"Do not send email. Defaults to false.", 0, 1));
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
	public void execute(XmlDoc.Element args, Inputs in, Outputs out,
			XmlWriter w) throws Throwable {

		// Parse input ID
		Boolean force = args.booleanValue("force", false);
		String studyID = args.value("id");
		String studyCID = args.value("cid");
		String email = args.stringValue("email", EMAIL);
		Boolean update = args.booleanValue("update", false);
		boolean noEmail = args.booleanValue("no-email", false);
		if(noEmail){
			email = null;
		}
		if (studyID != null && studyCID != null) {
			throw new Exception("Can't supply 'id' and 'cid'");
		}
		if (studyID == null && studyCID == null) {
			throw new Exception("Must supply 'id' or 'cid'");
		}
		if (studyCID == null) {
			studyCID = CiteableIdUtil.idToCid(executor(), studyID);
		}
		w.add("study-CID", studyCID);

		// Get the parent
		XmlDoc.Element studyMeta = AssetUtil.getAsset(executor(), studyCID, null);
		String model = studyMeta.value("asset/model");
		if (model == null
				|| (model != null && !model.equals("om.pssd.study"))) {
			throw new Exception("Supplied parent is not a Study");
		}

		// Have we already checked this Study sufficiently ?
		Boolean petIsChecked = checked(executor(), true, studyMeta);
		Boolean ctIsChecked = checked(executor(), false, studyMeta);
		if (force) {
			petIsChecked = false;
			ctIsChecked = false;
		}
		if (petIsChecked && ctIsChecked) {
			w.add("status", "Both PET and CT havea aready been checked - use force=true to over-ride");
			return;
		}

		// We are going to work with one PET DataSet (any will do) and
		// one CT DataSet (has to be the right one). We expect one or both
		// to be present generally.

		// Find one (good) PET DataSet under the given parent Study
		String petDataSetCID = null;
		if (!petIsChecked) {
			String[] t = findPETDataSet(executor(), studyCID);
			if (t != null) {
				if (t.length == 2) {
					// No good PET data sets found
					PluginLog.log().add(PluginLog.WARNING, t[1]);
					if (email != null) {
						send(executor(), email, t[1]);
					}
					w.add("status", t[1]);
					return;
				}
				petDataSetCID = t[0];
			}
		}

		// Now find the specific CT DataSet that we want under the parent Study
		String ctDataSetCID = null;
		if (!ctIsChecked) {
			ctDataSetCID = findCTDataSet(executor(), studyCID);
		}

		// Return if nothing found (could be a raw study)
		if (petDataSetCID == null && ctDataSetCID == null) {
			w.add("status", "No PET or CT data sets were found - may be a raw study.");
			return;
		}

		// Extract the native DICOM meta-data from the DataSets. Null if no cid
		XmlDoc.Element petDICOMMeta = dicomMetaData(executor(), petDataSetCID, null);
		XmlDoc.Element ctDICOMMeta = dicomMetaData(executor(), ctDataSetCID, null);

		// Find one "acquisition date" from the DataSets. We need to fetch this
		// from the DICOM header as its not in indexed meta-data.
		Date date = findDate(executor(), petDICOMMeta, ctDICOMMeta, studyCID, email);

		// No date means no PET or CT DataSet was found (or they didn't have a
		// date) so there is nothing to do
		if (date == null) {
			w.add("status", "No date could be found in the PET or CT DataSets");
			return;
		}

		// Find parent Subject from DaRIS Study
		String subject = null;
		if (petDataSetCID != null) {
			subject = CiteableIdUtil.getSubjectId(petDataSetCID);
		} else if (ctDataSetCID != null) {
			subject = CiteableIdUtil.getSubjectId(ctDataSetCID);
		}
		if (subject == null) {
			String message = "nig.pssd.mbc.petvar.check : Could not extract Subject citable ID from PET DataSet '"
					+ petDataSetCID + "' or CT DataSet '" + ctDataSetCID + "'";
			PluginLog.log().add(PluginLog.WARNING, message);
			if(email!=null){
				send(executor(), email, message);
			}
			w.add("status", message);
			return;
		}

		// Fetch Subject meta-data
		XmlDoc.Element subjectMeta = AssetUtil.getAsset(executor(), subject, null);

		// Fetch DICOM patient Meta from Subject and extract patient name
		XmlDoc.Element dicomPatient = subjectMeta.element("asset/meta/mf-dicom-patient");
		if (dicomPatient == null) {
			String error = "nig.pssd.mbc.petvar.check : Could not locate mf-dicom-patient record on Subject object. "
					+ subject;
			PluginLog.log().add(PluginLog.ERROR, error);
			if(email!=null) {
				send(executor(), email, error);
			}
			w.add("status", error);
			return;
		}

		DICOMPatient dp = new DICOMPatient(dicomPatient);
		String mbcPatientID = dp.getID();
		String patientName = dp.getFullName();
		w.add("MBC-patient-id", mbcPatientID);
	
		// Open FMP database with credential in server side resource file
		// holding
		// <dbname>,<ip>,<user>,<encoded pw>
		MBCFMP mbc = null;
		try {
			String t = System.getenv("HOME");
			String path = t + FMP_CRED_REL_PATH;
			mbc = new MBCFMP(path);
		} catch (Throwable tt) {
			throw new Exception(
					"Failed to establish JDBC connection to FileMakerPro");
		}

		// Find PET visits in FMP for this patient and date.
		ResultSet petVisits = null;
		try {
			petVisits = mbc.getPETVisits(mbcPatientID, null, date);
		} catch (Throwable t) {
			// Skip this one with message
			String error = "nig.pssd.mbc.petvar.check : For DataSet '"
					+ petDataSetCID + "' no PET visit found in FMP for subject "
					+ patientName + "(MBCID=" + mbcPatientID + ") and date "
					+ date.toString() + "\n";
			PluginLog.log().add(PluginLog.WARNING, error);
			if(email!=null){
				send(executor(), email, error);
			}
			w.add("status", error);
			mbc.closeConnection();
			return;
		}

		// Check there is at least one visit
		int n = numberVisits(petVisits);
		w.add("date", date);
		w.add("FMP-number-visits", n);
		if (n == 0) {
			String error = "For DataSet '" + petDataSetCID
					+ "' found no PET visits  in FMP for patient " + patientName
					+ "(MBCID=" + mbcPatientID + ") and date " + date.toString()
					+ "\n";
			w.add("status", error);
			PluginLog.log().add(PluginLog.WARNING, error);
			if(email!=null){
				send(executor(), email, error);
			}
			mbc.closeConnection();
			return;
		}

		// Find the first visit for which the DaRIS CID does not represent a
		// study. ROb inserts the Subject CID in FMP and this service updates it to the
		// actual Study
		int[] status = findVisit(studyCID, petVisits);
		int visitIdx = -1;
		if (status[0] == -1 && status[1] == -1) {
			String error = "For DataSet '" + petDataSetCID
					+ "' the algorithm to find the visit has failed";
			PluginLog.log().add(PluginLog.WARNING, error);
			if(email!=null){
				send(executor(), email, error);
			}
			w.add("status", error);
			mbc.closeConnection();
			return;
		} else {
			if (status[0] == 0) {
				// We did find a visit with a SUbject only CID
				w.add("FMP-visit-index", new String[] {
						"Study-CID-does-not-pre-exist-in-FMP", studyCID }, status[1]);
			} else if (status[0] == 1) {
				// We did not find a Visit with a Subject CID only, but we did
				// find a visit for which this Study CID was already set
				// That  just means we are redoing the same one already done
				// So we will do it again
				w.add("FMP-visit-index",
						new String[] { "Study-CID-pre-exists-in-FMP", studyCID },
						status[1]);
			}
		}

		// Compare DICOM with FMP values
		visitIdx = status[1];
		String diff = compareAndSetValues(executor(), update, visitIdx,
				studyCID, mbc, date, petDICOMMeta, ctDICOMMeta, petVisits,
				patientName, mbcPatientID, petDataSetCID, ctDataSetCID, w);

		// Populate email if errors found
		if (diff != null) {
			PluginLog.log().add(PluginLog.WARNING, diff);
			if(email!=null){
				send(executor(), email, diff);
			}
			w.add("status", diff);
		}

		// Close FMP
		mbc.closeConnection();
	}

	/**
	 * FInd the index of the first visit for which the DaRIS ID has a depth less
	 * than for a Study. ROb enters a SUbject CID into FMP. This is subsequently
	 * overwritten by this service with the actual Study CID
	 * 
	 * @param rs
	 * @return status [0] 1 if a visit with the Study CID is already set 
	 *                [0] 0 if a visit is found where the CID has depth less than a STudy
	 *                     (usually a SUbject) 
	 *                [0] -1 something is wrong 
	 *                [1] The visit index found 
	 *                [1] -1 something is wrong
	 * @throws Throwable
	 */
	private int[] findVisit(String studyCID, ResultSet rs) throws Throwable {
		int[] status = new int[2];
		status[0] = 0;
		status[1] = 0;
		rs.beforeFirst();
		int idx = 0;
		while (rs.next()) {
			String id = rs.getString("DARIS_ID");
			//			String scanType = rs.getString("Scan_Type");

			// We note if this CID is already in place. If it is, we are
			// redoing one already done.
			if (studyCID.equals(id)) {
				status[0] = 1;
				status[1] = idx;
				return status;
			}
			//
			int d = nig.mf.pssd.CiteableIdUtil.getIdDepth(id);
			int sd = CiteableIdUtil.studyDepth();
			if (d < sd) {
				status[0] = 0;
				status[1] = idx;
				return status;
			}
			idx++;
		}
		// shouldn't get here
		status[0] = -1;
		status[1] = -1;
		return status;
	}

	private Boolean checked(ServiceExecutor executor, Boolean isPET,
			XmlDoc.Element studyMeta) throws Throwable {

		XmlDoc.Element meta = studyMeta
				.element("asset/meta/nig-daris:pssd-mbic-fmp-check");
		if (meta == null)
			return false;
		if (isPET) {
			XmlDoc.Element t = meta.element("pet");
			if (t == null)
				return false;
			if (t.booleanValue() == true)
				return true;
		} else {
			XmlDoc.Element t = meta.element("ct");
			if (t == null)
				return false;
			if (t.booleanValue() == true)
				return true;
		}
		return false;
	}

	private XmlDoc.Element dicomMetaData(ServiceExecutor executor, String cid,
			String id) throws Throwable {
		if (cid == null && id == null)
			return null;
		if (cid != null)
			id = CiteableIdUtil.cidToId(executor, cid);
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", id);
		return executor.execute("dicom.metadata.get", dm.root());
	}

	private Date findDate(ServiceExecutor executor, XmlDoc.Element petDICOM,
			XmlDoc.Element ctDICOM, String studyCID, String email)
					throws Throwable {
		Date petDate = null;
		Date ctDate = null;

		// We may have PET or CT or both
		if (petDICOM != null) {
			petDate = petDICOM.dateValue("de[@tag='00080022']/value");
		}
		if (ctDICOM != null) {
			ctDate = ctDICOM.dateValue("de[@tag='00080022']/value");
		}
		// Compare
		if (petDate == null && ctDate == null)
			return null;
		if (petDate != null && ctDate != null) {
			String t1 = DateUtil.formatDate(petDate, "dd-MMM-yyyy");
			String t2 = DateUtil.formatDate(ctDate, "dd-MMM-yyyy");
			if (t1.equals(t2)) {
				return petDate;
			} else {
				String message = "nig.pssd.mbc.petvar.check : The acquisition dates of the located PET and CT DataSets under Study "
						+ studyCID
						+ " have different dates - something is seriously wrong";
				if (email != null) {
					send(executor, email, message);
				}
				throw new Exception(message);
			}
		} else if (petDate != null) {
			return petDate;
		} else if (ctDate != null) {
			return ctDate;
		}
		return null;
	}

	private String[] findPETDataSet(ServiceExecutor executor, String studyCID)
			throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pdist", "0");
		dm.add("action", "get-cid");
		dm.add("size", "infinity");
		String query = "(cid='" + studyCID + "' or cid starts with '" + studyCID
				+ "') and model='om.pssd.dataset' and xpath(mf-dicom-series/modality)='PT'";
		dm.add("where", query);
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r == null || !r.elementExists("cid"))
			return null;

		// We need a PET data set with the required DICOM meta-data element
		Collection<String> cids = r.values("cid");
		for (String cid : cids) {
			XmlDoc.Element dicomMeta = dicomMetaData(executor, cid, null);
			XmlDoc.Element rp = dicomMeta.element("de[@tag='00540016']");
			if (rp != null) {
				String[] t = { cid };
				return t;
			}
		}

		// Bummer
		String[] t = { studyCID,
				"No PET DataSet has the meta-data element [0054,0016] under study "
						+ studyCID };
		return t;
	}

	private String findCTDataSet(ServiceExecutor executor, String cid)
			throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pdist", "0");
		dm.add("action", "get-cid");
		dm.add("size", "1");

		// To get the right CT that matches what is expected in FMP, we *don't*
		// want the Topogram scan or the Patient Protocol scan
		String query = "(cid='" + cid + "' or cid starts with '" + cid
				+ "') and model='om.pssd.dataset' and xpath(mf-dicom-series/modality)='CT'"
				+ " and (not(xpath(mf-dicom-series/description) contains literal('Topogram')) and "
				+ " not(xpath(mf-dicom-series/description) contains literal('Patient Protocol')))";
		dm.add("where", query);
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r == null)
			return null;

		// FInd CID of DataSet
		return r.value("cid");
	}

	static public void send(ServiceExecutor executor, String email, String body)
			throws Throwable {
		// Send email if errors were found
		if (body != null) {
			String subject = "Errors found comparing PET DICOM meta-data with FileMakerPro data base entries";
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("async", "true");
			dm.add("to", email);
			dm.add("body", body);
			dm.add("subject", subject);
			executor.execute("mail.send", dm.root());
		}
	}

	static public int numberVisits(ResultSet rs) throws Throwable {
		if (rs == null)
			return 0;
		int n = 0;
		rs.beforeFirst();
		while (rs.next()) {
			n++;
		}
		return n;
	}

	private String compareAndSetValues(ServiceExecutor executor, Boolean update,
			int visitIdx, String studyCID, MBCFMP mbc, Date date,
			XmlDoc.Element petDICOMMeta, XmlDoc.Element ctDICOMMeta,
			ResultSet petVisits, String patientName, String mbcPatientID,
			String petDataSetCID, String ctDataSetCID, XmlWriter w)
					throws Throwable {

		// Fetch Values from PET DICOM if available
		String doseDICOMFormatted = null;
		String startInjectionTimeDICOM = null;
		String startInjectionTimeDICOM2 = null;
		String petScanTimeDICOM = null;
		w.push("DICOM");
		if (petDataSetCID != null) {
			w.push("PET");
			w.add("dataset-CID", petDataSetCID);
			if (petDICOMMeta == null) {
				w.add("dicom-meta", "null");
				return "PET DICOM meta-data null for " + petDataSetCID;
			}
			XmlDoc.Element rp = petDICOMMeta.element("de[@tag='00540016']");
			if (rp == null) {
				w.add("[0054,0016]", "null");
				return "PET DICOM meta-data element [0054,0016] is null for "
				+ petDataSetCID;
			}
			XmlDoc.Element rp2 = rp.element("de[@tag='FFFEE000']");
			//
			String doseDICOM = rp2.value("de[@tag='00181074']/value");
			Float f = Float.parseFloat(doseDICOM) / 1000000.0f;
			doseDICOMFormatted = String.format("%.1f%n", f).trim();
			w.add("dose", doseDICOM);
			//
			startInjectionTimeDICOM = rp2.value("de[@tag='00181072']/value");
			startInjectionTimeDICOM2 = startInjectionTimeDICOM.substring(0, 4); // Rob
			// enters
			// just
			// hh:mm
			// in
			// FMP
			w.add("injection-time", startInjectionTimeDICOM2);
			// Acquisition Time
			petScanTimeDICOM = petDICOMMeta.value("de[@tag='00080032']/value");
			w.add("scan-time", petScanTimeDICOM);
			w.pop();
		}

		// Fetch values from CT DICOM header if available
		String ctScanTimeDICOM = null;
		if (ctDataSetCID != null) {
			w.push("CT");
			w.add("dataset-CID", ctDataSetCID);
			if (ctDICOMMeta == null) {
				w.add("dicom-meta", "null");
				return "CT DICOM meta-data null for " + ctDataSetCID;
			}
			// Acquisition Time
			ctScanTimeDICOM = ctDICOMMeta.value("de[@tag='00080032']/value");
			w.add("scan-time", ctScanTimeDICOM);
			w.pop();
		}
		w.pop();

		// Prepare
		Boolean matchedDose = false;
		Boolean matchedInjectionTime = false;
		Vector<String> fmpInjectionTimes = new Vector<String>();
		Vector<String> fmpDoses = new Vector<String>();

		// Iterate over the FMP PET Visits
		petVisits.beforeFirst();
		int iVisit = 0;
		while (petVisits.next()) {
			if (iVisit == visitIdx) {
				w.add("visit-index", iVisit);

				// Fetch FMP values
				w.push("FMP");
				String doseFMP = petVisits.getString("injected_activity");
				String startInjectionTimeFMP = petVisits
						.getString("Injection Time");
				String scanTypeFMP = petVisits.getString("Scan_Type");
				w.add("scan-type", scanTypeFMP);
				w.add("dose", doseFMP);
				w.add("injection-time", startInjectionTimeFMP);
				w.pop();

				// Compare Injection start time. Compare or insert if missing
				if (petDataSetCID != null) {
					if (!startInjectionTimeFMP.isEmpty()) {
						String[] t = startInjectionTimeFMP.split(":");
						if (t.length != 3) {
							fmpInjectionTimes.add(startInjectionTimeFMP);
						} else {
							String t2 = t[0] + t[1]; // Rob only enters hh:mm
							if (!startInjectionTimeDICOM2.equals(t2)) {
								fmpInjectionTimes.add(
										startInjectionTimeFMP.substring(0, 5));
							} else {
								matchedInjectionTime = true;
							}
						}
					}
					w.add("injection-time-matched", matchedInjectionTime);

					// Compare dose
					if (!doseFMP.isEmpty()) {
						Float f2 = Float.parseFloat(doseFMP);
						String f2s = String.format("%.1f%n", f2).trim();
						// s2 = "380.0";
						if (!doseDICOMFormatted.equals(f2s)) {
							fmpDoses.add(f2s);
						} else {
							matchedDose = true;
						}
					}
					w.add("dose-matched", matchedDose);

					// Insert PET scan time from DICOM (hh:mm:ss) which is
					// primary
					String t2 = petScanTimeDICOM.substring(0, 2) + ":"
							+ petScanTimeDICOM.substring(2, 4) + ":"
							+ petScanTimeDICOM.substring(4, 6);
					if (update) {
						w.add("DICOM-PET-scan-time-inserted-into-FMP", t2);
						mbc.updateScanTime(mbcPatientID, date, scanTypeFMP, t2,
								true, false);
					}

					// Insert DaRIS ID into FMP
					if (update) {
						w.add("Study-CID-inserted-into-FMP", studyCID);
						mbc.updateDaRISID(mbcPatientID, date, scanTypeFMP,
								studyCID, false);
					}

					// Update the parent Study meta-data saying it's been
					// checked for PET
					if (update) {
						w.add("daris-study-metadata-PET-updated", "true");
						setStudyMetaData(executor, studyCID, true);
					} else {
						w.add("daris-study-metadata-PET-updated", "false");
					}
				}

				// Insert CT scan time from DICOM (hh:mm:ss) which is primary
				if (ctDataSetCID != null) {
					String t2 = ctScanTimeDICOM.substring(0, 2) + ":"
							+ ctScanTimeDICOM.substring(2, 4) + ":"
							+ ctScanTimeDICOM.substring(4, 6);

					if (update) {
						w.add("DICOM-CT-scan-time-inserted-into-FMP", t2);
						mbc.updateScanTime(mbcPatientID, date, scanTypeFMP, t2,
								false, false);
						mbc.updateDaRISID(mbcPatientID, date, scanTypeFMP,
								studyCID, false);
					}
					// Update the parent Study meta-data saying it's been
					// checked for CT
					if (update) {
						w.add("daris-study-metadata-CT-updated", "true");
						setStudyMetaData(executor, studyCID, false);
					} else {
						w.add("daris-study-metadata-CT-updated", "false");
					}
				}
			}
			iVisit++;
		}

		// Report inconsistencies
		String error = "";
		Boolean err = false;
		w.push("errors");
		if (petDataSetCID != null) {
			if (!matchedInjectionTime) {
				String t = formatVector(fmpInjectionTimes);
				String t2 = startInjectionTimeDICOM.substring(0, 2) + ":"
						+ startInjectionTimeDICOM.substring(2, 4);
				error += "\nFor " + patientName + "(MBC ID=" + mbcPatientID
						+ ") and date "
						+ DateUtil.formatDate(date, "dd-MMM-yyyy")
						+ " : DICOM ( " + t2 + " ) and FMP " + t
						+ " injection start times do not agree " + " (DataSet='"
						+ petDataSetCID + "')";
				err = true;
				w.add("injection-time", error);
			}

			if (!matchedDose) {
				String t = formatVector(fmpDoses);
				error += "\nFor " + patientName + "(MBC ID=" + mbcPatientID
						+ ") and date "
						+ DateUtil.formatDate(date, "dd-MMM-yyyy")
						+ " : DICOM (" + doseDICOMFormatted + ") and FMP  " + t
						+ " dose values do not agree " + " (DataSet='"
						+ petDataSetCID + "')";
				err = true;
				w.add("dose", error);
			}
		}
		w.pop();

		if (err) {
			// System.out.println(error);
			return error;
		}
		return null;
	}

	private void setStudyMetaData(ServiceExecutor executor, String studyCID,
			Boolean isPET) throws Throwable {

		// Set meta-data on the Study indicating the PET or CT checking has been
		// done
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", studyCID);
		dm.push("meta", new String[] { "action", "merge" });
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

	private String formatVector(Vector<String> vv) {
		if (vv.size() == 0)
			return null;
		String t = "( ";
		for (String v : vv) {
			t += v + " ";
		}
		t += ")";
		return t;
	}
}