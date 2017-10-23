package nig.mf.plugin.pssd.ni;

import java.util.Collection;

import mbc.FMP.MBCFMP;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
import arc.utils.StringUtil;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;

public class SvcMBCProjectMigrate extends PluginService {

	private Interface _defn;
	private static final String FMP_CRED_REL_PATH = "/.fmp/petct_fmpcheck";

	public SvcMBCProjectMigrate() {

		// matches DocType daris:pssd-repository-description

		_defn = new Interface();
		_defn.add(new Element("id", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate.", 1, 1));
		_defn.add(new Element("idx", StringType.DEFAULT, "The start idx of the subjects (defaults to 1).", 0, 1));
		_defn.add(new Element("size", StringType.DEFAULT, "The number of subjects to find (defaults to all).", 0, 1));
	}

	public String name() {

		return "nig.pssd.mbic.project.migrate";
	}

	public String description() {

		return "Migrates an MBC DaRIS project from visit-based Subject IDs to subject-based subject IDs.";
	}

	public Interface definition() {

		return _defn;
	}

	public Access access() {

		return ACCESS_MODIFY;
	}



	public void execute(XmlDoc.Element args, Inputs inputs, Outputs outputs, XmlWriter w)
			throws Throwable {

		// Parse arguments
		String oldProjectID = args.stringValue("id");
		String size = args.stringValue("size");
		String idx = args.stringValue("idx");;
		XmlDoc.Element projectMeta = AssetUtil.getAsset(executor(), oldProjectID, null);
		String methodID = projectMeta.value("asset/meta/daris:pssd-project/method/id");


		// OPen FMP
		MBCFMP mbc = null;
		try {
			String t = System.getenv("HOME");
			String path = t + FMP_CRED_REL_PATH;
			mbc = new MBCFMP(path);
		} catch (Throwable tt) {
			throw new Exception(
					"Failed to establish JDBC connection to FileMakerPro");
		}


		// Fetch the visit-based Subjects
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", oldProjectID);
		if (idx!=null) {
			dm.add("idx", idx);
		}
		if (size!=null) {
			dm.add("size", size);
		}
		XmlDoc.Element r = executor().execute("om.pssd.collection.member.list", dm.root());

		// Iterate through SUbjects
		Collection<String> subjectIDs = r.values("object/id");
		for (String subjectID : subjectIDs) {

			// Fetch asset meta
			XmlDoc.Element meta = AssetUtil.getAsset(executor(), subjectID, null);
			XmlDoc.Element dicomMeta =  meta.element("asset/meta/mf-dicom-patient");
			w.push("subject");
			w.add("id", subjectID);
			w.add(dicomMeta);

			// Try to lookup in FMP
			String fmpID = null;
			try {
				fmpID = findInFMP (executor(), mbc, dicomMeta, w);

				if (fmpID!=null) {
					w.add("found", "true");
					w.add("fmpPatientID", fmpID);
				} else {
					w.add("found", "false");

					// Create new Patient Record in FMP
				}


				// Now migrate the data
				String newProjectID = oldProjectID;
				//			String newSubjectID = migrateSubject (executor(), newProjectID, methodID, subjectID, fmpID);

			} catch (Throwable t) {
				w.add("FMPerror", t.getMessage());
			}
			w.pop();
		}

		// Close up FMP
		mbc.closeConnection();

	}

	private String findInFMP (ServiceExecutor executor, MBCFMP mbc, XmlDoc.Element dicomMeta, XmlWriter w) throws Throwable {
		String mbcID = null;

		// FInd Patient
		DICOMPatient dp = new DICOMPatient(dicomMeta);	
		if (dp.getFirstName()!=null && dp.getLastName()!=null && dp.getSex()!=null && dp.getDOB()!=null) {
			w.add("dicomMeta", "complete");
			// Convert to FMP form
			String sex = dp.getSex();
			String sex2 = null;
			if (sex!=null) {
				if (sex.equalsIgnoreCase("male")) {
					sex2 = "M";
				} else if (sex.equalsIgnoreCase("female")) {
					sex2 = "F";
				} else {
					sex2 = "Other";
				}
			}

			System.out.println("lastescaped="+ StringUtil.escapeSingleQuotes(dp.getLastName()));
			/*
			String ln =  StringUtil.escapeSingleQuotes(dp.getLastName());
			String fn = StringUtil.escapeSingleQuotes(dp.getFirstName());
			*/
			//
			String fn = dp.getFirstName().replaceAll("'",  "");
			String ln = dp.getLastName().replaceAll("'",  "");
			mbcID = mbc.findPatient(fn, ln, sex2, dp.getDOB());
		} else {
			w.add("dicomMeta", "incomplete");
			w.add("first", dp.getFirstName());
			w.add("last", dp.getLastName());
			w.add("sex", dp.getSex());
			w.add("dob", dp.getDOB());

		}
		return mbcID;
	}

	private String migrateSubject (ServiceExecutor executor,  String newProjectID, String methodID, String oldSubjectID, String fmpID) throws Throwable {
		// Create the new Subject and ExMethod 
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("pid", newProjectID);
		dm.add("method", methodID);	
		XmlDoc.Element r = executor.execute("om.pssd.subject.create", dm.root());
		String newSubjectID = r.value("id");
		String newExMethodID = r.value("id/@mid");

		// Copy over meta-data from old to new
		// use nig.asset.doc.copy

		// Now find the old Studies
		dm = new XmlDocMaker("args");
		dm.add("id", oldSubjectID);
		dm.add("where", "model='om.pssd.study' and cid starts with '" + oldSubjectID + "'");
		r = executor.execute("asset.query");
		Collection<String> oldStudyIDs = r.values("id");

		// Iterate through Studies
		for (String oldStudyID : oldStudyIDs) {
			// Generate new Study
			dm = new XmlDocMaker("args");
			dm.add("pid", newExMethodID);

			// Copy over meta-data from old Study

			// Now  clone (copy) the DataSets


		}

		return newSubjectID;

	}
}
