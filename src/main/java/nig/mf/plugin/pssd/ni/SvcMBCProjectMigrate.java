package nig.mf.plugin.pssd.ni;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Date;

import mbc.FMP.MBCFMP;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
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

		return "Migrates an MBC DaRIS project from visit-based Subject IDs to subject-based subject IDs.  If an output is supplied it generates a CSV file mapping DaRIS ID to FMP ID.";
	}

	public Interface definition() {

		return _defn;
	}

	public Access access() {

		return ACCESS_MODIFY;
	}


	@Override
	public int minNumberOfOutputs() {
		return 0;
	}

	@Override
	public int maxNumberOfOutputs() {
		return 1;
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

		// Output file
		PluginService.Output output = null;
		String type = "text/csv";
		StringBuilder sb = new StringBuilder();
		if (outputs.size()==1) {
			output = outputs.output(0);
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
		sb.append("DaRIS ID").append(",").append("first name").append(",").append("last name").append(",").append("sex").append(",").append("dob").append(",").append("FMP ID").append("\n");
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
				fmpID = findInFMP (executor(),subjectID, mbc, dicomMeta, sb, w);
				sb.append("\n");

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

		// Write CSV file
		if (outputs.size()==1) {
			String os = sb.toString();
			byte[] b = os.getBytes("UTF-8");
			output.setData(new ByteArrayInputStream(b), b.length, type);
		}

	}

	private String findInFMP (ServiceExecutor executor, String cid, MBCFMP mbc, XmlDoc.Element dicomMeta, StringBuilder sb, XmlWriter w) throws Throwable {
		String mbcID = null;

		// Parse meta-data
		DICOMPatient dp = new DICOMPatient(dicomMeta);	
		//
		String fn = dp.getFirstName();
		if (fn!=null) {
			fn = dp.getFirstName().replaceAll("'",  "");
		}
		String ln = dp.getLastName();
		if (ln!=null) {
			ln = dp.getLastName().replaceAll("'",  "");
		}
		//
		String sex = dp.getSex();
		//
		Date dob = dp.getDOB();
		String dob2 = null;
		if (dob!=null) {
			dob2 = DateUtil.formatDate(dob, "dd-MMM-yyyy");
		}

		if (fn!=null && ln!=null && dp.getSex()!=null && dp.getDOB()!=null) {
			w.add("dicomMeta", "complete");
			// Convert to FMP form
			String sex2 = sex;
			if (sex!=null) {
				if (sex.equalsIgnoreCase("male")) {
					sex2 = "M";
				} else if (sex.equalsIgnoreCase("female")) {
					sex2 = "F";
				} else {
					sex2 = "Other";
				}
			}


			// The convention in FMP is to NOT have single quotes in names like O'Connor.
			// So remove any
			/*
			String ln =  StringUtil.escapeSingleQuotes(dp.getLastName());
			String fn = StringUtil.escapeSingleQuotes(dp.getFirstName());
			 */
			mbcID = mbc.findPatient(fn, ln, sex2, dp.getDOB());
			//
			sb.append(cid).append(",").append(fn).append(",").append(ln).append(",").append(sex).append(",").append(dob2);

		} else {
			w.add("dicomMeta", "incomplete");
			w.add("first", dp.getFirstName());
			w.add("last", dp.getLastName());
			w.add("sex", dp.getSex());
			w.add("dob", dp.getDOB());
			sb.append(cid).append(",").append(fn).append(",").append(ln).append(",").append(sex).append(",").append(dob2);
		}
		if (mbcID!=null) {
			sb.append(",").append(mbcID);
		} else {
			sb.append(",").append("not-found");
		}
		//
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
