package nig.mf.plugin.pssd.ni;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.Date;
import java.util.Vector;

import mbciu.commons.SQLUtil;
import mbciu.mbc.MBCFMP;
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
import arc.mf.plugin.dtype.IntegerType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

public class SvcMBCPETVarCheck extends PluginService {
	// Relative location of resource file on server
	private static final String FMP_CRED_REL_PATH = "/.fmp/petct_fmpcheck";
	private static final String EMAIL = "williamsr@unimelb.edu.au";
	private static final String ID_AUTHORITY = "Melbourne Brain Centre Imaging Unit";
	private Interface _defn;

	public SvcMBCPETVarCheck() throws Throwable {

		_defn = new Interface();
		Interface.Element me = new Interface.Element("cid",
				CiteableIdType.DEFAULT,
				"The identity of the parent Study.  All child DataSets (and in a federation children will be found on all peers in the federation) containing DICOM data will be found and sent.",
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
				"Actually update FMP with the new values. Defaults to true.",
				0, 1);
		_defn.add(me);

		_defn.add(new Interface.Element("no-email", BooleanType.DEFAULT,
				"Do not send email. Defaults to false.", 0, 1));
		_defn.add(new Interface.Element("imax", IntegerType.DEFAULT,
				"Max DataSet CID child integer. Defaults to all", 0, 1));
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
		Boolean update = args.booleanValue("update", true);
		boolean noEmail = args.booleanValue("no-email", false);
		Integer iMax = args.intValue("imax", 100000000);
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
			w.add("status", "Both PET and CT have already been checked - use force=true to over-ride");
			return;
		}

		// We are going to work with one PET DataSet (any will do) and
		// one CT DataSet (has to be the right one). We expect one or both
		// to be present generally.

		// Find one (good) PET DataSet under the given parent Study
		String petDataSetCID = null;
		String subject = "Errors found comparing PET DICOM meta-data with FileMakerPro data base entries";
		if (!petIsChecked) {
			String[] t = findPETDataSet(executor(), studyCID, iMax);
			if (t != null) {
				if (t.length == 2) {
					// No good PET data sets found
					PluginLog.log().add(PluginLog.WARNING, t[1]);
					if (email != null) {
						send(executor(), subject, email, t[1]);
					}
					w.add("status", t[1]);
				} else {
					petDataSetCID = t[0];
				}
			}
		}

		// Now find the specific CT DataSet that we want under the parent Study
		String ctDataSetCID = null;
		if (!ctIsChecked) {
			ctDataSetCID = findCTDataSet(executor(), studyCID, iMax);
		}

		// Return if nothing found (could be a raw study)
		if (petDataSetCID == null && ctDataSetCID == null) {
			w.add("status", "No PET or CT data sets were found - may be a raw study.");
			return;
		}
		w.add("PET-DataSet", petDataSetCID);
		w.add("CT-DataSet", ctDataSetCID);

		// Extract the native DICOM meta-data from the DataSets. Null if no cid
		XmlDoc.Element petDICOMMeta = dicomMetaData(executor(), petDataSetCID, null);
		XmlDoc.Element ctDICOMMeta = dicomMetaData(executor(), ctDataSetCID, null);


		// Now we need to find the visit in FMP for these data. The FMP Visit ID is
		// stored in AccessionNumber (DICOM) and extracted into daris:pssd-study/other-id
		String fmpVisitID = findVisit (executor(), studyMeta);
		if (fmpVisitID==null) {
			// Skip this one with message
			String error = "nig.pssd.mbc.petvar.check : For Study '"
					+ studyCID + "' the FMP Visit ID was not found in the DICOM (AccessionNumber) or "
					+ " indexed meta-data (daris:pssd-study/other-id) \n";
			PluginLog.log().add(PluginLog.WARNING, error);
			if(email!=null){
				send(executor(), subject, email, error);
			}
			w.add("FMP-visit-id", "not-found-in-DaRIS-Study");
			return;
		} else {
			w.add("FMP-visit-id", fmpVisitID);
		}

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

		// Get the Visit from FMP and valudare
		ResultSet petVisit = mbc.getPETVisit(fmpVisitID);
		if (petVisit==null) {
			String error = "nig.pssd.mbc.petvar.check : could not retrieve the PET/CT visit from FileMakerPro for visit ID = " + fmpVisitID;
			PluginLog.log().add(PluginLog.WARNING, error);
			if(email!=null){
				send(executor(), subject, email, error);
			}
			mbc.closeConnection();
			throw new Exception (error);
		} else {
			// This should never happen
			if (SQLUtil.sizeResultSet(petVisit) != 1) {
				String error = "nig.pssd.mbc.petvar.check : There must be precisely one Visit in " +
						" FMP for visit ID = " + fmpVisitID;
				PluginLog.log().add(PluginLog.WARNING, error);
				if(email!=null){
					send(executor(), subject, email, error);
				}
				mbc.closeConnection();
				throw new Exception (error);
			}
		}


		// Compare DICOM with FMP values
		String diff = compareAndSetValues(executor(), update, fmpVisitID, petVisit,
				studyCID, mbc,  petDICOMMeta, ctDICOMMeta, 
				petDataSetCID, ctDataSetCID, w);

		// Populate email if errors found
		if (diff != null) {
			PluginLog.log().add(PluginLog.WARNING, diff);
			if(email!=null){
				send(executor(), subject, email, diff);
			}
			w.add("status", diff);
		}

		// Close FMP
		mbc.closeConnection();
	}



	public static String findVisit (ServiceExecutor executor, XmlDoc.Element studyMeta) throws Throwable {
		String visitID = studyMeta.value("asset/meta/daris:pssd-study/other-id[@type='" + ID_AUTHORITY + "']");
		if (visitID == null) {
			// Try to extract from DICOM
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("cid", studyMeta.value("asset/cid"));
			XmlDoc.Element r = executor.execute("nig.pssd.mbic.study.dicom.metadata.get", dm.root());
			visitID =  r.value("other-id");
		}
		return visitID;
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


	private String[] findPETDataSet(ServiceExecutor executor, String studyCID, Integer iMax)
			throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pdist", "0");
		dm.add("action", "get-cid");
		dm.add("size", "infinity");
		String query = "(cid='" + studyCID + "' or cid starts with '" + studyCID
				+ "') and model='om.pssd.dataset' and xpath(mf-dicom-series/modality)='PT'";
		dm.add("where", query);
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r == null || !r.elementExists("cid")) {
			return null;
		}

		// We need a PET data set with the required DICOM meta-data element
		Collection<String> cids = r.values("cid");
		if (cids==null) return null;
		for (String cid : cids) {
			String child = CiteableIdUtil.getLastSection(cid);
			Integer childIdx = Integer.parseInt(child);
			System.out.println("cid, child, idx="+cid + "," + child + "," + childIdx);
			if (childIdx<=iMax) {
				XmlDoc.Element dicomMeta = dicomMetaData(executor, cid, null);
				XmlDoc.Element rp = dicomMeta.element("de[@tag='00540016']");
				if (rp != null) {
					String[] t = { cid };
					return t;
				}
			}
		}

		// Bummer
		String[] t = { studyCID,
				"No PET DataSet has the meta-data element [0054,0016] under study "
						+ studyCID };
		return t;
	}

	private String findCTDataSet(ServiceExecutor executor, String cid, Integer iMax)
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
		String cidDS = r.value("cid");
		if (cidDS==null) return null;
		Integer childIdx = Integer.parseInt(CiteableIdUtil.getLastSection(cidDS));
		if (childIdx<=iMax) return cidDS;
		return null;

	}

	static public void send(ServiceExecutor executor, String email, String subject, String body)
			throws Throwable {
		// Send email if errors were found
		if (body != null) {
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("async", "true");
			dm.add("to", email);
			dm.add("subject", subject);
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

	private String compareAndSetValues(ServiceExecutor executor, Boolean update, String fmpVisitID, ResultSet petVisit, String studyCID, MBCFMP mbc, 
			XmlDoc.Element petDICOMMeta, XmlDoc.Element ctDICOMMeta, String petDataSetCID, String ctDataSetCID, XmlWriter w)
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

		// Fetch FMP values
		petVisit.beforeFirst();
		petVisit.next();
		w.push("FMP");
		String doseFMP = petVisit.getString("injected_activity");
		String startInjectionTimeFMP = petVisit.getString("Injection Time");
		String scanTypeFMP = petVisit.getString("Scan_Type");
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
				mbc.updatePETCTScanTime(fmpVisitID,  t2, true, false);
			}

			// Insert DaRIS ID into FMP
			if (update) {
				w.add("Study-CID-inserted-into-FMP", studyCID);
				mbc.updatePETCTDaRISID(fmpVisitID, studyCID, false);
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
				mbc.updatePETCTScanTime(fmpVisitID, t2, false, false);
				mbc.updatePETCTDaRISID(fmpVisitID, studyCID, false);
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


		// Report inconsistencies
		String error = "";
		Boolean err = false;
		w.push("errors");
		if (petDataSetCID != null) {
			if (!matchedInjectionTime) {
				String t = formatVector(fmpInjectionTimes);
				String t2 = startInjectionTimeDICOM.substring(0, 2) + ":"
						+ startInjectionTimeDICOM.substring(2, 4);
				error += "\nFor MBC Visit ID=" + fmpVisitID 
						+ " : DICOM ( " + t2 + " ) and FMP " + t
						+ " injection start times do not agree " + " (DataSet='"
						+ petDataSetCID + "')";
				err = true;
				w.add("injection-time", error);
			}

			if (!matchedDose) {
				String t = formatVector(fmpDoses);
				error += "\nFor MBC Visit ID=" + fmpVisitID
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