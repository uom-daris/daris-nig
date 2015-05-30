package nig.mf.plugin.pssd.ni.Retired;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.Date;

import nig.mf.dicom.plugin.util.DICOMModelUtil;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.util.DateUtil;

import aibl.FMP.SQLUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;
import arc.xml.XmlDoc.Element;

import mbc.FMP.MBCFMP;

public class SvcMBCProjectSet extends PluginService {

	private static final String MBC_IP = "128.250.95.21";  
	private static final String MBC_DB_NAME = "parkville";   
	private static final String MBC_USER_NAME = "mbc-sync";				 	
	private static final String MBC_ENCODED_PASSWORD = "czFuY190ZXN0IQ==";


	private Interface _defn;

	public SvcMBCProjectSet()  {
		_defn = new Interface();		
		_defn.add(new Interface.Element("where",StringType.DEFAULT, "Additional selection to restrict study asset selection.  E.g. namespace or date selection.", 0, 1));
		_defn.add(new Interface.Element("add", BooleanType.DEFAULT, "Add the records and relationship (default is false, just report intended work).", 0, 1));
		_defn.add(new Interface.Element("both-names", BooleanType.DEFAULT, "Require both first and last name to be considered (default true).Many test scans only have one name so we don't generally want to see those. ", 0, 1));
	}

	public Access access() {
		return ACCESS_MODIFY;
	}

	public Interface definition() {
		return _defn;
	}

	public String description() {
		return "Service to look for MBC imaging Unit Study assets with no relationship to Projects and create them by reading the FMP data base to find the project ID.";
	}

	public String name() {
		return "nig.dicom.mbc.project.set";
	}

	public void execute(Element args, Inputs inputs, Outputs outputs, XmlWriter w) throws Throwable {
		// Inputs
		String where = args.value("where");
		Boolean add = args.booleanValue("add", false);
		Boolean bothNames = args.booleanValue("both-names", true);

		// Find Studies (DICOM or RAW PET/CT) with no associated projects
		Collection<String> studyAssetIDs = DICOMModelUtil.findStudiesWithNoProject(executor(), where, DICOMModelUtil.FormatType.BOTH);

		// Iterate and find in FMP
		if (studyAssetIDs!=null) {

			// Open FMP database
			MBCFMP mbc = null;
			try {
				mbc = new MBCFMP(MBC_DB_NAME, MBC_USER_NAME, MBC_ENCODED_PASSWORD, MBC_IP);
			} catch (Throwable tt) {
				throw new Exception ("Failed to establish JDBC connection to FMP");
			}

			for (String studyAssetID : studyAssetIDs) {
				// Get the study asset
				XmlDoc.Element studyAsset = AssetUtil.getAsset(executor(), null, studyAssetID);

				// Find this Study (Visit) in the data-base and return its Project ID (String)
				XmlDocMaker dm = new XmlDocMaker("args");
				String projectID = getProject (executor(), mbc, studyAssetID, studyAsset, bothNames, dm);
				if (projectID==null) {
					w.push("study");
					w.add("id", studyAssetID);
					w.add(dm.root());
					w.push("project");
					w.add("fmp-project", "not-found");
					w.pop(); // project
					w.pop(); // study
				} else if (projectID.equals("not-checked-because-of-names-policy")) {
					// Don't put these in the writer
				} else {
					w.push("study");
					w.add("id", studyAssetID);
					w.add(dm.root());
					w.push("project");
					w.add("fmp-id", projectID);
					
					// Create the Project asset in the same namespace as the Study or find pre-existing by id
					String namespace = studyAsset.value("asset/namespace");
					if (add) {
						String projectAssetID = createOrFindProject (executor(), namespace, projectID);	
						w.add("id", projectAssetID);

						// Add relationships from Project to Patient (found via the patient) 
						// Add meta-data to Study 
						addRelationships (executor(), projectAssetID, studyAssetID, projectID);
					}
					w.pop();  // project
					w.pop();  // study
				}
			}
			mbc.closeConnection();
		}
	}

	private String getProject (ServiceExecutor executor, MBCFMP mbc, String studyAssetID, 
			XmlDoc.Element studyAsset, Boolean requireBothNames, XmlDocMaker w) throws Throwable {

		// The study may be DICOM or RAW. It doesn't matter as we just find by date	
		XmlDoc.Element dicom = studyAsset.element("asset/meta/mf-dicom-study");
		XmlDoc.Element raw = studyAsset.element("asset/meta/daris:siemens-raw-petct-study");
		Date date = null;
		if (dicom!=null) {
			date = dicom.dateValue("sdate");
		} else if (raw!=null) {
			date = raw.dateValue("date");
		} else {
			return null;
		}
		String dateStr = DateUtil.formatDate(date, "dd/MM/yyyy HH:mm");
		w.add("date", date);

		// FInd the Patient asset for this Study asset
		String patientAssetID = DICOMModelUtil.findPatientsFromStudy(executor, studyAssetID);

		// There really should be a patient.  We need to see if it's not found
		if (patientAssetID==null)  {
			w.add("patient", "not-found-in-MF");
			return null;
		} else {
			w.push("patient");
			w.add("id",  patientAssetID);
		}

		// Get details
		DICOMPatient dicomPatient = DICOMModelUtil.getPatientDetails(executor, patientAssetID);
		String name = dicomPatient.getFirstName() + " " + dicomPatient.getLastName();
		w.add ("name", name);
		w.add("DOB", dicomPatient.getDOB());
		w.add("sex", dicomPatient.getSex());

		// Now find the patient in FMP. Skip this one if we can't find it.
		// Test subjects generally only have 1 name (usually last) and aren't in FMP
		// So if there is only one name and we require both names, we won't look at it
		Boolean haveBothNames = dicomPatient.hasBothNames();
		if (requireBothNames && haveBothNames || !requireBothNames) {
			String fmpPatientID = findPatientInFMP (executor, mbc, dicomPatient);
			if (fmpPatientID==null) {
				w.add("fmp-patient", "not-found");
				w.pop();  // patient
				return null;
			} else {
				w.add ("fmp-patient", fmpPatientID);
				w.pop();  // patient


				// Now find the Visit in FMP data base for this patient and study date
				// THere should only be one visit per patient per date but I find sometimes
				// there are more. So juect get the first one.
				ResultSet rs = mbc.getPETVisits(fmpPatientID, null, date);
				if (rs==null) {
					w.add ("visit", new String[]{"date", dateStr, "name", name}, "not-found-in-FMP");
					return null;
				} else {
					String scanDate = SQLUtil.getFirstValue(rs, "Scan_Date");
					if (scanDate==null) {
						w.add ("visit", new String[]{"date", dateStr, "name", name}, "not-found-in-FMP");
						return null;
					} else {
						w.add ("visit", new String[]{"date", dateStr, "name", name}, "found-in-FMP");
						// We use the 'Refferer' field as the Project. It is consistently used.
						rs.first();
						return rs.getString("Refferer");      // NB spelling error in DB
					}
				}
			}
		} else {
			// We don't want these displayed to the caller so trap this special string
			return "not-checked-because-of-names-policy";
		}
	}





	private String findPatientInFMP (ServiceExecutor executor, MBCFMP mbc, DICOMPatient dicomPatient) throws Throwable {

		// Convert sex from DICOM to form required by FMP
		String sex = dicomPatient.getSex();
		if (sex.equalsIgnoreCase("male")) {
			sex = "M";
		} else if (sex.equalsIgnoreCase("female")) {
			sex = "F";
		} else {
			sex = "Other";
		}

		// FIsh out the primary key for patients
		return mbc.findPatient (dicomPatient.getFirstName(), dicomPatient.getLastName(), sex, 
				dicomPatient.getDOB());
	}





	private String createOrFindProject (ServiceExecutor executor, String namespace, String projectName) throws Throwable {

		// If it already exists, we are done
		String assetID = DICOMModelUtil.findProject(executor, projectName);
		if (assetID!=null) return assetID;

		// Otherwise make it
		XmlDocMaker dm = new XmlDocMaker ("args");
		dm.add("namespace", namespace);
		dm.push("meta");
		addProjectMeta (dm, projectName);
		dm.pop();
		XmlDoc.Element r = executor.execute("asset.create", dm.root());
		return r.value("id");
	}

	private void addRelationships (ServiceExecutor executor, String project, String study, String projectID) throws Throwable {

		// First find the patient record. Exception if more than one.
		String patient = DICOMModelUtil.findPatientsFromStudy(executor, study);

		// Now set relationships
		DICOMModelUtil.addPatientToProject (executor, project, patient);
		
		// We don't se relationship, but use the same methodology as the PSS DICOM
		// server and set the project meta-data
		setStudyMetaData (executor, study, projectID)	;
	}
	
	private void addProjectMeta (XmlDocMaker dm, String projectID) throws Throwable {
		dm.push("mf-dicom-project");
		dm.add("id", projectID);
		dm.pop();
	}
	
	private void setStudyMetaData (ServiceExecutor executor, String study, String projectID) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", study);
		dm.push("meta", new String[]{"action", "merge"});  // Handle pre-existing
		addProjectMeta(dm, projectID);
		dm.pop();
		executor.execute("asset.set", dm.root());
	}
}

