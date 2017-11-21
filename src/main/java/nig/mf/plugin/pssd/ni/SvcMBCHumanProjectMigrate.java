package nig.mf.plugin.pssd.ni;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Date;

import mbciu.commons.FMPAccess;
import mbciu.mbc.MBCFMP;
import nig.mf.dicom.plugin.util.DICOMPatient;
import nig.mf.plugin.util.AssetUtil;
import nig.mf.pssd.plugin.util.CiteableIdUtil;
import nig.util.DateUtil;
import arc.mf.plugin.PluginService;
import arc.mf.plugin.PluginTask;
import arc.mf.plugin.ServiceExecutor;
import arc.mf.plugin.PluginService.Interface.Element;
import arc.mf.plugin.dtype.BooleanType;
import arc.mf.plugin.dtype.CiteableIdType;
import arc.mf.plugin.dtype.StringType;
import arc.xml.XmlDoc;
import arc.xml.XmlDocMaker;
import arc.xml.XmlWriter;


/**
 * Finds the SUbject in FMP or creates a new FMP Patient record (auto allocates Patient ID)
 * FInds the Visit (Study) in FMP or creates a new FMP 7T visit record (all have 7T Study type 'other')
 *      which auto allocates the visit ID
 * The name of new Subjects is set to the FMP patient ID for convenience 
 * The FMP Patinent ID is also stored in nig-daris:nig-pssd-identity/id 
 * The FMP visit ID is stored in daris:pssd-study/other-id
 * The old H numbers (in the Subject name) are also stored on Studies in daris:pssd-study/other-id
 * The DICOM Patient ID element is set to the FMP patient ID
 * The DICOM AccessionNumber is set to the FMP Visit ID
 * The old H number is not stored in DICOM meta-data
 * 
 * @author nebk
 *
 */
public class SvcMBCHumanProjectMigrate extends PluginService {

	private class FMPVisitHolder {
		private String _patientID = null;
		private String _visitID = null;
		private Boolean _created = null;
		FMPVisitHolder(String patientID, String visitID, Boolean created) {
			_patientID = patientID;
			_visitID = visitID;
			_created = created;
		}
		String fmpPatientID () {return _patientID;};
		String fmpVisitID () {return _visitID;};
		Boolean created () {return _created;};
	}

	private Interface _defn;
	private static final String FMP_CRED_REL_PATH = "/.fmp/mbc_migrate";
	private static final String OTHER_ID_TYPE = "Melbourne Brain Centre Imaging Unit";
	private static final String OTHER_ID_TYPE_LEGACY = "Melbourne Brain Centre Imaging Unit 7T Legacy";

	public SvcMBCHumanProjectMigrate() {
		_defn = new Interface();
		_defn.add(new Element("patient-id",StringType.DEFAULT, "A fake FMP patient ID for testing without FMP access.", 0, 1));
		_defn.add(new Element("visit-id",StringType.DEFAULT, "A fake FMP visit ID for testing without FMP access.", 0, 1));
		_defn.add(new Element("from", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate from.", 1, 1));
		_defn.add(new Element("to", CiteableIdType.DEFAULT, "The citeable asset id of the Project to migrate to. Defaults to 'to'", 0, 1));
		_defn.add(new Element("idx", StringType.DEFAULT, "The start idx of the first subject to consider (defaults to 1).", 0, 1));
		_defn.add(new Element("size", StringType.DEFAULT, "The number of subjects to find (defaults to all).", 0, 1));
		_defn.add(new Element("list-only", BooleanType.DEFAULT, "Just list subject mapping to FMP, don't migrate any data (defaults to true).", 0, 1));
		_defn.add(new Element("clone-content", BooleanType.DEFAULT, "Actually clone the content of the DataSets.  If false (the default), the DataSets are cloned but without content.", 0, 1));
		_defn.add(new Element("copy-raw", BooleanType.DEFAULT, "If true (default), when cloning the Siemens RAW (only) DataSet copy the content.  If false, the RAW content is moved.  DICOM content is always copied.", 0, 1));
		_defn.add(new Element("resource-file", StringType.DEFAULT, "Relative resource file path for FMP credential. Defaults to '/.fmp/mbc_migrate'.", 0, 1));
	}

	public String name() {

		return "nig.pssd.mbic.mr.human.project.migrate";
	}

	public String description() {

		return "Migrates an MBC DaRIS Human project from visit-based Subject IDs to subject-based subject IDs.  If an output is supplied it generates a CSV file mapping DaRIS ID to FMP ID.";
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

	public boolean canBeAborted() {

		return true;
	}

	public void execute(XmlDoc.Element args, Inputs inputs, Outputs outputs, XmlWriter w)
			throws Throwable {

		// Parse arguments
		String oldProjectID = args.stringValue("from");
		String newProjectID = args.stringValue("to", oldProjectID);
		String size = args.stringValue("size");
		String idx = args.stringValue("idx");;
		Boolean listOnly = args.booleanValue("list-only", true);
		Boolean cloneContent = args.booleanValue("clone-content", false);
		String resourceFile = args.stringValue("resource-file", FMP_CRED_REL_PATH);
		//
		String patientIDFMP = args.stringValue("patient-id");
		String visitIDFMP = args.stringValue("visit-id");
		if (patientIDFMP!=null && visitIDFMP==null) {
			throw new Exception ("You must specify both patient-id and visit-id");
		}
		if (patientIDFMP==null && visitIDFMP!=null) {
			throw new Exception ("You must specify both patient-id and visit-id");
		}
		//
		Boolean copyRawContent = args.booleanValue("copy-raw",true);
		//
		XmlDoc.Element projectMeta = AssetUtil.getAsset(executor(), oldProjectID, null);
		String methodID = projectMeta.value("asset/meta/daris:pssd-project/method/id");

		// OPen FMP
		MBCFMP mbc = null;
		String home = System.getProperty("user.home");
		String path = home + resourceFile;
		if (patientIDFMP==null) {
			try {
				w.push("FileMakerPro");
				mbc = new MBCFMP(path);
				FMPAccess fmp = mbc.getFMPAccess();
				w.add("ip", fmp.getHostIP());
				w.add("db", fmp.getDataBaseName());
				w.add("user", fmp.getUserName());
				w.pop();
			} catch (Throwable tt) {
				throw new Exception(
						"Failed to establish JDBC connection to FileMakerPro with resource file  '" + path + "'.", tt);
			}
		}

		// Optional output CSV file
		PluginService.Output output = null;
		String type = "text/csv";
		StringBuilder sb = new StringBuilder();

		// Fetch the Subjects in the old archive project (visit based)
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.add("cid", oldProjectID);
		if (idx!=null) {
			dm.add("idx", idx);
		}
		if (size!=null) {
			dm.add("size", size);
		}
		dm.push("sort");
		dm.add("key", new String[]{"order", "asc"}, "cid");
		dm.pop();
		XmlDoc.Element r = executor().execute("daris.object.children.list", dm.root());

		// Iterate through SUbjects
		sb.append("DaRIS ID").append(",").append("first name").append(",").append("last name").append(",").append("sex").append(",").append("dob").append(",").append("FMP ID").append("\n");
		Collection<String> subjectIDs = r.values("object/@cid");
		
		for (String subjectID : subjectIDs) {
			PluginTask.checkIfThreadTaskAborted();

			// Fetch asset meta
			XmlDoc.Element oldSubjectMeta = AssetUtil.getAsset(executor(), subjectID, null);
			String oldSubjectName = oldSubjectMeta.value("asset/meta/daris:pssd-object/name");
			XmlDoc.Element oldDICOMMeta =  oldSubjectMeta.element("asset/meta/mf-dicom-patient");
			if (oldDICOMMeta==null) {
				throw new Exception ("The DICOM meta-data on subject " + subjectID + " is missing");
			}
			w.push("subject");
			w.add("old-id", subjectID);
			w.add(oldDICOMMeta);
			w.push("fmp");

			// Try to lookup in FMP or use given value for single shot testing
			String patientIDFMP2 = null;
			try {
				if (patientIDFMP==null) {
					patientIDFMP2 = findSubjectInFMP (executor(), subjectID, mbc, oldDICOMMeta, sb, w);

					if (patientIDFMP2!=null) {
						w.add("found", "true");
						w.add("patient-id", patientIDFMP2);
					} else {
						w.add("found", "false");
						// Create new Patient Record in FMP
						if (!listOnly) {
							createPatientInFMP (oldDICOMMeta, mbc);
							patientIDFMP2 = findSubjectInFMP (executor(), subjectID, mbc, oldDICOMMeta, null, null);
							w.add("patient-id", patientIDFMP2);
							w.add("created", "true");
						} else {
							w.add("created", "false");
						}
					}
					w.pop();
				} else {
					w.add("found", "ID-given");
					w.add("patient-id", patientIDFMP);	
					w.pop();
					patientIDFMP2 = patientIDFMP;
				}
				sb.append("\n");

				// Proceed now we have a FMP SUbject ID one way or the other
				if (patientIDFMP2!=null) {

					// Now migrate the data for this Subject
					if (!listOnly) {
						migrateSubject (executor(), mbc, newProjectID, methodID, subjectID, oldSubjectName, oldDICOMMeta,  patientIDFMP2,  visitIDFMP, cloneContent, copyRawContent, w);
					}
				}
			} catch (Throwable t) {
				StackTraceElement[] ste = t.getStackTrace();
				w.push("error");
				for (int i=0; i<ste.length; i++) {
				   w.add("error", ste[i].toString());
				}
				w.pop();
				w.pop();
			}
			w.pop();
		}

		// Close up FMP
		if (patientIDFMP==null) {
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





	private void createPatientInFMP (XmlDoc.Element dicomMeta, MBCFMP mbc) throws Throwable {

		DICOMPatient dp = new DICOMPatient(dicomMeta);	

		// Remove single quotes from names
		String fn = dp.getFirstName();	
		if (fn!=null) {
			fn = dp.getFirstName().replaceAll("'","");
		}
		String ln = dp.getLastName();
		if (ln!=null) {
			ln = dp.getLastName().replaceAll("'", "");
		}
		//
		String sex = dp.getSex();
		//
		Date dob = dp.getDOB();

		if (fn!=null && ln!=null && dp.getSex()!=null && dp.getDOB()!=null) {
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
			/*
			System.out.println("name=" + fn + " " + ln);
			System.out.println("sex="+sex2);
			System.out.println("dob="+dob);
			 */
			String notes = "Auto created by DaRIS service nig.pssd.mbic.mr.human.project.migrate at " + DateUtil.todaysTime() + " during archive migration to the Subject-centric structure";
			mbc.createPatient(fn, ln, sex2, dob, notes);
		}

	}

	private String findSubjectInFMP (ServiceExecutor executor, String cid, MBCFMP mbc, XmlDoc.Element dicomMeta, StringBuilder sb, XmlWriter w) throws Throwable {
		String mbcID = null;

		// Parse meta-data
		DICOMPatient dp = new DICOMPatient(dicomMeta);	
		//

		// The convention in FMP is to NOT have single quotes in names like O'Connor.
		// So remove any
		/*
		String ln =  StringUtil.escapeSingleQuotes(dp.getLastName());
		String fn = StringUtil.escapeSingleQuotes(dp.getFirstName());
		 */
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
			if (w!=null) {
				w.add("dicomMeta", "complete");
			}
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

			mbcID = mbc.findPatient(fn, ln, sex2, dp.getDOB());
			//
			if (sb!=null) {
				sb.append(cid).append(",").append(fn).append(",").append(ln).append(",").append(sex).append(",").append(dob2);
			}

		} else {
			if (w!=null) {
				w.add("dicomMeta", "incomplete");
				w.add("first", dp.getFirstName());
				w.add("last", dp.getLastName());
				w.add("sex", dp.getSex());
				w.add("dob", dp.getDOB());
			}
			if (sb!=null) {
				sb.append(cid).append(",").append(fn).append(",").append(ln).append(",").append(sex).append(",").append(dob2);
			}
		}
		if (sb!=null) {
			if (mbcID!=null) {
				sb.append(",").append(mbcID);
			} else {
				sb.append(",").append("not-found");
			}
		}
		//
		return mbcID;
	}

	private void  migrateSubject (ServiceExecutor executor,  MBCFMP mbc, String newProjectID, String methodID, String oldSubjectID, 
			String oldSubjectName, XmlDoc.Element oldDICOMMeta, String patientIDFMP, String visitIDFMP, Boolean cloneContent, Boolean copyRawContent, XmlWriter w) throws Throwable {
		PluginTask.checkIfThreadTaskAborted();

		// FInd existing or create new Subject. We use mf-dicom-patient/id to find the Subject
		// mf-dicom-patient must be there
		DICOMPatient dp = new DICOMPatient(oldDICOMMeta);	
		String newSubjectID = findOrCreateSubject (executor, patientIDFMP, methodID, oldSubjectID, dp, newProjectID);
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


		// Now find the  Studies to migrate. FInd them in time order because we want DICOM first.
		XmlDocMaker dm = new XmlDocMaker("args");
		dm.push("sort");
		dm.add("key", "ctime");
		dm.pop();
		dm.add("size", "infinity");
		dm.add("where", "model='om.pssd.study' and cid starts with '" + oldSubjectID + "'");
		XmlDoc.Element r = executor.execute("asset.query", dm.root());
		Collection<String> oldIDs = r.values("id");

		// No old Studies to migrate
		if (oldIDs==null) return;

		// Iterate through the Studies
		for (String oldID : oldIDs) {
			PluginTask.checkIfThreadTaskAborted();

			String oldStudyID = CiteableIdUtil.idToCid(executor, oldID);

			w.push("study");
			w.add("old-id", oldStudyID);

			// Create a visit record in FMP if needed.
			FMPVisitHolder fmpVisitHolder = null;
			if (visitIDFMP==null) {
				fmpVisitHolder  = findOrCreate7TFMPVisit (executor, mbc, patientIDFMP, oldStudyID, w);
			}

			// Find or create the new Study - dicom or raw in DaRIS
			// We also update FMP with the new Study ID
			String newStudyID = findOrCreateStudy (executor, mbc, oldSubjectName, oldStudyID, newExMethodID,fmpVisitHolder, w);

			// Now  find extant or clone the DataSets
			findOrCloneDataSets (executor, patientIDFMP, fmpVisitHolder.fmpVisitID(), oldStudyID, newStudyID, cloneContent, copyRawContent,  w);

			// CHeck number of DataSets is correct
			int nIn = nChildren (executor, oldStudyID, "om.pssd.dataset");
			int nOut = nChildren (executor, newStudyID, "om.pssd.dataset");
			w.add("datasets-in", nIn);
			w.add("datasets-out", nOut);
			w.pop();
		}
	}


	private FMPVisitHolder findOrCreate7TFMPVisit  (ServiceExecutor executor, MBCFMP mbc, String fmpSubjectID, String oldStudyID, XmlWriter w) throws Throwable {
		XmlDoc.Element oldStudyMeta = AssetUtil.getAsset(executor, oldStudyID, null);
		XmlDoc.Element oldDICOM = oldStudyMeta.element("asset/meta/mf-dicom-study");
		XmlDoc.Element oldRaw = oldStudyMeta.element("asset/meta/daris:siemens-raw-mr-study");

		String fmpVisitID = null;
		Date vdate = null;
		String height = null;
		String weight = null;
		if (oldDICOM != null) {
			vdate = oldDICOM.dateValue("sdate");
			height = oldDICOM.value("subject/size");   // metres (DICOM standard)
			if (height!=null) {
				Float heightf = Float.parseFloat(height);
				heightf = heightf * 100.0f;            // cm
				height = Float.toString(heightf);
			}
			weight = oldDICOM.value("subject/weight");
		} else if (oldRaw!=null) {
			vdate = oldRaw.dateValue("date");
		}

		// Find visit by subject ID and date
		// NB the raw date may not be the same as the acquisition date
		// We should have only one visit that serves both the DICOM and the Raw data
		// The DICOM Study is always the first one.  So it gets migrated first, and the
		// visit gets created in FMP.  Then when the Raw study is migrated, it finds
		// the visit already existing in FMP.
		Boolean created = false;
		fmpVisitID = mbc.find7TMRVisit (fmpSubjectID, vdate, false);
		if (fmpVisitID==null) {
			fmpVisitID = mbc.create7TVisit(fmpSubjectID, vdate, height, weight, false);
			w.push("fmp");
			w.add("visit-id", new String[]{"status", "created"}, fmpVisitID);
			String notes = "Auto created by DaRIS service nig.pssd.mbic.mr.human.project.migrate at " + DateUtil.todaysTime() + " during archive migration to the Subject-centric structure";
			mbc.updateStringInVisit(fmpSubjectID, vdate, fmpVisitID, "MRIvisitnotes", notes,  "7TMR", false);
			w.add("id", fmpSubjectID);
			w.add("date", vdate);
			w.pop();
			created = true;
		} else {
			w.push("fmp");
			w.add("visit-id", new String[]{"status", "found"}, fmpVisitID);
			w.add("id", fmpSubjectID);
			w.add("date", vdate);
			w.pop();		
		}
		return new FMPVisitHolder(fmpSubjectID, fmpVisitID, created);
	}



	private String findOrCreateStudy (ServiceExecutor executor, MBCFMP mbc, String oldSubjectName, String oldStudyID, 
			String newExMethodID, FMPVisitHolder fmpVisitHolder, XmlWriter w) throws Throwable {
		PluginTask.checkIfThreadTaskAborted();

		// Fetch old meta-data
		XmlDoc.Element oldStudyMeta = AssetUtil.getAsset(executor, oldStudyID, null);
		XmlDoc.Element oldDICOM = oldStudyMeta.element("asset/meta/mf-dicom-study");
		XmlDoc.Element oldRaw = oldStudyMeta.element("asset/meta/daris:siemens-raw-mr-study");
		if (oldDICOM!=null && oldRaw!=null) {
			throw new Exception ("Study "+ oldStudyID + " holds both DICOM and Raw meta-data - this is not good");
		}
		Boolean isDICOM = (oldDICOM!=null);

		// See if new STudy pre-exists by study UID
		String newStudyID = null;
		Integer nChildren = nChildren (executor, newExMethodID, "om.pssd.study");
		if (nChildren!=0) {

			// It has some children - maybe the one we want.

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
				String where =  "cid starts with '" + newExMethodID + "' and model='om.pssd.study' and " +
						" xpath(daris:siemens-raw-mr-study/ingest/date)='" + ingestDate  + "'";
				if (date!=null) {
					where += " and xpath(daris:siemens-raw-mr-study/date)='" + date + "'";
				}
				dm.add("where", where);
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
		PluginTask.checkIfThreadTaskAborted();
		if (newStudyID==null) {
			XmlDocMaker dm = new XmlDocMaker("args");
			dm.add("pid", newExMethodID);
			dm.add("step", 1);                // It's always step 1 in the archive
			dm.add("fillin", "true");
			String oldName = oldStudyMeta.value("asset/meta/daris:pssd-object/name");
			String oldDescription = oldStudyMeta.value("asset/meta/daris:pssd-object/description");
			if (oldName!=null) {
				dm.add("name", oldName);
			}
			if (oldDescription!=null) {
				dm.add("description", oldDescription);
			}

			// 
			// Add the new visit ID onto the Study
			dm.add("other-id", new String[]{"type", OTHER_ID_TYPE}, fmpVisitHolder.fmpVisitID());

			// Preserve the H number in Study meta-data
			if (oldSubjectName!=null) {
				dm.add("other-id", new String[]{"type", OTHER_ID_TYPE_LEGACY}, oldSubjectName); 
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
				dm.add("doc", new String[]{"ns", "om.pssd.study", "tag", "pssd.meta"}, "daris:siemens-raw-mr-study");
				dm.add("to", new String[]{"ns", "om.pssd.study", "tag", "pssd.meta"}, to);
			}
			executor.execute("nig.asset.doc.copy", dm.root());

			// Finally, update the FMP visit ID with the new DaRIS Study ID and time.  We only do this
			// If the visit was just created in FMP and its a DICOM Study (one FMP Visit accomodates both DICOM and Raw)
			if (fmpVisitHolder.created() && isDICOM) {
				mbc.updateStringInVisit(fmpVisitHolder.fmpPatientID(), null, fmpVisitHolder.fmpVisitID(), "MRDARIS_ID",  newStudyID, "7TMR", false);

			}
		}
		//
		return newStudyID;
	}



	private String findOrCreateSubject (ServiceExecutor executor, String fmpSubjectID, String methodID, String oldSubjectID, 
			DICOMPatient dp, String newProjectID) throws Throwable {

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
		dm.add("fillin", true);
		String newSubjectName = fmpSubjectID;
		if (dp!=null) {
			newSubjectName += "-" + dp.getLastName();
		}
		dm.add("name", newSubjectName);
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


	private void findOrCloneDataSets (ServiceExecutor executor, String fmpSubjectID, String fmpVisitID, String oldStudyID, 
			String newStudyID, Boolean cloneContent, Boolean copyRawContent, XmlWriter w) throws Throwable {
		PluginTask.checkIfThreadTaskAborted();

		// Find old DataSets
		Collection<String> oldDataSetIDs = childrenIDs (executor, oldStudyID);

		// Iterate and clone
		if (oldDataSetIDs!=null) {
			for (String oldDataSetID : oldDataSetIDs) {
				PluginTask.checkIfThreadTaskAborted();

//				w.push("dataset");
//				w.add("old-id", oldDataSetID);
				// Get old asset meta-data and UID
				XmlDoc.Element asset = AssetUtil.getAsset(executor, oldDataSetID, null);

				XmlDoc.Element oldDICOM = asset.element("asset/meta/mf-dicom-series");
				XmlDoc.Element oldRaw = asset.element("asset/meta/daris:siemens-raw-mr-series");
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
					// FOUnd
////					w.add("new-id", new String[]{"status", "found"}, CiteableIdUtil.idToCid(executor, id));
				} else {

					// Create new by cloning it (coping all meta-data)
					dm = new XmlDocMaker("args");
					dm.add("id", oldDataSetID);
					dm.add("pid", newStudyID);
					dm.add("fillin", "true");
					String newDataSetID = null;
					if (isDICOM) {
						if (cloneContent) {
							dm.add("content", "true");
						} else {
							dm.add("content", "false");						
						}
						r = executor.execute("om.pssd.dataset.clone", dm.root());
						newDataSetID = r.value("id");

						// Now we need to update the DICOM header because the Patient ID is changing
						// from the Visit-based H number to the FMP generated ID
						if (cloneContent) {
							dm = new XmlDocMaker("args");
							dm.add("cid", newDataSetID);
							// Write FMP subject ID into Patient ID
							dm.push("element", new String[]{"action", "merge", "tag", "00100020"});
							dm.add("value", fmpSubjectID);
							dm.pop();
							// Write the FMP visit ID into the Accession Number
							dm.push("element", new String[]{"action", "merge", "tag", "00080050"});
							dm.add("value", fmpVisitID);
							dm.pop();
							executor.execute("daris.dicom.metadata.set", dm.root());
//							w.add("new-id", new String[]{"status", "created", "content", "copied"}, newDataSetID);
						} else {
//							w.add("new-id", new String[]{"status", "created", "content", "none"}, newDataSetID);
						}

					} else {
						if (cloneContent) {
							// If we clone the raw datset content, we may copy or move the content. 
							// If we move, it's a 2-step process
							if (copyRawContent) {
								dm.add("content", "true");
							} else {
								dm.add("content", "false");
							}
						} else {
							dm.add("content", "false");
						}
						r = executor.execute("om.pssd.dataset.clone", dm.root());		
					    newDataSetID = r.value("id");

						// Now move the content over as needed
						if (cloneContent) {
							if (!copyRawContent) {
								String contentURL = asset.value("asset/content/url");
								String contentType = asset.value("asset/content/type");
								if (contentURL!=null && contentType!=null) {
									setAssetContentUrlAndType (newDataSetID, contentURL, contentType);
									internalizeAssetByMove (newDataSetID);
//									w.add("new-id", new String[]{"status", "created", "content", "moved"}, newDataSetID);
								} else {
//									w.add("new-id", new String[]{"status", "created", "content", "failed"}, newDataSetID);
								}
							} else {
//								w.add("new-id", new String[]{"status", "created", "content", "copied"}, newDataSetID);
							}
						} else {
//							w.add("new-id", new String[]{"status", "created", "content", "none"}, newDataSetID);
						}
					}
					
					// Prune new DataSet
					dm = new XmlDocMaker("args");
					dm.add("id", CiteableIdUtil.cidToId(executor, newDataSetID));
					executor.execute("asset.prune", dm.root());
				}
//				w.pop();			
				
			
			}
		}
	}



	private void setAssetContentUrlAndType(String cid, String contentUrl, String contentType) throws Throwable {

		// asset.set :cid $cid :url -by reference $url
		XmlDocMaker doc = new XmlDocMaker("args");
		doc.add("cid", cid);
		doc.add("url", new String[] { "by", "reference" }, contentUrl);
		doc.add("ctype", contentType);
		executor().execute("asset.set", doc.root());
	}


	private void internalizeAssetByMove (String cid) throws Throwable {

		XmlDocMaker doc = new XmlDocMaker("args");
		doc.add("cid", cid);
		doc.add("method", "move");
		executor().execute("asset.internalize", doc.root());
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
