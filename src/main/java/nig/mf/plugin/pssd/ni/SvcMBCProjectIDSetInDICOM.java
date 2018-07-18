package nig.mf.plugin.pssd.ni;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.Vector;

import arc.mf.plugin.PluginLog;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.dtype.AssetType;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.EnumType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;
import mbciu.commons.SQLUtil;
import mbciu.mbc.MBCFMP;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;

public class SvcMBCProjectIDSetInDICOM extends PluginService {
	// Relative location of resource file on server
	private static final String FMP_CRED_REL_PATH = "/.fmp/petct_fmpcheck";
	private static final String EMAIL = "williamsr@unimelb.edu.au";
	private static final String[] PROJECT_LIST = {"threed"};

	private Interface _defn;

	public SvcMBCProjectIDSetInDICOM() throws Throwable {

		_defn = new Interface();
		//

		Interface.Element me = new Interface.Element("cid",
				CiteableIdType.DEFAULT,
				"The citeable ID of the parent Study holding the DICOM modality DataSets.",
				0, 1);

		_defn.add(me);
		//
		me = new Interface.Element("id", AssetType.DEFAULT,
				"The asset ID (not citeable) of the parent Study holding the DICOM modality DataSets.",
				0, 1);
		_defn.add(me);
		//
		me = new Interface.Element("email", StringType.DEFAULT,
				"The destination email address to send a report to if an exception occurs. Over-rides any built in one.",
				0, 1);
		_defn.add(me);
		//
		_defn.add(new Interface.Element("no-email", BooleanType.DEFAULT,
				"Do not send email. Defaults to false.", 0, 1));
		_defn.add(new Interface.Element("project",  new EnumType(new String[] { "ThreeD"}),
				"The name of a project. If that name matches the name in FMP in the Visit record, then the meta-data will be updated.", 0, Integer.MAX_VALUE));

	}

	@Override
	public String name() {
		return "nig.pssd.mbic.dicom.project-id.set";
	}

	@Override
	public String description() {

		return "Finds the visit in File Maker Pro and extracts the project name and project-based subject ID.  Inserts these into any DICOM files under the Study in the DICOM 'Patient Comments' field.";
	}

	@Override
	public Interface definition() {

		return _defn;
	}

	@Override
	public Access access() {

		return ACCESS_ADMINISTER;
	}

	@Override
	public boolean canBeAborted() {

		return false;
	}

	@Override
	public void execute(XmlDoc.Element args, Inputs in, Outputs out,
			XmlWriter w) throws Throwable {

		// Parse input ID
		String studyCID = args.value("cid");
		String studyID = args.value("id");
		String email = args.stringValue("email", EMAIL);
		boolean noEmail = args.booleanValue("no-email", false);
		if (noEmail) {
			email = null;
		}
		// Allowed projects
		Collection<String> projectsLocal = args.values("project");
		Vector<String> projects = new Vector<String>();
		if (projectsLocal==null) {
			for (String PROJECT : PROJECT_LIST) {
				projects.add(PROJECT);
			}
		} else {
			projects.addAll(projectsLocal);
		}


		// Check
		if (studyID == null && studyCID == null) {
			throw new Exception("Must supply 'id' or 'cid'");
		}
		if (studyCID == null) {
			studyCID = CiteableIdUtil.idToCid(executor(), studyID);
		}

		// Find the FMP Visit ID from the STudy meta-data
		XmlDoc.Element studyMeta = AssetUtil.getAsset(executor(), studyCID, null);
		
		// We only want DICOM
		if (studyMeta.element("asset/meta/mf-dicom-study") == null) return;
	
		//
		String fmpVisitID = SvcMBCPETVarCheck.findVisit(executor(), studyMeta);
		if (fmpVisitID==null) {
			// Skip this one with message
			String error = "nig.pssd.mbic.dicom.project-id.set : For Study '"
					+ studyCID + "' the FMP Visit ID was not found in the DICOM (AccessionNumber) or "
					+ " indexed meta-data (daris:pssd-study/other-id) \n";
			PluginLog.log().add(PluginLog.WARNING, error);
			if(email!=null){
				String subject = "Error in service to update DICOM meta-data with project-based subject ID for Study "+studyCID;
				SvcMBCPETVarCheck.send(executor(), email, subject, error);
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
					"nig.pssd.mbic.dicom.project-id.set : Failed to establish JDBC connection to FileMakerPro");
		}


		// Get and check the FMP Visit
		ResultSet petVisit = mbc.getPETVisit(fmpVisitID);
		if (petVisit==null) {
			String subject = "Error in service to update DICOM meta-data with project-based subject ID for Study "+studyCID;
			String error = "nig.pssd.mbic.dicom.project-id.set : could not retrieve the PET/CT visit from FileMakerPro for visit ID = " + fmpVisitID;
			PluginLog.log().add(PluginLog.WARNING, error);
			if(email!=null){
				SvcMBCPETVarCheck.send(executor(), email, subject, error);
			}
			mbc.closeConnection();
			throw new Exception (error);
		} else {
			// This should never happen
			if (SQLUtil.sizeResultSet(petVisit) != 1) {
				String subject = "Error in service to update DICOM meta-data with project-based subject ID for Study "+studyCID;
				String error = "nig.pssd.mbic.dicom.project-id.set : There must be precisely one Visit in " +
						" FMP for visit ID = " + fmpVisitID;
				PluginLog.log().add(PluginLog.WARNING, error);
				if(email!=null){
					SvcMBCPETVarCheck.send(executor(), email, subject, error);
				}
				mbc.closeConnection();
				throw new Exception (error);
			}
		}
		mbc.closeConnection();


		// Fetch the Project ID from FMP
		petVisit.beforeFirst();
		petVisit.next();
		w.push("FMP");
		String projectName = petVisit.getString("Projectname");
		String projectSubjectID = petVisit.getString("Projectsubjectid");
		// If no project element (old visits), bug out.
		if (projectName==null || projectSubjectID==null) return;

		w.add("project-name", projectName);
		w.add("project-subject-id", projectSubjectID);


		// See if the project for this Visit is on the wanted list
		boolean keep = false;
		if (projects.size()>0) {
			for (String project : projects) {
				if (project.equalsIgnoreCase(projectName)) {
					keep = true;
				}
			}
		} else {
			keep = true;
		}
		w.add("keep", keep);
		w.pop();
		if (!keep) return;

		// Update the DICOM files.
		Boolean updated = updateDICOM (executor(), studyCID, projectName, projectSubjectID, w);
		if (updated && email!=null) {
			String subject = "Update DICOM meta-data with project-based subject ID for Study "+studyCID;
			SvcMBCPETVarCheck.send(executor(), email, subject, "DICOM data sets for study '"+ studyCID + "' were updated with the project-based subject ID for project '" + projectName + "'");
		}

	}

	private Boolean  updateDICOM (ServiceExecutor executor, String studyCID, String project, String projectID, XmlWriter w) throws Throwable {
		// FInd the DICOM children DataSets
		XmlDocMaker dm = new XmlDocMaker("args");
		String where = "cid starts with '" + studyCID + "' and mf-dicom-series has value";
		dm.add("where", where);
		XmlDoc.Element r = executor().execute("asset.query", dm.root());
		if (r==null) return false;
		//
		Collection<String> ids = r.values("id");
		if (ids==null) return false;
		//
		String tag = "00104000";
		String value = "P=" + project + " S="+projectID;
		Boolean updated = false;
		for (String id : ids) {
			// See if this data set has already been updated
			XmlDoc.Element dataSetMeta = AssetUtil.getAsset(executor, null, id);
			if (!checked(dataSetMeta)) {
				dm = new XmlDocMaker("args");
				dm.add("id", id);
				dm.push("element", new String[]{"action", "merge", "tag", tag});
				dm.add("value", value);
				dm.pop();
				executor.execute("daris.dicom.metadata.set", dm.root());
				
				// Set meta-data saying this data set has been updated
				setDataSetMeta (executor, CiteableIdUtil.idToCid(executor, id));
				w.add("id", new String[]{"updated", "true"}, id);
				updated = true;
			} else {
				w.add("id", new String[]{"updated", "false"}, id);
			}
		}
		return updated;
	}

	private void setDataSetMeta (ServiceExecutor executor, String cid) throws Throwable {

		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", cid);
		dm.push("meta");
		dm.push("nig-daris:pssd-mbic-fmp-dataset-check");
		dm.add("dicom-project-id", "true");
		dm.pop();
		dm.pop();
		executor.execute("om.pssd.dataset.derivation.update", dm.root());

	}

	private Boolean checked(XmlDoc.Element dataSetMeta) throws Throwable {

		XmlDoc.Element meta = dataSetMeta.element("asset/meta/nig-daris:pssd-mbic-fmp-dataset-check");
		if (meta == null) return false;
		return meta.booleanValue("dicom-project-id", false);
	}



}