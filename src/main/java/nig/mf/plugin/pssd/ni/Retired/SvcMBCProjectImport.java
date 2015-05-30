package nig.mf.plugin.pssd.ni.Retired;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import nig.mf.dicom.plugin.util.DICOMModelUtil;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.mf.pssd.plugin.util.PSSDUtil;
import nig.util.DateUtil;

import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.PluginService.Interface.Attribute;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

/**
 * Imports MBC Imaging Unit data from the DICOM data model archive to PSSD
 * 
 * @author nebk
 * 
 */
public class SvcMBCProjectImport extends PluginService {
	private Interface _defn;


	public SvcMBCProjectImport() {

		_defn = new Interface();
		_defn.add(new Element("pid", CiteableIdType.DEFAULT, "The citeable asset id of the Project to import to.",
				1, 1));
		_defn.add(new Element("namespace", StringType.DEFAULT, 
				"Namespace to import.  Defaults to 'MBIC-Archive'.", 0, Integer.MAX_VALUE));
		//		
		Element el = new Element("mbc-id", StringType.DEFAULT, 
				"MBC File Maker Pro/Archive patient ID (mf-dicom-patient/id) to import.", 0, Integer.MAX_VALUE);
		el.add(new Attribute("study-id", AssetType.DEFAULT, "A specific Study of the patient to import", 0));
		_defn.add(el);
		_defn.add(new Element("mbc-asset-id", StringType.DEFAULT, 
				"Asset ID of the patient record to import (instead of mbc-id).", 0, Integer.MAX_VALUE));
		//
		_defn.add(new Element ("study-type", StringType.DEFAULT, "Specify the Study type (om.pssd.study.type.describe.", 1, 1));

		_defn.add(new Element ("method", StringType.DEFAULT, "Specify the Method citable ID.", 1, 1));
		_defn.add(new Element ("step", CiteableIdType.DEFAULT, "Specify the step in the Method; assumes all Studies from the same step.", 1, 1));
		_defn.add(new Element ("internalize", StringType.DEFAULT, "Internalize content; copy (default), move, or none (you must internalize after yourself).", 0, 1));
		_defn.add(new Element("type", StringType.DEFAULT, 
				"Types of Studies to import; 'dicom', 'raw', 'all'. Defaults to 'all'.", 0, 1));
	}

	public String name() {
		return "nig.pssd.mbc.project.import";
	}

	public String description() {
		return "Service to import MBC Imaging Unit data from DICOM data model (DICOM and Raw data) archive to PSSD data model. Creates Subjects as needed. Will re-use pre-existing Subjects.  Creates Studies as needed. Will skip a Stuy if already found.";
	}

	public Interface definition() {
		return _defn;
	}

	public Access access() {
		return ACCESS_ADMINISTER;
	}

	public void execute(XmlDoc.Element args, Inputs in, Outputs out, XmlWriter w) throws Throwable {
		// Must be system-admin 
		nig.mf.pssd.plugin.util.PSSDUtil.isSystemAdministrator(executor());

		// Get project IDs and validate
		String pid = args.value("pid");	
		PSSDUtil.isValidProject(executor(), pid, true);
		String studyType = args.value("study-type");
		//
		if (PSSDUtil.isReplica(executor(), pid)) {
			throw new Exception ("This project is a replica and cannot be imported in to.");
		}
		String method = args.value("method");
		String step = args.value("step");
		String nameSpace = args.stringValue("namespace", "MBIC-Archive");
		String internalize = args.stringValue("internalize", "copy");
		String type = args.stringValue("type", "all");

		// Subject IDs from input
		Collection<String> mbcSubjectIDs = args.values("mbc-id"); 
		Collection<String> patientAssetIDs = args.values("mbc-asset-id");
		if (mbcSubjectIDs!=null && patientAssetIDs!=null) {
			throw new Exception ("You must specify only one of 'mbc-id' or 'mbc-asset-id'");
		}
		if (mbcSubjectIDs==null && patientAssetIDs==null) {
			throw new Exception ("You must specify one of 'mbc-id' or 'mbc-asset-id'");
		}

		// Check that each MBC Subject ID  is a Singleton.  Must resolve this first if not the case.
		// Returns list of asset IDs for dicom/patient records
		if (patientAssetIDs==null) {
			patientAssetIDs = checkSubjectSingletons(executor(), nameSpace, mbcSubjectIDs);
		} else {
			mbcSubjectIDs = makeMBCSubjectIDs (executor(), patientAssetIDs);
			if (mbcSubjectIDs.size()==0) throw new Exception("Failed to convert asset IDs to MBC Subject IDs");
		}

		// Loop over input subject MBC IDs
		int i = 0;
		String[] tIDs = patientAssetIDs.toArray(new String[patientAssetIDs.size()]);
		for (String mbcSubjectID : mbcSubjectIDs) {
			String patientAssetID = tIDs[i];

			// Does PSSD SUbject already exist ? 
			Boolean created = true;
			String subjectCID = findSubject (executor(), pid, mbcSubjectID);
			if (subjectCID==null) {

				// Create and set Method meta-data
				subjectCID = createSubject (executor(), pid, method);

				// Copy over mf-dicom-patient from input to "private" namespace on Subject
				copyPatientMeta (executor(), patientAssetID, subjectCID);

				// Set NIG-domain meta-data.
				setDomainMeta (executor(), patientAssetID, subjectCID);
			} else {
				created = false;
			}		

			// Add subject
			DICOMPatient patient = DICOMModelUtil.getPatientDetails(executor(), patientAssetID);					
			w.push("subject", new String[]{"asset-id", patientAssetID, "mbc-subject-id", mbcSubjectID, "name", patient.getFullName(), "created", created.toString()}, subjectCID);

			// Import the Studies to the ExMethod of the SUbject
			importStudies (executor(), subjectCID, patientAssetID, studyType, step, internalize, type, w);
			w.pop();
			i++;
		}
	}


	private static Collection<String> makeMBCSubjectIDs (ServiceExecutor executor, Collection<String> patientAssetIDs) throws Throwable {
		ArrayList<String> t = new ArrayList<String>();
		for (String patientAssetID : patientAssetIDs) {
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("id", patientAssetID);
			XmlDoc.Element r = executor.execute("asset.get", dm.root());
			t.add(r.value("asset/meta/mf-dicom-patient/id"));
		}
		return t;
	}

	private static void importStudies (ServiceExecutor executor, String sid, String subjectAssetID,
			String studyType, String step, String internalize, String type, XmlWriter w) throws Throwable {
		w.push("study");
		if (type.equals("dicom")|| type.equals("all")) {
			importDICOMStudies (executor, sid, subjectAssetID, studyType, step, internalize, w);
		}
		if (type.equals("raw")|| type.equals("all")) {
			importRawStudies (executor, sid, subjectAssetID, studyType, step, internalize, w);	
		}
		w.pop();
	}

	/**
	 * There is no point to sharing this code in commons or writing a service
	 * as this is a once off
	 * 
	 * @param executor
	 * @param sid
	 * @param subjectAssetID
	 * @param step
	 * @param w
	 * @throws Throwable
	 */
	private static void importRawStudies (ServiceExecutor executor, String sid, String subjectAssetID,
			String studyType, String step, String internalize, XmlWriter w) throws Throwable {

		// Find all Raw studies in the DICOM data model
		Collection<String> studyAssetIDs = DICOMModelUtil.findStudies(executor, subjectAssetID, DICOMModelUtil.FormatType.SIEMENS_RAW);
		if (studyAssetIDs==null || studyAssetIDs.size()==0) return;

		// FInd ExMethod child - assume first
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", sid);
		XmlDoc.Element r = executor.execute("om.pssd.collection.member.list", dm.root());
		String eid = r.value("object/id");
		if (eid==null) throw new Exception("No ExMethod object found");

		// Iterate
		for (String studyAssetID : studyAssetIDs) {

			// See if this Study has already been imported to this ExMethod
			// We only have the date to go by
			String date = getRawStudyDate (executor, studyAssetID);
			String studyCID = findRawStudy (executor, eid, date);
			if (studyCID!=null) {
				w.add("id", new String[]{"type", "Raw", "create", "false"}, studyCID);
			} else {

				// Create Study
				studyCID = createRawStudy (executor, eid, studyType, step, studyAssetID);

				// Import Series
				importRawSeries (executor, studyCID, studyAssetID);

				// Internalize the content for this Study
				if (!internalize.equals("none")) {
					internalizeContent (executor, studyCID, internalize);
				}

				w.add("id", new String[]{"type", "Raw", "create", "true"}, studyCID);
			}
		}

	}

	private static void internalizeContent (ServiceExecutor executor, String cid, String internalize) throws Throwable {

		XmlDocMaker doc = new XmlDocMaker("args");
		doc.add("where", "cid starts with '" + cid + "' and content is external");
		doc.add("size", "infinity");
		doc.add("action", "pipe");
		doc.push("service", new String[] { "name", "asset.internalize" });
		doc.add("method", internalize);
		doc.pop();
		doc.add("pdist", 0);       // FOrce local
		doc.add("stoponerror", true);
		executor.execute("asset.query", doc.root());
	}


	private static void importRawSeries (ServiceExecutor executor, String studyCID, String studyAssetID) throws Throwable {
		// Find all of the input Series
		Collection<String> seriesAssetIDs = DICOMModelUtil.findSeries(executor, studyAssetID, DICOMModelUtil.FormatType.SIEMENS_RAW);
		if (seriesAssetIDs==null) return;
		for (String seriesAssetID : seriesAssetIDs) {
			importOneRawSeries (executor, studyCID, seriesAssetID);
		}
	}

	private static void importOneRawSeries (ServiceExecutor executor, String studyCID, String seriesAssetID) throws Throwable {

		// Get old meta
		XmlDoc.Element seriesMeta = AssetUtil.getAsset(executor, null, seriesAssetID);

		// Create the primary dataset with the desired meta-data
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("fillin", true);
		dm.add("pid", studyCID);
		dm.add("type", seriesMeta.value("asset/type"));
		dm.add("description", "Raw data");
		dm.push("meta");
		dm.add(seriesMeta.element("asset/meta/daris:siemens-raw-petct-series "));
		dm.push("mf-note");
		dm.add("note", "Imported from " + seriesAssetID);
		dm.pop();
		dm.pop();
		XmlDoc.Element r = executor.execute("om.pssd.dataset.primary.create", dm.root());
		String seriesCID = r.value("id");

		// Set content
		String contentURL = seriesMeta.value("asset/content/url");
		String contentType = seriesMeta.value("asset/content/type");
		setAssetContent (executor, seriesCID, contentURL, contentType);
	}

	private static void setAssetContent (ServiceExecutor executor, String cid, String contentUrl, String contentType) throws Throwable {

		// asset.set :cid $cid :url -by reference $url
		XmlDocMaker doc = new XmlDocMaker("args");
		doc.add("cid", cid);
		doc.add("url", new String[] { "by", "reference" }, contentUrl);
		doc.add("ctype", contentType);
		executor.execute("asset.set", doc.root());
	}


	private static String createRawStudy (ServiceExecutor executor, String eid, String studyType, String step, String studyAssetID) throws Throwable {


		// Prepare for creation
		XmlDocMaker doc = new XmlDocMaker("args");
		doc.add("fillin", true);
		doc.add("pid", eid);
		doc.add("type", studyType);
		doc.add("step", step);

		// Copy over meta-data
		XmlDoc.Element meta = AssetUtil.getAsset(executor, null, studyAssetID);

		//  This is ridiculous effort to get rid of the 'time' part
		XmlDoc.Element t = meta.element("asset/meta/daris:siemens-raw-petct-study");
		XmlDoc.Element tdate = t.element("date");
		Date date = tdate.dateValue();
		String sdate = DateUtil.formatDate(date, false, null);
		tdate.setValue(sdate);

		doc.push("meta");
		doc.add(t);
		doc.push("mf-note");
		doc.add("note", "imported from asset id " + studyAssetID);
		doc.pop();
		doc.pop();

		// Create
		XmlDoc.Element r = executor.execute("om.pssd.study.create", doc.root());
		return r.value("id");
	}


	private static void importDICOMStudies (ServiceExecutor executor, String sid, String subjectAssetID,
			String studyType, String step, String internalize, XmlWriter w) throws Throwable {

		// Find all DICOM studies in the DICOM data model
		Collection<String> studyAssetIDs = DICOMModelUtil.findStudies(executor, subjectAssetID, DICOMModelUtil.FormatType.DICOM);
		if (studyAssetIDs==null || studyAssetIDs.size()==0) return;

		// FInd ExMethod child - assume first
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", sid);
		XmlDoc.Element r = executor.execute("om.pssd.collection.member.list", dm.root());
		String eid = r.value("object/id");
		if (eid==null) throw new Exception("No ExMethod object found");

		// Iterate
		for (String studyAssetID : studyAssetIDs) {

			// See if this Study has already been imported to this ExMethod
			String uid = getDICOMStudyUID (executor, studyAssetID);
			String studyCID = findDICOMStudy (executor, eid, uid);
			if (studyCID!=null) {
				w.add("id", new String[]{"type", "DICOM", "create", "false"}, studyCID);
			} else {

				dm = new XmlDocMaker("args");
				dm.add("id", studyAssetID);
				dm.add("destroy-old-assets", false);
				dm.add("internalize", internalize);
				dm.add("pid", eid);
				dm.add("type", studyType);
				dm.add("step", step);
				XmlDoc.Element r2 = executor.execute("om.pssd.dicom.study.retrofit", dm.root());
				w.add("id", new String[]{"type", "DICOM", "create", "true"}, r2.value("id"));	
			}
		}	
	}


	private static String findDICOMStudy (ServiceExecutor executor, String eid, String uid) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pdist", 0);
		dm.add("where", "(cid starts with '" + eid + "') and (xpath(mf-dicom-study/uid)='" + uid + "')");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		String id = r.value("id");
		if (id==null) return null;
		return CiteableIdUtil.idToCid(executor, id);
	}

	private static String findRawStudy (ServiceExecutor executor, String eid, String date) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pdist", 0);
		dm.add("where", "(cid starts with '" + eid + "') and (xpath(daris:siemens-raw-petct-study/date)='" + date + "')");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		String id = r.value("id");
		if (id==null) return null;
		return CiteableIdUtil.idToCid(executor, id);
	}


	private static String getDICOMStudyUID (ServiceExecutor executor, String studyID) throws Throwable {
		XmlDoc.Element r = AssetUtil.getAsset(executor, null, studyID);
		return r.value("asset/meta/mf-dicom-study/uid");

	}

	private static String getRawStudyDate (ServiceExecutor executor, String studyID) throws Throwable {
		XmlDoc.Element r = AssetUtil.getAsset(executor, null, studyID);
		return r.value("asset/meta/daris:siemens-raw-petct-study/date");
	}

	private static void setDomainMeta (ServiceExecutor executor, String patientAssetID, String sid) throws Throwable {

		// We are going to populate the DICOM API structure	so we can
		// use the domain meta-data framework
		XmlDoc.Element r = AssetUtil.getAsset(executor, null, patientAssetID);
		XmlDoc.Element pm = r.element("asset/meta/mf-dicom-patient");

		//
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", sid);
		dm.push("dicom");
		dm.add("station", "CTAWP71222");
		dm.push("subject");
		dm.add("dob", pm.dateValue("dob"));
		dm.add("sex", pm.value("sex"));
		//
		DICOMPatient dpn = new DICOMPatient(pm);
		dm.add("name", dpn.getFullName());         // Constructs if missing
		dm.add("id", pm.value("id"));
		dm.pop();
		dm.pop();
		executor.execute("nig.pssd.subject.meta.set", dm.root());
	}

	private static void copyPatientMeta (ServiceExecutor executor, String patientAssetID, String sid) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("action", "add");
		dm.add("doc", "mf-dicom-patient");
		//
		dm.add("from", patientAssetID);
		//	
		String id = CiteableIdUtil.cidToId(executor, sid);
		dm.add("to", new String[]{"ns", "pssd.private"}, id);
		//
		executor.execute("nig.asset.doc.copy", dm.root());
	}

	private static ArrayList<String> checkSubjectSingletons(ServiceExecutor executor, String nameSpace, Collection<String> mbcSubjectIDs) throws Throwable {

		ArrayList<String> ids = new ArrayList<String>();
		for (String mbcSubjectID : mbcSubjectIDs) {
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("pdist", 0);
			dm.add("where", "namespace='" + nameSpace + "' and xpath(mf-dicom-patient/id)='" + mbcSubjectID + "'");
			XmlDoc.Element r = executor.execute("asset.query", dm.root());
			Collection<String> t = r.values("id");
			if (t==null) {
				throw new Exception ("MBC Subject ID " + mbcSubjectID + " has no patient records - resolve");
			}

			if (t.size()>1) {
				throw new Exception ("MBC Subject ID " + mbcSubjectID + " has multiple patient records - resolve");
			}
			if (r.value("id") == null) {
				throw new Exception ("MBC Subject ID " + mbcSubjectID + " has no patient records - resolve");
			}
			ids.add(r.value("id"));
		}
		return ids;

	}


	private static String findSubject (ServiceExecutor executor, String projectID, String mbcSubjectID) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pdist", 0);
		String query = "(cid starts with '" + projectID + "') and xpath(nig-daris:pssd-identity/id)='" + mbcSubjectID + "'";
		dm.add("where", query);
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		if (r==null) return null;
		Collection<String> ids = r.values("id");
		if (ids==null) return null;
		//
		if (ids.size() > 1) {
			throw new Exception ("Multiple PSSD Subjects found for MBC Subject ID " + mbcSubjectID);
		}
		return CiteableIdUtil.idToCid(executor, r.value("id"));
	}

	private static String createSubject (ServiceExecutor executor, String projectID, String methodID) throws Throwable {

		// Create Subject and set method meta-data
		String ids[] = PSSDUtil.createSubject(executor, projectID, null, methodID, null, true);
		return ids[0];
	}
}
