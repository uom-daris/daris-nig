package nig.mf.plugin.pssd.ni;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Date;

import mbc.FMP.MBCFMP;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.BooleanType;
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
		_defn.add(new Element("fmpid",StringType.DEFAULT, "A fake FMP patient ID for testing without FMP access.", 0, 1));
		_defn.add(new Element("from", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate from.", 1, 1));
		_defn.add(new Element("to", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate to. Defaults to 'to'", 0, 1));
		_defn.add(new Element("idx", StringType.DEFAULT, "The start idx of the subjects (defaults to 1).", 0, 1));
		_defn.add(new Element("size", StringType.DEFAULT, "The number of subjects to find (defaults to all).", 0, 1));
		_defn.add(new Element("list-only", BooleanType.DEFAULT, "Just list mapping to FMP, don't migrate any data (defaults to true).", 0, 1));
		_defn.add(new Element("clone", BooleanType.DEFAULT, "Actually clone the data-sets. If false, all objects are made to the Study level.", 0, 1));
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
		String oldProjectID = args.stringValue("from");
		String newProjectID = args.stringValue("to", oldProjectID);
		String size = args.stringValue("size");
		String idx = args.stringValue("idx");;
		Boolean list = args.booleanValue("list-only", true);
		Boolean clone = args.booleanValue("clone", false);
		String fmpID = args.stringValue("fmpid");
		//
		XmlDoc.Element projectMeta = AssetUtil.getAsset(executor(), oldProjectID, null);
		String methodID = projectMeta.value("asset/meta/daris:pssd-project/method/id");

		// OPen FMP
		MBCFMP mbc = null;
		if (fmpID==null) {
			try {
				String t = System.getenv("HOME");
				String path = t + FMP_CRED_REL_PATH;
				mbc = new MBCFMP(path);
			} catch (Throwable tt) {
				throw new Exception(
						"Failed to establish JDBC connection to FileMakerPro");
			}
		}

		// Optional output CSV file
		PluginService.Output output = null;
		String type = "text/csv";
		StringBuilder sb = new StringBuilder();

		// Fetch the visit-based Subjects from the DaRIS project
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", oldProjectID);
		if (idx!=null) {
			dm.add("idx", idx);
		}
		if (size!=null) {
			dm.add("size", size);
		}
		dm.add("sort", "true");
		XmlDoc.Element r = executor().execute("om.pssd.collection.member.list", dm.root());

		// Iterate through SUbjects
		sb.append("DaRIS ID").append(",").append("first name").append(",").append("last name").append(",").append("sex").append(",").append("dob").append(",").append("FMP ID").append("\n");
		Collection<String> subjectIDs = r.values("object/id");
		for (String subjectID : subjectIDs) {

			// Fetch asset meta
			XmlDoc.Element meta = AssetUtil.getAsset(executor(), subjectID, null);
			XmlDoc.Element dicomMeta =  meta.element("asset/meta/mf-dicom-patient");
			w.push("subject");
			w.add("old-id", subjectID);
			w.add(dicomMeta);

			// Try to lookup in FMP or use given value
			String fmpID2 = null;
			if (fmpID==null) {
				try {
					fmpID2 = findInFMP (executor(),subjectID, mbc, dicomMeta, sb, w);

					if (fmpID2!=null) {
						w.add("found", "true");
						w.add("fmpPatientID", fmpID2);
					} else {
						w.add("found", "false");
						// Create new Patient Record in FMP
					}

				} catch (Throwable t) {
					w.add("FMPerror", t.getMessage());
				}
			} else {
				fmpID2 = fmpID;
				w.add("found", "true");
				w.add("fmpPatientID", fmpID2);				
			}

			// If we didn't find  the Subject in FMP proceed
			if (fmpID2==null) {
				// Create FMP entry
				throw new Exception ("No FMP ID");
			}

			// Proceed now we have a FMP SUbject ID one way or the other
			sb.append("\n");

			// Now migrate the data for this Subject
			if (!list) {
				String newSubjectID = migrateSubject (executor(), newProjectID, methodID, subjectID, fmpID2, clone, w);
			}
			//
			w.pop();
		}

		// Close up FMP
		if (fmpID==null) {
			mbc.closeConnection();
		}

		// Write CSV file
		if (outputs!=null && outputs.size()==1) {
			String os = sb.toString();
			byte[] b = os.getBytes("UTF-8");
			output = outputs.output(0);
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

	private String migrateSubject (ServiceExecutor executor,  String newProjectID, String methodID, String oldSubjectID, 
			String fmpSubjectID, Boolean clone, XmlWriter w) throws Throwable {

		// FInd existing or create new Subject. We use mf-dicom-patient/id to find the Subject
		String newSubjectID = findOrCreateSubject (executor, fmpSubjectID, methodID, oldSubjectID, newProjectID);
		w.add("new-id", newSubjectID);
		// Find the new ExMethod (it's auto created when the Subject is made)
		Collection<String> newExMethodIDs = childrenIDs (executor, newSubjectID);
		if (newExMethodIDs.size()>1) {
			// This isn't possible as we only ever make one !
			// Need to check collection before hand
			throw new Exception ("Unhandled multiple number of ExMethods under Subject " + newSubjectID);
		}
		String newExMethodID = null;
		for (String t : newExMethodIDs) {
			newExMethodID = t;
		}


		// Now find the  Studies to migrate
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.push("sort");
		dm.add("key", "ctime");
		dm.pop();
		dm.add("size", "infinity");
		dm.add("where", "model='om.pssd.study' and cid starts with '" + oldSubjectID + "'");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		Collection<String> oldIDs = r.values("id");

		// No old Studies to migrate
		if (oldIDs==null) return newSubjectID;

		// Iterate through the Studies
		for (String oldID : oldIDs) {
			String oldStudyID = CiteableIdUtil.idToCid(executor, oldID);

			w.push("study");
			w.add("old-id", oldStudyID);

			// Find or create the new Study - dicom or raw
			String newStudyID = findOrCreateStudy (executor, oldStudyID, newExMethodID, w);

			// Now  find or clone (copy) the DataSets
			if (clone) {
				findOrCloneDataSets (executor, oldStudyID, newStudyID, w);
			}

			// CHeck number of DataSets is correct
			int nIn = nChildren (executor, oldStudyID, "om.pssd.dataset");
			int nOut = nChildren (executor, newStudyID, "om.pssd.dataset");
			w.add("datasets-in", nIn);
			w.add("datasets-out", nOut);
			w.pop();
		}

		return newSubjectID;

	}

	private String findOrCreateStudy (ServiceExecutor executor, String oldStudyID, 
			String newExMethodID, XmlWriter w) throws Throwable {

		// Fetch old meta-data
		XmlDoc.Element oldStudyMeta = AssetUtil.getAsset(executor, oldStudyID, null);

		// See if new STudy pre-exists by study UID
		String newStudyID = null;
		Integer nChildren = nChildren (executor, newExMethodID, "om.pssd.study");
		Boolean isDICOM = false;
		if (nChildren!=0) {

			// It has some children - maybe the one we want.
			XmlDoc.Element oldDICOM = oldStudyMeta.element("asset/meta/mf-dicom-study");
			XmlDoc.Element oldRaw = oldStudyMeta.element("asset/meta/siemens-raw-mr-study");
			if (oldDICOM!=null && oldRaw!=null) {
				throw new Exception ("Study "+ oldStudyID + " holds both DICOM and Raw meta-data - this is not good");
			}
			isDICOM = (oldDICOM!=null);

			if (isDICOM) {
				String uid = oldDICOM.value("uid");

				// The old Study is DICOM.  Try and see if it already exists
				// in the migrated Project
				XmlDocMaker dm = new XmlDocMaker("args");
				dm.add("where", "cid starts with '" + newExMethodID + "' and model='om.pssd.study' and " +
						"xpath(mf-dicom-study/uid)='" + uid + "'");
				XmlDoc.Element r = executor.execute("asset.query", dm.root());
				Collection<String> ids = r.values("id");
				if (ids!=null) {
					if (ids.size()>1) {
						throw new Exception("Found multiple instances of DICOM Study with uid '"+uid+"' under ExMethod '"+newExMethodID+"'");
					}

					// Here is the good one. It may not have any DataSets...
					String t =  ids.iterator().next();	
					newStudyID = CiteableIdUtil.idToCid(executor, t);
					w.add("new-id", new String[]{"status", "found"}, newStudyID);
				}
			} else {
				String date = oldRaw.value("date");
				String ingestDate = oldRaw.value("ingest/date");
				XmlDocMaker dm = new XmlDocMaker("args");
				dm.add("where", "cid starts with '" + newExMethodID + "' and model='om.pssd.study' and " +
						"xpath(siemens-raw-mr-study/date)='" + date + 
						"' and xpath(siemens-raw-mr-study/ingest/date)='" + ingestDate  + "'");
				XmlDoc.Element r = executor.execute("asset.query", dm.root());
				Collection<String> ids = r.values("id");
				if (ids!=null) {
					if (ids.size()>1) {
						throw new Exception("Found multiple instances of Raw Study with date '"+date+"' under ExMethod '"+newExMethodID+"'");
					}

					// Here is the good one. It may not have any DataSets...
					String t =  ids.iterator().next();	
					newStudyID = CiteableIdUtil.idToCid(executor, t);
					w.add("new-id", new String[]{"status", "found"}, newStudyID);
				}
			}
		} 

		// We didn't find it so make new Study
		// TBD locate the old Subject name on the Study somewhere, probably as the pssd-object/name or?
		if (newStudyID==null) {
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("pid", newExMethodID);
			dm.add("step", 1);                // It's always step 1 in the archive
			String oldName = oldStudyMeta.value("asset/meta/daris:pssd-object/name");
			String oldDescription = oldStudyMeta.value("asset/meta/daris:pssd-object/descrption");
			if (oldName!=null) {
				dm.add("name", oldName);
			}
			if (oldDescription!=null) {
				dm.add("description", oldDescription);
			}
			XmlDoc.Element r = executor.execute("om.pssd.study.create", dm.root());
			newStudyID = r.value("id");
			w.add("new-id", new String[]{"status", "created"}, newStudyID);

			// Copy the mf-dicom-study meta-data across
			String from = CiteableIdUtil.cidToId(executor, oldStudyID);
			String to = CiteableIdUtil.cidToId(executor, newStudyID);

			dm = new XmlDocMaker("args");
			dm.add("action", "add");
			dm.add("from", from);
			if (isDICOM) {
				dm.add("doc", new String[]{"ns", "dicom", "tag", "pssd.meta"}, "mf-dicom-study");
				dm.add("to", new String[]{"ns", "dicom", "tag", "pssd.meta"}, to);
			} else {
				dm.add("doc", new String[]{"ns", "om.pssd.study", "tag", "pssd.meta"}, "mf-dicom-study");
				dm.add("to", new String[]{"ns", "om.pssd.study", "tag", "pssd.meta"}, to);
			}
			executor.execute("nig.asset.doc.copy", dm.root());
		}
		//
		return newStudyID;
	}



	private String findOrCreateSubject (ServiceExecutor executor, String fmpSubjectID, String methodID, String oldSubjectID, String newProjectID) throws Throwable {

		// See if we can find the Subject pre-existing
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.push("sort");
		dm.add("key", "ctime");
		dm.pop();
		String where = "cid starts with '" + newProjectID + "' and model='om.pssd.subject' and ";
		where += "(xpath(mf-dicom-patient/id)='"+fmpSubjectID+"')";
		dm.add("where", where);
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		String newSubjectID = r.value("id");
		if (newSubjectID!=null) {
			return CiteableIdUtil.idToCid(executor, newSubjectID);
		}

		// Create the new Subject and ExMethod  if needed
		dm = new XmlDocMaker("args");
		dm.add("pid", newProjectID);
		dm.add("method", methodID);	
		//		dm.add("fillin", true);
		r = executor.execute("om.pssd.subject.create", dm.root());
		newSubjectID = r.value("id");

		// Now copy meta-data across
		String from = CiteableIdUtil.cidToId(executor, oldSubjectID);
		String to = CiteableIdUtil.cidToId(executor, newSubjectID);
		//
		dm = new XmlDocMaker("args");
		dm.add("action", "add");
		dm.add("from", from);
		dm.add("doc", "mf-dicom-patient");
		dm.add("to", new String[]{"ns", "pssd.private"}, to);
		executor.execute("nig.asset.doc.copy", dm.root());

		// Now update the meta-data. We are going to replace the existing Patient ID with the FMP ID
		dm = new XmlDocMaker("args");
		dm.add("id", to);
		dm.push("meta", new String[]{"action", "merge"});
		dm.push("mf-dicom-patient", new String[]{"ns", "pssd.private"});
		dm.add("id", fmpSubjectID);
		dm.pop();
		// We also transfer the FMP SUbject ID into the identity meta-data
		dm.push("nig-daris:pssd-identity", new String[]{"ns", "pssd.public"});
		dm.add("id", new String[]{"type", "Melbourne Brain Centre Imaging Unit"}, fmpSubjectID);
		dm.pop();
		//
		dm.pop();
		executor.execute("asset.set", dm.root());
		//
		return newSubjectID;
	}


	private void findOrCloneDataSets (ServiceExecutor executor, String oldStudyID, 
			String newStudyID, XmlWriter w) throws Throwable {

		// Find old DataSets
		Collection<String> oldDataSetIDs = childrenIDs (executor, oldStudyID);

		// Iterate and clone
		// TBD update the DICOM headers so that the  patient ID = FMP ID (like the indexed meta-data)
		if (oldDataSetIDs!=null) {
			for (String oldDataSetID : oldDataSetIDs) {
				w.push("dataset");
				w.add("old-id", oldDataSetID);
				// Get old asset meta-data and UID
				XmlDoc.Element asset = AssetUtil.getAsset(executor, oldDataSetID, null);

				XmlDoc.Element oldDICOM = asset.element("asset/meta/mf-dicom-series");
				XmlDoc.Element oldRaw = asset.element("asset/meta/siemens-raw-mr-series");
				if (oldDICOM!=null && oldRaw!=null) {
					throw new Exception ("DataSet "+ oldDataSetID + " holds both DICOM and Raw meta-data - this is not good");
				}
				Boolean isDICOM = (oldDICOM!=null);

				// See if we can find it the DataSet already migrated
				XmlDocMaker dm = new XmlDocMaker("args");
				if (isDICOM) {
					String uid = oldDICOM.value("uid");
					dm.add("where", "cid starts with '" + newStudyID + "' and model='om.pssd.dataset' and "+
							"xpath(mf-dicom-series/uid)='"+uid+"'");
				} else {
					String fn = asset.value("asset/meta/daris:pssd-filename/original");
					dm.add("where", "cid starts with '" + newStudyID + "' and model='om.pssd.dataset' and "+
							"xpath(daris:pssd-filename/original)='"+fn+"'");
				}
				XmlDoc.Element r = executor.execute("asset.query", dm.root());
				String id = r.value("id");
				if (id!=null) {
					w.add("new-id", new String[]{"status", "found"}, CiteableIdUtil.idToCid(executor, id));
				} else {

					// Create new by cloning it (coping all meta-data)
					// TBD enhance clone with move option for 
					dm = new XmlDocMaker("args");
					dm.add("id", oldDataSetID);
					dm.add("pid", newStudyID);
					r = executor.execute("om.pssd.dataset.clone", dm.root());		
					w.add("new-id", new String[]{"status", "created"},r.value("id"));
				}
				w.pop();
			}
		}
	}

	Integer nChildren(ServiceExecutor executor, String cid, String model) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("where", "cid starts with '" + cid + "' and model='"+model+"'");
		dm.add("action", "count");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		return Integer.parseInt(r.value("value"));
	}


	Collection<String> childrenIDs (ServiceExecutor executor, String cid) throws Throwable {
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("id", cid);
		dm.add("sort", "true");
		XmlDoc.Element r = executor.execute("om.pssd.collection.member.list", dm.root());
		return r.values("object/id");
	}
}
